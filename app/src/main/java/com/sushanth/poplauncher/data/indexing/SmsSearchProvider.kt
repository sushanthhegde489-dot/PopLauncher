package com.sushanth.poplauncher.data.indexing

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.sushanth.poplauncher.data.model.MessageEntity
import com.sushanth.poplauncher.data.model.SearchEntity
import com.sushanth.poplauncher.data.model.SearchType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SmsSearchProvider : SearchProvider {

    override val type: SearchType = SearchType.SMS
    override val displayName: String = "Messages"

    override suspend fun isAvailable(context: Context): Boolean {
        // Now using intent fallback, so always available
        return true
    }

    override suspend fun index(context: Context): List<SearchEntity> {
        return emptyList()
    }

    override suspend fun search(query: String, context: Context): List<SearchEntity> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return@withContext emptyList()

            // Rather than requiring READ_SMS permission, we launch the SMS app's search intent directly.
            val defaultSmsPackage = try {
                Telephony.Sms.getDefaultSmsPackage(context)
            } catch (_: Exception) { null }

            val searchIntent = Intent(Intent.ACTION_SEARCH).apply {
                if (defaultSmsPackage != null) {
                    setPackage(defaultSmsPackage)
                }
                putExtra("query", trimmed)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val targetIntent = if (searchIntent.resolveActivity(context.packageManager) != null) {
                searchIntent
            } else {
                defaultSmsPackage?.let { context.packageManager.getLaunchIntentForPackage(it) }
                    ?: Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_APP_MESSAGING)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
            }

            listOf(
                MessageEntity(
                    id = "sms_search",
                    title = "Search Messages",
                    subtitle = "Search SMS for \"$trimmed\"",
                    source = "Messages",
                    address = "",
                    body = trimmed,
                    launchIntent = targetIntent,
                    searchableTerms = listOf(trimmed)
                )
            )
        }
}
