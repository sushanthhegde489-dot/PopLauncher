package com.sushanth.poplauncher.data.indexing

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.CallLog
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

        // 3. Query SMS message content for text match (if READ_SMS permission is granted)
        // BUG FIX: Only show SMS conversations if SMS provider is enabled
        val isSmsEnabled = settingsRepository?.settings?.value?.enabledProviders?.contains(SearchType.SMS) ?: true
        if (isSmsEnabled && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            try {
                val cursor = context.contentResolver.query(
                    Telephony.Sms.CONTENT_URI,
                    arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                    "${Telephony.Sms.BODY} LIKE ? OR ${Telephony.Sms.ADDRESS} LIKE ?",
                    arrayOf("%$trimmed%", "%$trimmed%"),
                    "${Telephony.Sms.DATE} DESC LIMIT 8"
                )
                cursor?.use { c ->
                    val addrCol = c.getColumnIndex(Telephony.Sms.ADDRESS)
                    val bodyCol = c.getColumnIndex(Telephony.Sms.BODY)
                    val dateCol = c.getColumnIndex(Telephony.Sms.DATE)

                    while (c.moveToNext()) {
                        val address = if (addrCol >= 0) c.getString(addrCol) ?: "" else ""
                        val body = if (bodyCol >= 0) c.getString(bodyCol) ?: "" else ""
                        val date = if (dateCol >= 0) c.getLong(dateCol) else 0L

                        if (address.isNotBlank()) {
                            val preview = if (body.length > 55) body.take(52) + "..." else body
                            results.add(
                                ConversationEntity(
                                    id = "sms_msg:${address}_$date",
                                    title = address,
                                    subtitle = preview,
                                    source = "Messages",
                                    appPackage = defaultSmsPackage,
                                    lastMessage = body,
                                    timestamp = date,
                                    launchIntent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:$address")).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    },
                                    searchableTerms = listOf(address, body)
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 4. Query Call Log (if Call Log provider is enabled and READ_CALL_LOG permission is granted)
        val isCallLogEnabled = settingsRepository?.settings?.value?.enabledProviders?.contains(SearchType.CALL_LOG) ?: true
        if (isCallLogEnabled && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALL_LOG) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            try {
                val dialerPackage = (context.getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager)?.defaultDialerPackage
                val cursor = context.contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.DATE),
                    "${CallLog.Calls.CACHED_NAME} LIKE ? OR ${CallLog.Calls.NUMBER} LIKE ?",
                    arrayOf("%$trimmed%", "%$trimmed%"),
                    "${CallLog.Calls.DATE} DESC LIMIT 5"
                )
                cursor?.use { c ->
                    val numCol = c.getColumnIndex(CallLog.Calls.NUMBER)
                    val nameCol = c.getColumnIndex(CallLog.Calls.CACHED_NAME)
                    val dateCol = c.getColumnIndex(CallLog.Calls.DATE)

                    while (c.moveToNext()) {
                        val number = if (numCol >= 0) c.getString(numCol) ?: "" else ""
                        val name = if (nameCol >= 0) c.getString(nameCol) ?: "" else ""
                        val date = if (dateCol >= 0) c.getLong(dateCol) else 0L

                        val display = name.ifBlank { number }
                        if (display.isNotBlank()) {
                            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                                if (number.isNotBlank()) {
                                    data = Uri.parse("tel:${Uri.encode(number)}")
                                }
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            results.add(
                                ConversationEntity(
                                    id = "call_entry:${number}_$date",
                                    title = display,
                                    subtitle = if (name.isNotBlank()) "Call: $number" else "Phone call",
                                    source = "Phone",
                                    appPackage = dialerPackage,
                                    timestamp = date,
                                    launchIntent = dialIntent,
                                    searchableTerms = listOf(display, number)
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
