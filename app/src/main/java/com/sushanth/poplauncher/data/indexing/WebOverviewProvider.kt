package com.sushanth.poplauncher.data.indexing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

object WebOverviewProvider {

    /**
     * Fetches an overview for the query.
     * If an optional Gemini API key is configured by the user, calls the official Gemini API.
     * Otherwise, queries the privacy-preserving DuckDuckGo Instant Answer JSON API.
     */
    suspend fun fetchOverview(query: String, geminiApiKey: String? = null): String? = coroutineScope {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@coroutineScope null

        // 1. If user supplied a Gemini API key in settings, prioritize direct Gemini generation
        if (!geminiApiKey.isNullOrBlank()) {
            val geminiResult = withContext(Dispatchers.IO) { fetchFromGemini(trimmed, geminiApiKey) }
            if (!geminiResult.isNullOrBlank()) {
                return@coroutineScope cleanText(geminiResult)
            }
        }

        // 2. Default: Privacy-preserving DuckDuckGo Instant Answer API (structured JSON, no tracking)
        val ddgResult = withContext(Dispatchers.IO) { fetchFromDuckDuckGo(trimmed) }
        if (!ddgResult.isNullOrBlank()) {
            return@coroutineScope cleanText(ddgResult)
        }

        null
    }

    private fun fetchFromGemini(query: String, apiKey: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URI("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey").toURL()
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 4000
                readTimeout = 4000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", "PopLauncher/1.0")
            }

            val contentObj = JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().apply {
                    put("text", "Provide a direct, factual 2-sentence summary answering: $query. Plain text only, no markdown, asterisks, or bullet points.")
                }))
            }
            val payload = JSONObject().apply {
                put("contents", JSONArray().put(contentObj))
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readTextLimited(MAX_RESPONSE_CHARS) }
                val root = JSONObject(response)
                val candidates = root.optJSONArray("candidates")
                val parts = candidates?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                val text = parts?.optJSONObject(0)?.optString("text")
                if (!text.isNullOrBlank()) {
                    return text
                }
            }
            null
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun fetchFromDuckDuckGo(query: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = URI("https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1").toURL()
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3000
                readTimeout = 3000
                setRequestProperty("User-Agent", "PopLauncher/1.0 (Android; Privacy Search)")
                setRequestProperty("Accept", "application/json")
            }

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readTextLimited(MAX_RESPONSE_CHARS) }
                val json = JSONObject(response)
                val abstractText = json.optString("AbstractText", "")
                if (abstractText.isNotBlank()) {
                    return abstractText
                }
                val answer = json.optString("Answer", "")
                if (answer.isNotBlank()) {
                    return answer
                }
                val relatedTopics = json.optJSONArray("RelatedTopics")
                if (relatedTopics != null && relatedTopics.length() > 0) {
                    for (i in 0 until relatedTopics.length()) {
                        val item = relatedTopics.optJSONObject(i)
                        val text = item?.optString("Text", "") ?: ""
                        if (text.isNotBlank()) {
                            return text
                        }
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun BufferedReader.readTextLimited(maxChars: Int): String {
        val buffer = CharArray(4096)
        val out = StringBuilder()
        while (out.length < maxChars) {
            val read = read(buffer, 0, minOf(buffer.size, maxChars - out.length))
            if (read <= 0) break
            out.appendRange(buffer, 0, read)
        }
        return out.toString()
    }

    private const val MAX_RESPONSE_CHARS = 8 * 1024
    private val HTML_TAG_REGEX = Regex("<[^>]*>")
    private val WHITESPACE_REGEX = Regex("\\s+")
    private val MARKDOWN_REGEX = Regex("""\*\*|\*|#""")
    private val PHONETIC_REGEX = Regex("""\(\s*/[^)]+/\s*\)""")
    private val CITATION_REGEX = Regex("""\[\d+]""")
    private val SENTENCE_SPLIT_REGEX = Regex("""(?<=[.!?])\s+""")

    private fun stripHtml(html: String): String {
        return html.replace(HTML_TAG_REGEX, "")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace(WHITESPACE_REGEX, " ")
            .trim()
    }

    private fun cleanText(raw: String): String {
        var t = stripHtml(raw)
        t = t.replace(MARKDOWN_REGEX, "") // strip markdown bold/italics/headers
        t = t.replace(PHONETIC_REGEX, "")
        t = t.replace(CITATION_REGEX, "")
        t = t.replace(WHITESPACE_REGEX, " ").trim()

        val sentences = t.split(SENTENCE_SPLIT_REGEX)
        var result = ""
        for (s in sentences) {
            val candidate = if (result.isEmpty()) s else "$result $s"
            if (candidate.length <= 320) {
                result = candidate
            } else {
                if (result.isEmpty()) {
                    val cut = s.take(300)
                    val lastSpace = cut.lastIndexOf(' ')
                    result = if (lastSpace > 50) cut.substring(0, lastSpace) + "..." else "$cut..."
                }
                break
            }
        }

        if (result.isNotBlank() && !result.endsWith(".") && !result.endsWith("!") && !result.endsWith("?") && !result.endsWith("...")) {
            result += "."
        }
        return result.ifBlank { t }
    }
}
