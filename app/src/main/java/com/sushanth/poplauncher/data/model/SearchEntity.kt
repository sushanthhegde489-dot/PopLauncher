package com.sushanth.poplauncher.data.model

import android.content.ComponentName
import android.content.Intent
import android.os.UserHandle

enum class SearchType {
    APP,
    PERSON,
    CONVERSATION,
    FILE,
    MEDIA,
    GOOGLE_OVERVIEW
}

sealed interface SearchEntity {
    val id: String
    val title: String
    val subtitle: String?
    val source: String
    val iconUri: String?
    val launchIntent: Intent?
    val searchableTerms: List<String>
}

data class AppEntity(
    override val id: String,
    override val title: String,
    override val subtitle: String? = null,
    override val source: String = "App",
    override val iconUri: String? = null,
    val packageName: String,
    val componentName: ComponentName? = null,
    val userHandle: UserHandle? = null,
    val isClone: Boolean = false,
    val isPrivate: Boolean = false,
    override val launchIntent: Intent?,
    val isFrictionApp: Boolean = false,
    override val searchableTerms: List<String> = listOf(title, packageName)
) : SearchEntity

data class PersonEntity(
    override val id: String,
    override val title: String,
    override val subtitle: String? = null,
    override val source: String = "Contact",
    override val iconUri: String? = null,
    val contactId: String,
    val phoneNumbers: List<String> = emptyList(),
    override val launchIntent: Intent?,
    val relatedConversations: List<ConversationEntity> = emptyList(),
    override val searchableTerms: List<String> = emptyList()
) : SearchEntity

data class ConversationEntity(
    override val id: String,
    override val title: String,
    override val subtitle: String? = null,
    override val source: String, // e.g. "WhatsApp", "Messages", "Telegram"
    override val iconUri: String? = null,
    val personAssociation: String? = null,
    val appPackage: String? = null,
    override val launchIntent: Intent?,
    override val searchableTerms: List<String> = emptyList()
) : SearchEntity

data class FileEntity(
    override val id: String,
    override val title: String,
    override val subtitle: String? = null,
    override val source: String = "Files",
    override val iconUri: String? = null,
    val mimeType: String,
    val size: Long,
    override val launchIntent: Intent?,
    override val searchableTerms: List<String> = emptyList()
) : SearchEntity

data class MediaEntity(
    override val id: String,
    override val title: String,
    override val subtitle: String? = null,
    override val source: String = "Photos",
    override val iconUri: String? = null,
    val isVideo: Boolean = false,
    val dateAdded: Long = 0L,
    override val launchIntent: Intent?,
    override val searchableTerms: List<String> = emptyList()
) : SearchEntity

data class DeepSearchEntity(
    override val id: String,
    override val title: String,
    override val subtitle: String? = null,
    override val source: String,
    override val iconUri: String? = null,
    val appPackage: String,
    override val launchIntent: Intent?,
    override val searchableTerms: List<String> = emptyList()
) : SearchEntity

data class WebOverviewEntity(
    override val id: String = "web_overview",
    val query: String,
    override val title: String,
    override val subtitle: String? = null,
    override val source: String = "Web Search",
    override val iconUri: String? = null,
    val browserIntent: Intent,
    override val launchIntent: Intent? = browserIntent,
    val deepSearchOptions: List<DeepSearchEntity> = emptyList(),
    val summaryText: String? = null,
    val isLoading: Boolean = false,
    val isLiveWeb: Boolean = false,
    override val searchableTerms: List<String> = emptyList()
) : SearchEntity

data class AppGroupEntity(
    override val id: String,
    val groupId: String,
    val groupName: String,
    val packageNames: Set<String>,
    val appCount: Int,
    override val title: String = groupName,
    override val subtitle: String? = "$appCount apps • Group",
    override val source: String = "App Group",
    override val iconUri: String? = null,
    override val launchIntent: Intent? = null,
    override val searchableTerms: List<String> = listOf(groupName, "group", "folder", "category")
) : SearchEntity
