package com.sushanth.poplauncher.data.indexing

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.sushanth.poplauncher.data.model.ConversationEntity
import com.sushanth.poplauncher.data.model.PersonEntity
import com.sushanth.poplauncher.data.model.SearchEntity
import com.sushanth.poplauncher.data.model.SearchType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContactSearchProvider : SearchProvider {

    override val type: SearchType = SearchType.PERSON
    override val displayName: String = "People & Contacts"

    private val conversationProviders: List<ConversationProvider> = listOf(
        WhatsAppConversationProvider(),
        WhatsAppBusinessConversationProvider(),
        TelegramConversationProvider(),
        SignalConversationProvider(),
        SystemShortcutConversationProvider()
    )

    override suspend fun isAvailable(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    override suspend fun index(context: Context): List<SearchEntity> = withContext(Dispatchers.IO) {
        if (!isAvailable(context)) return@withContext emptyList()
        val results = mutableListOf<SearchEntity>()
        val uri = ContactsContract.Contacts.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
        )
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                while (cursor.moveToNext() && results.size < 500) {
                    val id = if (idIdx >= 0) cursor.getString(idIdx) ?: "" else ""
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "Contact" else "Contact"
                    if (id.isNotBlank()) {
                        results.add(
                            PersonEntity(
                                id = "person_index:$id",
                                title = name,
                                source = "Contact",
                                contactId = id,
                                launchIntent = null
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}
        results
    }

    private data class RawContact(
        val contactId: String,
        val displayName: String,
        val photoUri: String?,
        val phoneNumbers: MutableList<String> = mutableListOf()
    )

    override suspend fun search(query: String, context: Context): List<SearchEntity> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isEmpty() || !isAvailable(context)) return@withContext emptyList()

            val searchQueries = listOf(trimmed)

            val contactsMap = linkedMapOf<String, RawContact>()
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI
            )
            val sortOrder = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"

            for (q in searchQueries) {
                val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
                val selectionArgs = arrayOf("%$q%", "%$q%")

                try {
                    context.contentResolver.query(
                        uri,
                        projection,
                        selection,
                        selectionArgs,
                        sortOrder
                    )?.use { cursor ->
                        val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                        val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        val photoIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

                        while (cursor.moveToNext() && contactsMap.size < 20) {
                            val contactId = if (idIdx >= 0) cursor.getString(idIdx) ?: "" else ""
                            if (contactId.isBlank()) continue
                            val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "Contact" else "Contact"
                            val number = if (numIdx >= 0) cursor.getString(numIdx)?.trim() ?: "" else ""
                            val photo = if (photoIdx >= 0) cursor.getString(photoIdx) else null

                            val existing = contactsMap.getOrPut(contactId) {
                                RawContact(
                                    contactId = contactId,
                                    displayName = name,
                                    photoUri = photo
                                )
                            }

                            if (number.isNotBlank() && !existing.phoneNumbers.contains(number)) {
                                existing.phoneNumbers.add(number)
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Graceful fallback
                }
            }

            val defaultSmsPackage = try {
                Telephony.Sms.getDefaultSmsPackage(context)?.takeIf { PackageCheckCache.isInstalled(context, it) }
                    ?: context.packageManager.resolveActivity(
                        Intent(Intent.ACTION_SENDTO, "smsto:123".toUri()),
                        PackageManager.MATCH_DEFAULT_ONLY
                    )?.activityInfo?.packageName
            } catch (_: Exception) {
                null
            }

            val results = mutableListOf<PersonEntity>()
            for (raw in contactsMap.values.take(5)) {
                try {
                    val rawNumber = raw.phoneNumbers.firstOrNull() ?: ""
                    val primaryNumber = if (rawNumber.isNotBlank()) {
                        android.telephony.PhoneNumberUtils.normalizeNumber(rawNumber)
                    } else ""
                    val dialIntent = if (primaryNumber.isNotBlank()) {
                        Intent(Intent.ACTION_DIAL).apply {
                            data = "tel:${Uri.encode(primaryNumber)}".toUri()
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }.takeIf { it.resolveActivity(context.packageManager) != null }
                    } else null

                    val relatedConvs = mutableListOf<ConversationEntity>()

                    // Add native SMS conversation
                    if (primaryNumber.isNotBlank()) {
                        val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                            data = "smsto:${Uri.encode(primaryNumber)}".toUri()
                            defaultSmsPackage?.let { setPackage(it) }
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }.takeIf { it.resolveActivity(context.packageManager) != null }

                        if (smsIntent != null) {
                            relatedConvs.add(
                                ConversationEntity(
                                    id = "sms_conv:${raw.contactId}",
                                    title = "Message ${raw.displayName}",
                                    subtitle = "Messages • $primaryNumber",
                                    source = "Messages",
                                    personAssociation = raw.displayName,
                                    appPackage = defaultSmsPackage,
                                    launchIntent = smsIntent
                                )
                            )
                        }
                    }

                    // Query all messaging providers (WhatsApp, WhatsApp Business, Telegram, Signal, Shortcuts)
                    for (cp in conversationProviders) {
                        try {
                            relatedConvs.addAll(cp.getConversationsForPerson(raw.displayName, raw.phoneNumbers, context))
                        } catch (_: Exception) {}
                    }

                    results.add(
                        PersonEntity(
                            id = "person:${raw.contactId}",
                            title = raw.displayName,
                            subtitle = primaryNumber.ifBlank { null },
                            source = "Contact",
                            iconUri = raw.photoUri,
                            contactId = raw.contactId,
                            phoneNumbers = raw.phoneNumbers,
                            launchIntent = dialIntent,
                            relatedConversations = relatedConvs
                                .filter { it.appPackage == null || PackageCheckCache.isInstalled(context, it.appPackage) }
                                .distinctBy { it.id },
                            searchableTerms = listOf(raw.displayName) + raw.phoneNumbers
                        )
                    )
                } catch (_: Exception) {}
            }

            results.distinctBy { it.id }
        }
}