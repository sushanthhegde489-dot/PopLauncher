package com.sushanth.poplauncher.data.indexing

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.sushanth.poplauncher.data.model.DeepSearchEntity

object DeepAppSearchProvider {

    private data class TargetSearchApp(
        val appName: String,
        val packageName: String,
        val createIntent: (String) -> Intent
    )

    private val supportedApps = listOf(
        TargetSearchApp(
            appName = "Drive",
            packageName = "com.google.android.apps.docs",
            createIntent = { query ->
                Intent(Intent.ACTION_VIEW, Uri.parse("https://drive.google.com/drive/search?q=${Uri.encode(query)}")).apply {
                    setPackage("com.google.android.apps.docs")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        ),
        TargetSearchApp(
            appName = "Photos",
            packageName = "com.google.android.apps.photos",
            createIntent = { query ->
                Intent(Intent.ACTION_VIEW, Uri.parse("https://photos.google.com/search/${Uri.encode(query)}")).apply {
                    setPackage("com.google.android.apps.photos")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        ),
        TargetSearchApp(
            appName = "YouTube",
            packageName = "com.google.android.youtube",
            createIntent = { query ->
                Intent(Intent.ACTION_SEARCH).apply {
                    setPackage("com.google.android.youtube")
                    putExtra("query", query)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        ),
        TargetSearchApp(
            appName = "Spotify",
            packageName = "com.spotify.music",
            createIntent = { query ->
                Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:${Uri.encode(query)}")).apply {
                    setPackage("com.spotify.music")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        ),
        TargetSearchApp(
            appName = "Gmail",
            packageName = "com.google.android.gm",
            createIntent = { query ->
                Intent(Intent.ACTION_SEARCH).apply {
                    setPackage("com.google.android.gm")
                    putExtra(SearchManager.QUERY, query)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        ),
        TargetSearchApp(
            appName = "Maps",
            packageName = "com.google.android.apps.maps",
            createIntent = { query ->
                Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(query)}")).apply {
                    setPackage("com.google.android.apps.maps")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        ),
        TargetSearchApp(
            appName = "Play Store",
            packageName = "com.android.vending",
            createIntent = { query ->
                Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=${Uri.encode(query)}")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        )
    )

    fun getDeepSearchOptions(query: String, context: Context): List<DeepSearchEntity> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val pm = context.packageManager
        val results = mutableListOf<DeepSearchEntity>()

        for (app in supportedApps) {
            val isInstalled = try {
                pm.getPackageInfo(app.packageName, 0)
                true
            } catch (_: Exception) {
                false
            }

            if (isInstalled) {
                val intent = try {
                    val candidate = app.createIntent(trimmed)
                    if (candidate.resolveActivity(pm) != null) candidate else null
                } catch (_: Exception) {
                    null
                }

                if (intent != null) {
                    results.add(
                        DeepSearchEntity(
                            id = "deep_search:${app.packageName}",
                            title = "Search in ${app.appName}",
                            subtitle = "\"$trimmed\"",
                            source = app.appName,
                            appPackage = app.packageName,
                            launchIntent = intent,
                            searchableTerms = listOf(app.appName, trimmed)
                        )
                    )
                }
            }
        }

        return results
    }
}
