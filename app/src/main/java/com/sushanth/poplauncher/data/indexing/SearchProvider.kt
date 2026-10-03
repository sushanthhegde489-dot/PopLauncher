package com.sushanth.poplauncher.data.indexing

import android.content.Context
import com.sushanth.poplauncher.data.model.SearchEntity
import com.sushanth.poplauncher.data.model.SearchType

interface SearchProvider {
    val type: SearchType
    val displayName: String

    suspend fun isAvailable(context: Context): Boolean
    suspend fun index(context: Context): List<SearchEntity>
    suspend fun search(query: String, context: Context): List<SearchEntity>
}
