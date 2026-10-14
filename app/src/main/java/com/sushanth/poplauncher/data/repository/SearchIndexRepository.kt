package com.sushanth.poplauncher.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class IndexedLaunch(
    val entityId: String,
    val count: Int,
    val lastUsed: Long
)

private val Context.dataStore by preferencesDataStore(name = "pop_search_index")

class SearchIndexRepository(context: Context, private val coroutineScope: CoroutineScope) {

    private val appContext = context.applicationContext
    private val lock = Any()
    // In-memory cache for ultra-fast, zero-battery queries: normalized query -> list of launches
    private val indexCache = LinkedHashMap<String, MutableList<IndexedLaunch>>(MAX_QUERIES, 0.75f, true)
    private var persistJob: Job? = null

    init {
        coroutineScope.launch(Dispatchers.IO) {
            loadIndex()
        }
    }

    private suspend fun loadIndex() {
        val prefs = appContext.dataStore.data.first()
        val raw = prefs[KEY_INDEX_DATA] ?: return
        try {
            val root = JSONObject(raw)
            val keys = root.keys()
            synchronized(lock) {
                while (keys.hasNext() && indexCache.size < MAX_QUERIES) {
                    val q = keys.next()
                    val arr = root.getJSONArray(q)
                    val list = mutableListOf<IndexedLaunch>()
                    for (i in 0 until minOf(arr.length(), MAX_ITEMS_PER_QUERY)) {
                        val obj = arr.getJSONObject(i)
                        list.add(
                            IndexedLaunch(
                                entityId = obj.getString("id"),
                                count = obj.optInt("count", 1),
                                lastUsed = obj.optLong("lastUsed", System.currentTimeMillis())
                            )
                        )
                    }
                    indexCache[q] = list
                }
            }
        } catch (_: Exception) {}
    }

    private suspend fun persistIndex() {
        try {
            val root = JSONObject()
            val entriesSnapshot = synchronized(lock) {
                indexCache.entries.take(MAX_QUERIES).map { (k, v) -> k to v.take(MAX_ITEMS_PER_QUERY).toList() }
            }
            for ((q, list) in entriesSnapshot) {
                val arr = JSONArray()
                for (item in list) {
                    val obj = JSONObject().apply {
                        put("id", item.entityId)
                        put("count", item.count)
                        put("lastUsed", item.lastUsed)
                    }
                    arr.put(obj)
                }
                root.put(q, arr)
            }
            appContext.dataStore.edit { prefs ->
                prefs[KEY_INDEX_DATA] = root.toString()
            }
        } catch (_: Exception) {}
    }

    suspend fun recordLaunch(query: String, entityId: String) = withContext(Dispatchers.IO) {
        val q = query.trim().lowercase()
        if (q.isEmpty() || entityId.isEmpty()) return@withContext

        synchronized(lock) {
            val list = indexCache.getOrPut(q) { mutableListOf() }
            val existingIdx = list.indexOfFirst { it.entityId == entityId }
            val now = System.currentTimeMillis()

            if (existingIdx >= 0) {
                val old = list[existingIdx]
                list[existingIdx] = old.copy(count = old.count + 1, lastUsed = now)
            } else {
                list.add(IndexedLaunch(entityId = entityId, count = 1, lastUsed = now))
            }

            // Sort by count desc then lastUsed desc and bound per-query list size
            list.sortWith(compareByDescending<IndexedLaunch> { it.count }.thenByDescending { it.lastUsed })
            while (list.size > MAX_ITEMS_PER_QUERY) {
                list.removeAt(list.lastIndex)
            }

            // Bound total queries in memory using LRU order
            while (indexCache.size > MAX_QUERIES) {
                val eldestKey = indexCache.entries.firstOrNull()?.key ?: break
                indexCache.remove(eldestKey)
            }
        }
        schedulePersist()
    }

    private fun schedulePersist() {
        synchronized(lock) {
            persistJob?.cancel()
            persistJob = coroutineScope.launch(Dispatchers.IO) {
                delay(2000L)
                persistIndex()
            }
        }
    }

    /**
     * Returns affinity scores for all entities against the current query or prefix.
     * Higher score means the user previously chose this result for this query.
     */
    fun getAffinityScores(query: String): Map<String, Int> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyMap()

        val results = mutableMapOf<String, Int>()

        synchronized(lock) {
            val exactIds = hashSetOf<String>()
            // Exact query score
            val exactList = indexCache[q]
            if (exactList != null) {
                for (item in exactList) {
                    results[item.entityId] = item.count * 10
                    exactIds.add(item.entityId)
                }
            }

            // Prefix match score (highest count across matching prefixes wins if not already an exact match)
            for ((cachedQuery, list) in indexCache) {
                if (cachedQuery == q) continue

                if (q.startsWith(cachedQuery) || cachedQuery.startsWith(q)) {
                    for (item in list) {
                        if (exactIds.contains(item.entityId)) continue

                        val currentScore = results[item.entityId] ?: 0
                        val newScore = item.count * 3
                        if (newScore > currentScore) {
                            results[item.entityId] = newScore
                        }
                    }
                }
            }
        }
        return results
    }

    companion object {
        private const val MAX_QUERIES = 100
        private const val MAX_ITEMS_PER_QUERY = 10
        private val KEY_INDEX_DATA = stringPreferencesKey("search_index_json")
    }
}
