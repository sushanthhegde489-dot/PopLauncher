package com.sushanth.poplauncher.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

class SearchIndexRepository(private val context: Context, private val coroutineScope: CoroutineScope) {

    private val lock = Any()
    // In-memory cache for ultra-fast, zero-battery queries: normalized query -> list of launches
    private val indexCache = mutableMapOf<String, MutableList<IndexedLaunch>>()

    init {
        coroutineScope.launch(Dispatchers.IO) {
            loadIndex()
        }
    }

    private suspend fun loadIndex() {
        val prefs = context.dataStore.data.first()
        val raw = prefs[KEY_INDEX_DATA] ?: return
        try {
            val root = JSONObject(raw)
            val keys = root.keys()
            synchronized(lock) {
                while (keys.hasNext()) {
                    val q = keys.next()
                    val arr = root.getJSONArray(q)
                    val list = mutableListOf<IndexedLaunch>()
                    for (i in 0 until arr.length()) {
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
                indexCache.entries.take(100).map { (k, v) -> k to v.take(10).toList() }
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
            context.dataStore.edit { prefs ->
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

            if (existingIdx >= 0) {
                val old = list[existingIdx]
                list[existingIdx] = old.copy(count = old.count + 1, lastUsed = System.currentTimeMillis())
            } else {
                list.add(IndexedLaunch(entityId = entityId, count = 1, lastUsed = System.currentTimeMillis()))
            }

            // Sort by count desc then lastUsed desc
            list.sortWith(compareByDescending<IndexedLaunch> { it.count }.thenByDescending { it.lastUsed })
        }
        persistIndex()
    }

    /**
     * Returns an affinity score for a given entityId against the current query or prefix.
     * Higher score means the user previously chose this result for this query.
     */
    fun getAffinityScore(query: String, entityId: String): Int {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return 0

        synchronized(lock) {
            // Exact query score
            val exactMatch = indexCache[q]?.firstOrNull { it.entityId == entityId }
            if (exactMatch != null) {
                return exactMatch.count * 10
            }

            // Prefix match score
            var prefixScore = 0
            for ((cachedQuery, list) in indexCache) {
                if (q.startsWith(cachedQuery) || cachedQuery.startsWith(q)) {
                    val match = list.firstOrNull { it.entityId == entityId }
                    if (match != null) {
                        prefixScore = maxOf(prefixScore, match.count * 3)
                    }
                }
            }

            return prefixScore
        }
    }

    companion object {
        private val KEY_INDEX_DATA = stringPreferencesKey("search_index_json")
    }
}
