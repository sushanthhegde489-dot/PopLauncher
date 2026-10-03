package com.sushanth.poplauncher.data.indexing

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.net.Uri
import android.os.Process
import com.sushanth.poplauncher.data.model.ConversationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

interface ConversationProvider {
    val sourceName: String
    suspend fun getConversationsForPerson(
        name: String,
        phoneNumbers: List<String>,
        context: Context
    ): List<ConversationEntity>
    suspend fun searchConversations(query: String, context: Context): List<ConversationEntity>
}

/**
 * Discovers messaging shortcuts and conversation links exposed by Android launcher APIs.
 */
class SystemShortcutConversationProvider : ConversationProvider {
    override val sourceName: String = "Direct Shortcuts"

    @Volatile
    private var cachedShortcuts: List<ShortcutInfo>? = null
    @Volatile
    private var lastShortcutFetchTime: Long = 0L

    private fun getShortcutsFast(launcherApps: LauncherApps): List<ShortcutInfo> {
        val now = System.currentTimeMillis()
        val current = cachedShortcuts
        if (current != null && (now - lastShortcutFetchTime) < 3000L) {
            return current
        }
        val query = LauncherApps.ShortcutQuery().apply {
            setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
            )
        }
        val fresh = try {
            launcherApps.getShortcuts(query, Process.myUserHandle()) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        cachedShortcuts = fresh
        lastShortcutFetchTime = now
        return fresh
    }

    override suspend fun getConversationsForPerson(
        name: String,
        phoneNumbers: List<String>,
        context: Context
    ): List<ConversationEntity> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ConversationEntity>()

        try {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps ?: return@withContext results
            if (!launcherApps.hasShortcutHostPermission()) return@withContext results

            val shortcuts = getShortcutsFast(launcherApps)
            val lowerName = name.trim().lowercase()

            for (shortcut in shortcuts) {
                val label = shortcut.shortLabel?.toString() ?: shortcut.longLabel?.toString() ?: ""
                val lowerLabel = label.lowercase()
                val isMatch = lowerName.isNotEmpty() && lowerLabel.contains(lowerName)

                if (isMatch) {
                    val appName = try {
                        context.packageManager.getApplicationLabel(
                            context.packageManager.getApplicationInfo(shortcut.`package`, 0)
                        ).toString()
                    } catch (_: Exception) {
                        shortcut.`package`
                    }

                    results.add(
                        ConversationEntity(
                            id = "shortcut:${shortcut.`package`}:${shortcut.id}",
                            title = label,
                            subtitle = "Conversation on $appName",
                            source = appName,
                            personAssociation = name,
                            appPackage = shortcut.`package`,
                            launchIntent = Intent(Intent.ACTION_MAIN).apply {
                                setPackage(shortcut.`package`)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                putExtra("shortcut_id", shortcut.id)
                                putExtra("shortcut_package", shortcut.`package`)
                            }
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Graceful fallback if permission or manufacturer restriction occurs
        }
        results.distinctBy { it.id }
    }

    override suspend fun searchConversations(query: String, context: Context): List<ConversationEntity> =
        getConversationsForPerson(query, emptyList(), context)
}

/**
 * Helper to cache package installation checks
 */
internal object PackageCheckCache {
    private val cache = ConcurrentHashMap<String, Pair<Boolean, Long>>()

    fun isInstalled(context: Context, packageName: String): Boolean {
        val now = System.currentTimeMillis()
        val cached = cache[packageName]
        if (cached != null && (now - cached.second) < 10000L) {
            return cached.first
        }
        val installed = try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
        cache[packageName] = Pair(installed, now)
        return installed
    }
}

/**
 * WhatsApp conversation launching via direct phone deep links
 */
class WhatsAppConversationProvider : ConversationProvider {
    override val sourceName: String = "WhatsApp"

    override suspend fun getConversationsForPerson(
        name: String,
        phoneNumbers: List<String>,
        context: Context
    ): List<ConversationEntity> = withContext(Dispatchers.IO) {
        val isInstalled = PackageCheckCache.isInstalled(context, "com.whatsapp")

        if (!isInstalled || phoneNumbers.isEmpty()) return@withContext emptyList()

        phoneNumbers.mapNotNull { rawNumber ->
            val cleaned = rawNumber.replace("[^0-9+]".toRegex(), "")
            if (cleaned.length < 5) return@mapNotNull null

            val uri = Uri.parse("https://api.whatsapp.com/send?phone=$cleaned")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            ConversationEntity(
                id = "whatsapp:$cleaned",
                title = "Chat with $name",
                subtitle = "WhatsApp • $cleaned",
                source = "WhatsApp",
                personAssociation = name,
                appPackage = "com.whatsapp",
                launchIntent = intent,
                searchableTerms = listOf(name, "WhatsApp", cleaned)
            )
        }
    }

    override suspend fun searchConversations(query: String, context: Context): List<ConversationEntity> = emptyList()
}

/**
 * WhatsApp Business conversation launching
 */
class WhatsAppBusinessConversationProvider : ConversationProvider {
    override val sourceName: String = "WhatsApp Business"

    override suspend fun getConversationsForPerson(
        name: String,
        phoneNumbers: List<String>,
        context: Context
    ): List<ConversationEntity> = withContext(Dispatchers.IO) {
        val isInstalled = PackageCheckCache.isInstalled(context, "com.whatsapp.w4b")

        if (!isInstalled || phoneNumbers.isEmpty()) return@withContext emptyList()

        phoneNumbers.mapNotNull { rawNumber ->
            val cleaned = rawNumber.replace("[^0-9+]".toRegex(), "")
            if (cleaned.length < 5) return@mapNotNull null

            val uri = Uri.parse("https://api.whatsapp.com/send?phone=$cleaned")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.whatsapp.w4b")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            ConversationEntity(
                id = "whatsapp_biz:$cleaned",
                title = "Business Chat with $name",
                subtitle = "WhatsApp Business • $cleaned",
                source = "WhatsApp Business",
                personAssociation = name,
                appPackage = "com.whatsapp.w4b",
                launchIntent = intent,
                searchableTerms = listOf(name, "WhatsApp Business", cleaned)
            )
        }
    }

    override suspend fun searchConversations(query: String, context: Context): List<ConversationEntity> = emptyList()
}

/**
 * Telegram conversation launching via tg:// deep link
 */
class TelegramConversationProvider : ConversationProvider {
    override val sourceName: String = "Telegram"

    override suspend fun getConversationsForPerson(
        name: String,
        phoneNumbers: List<String>,
        context: Context
    ): List<ConversationEntity> = withContext(Dispatchers.IO) {
        val isInstalled = PackageCheckCache.isInstalled(context, "org.telegram.messenger")

        if (!isInstalled) return@withContext emptyList()

        val results = mutableListOf<ConversationEntity>()

        phoneNumbers.forEach { rawNumber ->
            val cleaned = rawNumber.replace("[^0-9+]".toRegex(), "")
            if (cleaned.length >= 6) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("tg://msg?to=$cleaned")).apply {
                    setPackage("org.telegram.messenger")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                results.add(
                    ConversationEntity(
                        id = "telegram_num:$cleaned",
                        title = "Telegram Chat with $name",
                        subtitle = "Telegram • $cleaned",
                        source = "Telegram",
                        personAssociation = name,
                        appPackage = "org.telegram.messenger",
                        launchIntent = intent,
                        searchableTerms = listOf(name, "Telegram", cleaned)
                    )
                )
            }
        }

        results.distinctBy { it.id }
    }

    override suspend fun searchConversations(query: String, context: Context): List<ConversationEntity> = emptyList()
}

/**
 * Signal Private Messenger conversation launching
 */
class SignalConversationProvider : ConversationProvider {
    override val sourceName: String = "Signal"

    override suspend fun getConversationsForPerson(
        name: String,
        phoneNumbers: List<String>,
        context: Context
    ): List<ConversationEntity> = withContext(Dispatchers.IO) {
        val isInstalled = PackageCheckCache.isInstalled(context, "org.thoughtcrime.securesms")

        if (!isInstalled || phoneNumbers.isEmpty()) return@withContext emptyList()

        phoneNumbers.mapNotNull { rawNumber ->
            val cleaned = rawNumber.replace("[^0-9+]".toRegex(), "")
            if (cleaned.length < 5) return@mapNotNull null

            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$cleaned")).apply {
                setPackage("org.thoughtcrime.securesms")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            ConversationEntity(
                id = "signal:$cleaned",
                title = "Signal Chat with $name",
                subtitle = "Signal • $cleaned",
                source = "Signal",
                personAssociation = name,
                appPackage = "org.thoughtcrime.securesms",
                launchIntent = intent,
                searchableTerms = listOf(name, "Signal", cleaned)
            )
        }
    }

    override suspend fun searchConversations(query: String, context: Context): List<ConversationEntity> = emptyList()
}
