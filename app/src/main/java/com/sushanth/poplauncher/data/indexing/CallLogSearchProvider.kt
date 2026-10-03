package com.sushanth.poplauncher.data.indexing

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import androidx.core.content.ContextCompat
import com.sushanth.poplauncher.data.model.SearchEntity
import com.sushanth.poplauncher.data.model.SearchType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CallLogSearchProvider : SearchProvider {

    override val type: SearchType = SearchType.CALL_LOG
    override val displayName: String = "Call History"

    override suspend fun isAvailable(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
    }

    override suspend fun index(context: Context): List<SearchEntity> {
        return emptyList()
    }

    override suspend fun search(query: String, context: Context): List<SearchEntity> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isEmpty() || !isAvailable(context)) return@withContext emptyList()

            val results = mutableListOf<SearchEntity>()
            val uri = CallLog.Calls.CONTENT_URI
            val projection = arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.NUMBER,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE
            )

            val selection = "${CallLog.Calls.CACHED_NAME} LIKE ? OR ${CallLog.Calls.NUMBER} LIKE ?"
            val selectionArgs = arrayOf("%$trimmed%", "%$trimmed%")
            val sortOrder = "${CallLog.Calls.DATE} DESC LIMIT 5"

            try {
                context.contentResolver.query(
                    uri,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(CallLog.Calls._ID)
                    val nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                    val numIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                    val typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE)

                    while (cursor.moveToNext() && results.size < 5) {
                        val id = if (idIdx >= 0) cursor.getString(idIdx) else ""
                        val name = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                        val number = if (numIdx >= 0) cursor.getString(numIdx) ?: "" else ""
                        val callType = if (typeIdx >= 0) cursor.getInt(typeIdx) else CallLog.Calls.INCOMING_TYPE

                        val typeDesc = when (callType) {
                            CallLog.Calls.INCOMING_TYPE -> "Incoming call"
                            CallLog.Calls.OUTGOING_TYPE -> "Outgoing call"
                            CallLog.Calls.MISSED_TYPE -> "Missed call"
                            else -> "Call"
                        }

                        val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                            if (number.isNotBlank()) {
                                data = Uri.parse("tel:${Uri.encode(number)}")
                            }
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }

                        val title = name ?: number
                        val subtitle = if (name != null) "$typeDesc • $number" else typeDesc

                        val entityId = if (!id.isNullOrBlank()) "call:$id" else "call:${results.size}_$number"

                        results.add(
                            com.sushanth.poplauncher.data.model.CallEntity(
                                id = entityId,
                                title = title,
                                subtitle = subtitle,
                                source = "Phone",
                                number = number,
                                callType = callType,
                                launchIntent = dialIntent,
                                searchableTerms = listOfNotNull(name, number)
                            )
                        )
                    }
                }
            } catch (_: Exception) {
                // Graceful fallback
            }

            results.distinctBy { it.id }
        }
}
