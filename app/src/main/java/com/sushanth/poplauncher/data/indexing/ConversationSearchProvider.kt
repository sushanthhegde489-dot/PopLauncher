package com.sushanth.poplauncher.data.indexing

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.PhoneNumberUtils
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
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
        val settings = settingsRepository?.settings?.value
        val isConversationEnabled = settings?.enabledProviders?.contains(SearchType.CONVERSATION) ?: true
        if (!isConversationEnabled) return@withContext emptyList()
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()

        val results = mutableListOf<ConversationEntity>()
        val frictionPackages = settings?.frictionAppPackages ?: emptySet()
        val allowedConversationPackages = settings?.conversationAppPackages
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
                            lowerLabel.split(WHITESPACE_REGEX).any { it.startsWith(queryLower) } ||
                            (queryLower.length >= 3 && lowerLabel.contains(queryLower))

                    if (matches) {
                        val appName = PackageCheckCache.getAppLabel(context, pkg)

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

        // 2. Query ContactsContract for Contacts with WhatsApp, Telegram & SMS Chat Actions
        val isWhatsAppActive = !frictionPackages.contains("com.whatsapp") &&
                (allowedConversationPackages == null || allowedConversationPackages.contains("com.whatsapp")) &&
                PackageCheckCache.isInstalled(context, "com.whatsapp")
        val isTelegramActive = !frictionPackages.contains("org.telegram.messenger") &&
                (allowedConversationPackages == null || allowedConversationPackages.contains("org.telegram.messenger")) &&
                PackageCheckCache.isInstalled(context, "org.telegram.messenger")
        val isSmsActive = !frictionPackages.contains(defaultSmsPackage) &&
                (allowedConversationPackages == null || allowedConversationPackages.contains(defaultSmsPackage)) &&
                PackageCheckCache.isInstalled(context, defaultSmsPackage)

        if ((isWhatsAppActive || isTelegramActive || isSmsActive) &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                val projection = arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                )
                val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
                val selectionArgs = arrayOf("%$queryLower%", "%$queryLower%")

                val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                val sortOrder = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
                val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        val args = Bundle().apply {
                            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder)
                            putInt(ContentResolver.QUERY_ARG_LIMIT, 6)
                        }
                        context.contentResolver.query(uri, projection, args, null)
                    } catch (_: Exception) {
                        context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
                    }
                } else {
                    context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
                }
                cursor?.use { cursor ->
                    val nameCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                    while (cursor.moveToNext()) {
                        val name = if (nameCol >= 0) cursor.getString(nameCol) ?: "" else ""
                        val number = if (numCol >= 0) cursor.getString(numCol)?.trim() ?: "" else ""
                        if (name.isBlank() && number.isBlank()) continue

                        val cleanedNum = PhoneNumberUtils.normalizeNumber(number)
                        val displayName = name.ifBlank { number }

                        if (isWhatsAppActive && cleanedNum.length >= 7) {
                            results.add(
                                ConversationEntity(
                                    id = "wa:$cleanedNum",
                                    title = "Chat with $displayName",
                                    subtitle = "WhatsApp • $number",
                                    source = "WhatsApp",
                                    personAssociation = displayName,
                                    appPackage = "com.whatsapp",
                                    launchIntent = Intent(Intent.ACTION_VIEW, "https://api.whatsapp.com/send?phone=$cleanedNum".toUri()).apply {
                                        setPackage("com.whatsapp")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    },
                                    searchableTerms = listOf(displayName, "WhatsApp", cleanedNum)
                                )
                            )
                        }

                        if (isTelegramActive && cleanedNum.length >= 7) {
                            results.add(
                                ConversationEntity(
                                    id = "tg:$cleanedNum",
                                    title = "Telegram with $displayName",
                                    subtitle = "Telegram • $number",
                                    source = "Telegram",
                                    personAssociation = displayName,
                                    appPackage = "org.telegram.messenger",
                                    launchIntent = Intent(Intent.ACTION_VIEW, "tg://msg?to=$cleanedNum".toUri()).apply {
                                        setPackage("org.telegram.messenger")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    },
                                    searchableTerms = listOf(displayName, "Telegram", cleanedNum)
                                )
                            )
                        }

                        if (isSmsActive && cleanedNum.isNotBlank()) {
                            results.add(
                                ConversationEntity(
                                    id = "sms:$cleanedNum",
                                    title = "Message $displayName",
                                    subtitle = "Messages • $number",
                                    source = "Messages",
                                    personAssociation = displayName,
                                    appPackage = defaultSmsPackage,
                                    launchIntent = Intent(Intent.ACTION_SENDTO, "smsto:${Uri.encode(cleanedNum)}".toUri()).apply {
                                        setPackage(defaultSmsPackage)
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

        results.distinctBy { it.id }
    }

    companion object {
        private val WHITESPACE_REGEX = Regex("\\s+")
    }
}
