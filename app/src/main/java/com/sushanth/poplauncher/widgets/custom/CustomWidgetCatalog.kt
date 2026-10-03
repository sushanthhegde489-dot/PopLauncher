package com.sushanth.poplauncher.widgets.custom

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI

/** Metadata + structured definition published by the creator. No executable code is accepted. */
data class PublishedCustomWidget(
    val id: String,
    val name: String,
    val description: String,
    val version: Int,
    val icon: String?,
    val thumbnailUrl: String?,
    val minAppVersionCode: Int?,
    val maxAppVersionCode: Int?,
    val deprecated: Boolean,
    val deprecationMessage: String?,
    val definition: CustomWidget
)

data class WidgetManifest(
    val schemaVersion: Int,
    val widgets: List<PublishedCustomWidget>
) {
    companion object {
        const val SUPPORTED_SCHEMA_VERSION = 1
        const val MAX_MANIFEST_BYTES = 512 * 1024
        const val MAX_WIDGETS = 100

        fun fromJson(root: JSONObject, appVersionCode: Int, allowedThumbnailHost: String? = null): WidgetManifest? {
            val schema = root.optInt("schemaVersion", -1)
            if (schema != SUPPORTED_SCHEMA_VERSION) return null
            val array = root.optJSONArray("widgets") ?: return null
            if (array.length() > MAX_WIDGETS) return null

            val widgets = buildList<PublishedCustomWidget> {
                for (i in 0 until array.length()) {
                    val parsed = parseWidget(array.optJSONObject(i) ?: continue, appVersionCode, allowedThumbnailHost) ?: continue
                    if (any { it.id == parsed.id }) return null
                    add(parsed)
                }
            }
            return WidgetManifest(schema, widgets)
        }

        private fun parseWidget(json: JSONObject, appVersionCode: Int, allowedThumbnailHost: String?): PublishedCustomWidget? {
            val id = json.optString("id").trim()
            if (!ID_REGEX.matches(id)) return null
            val name = json.optString("name").trim().takeIf { it.isNotBlank() }?.take(MAX_NAME_LENGTH) ?: return null
            val description = json.optString("description").trim().takeIf { it.isNotBlank() }?.take(MAX_DESCRIPTION_LENGTH) ?: return null
            val version = json.optInt("version", -1)
            if (version < 1) return null

            val minVersion = json.optInt("minAppVersionCode", -1).takeIf { it >= 0 }
            val maxVersion = json.optInt("maxAppVersionCode", -1).takeIf { it >= 0 }
            if (minVersion != null && appVersionCode < minVersion) return null
            if (maxVersion != null && appVersionCode > maxVersion) return null
            if (minVersion != null && maxVersion != null && minVersion > maxVersion) return null

            val definition = json.optJSONObject("definition") ?: return null
            val parsedDefinition = runCatching { CustomWidget.fromJson(definition) }.getOrNull() ?: return null
            if (parsedDefinition.id != id || parsedDefinition.elements.isEmpty()) return null

            val icon = json.optString("icon").trim().takeIf { it.isNotBlank() }?.take(4)
            val thumbnailUrl = json.optString("thumbnailUrl").trim().takeIf { it.isNotBlank() }?.take(512)
            if (thumbnailUrl != null && !isHttpsUrl(thumbnailUrl)) return null
            val safeThumbnailUrl = thumbnailUrl?.takeIf { value ->
                allowedThumbnailHost == null || runCatching { URI(value).host.equals(allowedThumbnailHost, true) }.getOrDefault(false)
            }

            val deprecated = json.optBoolean("deprecated", false)
            val deprecationMessage = json.optString("deprecationMessage").trim().takeIf { it.isNotBlank() }?.take(256)

            return PublishedCustomWidget(
                id = id,
                name = name,
                description = description,
                version = version,
                icon = icon,
                thumbnailUrl = safeThumbnailUrl,
                minAppVersionCode = minVersion,
                maxAppVersionCode = maxVersion,
                deprecated = deprecated,
                deprecationMessage = deprecationMessage,
                definition = parsedDefinition.copy(sourceVersion = version)
            )
        }

        fun isHttpsUrl(value: String): Boolean = runCatching { URI(value).scheme.equals("https", true) && !URI(value).host.isNullOrBlank() }.getOrDefault(false)

        private val ID_REGEX = Regex("[a-z0-9][a-z0-9._-]{0,63}")
        private const val MAX_NAME_LENGTH = 96
        private const val MAX_DESCRIPTION_LENGTH = 256
    }
}

enum class CatalogStatus { IDLE, LOADING, READY_NETWORK, READY_CACHE, READY_BUNDLED, ERROR }

class CustomWidgetCatalogRepository(
    context: Context,
    private val manifestUrl: String
) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val appVersionCode = PackageInfoCompat.getLongVersionCode(
        appContext.packageManager.getPackageInfo(appContext.packageName, 0)
    ).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    private val _catalog = MutableStateFlow<List<PublishedCustomWidget>>(emptyList())
    val catalog: StateFlow<List<PublishedCustomWidget>> = _catalog.asStateFlow()

    private val _status = MutableStateFlow(CatalogStatus.IDLE)
    val status: StateFlow<CatalogStatus> = _status.asStateFlow()

    private val _ignoredIds = MutableStateFlow(loadIgnoredIds())
    val ignoredIds: StateFlow<Set<String>> = _ignoredIds.asStateFlow()

    init {
        loadCachedOrBundled()
    }

    suspend fun refresh(): CatalogStatus = withContext(Dispatchers.IO) {
        _status.value = CatalogStatus.LOADING
        if (!isConfiguredUrl(manifestUrl)) {
            _status.value = if (_catalog.value.isNotEmpty()) CatalogStatus.READY_BUNDLED else CatalogStatus.ERROR
            return@withContext _status.value
        }

        try {
            val body = fetchText(manifestUrl, WidgetManifest.MAX_MANIFEST_BYTES)
            val manifest = WidgetManifest.fromJson(JSONObject(body), appVersionCode, URI(manifestUrl).host)
            if (manifest == null) throw IllegalArgumentException("Unsupported or invalid widget manifest")
            prefs.edit().putString(KEY_CACHED_MANIFEST, body).apply()
            _catalog.value = manifest.widgets
            _status.value = CatalogStatus.READY_NETWORK
        } catch (_: Exception) {
            _status.value = if (_catalog.value.isNotEmpty()) CatalogStatus.READY_CACHE else CatalogStatus.ERROR
        }
        _status.value
    }

    fun ignore(id: String) {
        val next = _ignoredIds.value + id
        _ignoredIds.value = next
        prefs.edit().putStringSet(KEY_IGNORED, next).apply()
    }

    fun restore(id: String) {
        val next = _ignoredIds.value - id
        _ignoredIds.value = next
        prefs.edit().putStringSet(KEY_IGNORED, next).apply()
    }

    private fun loadCachedOrBundled() {
        val cached = prefs.getString(KEY_CACHED_MANIFEST, null)
        val cachedManifest = cached?.let { runCatching { WidgetManifest.fromJson(JSONObject(it), appVersionCode, runCatching { URI(manifestUrl).host }.getOrNull()) }.getOrNull() }
        if (cachedManifest != null) {
            _catalog.value = cachedManifest.widgets
            _status.value = CatalogStatus.READY_CACHE
            return
        }

        val bundled = runCatching {
            appContext.assets.open("custom-widgets/manifest.json").bufferedReader().use { it.readText() }
        }.getOrNull()?.let { runCatching { WidgetManifest.fromJson(JSONObject(it), appVersionCode, runCatching { URI(manifestUrl).host }.getOrNull()) }.getOrNull() }
        if (bundled != null) {
            _catalog.value = bundled.widgets
            _status.value = CatalogStatus.READY_BUNDLED
        }
    }

    private fun fetchText(url: String, maxBytes: Int): String {
        val connection = URI(url).toURL().openConnection() as? HttpURLConnection ?: error("Unsupported connection")
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        connection.requestMethod = "GET"
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/json")
        
        // Add ETag/If-Modified-Since caching headers logic here if possible
        val etag = prefs.getString(KEY_ETAG, null)
        val lastModified = prefs.getString(KEY_LAST_MODIFIED, null)
        if (etag != null) {
            connection.setRequestProperty("If-None-Match", etag)
        }
        if (lastModified != null) {
            connection.setRequestProperty("If-Modified-Since", lastModified)
        }

        try {
            if (connection.responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
                return prefs.getString(KEY_CACHED_MANIFEST, "") ?: ""
            }
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            
            val newETag = connection.getHeaderField("ETag")
            val newLastModified = connection.getHeaderField("Last-Modified")
            prefs.edit().apply {
                putString(KEY_ETAG, newETag)
                putString(KEY_LAST_MODIFIED, newLastModified)
            }.apply()
            
            val input = connection.inputStream
            return input.use {
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val count = it.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > maxBytes) error("Manifest too large")
                    output.write(buffer, 0, count)
                }
                output.toString(Charsets.UTF_8.name())
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun loadIgnoredIds(): Set<String> = prefs.getStringSet(KEY_IGNORED, emptySet()) ?: emptySet()

    private fun isConfiguredUrl(url: String): Boolean =
        WidgetManifest.isHttpsUrl(url) && !url.contains("YOUR_GITHUB_USERNAME", true) && !url.contains("YOUR_REPO", true)

    companion object {
        private const val PREFS = "pop_launcher_custom_widget_catalog"
        private const val KEY_CACHED_MANIFEST = "cached_manifest"
        private const val KEY_IGNORED = "ignored_ids"
        private const val KEY_ETAG = "manifest_etag"
        private const val KEY_LAST_MODIFIED = "manifest_last_modified"
    }
}
