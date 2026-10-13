package com.sushanth.poplauncher.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import com.sushanth.poplauncher.data.indexing.ContactSearchProvider
import com.sushanth.poplauncher.data.indexing.ConversationSearchProvider
import com.sushanth.poplauncher.data.indexing.DeepAppSearchProvider
import com.sushanth.poplauncher.data.indexing.FileSearchProvider
import com.sushanth.poplauncher.data.indexing.MediaSearchProvider
import com.sushanth.poplauncher.data.indexing.SearchProvider
import com.sushanth.poplauncher.data.indexing.SettingsShortcutSearchProvider
import com.sushanth.poplauncher.data.indexing.WebOverviewProvider
import com.sushanth.poplauncher.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.concurrent.atomic.AtomicLong


data class ProviderDiagnostic(
    val providerType: SearchType,
    val isAvailable: Boolean,
    val errorMessage: String? = null,
    val resultCount: Int = 0,
)

data class SearchResultState(
    val query: String = "",
    val activeBubbleApp: AppEntity? = null,
    val standardResults: List<SearchEntity> = emptyList(),
    val webOverview: WebOverviewEntity? = null,
    val isSearching: Boolean = false,
    val providerDiagnostics: List<ProviderDiagnostic> = emptyList(),
    val providerErrors: List<String> = emptyList(),
)

data class IndexingStatusState(
    val isIndexing: Boolean = false,
    val progress: Float = 1.0f,
    val totalIndexedItems: Int = 0,
    val countsCategoryMap: Map<String, Int> = emptyMap(),
    val lastIndexTimestamp: Long = System.currentTimeMillis()
)

class SearchRepository(
    private val context: Context,
    private val appRepository: AppRepository,
    private val settingsRepository: SettingsRepository,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    val searchIndexRepository: SearchIndexRepository = SearchIndexRepository(context, coroutineScope)
) {
    private val settingsShortcutProvider = SettingsShortcutSearchProvider()

    private val nonAppProviders: List<SearchProvider> = listOf(
        settingsShortcutProvider,
        ConversationSearchProvider(settingsRepository),
        ContactSearchProvider(),
        FileSearchProvider(),
        MediaSearchProvider(settingsRepository)
    )

    private val _searchState = MutableStateFlow(SearchResultState())
    val searchState: StateFlow<SearchResultState> = _searchState.asStateFlow()

    private val _indexingStatus = MutableStateFlow(IndexingStatusState())
    val indexingStatus: StateFlow<IndexingStatusState> = _indexingStatus.asStateFlow()

    private var currentOverviewJob: Job? = null
    private val searchGeneration = AtomicLong(0L)

    init {
        coroutineScope.launch {
            refreshFullIndex()
        }
        // Provider switches must take effect immediately for the current query.
        // Previously the setting changed visually, but existing results stayed on screen
        // until the user edited the query again.
        coroutineScope.launch {
            settingsRepository.settings
                .distinctUntilChanged { old, new ->
                    (old.enabledProviders == new.enabledProviders) && (old.caseSensitive == new.caseSensitive)
                }
                .collect {
                    val currentQuery = _searchState.value.query
                    if (currentQuery.isNotBlank()) {
                        search(currentQuery)
                    }
                }
        }
    }

    fun triggerReindex() {
        coroutineScope.launch {
            refreshFullIndex()
        }
    }

    /** Re-runs the visible query, e.g. after a permission was granted in system settings. */
    fun refreshCurrentQuery() {
        val currentQuery = _searchState.value.query
        if (currentQuery.isBlank()) return
        coroutineScope.launch { search(currentQuery) }
    }

    /**
     * Called to throttle reindexing to at most once every 6 hours.
     * The timestamp lives here so it survives Activity recreation on
     * configuration changes and back-stack navigation.
     */
    @Volatile private var lastIndexTimestamp: Long = 0L
    private val reindexIntervalMs: Long = 6 * 60 * 60 * 1_000L // 6 hours

    fun triggerReindexIfDue() {
        val now = System.currentTimeMillis()
        if (now - lastIndexTimestamp >= reindexIntervalMs) {
            lastIndexTimestamp = now
            triggerReindex()
        }
    }

    suspend fun refreshFullIndex() = withContext(Dispatchers.IO) {
        _indexingStatus.value = _indexingStatus.value.copy(isIndexing = true, progress = 0.05f)
        try {
            val categoryCounts = mutableMapOf<String, Int>()

            // 1. Installed Apps & System Settings
            _indexingStatus.value = _indexingStatus.value.copy(progress = 0.25f)
            val installedAppsCount = appRepository.installedApps.value.size
            val settingsShortcutsCount = settingsShortcutProvider.index(context).size
            categoryCounts["Apps & Settings"] = installedAppsCount + settingsShortcutsCount

            // 2. Contacts & People
            _indexingStatus.value = _indexingStatus.value.copy(progress = 0.50f)
            val contactProvider = nonAppProviders.firstOrNull { it is ContactSearchProvider }
            val contactsCount = contactProvider?.index(context)?.size ?: 0
            categoryCounts["Contacts & People"] = contactsCount

            // 3. Local Files & Documents
            _indexingStatus.value = _indexingStatus.value.copy(progress = 0.75f)
            val fileProvider = nonAppProviders.firstOrNull { it is FileSearchProvider }
            val filesCount = fileProvider?.index(context)?.size ?: 0
            categoryCounts["Files & Documents"] = filesCount

            // 4. Photos & Media
            _indexingStatus.value = _indexingStatus.value.copy(progress = 0.95f)
            val mediaProvider = nonAppProviders.firstOrNull { it is MediaSearchProvider }
            val mediaCount = mediaProvider?.index(context)?.size ?: 0
            categoryCounts["Photos & Videos"] = mediaCount

            val totalCount = categoryCounts.values.sum()
            _indexingStatus.value = IndexingStatusState(
                isIndexing = false,
                progress = 1.0f,
                totalIndexedItems = totalCount,
                countsCategoryMap = categoryCounts,
                lastIndexTimestamp = System.currentTimeMillis()
            )
        } catch (_: Exception) {
            _indexingStatus.value = _indexingStatus.value.copy(isIndexing = false, progress = 1.0f)
        }
    }

    suspend fun search(rawQuery: String) = withContext(Dispatchers.IO) {
        val query = rawQuery.trim()
        val generation = searchGeneration.incrementAndGet()
        if (query.isEmpty()) {
            currentOverviewJob?.cancel()
            _searchState.value = SearchResultState()
            return@withContext
        }

        _searchState.value = _searchState.value.copy(isSearching = true, query = query)

        val settings = settingsRepository.settings.value
        val frictionPackages = settings.frictionAppPackages
        val caseSensitive = settings.caseSensitive

        // 1. Check for exact friction app match
        val frictionExactMatch: AppEntity? = try {
            appRepository.findFrictionExactMatch(
                query = query,
                frictionPackages = frictionPackages,
                caseSensitive = caseSensitive
            )
        } catch (_: Throwable) {
            null
        }

        if (generation != searchGeneration.get()) return@withContext

        // 2 & 3. Search standard apps and other enabled providers in parallel
        val providerDiagnostics = Collections.synchronizedList(mutableListOf<ProviderDiagnostic>())

        val affinityScores = searchIndexRepository.getAffinityScores(query)
        val (appResultsPair, otherResults) = coroutineScope {
            val appSearchDeferred = async {
                val standardApps = if (settings.enabledProviders.contains(SearchType.APP)) {
                    try {
                        appRepository.searchStandardApps(
                            query = query,
                            frictionPackages = frictionPackages,
                            hiddenPackages = settings.hiddenAppPackages,
                            caseSensitive = caseSensitive
                        )
                    } catch (_: Throwable) {
                        emptyList()
                    }
                } else {
                    emptyList()
                }

                val matchingGroups = if (settings.enabledProviders.contains(SearchType.APP)) {
                    val q = if (caseSensitive) query.trim() else query.trim().lowercase()
                    settings.appGroups.asSequence().filter { group ->
                        val gName = if (caseSensitive) group.name else group.name.lowercase()
                        gName.contains(q) || (q.length >= 3 && ("group".startsWith(q) || "folder".startsWith(q) || "category".startsWith(q)))
                    }.map { group ->
                        AppGroupEntity(
                            id = "group:${group.id}",
                            groupId = group.id,
                            groupName = group.name,
                            packageNames = group.packageNames,
                            appCount = group.packageNames.size
                        )
                    }.toList()
                } else {
                    emptyList()
                }

                matchingGroups + standardApps
            }

            val nonAppSearchDeferred = async {
                nonAppProviders
                    .filter { settings.enabledProviders.contains(it.type) }
                    .map { provider ->
                        async {
                            val available = try {
                                provider.isAvailable(context)
                            } catch (t: Throwable) {
                                Log.w("PopLauncher", "Error checking availability for ${provider.type}: ${t.message}", t)
                                false
                            }
                            if (!available) {
                                return@async Pair(
                                    ProviderDiagnostic(
                                        providerType = provider.type,
                                        isAvailable = false,
                                        errorMessage = if (provider.type == SearchType.FILE) {
                                            "Allow file access to search documents, PDFs and downloads"
                                        } else {
                                            "${provider.displayName} permission required"
                                        }
                                    ),
                                    emptyList()
                                )
                            }
                            try {
                                val results = provider.search(query, context)
                                Pair(
                                    ProviderDiagnostic(
                                        providerType = provider.type,
                                        isAvailable = true,
                                        resultCount = results.size
                                    ),
                                    results
                                )
                            } catch (t: Throwable) {
                                Log.e("PopLauncher", "Error executing search for ${provider.type}: ${t.message}", t)
                                Pair(
                                    ProviderDiagnostic(
                                        providerType = provider.type,
                                        isAvailable = true,
                                        errorMessage = "Search failed: ${t.localizedMessage ?: "Unknown error"}"
                                    ),
                                    emptyList<SearchEntity>()
                                )
                            }
                        }
                    }
                    .flatMap { deferred ->
                        try {
                            val (diag, res) = deferred.await()
                            providerDiagnostics.add(diag)
                            res
                        } catch (t: Throwable) {
                            Log.e("PopLauncher", "Search deferred failed: ${t.message}", t)
                            emptyList<SearchEntity>()
                        }
                    }
            }

            val appResults = appSearchDeferred.await()
            val allAppCandidates = if (frictionExactMatch != null) {
                listOf(frictionExactMatch) + appResults
            } else {
                appResults
            }
            if (generation == searchGeneration.get() && allAppCandidates.isNotEmpty()) {
                val appCandidates = allAppCandidates.filter { entity ->
                    when (entity) {
                        is AppEntity -> appRepository.isPackageInstalled(entity.packageName)
                        else -> true
                    }
                }.distinctBy { it.id }.filter { it.title.isNotBlank() }
                
                val sortedApps = appCandidates.sortedWith(
                    compareBy<SearchEntity> { getPriorityOrder(it) }
                        .thenByDescending { affinityScores[it.id] ?: 0 }
                )
                _searchState.value = _searchState.value.copy(
                    standardResults = sortedApps,
                    isSearching = true
                )
            }

            Pair(appResults, nonAppSearchDeferred.await())
        }

        if (generation != searchGeneration.get()) return@withContext

        // Fast in-memory package verification without Binder IPC calls
        fun isPackageInstalled(pkg: String?): Boolean {
            return appRepository.isPackageInstalled(pkg)
        }

        val filteredCandidateResults = (appResultsPair + otherResults)
            .filter { entity ->
                // Guarantee: Never give search results for apps that do not exist on the user's phone
                when (entity) {
                    is AppEntity -> isPackageInstalled(entity.packageName)
                    is ConversationEntity -> entity.appPackage == null || isPackageInstalled(entity.appPackage)
                    is DeepSearchEntity -> entity.id.startsWith("system:") || isPackageInstalled(entity.appPackage)
                    is AppGroupEntity -> true
                    else -> true
                }
            }
            .distinctBy { it.id }
            .filter { it.title.isNotBlank() }

        val combinedCandidates = if (frictionExactMatch != null) {
            listOf(frictionExactMatch) + filteredCandidateResults
        } else {
            filteredCandidateResults
        }

        val combinedStandardResults = combinedCandidates
            .distinctBy { it.id }
            .sortedWith(
                compareBy<SearchEntity> { getPriorityOrder(it) }
                    .thenByDescending { entity -> affinityScores[entity.id] ?: 0 }
            )

        // 4. Web Overview Fallback + Deep App Search when ZERO local results found and GOOGLE_OVERVIEW is enabled
        val shouldShowWebOverview = SearchType.GOOGLE_OVERVIEW in settings.enabledProviders &&
                combinedStandardResults.isEmpty() && frictionExactMatch == null && query.isNotEmpty()
        val webOverview = if (shouldShowWebOverview) {
            val searchUrl = "https://www.google.com/search?q=${Uri.encode(query)}"
            val browserIntent = Intent(Intent.ACTION_VIEW, searchUrl.toUri()).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val deepSearchOptions = DeepAppSearchProvider.getDeepSearchOptions(query, context)
            WebOverviewEntity(
                query = query,
                title = "AI Overview for \"$query\"",
                subtitle = "Generating overview...",
                browserIntent = browserIntent,
                deepSearchOptions = deepSearchOptions,
                summaryText = null,
                isLoading = true,
                isLiveWeb = false
            )
        } else {
            null
        }

        val providerErrors = providerDiagnostics
            .filter { !it.isAvailable || it.errorMessage != null }
            .mapNotNull { it.errorMessage }

        val currentState = _searchState.value
        val preservedBubble = if (currentState.activeBubbleApp != null &&
            (currentState.activeBubbleApp.title.equals(query, ignoreCase = !caseSensitive) ||
             currentState.activeBubbleApp.packageName.equals(query, ignoreCase = true))) {
            currentState.activeBubbleApp
        } else {
            null
        }

        if (generation != searchGeneration.get()) return@withContext

        _searchState.value = SearchResultState(
            query = query,
            activeBubbleApp = preservedBubble,
            standardResults = combinedStandardResults,
            webOverview = webOverview,
            isSearching = false,
            providerDiagnostics = providerDiagnostics,
            providerErrors = providerErrors
        )

        // Asynchronously pull overview
        currentOverviewJob?.cancel()
        if (webOverview != null) {
            currentOverviewJob = coroutineScope.launch {
                val summary = WebOverviewProvider.fetchOverview(
                    query = query,
                    geminiApiKey = settings.geminiApiKey
                )
                val currentState = _searchState.value
                if (generation == searchGeneration.get() && currentState.query == query && currentState.webOverview != null) {
                    val updatedOverview = currentState.webOverview.copy(
                        summaryText = summary,
                        isLoading = false,
                        isLiveWeb = summary != null
                    )
                    _searchState.value = currentState.copy(webOverview = updatedOverview)
                }
            }
        }
    }

    fun triggerFrictionBubble(app: AppEntity) {
        currentOverviewJob?.cancel()
        _searchState.value = _searchState.value.copy(
            activeBubbleApp = app
        )
    }

    fun submitSearch(rawQuery: String): Boolean {
        val query = rawQuery.trim()
        if (query.isEmpty()) return false

        val settings = settingsRepository.settings.value
        val frictionPackages = settings.frictionAppPackages
        val caseSensitive = settings.caseSensitive

        val frictionMatch = try {
            appRepository.findFrictionExactMatch(
                query = query,
                frictionPackages = frictionPackages,
                caseSensitive = caseSensitive
            )
        } catch (_: Throwable) {
            null
        }

        if (frictionMatch != null) {
            currentOverviewJob?.cancel()
            _searchState.value = _searchState.value.copy(
                activeBubbleApp = frictionMatch
            )
            return true
        }
        return false
    }

    fun clearSearch() {
        currentOverviewJob?.cancel()
        _searchState.value = SearchResultState()
    }

    private fun getPriorityOrder(entity: SearchEntity): Int {
        if (entity.id.startsWith("system:private_space") || entity.id.startsWith("system:hidden_apps")) {
            return 0
        }
        return when (entity) {
            is AppEntity, is DeepSearchEntity, is AppGroupEntity -> 1
            is PersonEntity, is CallEntity -> 2
            is MessageEntity -> 3
            is ConversationEntity -> 4
            is MediaEntity, is FileEntity -> 5
            else -> 6
        }
    }
}
