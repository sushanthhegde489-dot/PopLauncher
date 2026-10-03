package com.sushanth.poplauncher.data.indexing

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.ContactsContract
import android.provider.Telephony
import com.sushanth.poplauncher.data.model.ConversationEntity
import com.sushanth.poplauncher.data.model.SearchEntity
import com.sushanth.poplauncher.data.model.SearchType
import com.sushanth.poplauncher.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ConversationSearchProvider(
    private val settingsRepository: SettingsRepository? = null
) : SearchProvider {

    override val type: SearchType = SearchType.CONVERSATION
    override val displayName: String = "Conversations"

    override suspend fun isAvailable(context: Context): Boolean = true

    override suspend fun index(context: Context): List<SearchEntity> = emptyList()

    override suspend fun search(query: String, context: Context): List<SearchEntity> = withContext(Dispatchers.IO) {
        // BUG FIX: Check if CONVERSATION provider is enabled before returning results
        val isConversationEnabled = settingsRepository?.settings?.value?.enabledProviders?.contains(SearchType.CONVERSATION) ?: true
        if (!isConversationEnabled) return@withContext emptyList()
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()

        val results = mutableListOf<ConversationEntity>()
        val frictionPackages = settingsRepository?.settings?.value?.frictionAppPackages ?: emptySet()
        val allowedConversationPackages = settingsRepository?.settings?.value?.conversationAppPackages
        val queryLower = trimmed.lowercase()

        val defaultSmsPackage = try {
            Telephony.Sms.getDefaultSmsPackage(context) ?: "com.google.android.apps.messaging"
        } catch (_: Exception) {
            "com.google.android.apps.messaging"
        }


        // 1. Query LauncherApps Conversation Shortcuts
        try {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
            if (launcherApps != null && launcherApps.hasShortcutHostPermission()) {
                val q = LauncherApps.ShortcutQuery().apply {
                    setQueryFlags(
                        LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                    )
                }
                val shortcuts = launcherApps.getShortcuts(q, Process.myUserHandle()) ?: emptyList()
                for (s in shortcuts) {
                    val pkg = s.`package`
                    // Never leak conversations for friction apps (e.g. Instagram)
                    if (frictionPackages.contains(pkg)) continue
                    if (allowedConversationPackages != null && !allowedConversationPackages.contains(pkg)) continue

                    val label = s.shortLabel?.toString() ?: s.longLabel?.toString() ?: ""
                    if (label.isBlank()) continue
                    val lowerLabel = label.lowercase()

                    // Only match if contact name or conversation label matches the query
                    // NEVER match by package name (prevents searching "insta" from returning Instagram chats)
                    val matches = lowerLabel.startsWith(queryLower) ||
                            lowerLabel.split("\\s+".toRegex()).any { it.startsWith(queryLower) } ||
                            (queryLower.length >= 3 && lowerLabel.contains(queryLower))

                    if (matches) {
                        val appName = try {
                            val applicationInfo = context.packageManager.getApplicationInfo(pkg, 0)
                            context.packageManager.getApplicationLabel(applicationInfo).toString()
                        } catch (_: Exception) {
                            pkg
                        }

                        // Explicit intent targeting the app package to ensure zero disambiguation / chooser popups
                        val directAppIntent = (context.packageManager.getLaunchIntentForPackage(pkg) ?: Intent(Intent.ACTION_MAIN).apply {
                            setPackage(pkg)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }).apply {
                            putExtra("shortcut_package", pkg)
                            putExtra("shortcut_id", s.id)
                        }

                        results.add(
                            ConversationEntity(
                                id = "shortcut:${pkg}:${s.id}",
                                title = label,
                                subtitle = "Direct chat on $appName",
                                source = appName,
                                personAssociation = label,
                                appPackage = pkg,
                                launchIntent = directAppIntent,
                                searchableTerms = listOf(label, appName)
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Query ContactsContract for Contacts with WhatsApp, Telegram, Signal & SMS Chat Actions
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            try {
                val projection = arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                )
                val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
                val selectionArgs = arrayOf("%$queryLower%", "%$queryLower%")

                context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC LIMIT 6"
                )?.use { cursor ->
                    val nameCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                    val isWhatsAppInstalled = PackageCheckCache.isInstalled(context, "com.whatsapp")
                    val isTelegramInstalled = PackageCheckCache.isInstalled(context, "org.telegram.messenger")
                    val isSmsInstalled = PackageCheckCache.isInstalled(context, defaultSmsPackage)
                    val isWhatsAppAllowed = allowedConversationPackages == null || allowedConversationPackages.contains("com.whatsapp")
                    val isTelegramAllowed = allowedConversationPackages == null || allowedConversationPackages.contains("org.telegram.messenger")
                    val isSmsAllowed = allowedConversationPackages == null || allowedConversationPackages.contains(defaultSmsPackage)

                    while (cursor.moveToNext()) {
                        val name = if (nameCol >= 0) cursor.getString(nameCol) ?: "" else ""
                        val number = if (numCol >= 0) cursor.getString(numCol)?.trim() ?: "" else ""
                        if (name.isBlank() && number.isBlank()) continue

                        val cleanedNum = number.replace("[^0-9+]".toRegex(), "")
                        val displayName = name.ifBlank { number }

                        // Direct WhatsApp chat (only if WhatsApp is installed and allowed)
                        if (isWhatsAppInstalled && isWhatsAppAllowed && cleanedNum.length >= 7) {
                            results.add(
                                ConversationEntity(
                                    id = "wa:$cleanedNum",
                                    title = "Chat with $displayName",
                                    subtitle = "WhatsApp • $number",
                                    source = "WhatsApp",
                                    personAssociation = displayName,
                                    appPackage = "com.whatsapp",
                                    launchIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$cleanedNum")).apply {
                                        setPackage("com.whatsapp")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    },
                                    searchableTerms = listOf(displayName, "WhatsApp", cleanedNum)
                                )
                            )
                        }

                        // Direct Telegram chat (only if Telegram is installed and allowed)
                        if (isTelegramInstalled && isTelegramAllowed && cleanedNum.length >= 7) {
                            results.add(
                                ConversationEntity(
                                    id = "tg:$cleanedNum",
                                    title = "Telegram with $displayName",
                                    subtitle = "Telegram • $number",
                                    source = "Telegram",
                                    personAssociation = displayName,
                                    appPackage = "org.telegram.messenger",
                                    launchIntent = Intent(Intent.ACTION_VIEW, Uri.parse("tg://msg?to=$cleanedNum")).apply {
                                        setPackage("org.telegram.messenger")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    },
                                    searchableTerms = listOf(displayName, "Telegram", cleanedNum)
                                )
                            )
                        }

                        // Direct SMS message (only if SMS app is installed and allowed)
                        if (isSmsInstalled && isSmsAllowed && number.isNotBlank()) {
                            results.add(
                                ConversationEntity(
                                    id = "sms:$cleanedNum",
                                    title = "Message $displayName",
                                    subtitle = "Messages • $number",
                                    source = "Messages",
                                    personAssociation = displayName,
                                    appPackage = defaultSmsPackage,
                                    launchIntent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:$number")).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    },
                                    searchableTerms = listOf(displayName, "Messages", number)
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {}
        }




        results
            .filter { it.appPackage == null || !frictionPackages.contains(it.appPackage) }
            .distinctBy { it.id }
    }
}
