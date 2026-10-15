package com.sushanth.poplauncher.data.indexing

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.PhoneNumberUtils
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.sushanth.poplauncher.data.model.ConversationEntity
import com.sushanth.poplauncher.data.model.PersonEntity
import com.sushanth.poplauncher.data.model.SearchEntity
import com.sushanth.poplauncher.data.model.SearchType
import com.sushanth.poplauncher.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContactSearchProvider(
    private val settingsRepository: SettingsRepository? = null
) : SearchProvider {

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

    override suspend fun index(context: Context): List<SearchEntity> = emptyList()

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

            val contactsMap = linkedMapOf<String, RawContact>()
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI
            )
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
            val selectionArgs = arrayOf("%$trimmed%", "%$trimmed%")
            val sortOrder = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"

            try {
                val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        val queryArgs = Bundle().apply {
                            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder)
                            putInt(ContentResolver.QUERY_ARG_LIMIT, 20)
                        }
                        context.contentResolver.query(uri, projection, queryArgs, null)
                    } catch (_: Exception) {
                        context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
                    }
                } else {
                    context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
                }
                cursor?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                    val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val photoIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

                    while (cursor.moveToNext()) {
                        val contactId = if (idIdx >= 0) cursor.getString(idIdx) ?: "" else ""
                        if (contactId.isBlank()) continue
                        if (!contactsMap.containsKey(contactId) && contactsMap.size >= MAX_CONTACT_RESULTS) {
                            continue
                        }
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

            if (contactsMap.isEmpty()) return@withContext emptyList()

            val settings = settingsRepository?.settings?.value
            val frictionPackages = settings?.frictionAppPackages ?: emptySet()
            val allowedConvPackages = settings?.conversationAppPackages

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
            for (raw in contactsMap.values) {
                try {
                    val rawNumber = raw.phoneNumbers.firstOrNull() ?: ""
                    val primaryNumber = if (rawNumber.isNotBlank()) {
                        PhoneNumberUtils.normalizeNumber(rawNumber)
                    } else ""
                    val dialIntent = if (primaryNumber.isNotBlank()) {
                        Intent(Intent.ACTION_DIAL).apply {
                            data = "tel:${Uri.encode(primaryNumber)}".toUri()
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }.takeIf { it.resolveActivity(context.packageManager) != null }
                    } else null

                    val relatedConvs = mutableListOf<ConversationEntity>()

                    // Add native SMS conversation
                    if (primaryNumber.isNotBlank() &&
                        (defaultSmsPackage == null || (!frictionPackages.contains(defaultSmsPackage) && (allowedConvPackages == null || allowedConvPackages.contains(defaultSmsPackage))))
                    ) {
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
                                .filter { conv ->
                                    val pkg = conv.appPackage
                                    pkg == null || (
                                        !frictionPackages.contains(pkg) &&
                                        (allowedConvPackages == null || allowedConvPackages.contains(pkg)) &&
                                        PackageCheckCache.isInstalled(context, pkg)
                                    )
                                }
                                .distinctBy { it.id },
                            searchableTerms = listOf(raw.displayName) + raw.phoneNumbers
                        )
                    )
                } catch (_: Exception) {}
            }

            results.distinctBy { it.id }
        }

    companion object {
        private const val MAX_CONTACT_RESULTS = 5
    }
}