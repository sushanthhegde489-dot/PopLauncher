package com.sushanth.poplauncher.data.indexing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

object WebOverviewProvider {

    suspend fun fetchOverview(query: String): String? = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext null

        // 1. Primary: Fetch fast AI summary from Pollinations AI text endpoint
        val aiResult = fetchFromPollinationsAi(trimmed)
        if (!aiResult.isNullOrBlank()) {
            return@withContext cleanText(aiResult)
        }

        // 2. Fallback: DuckDuckGo Instant Answer
        val ddgResult = fetchFromDuckDuckGo(trimmed)
        if (!ddgResult.isNullOrBlank()) {
            return@withContext cleanText(ddgResult)
        }

        null
    }

    private fun fetchFromPollinationsAi(query: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URI("https://text.pollinations.ai/").toURL()
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 4000
                readTimeout = 4000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", "PopLauncher/1.0 (Android AI Search)")
            }

            val systemMessage = JSONObject().apply {
                put("role", "system")
                put("content", "You are a concise AI search assistant. Provide a direct, factual, 2-3 sentence overview answering the user query. Do not use markdown (no asterisks, bolding, hashtags, bullet points, or HTML). Plain text only.")
            }
            val userMessage = JSONObject().apply {
                put("role", "user")
                put("content", query)
            }
            val payload = JSONObject().apply {
                put("messages", JSONArray().apply {
                    put(systemMessage)
                    put(userMessage)
                })
                put("model", "openai")
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readTextLimited(MAX_RESPONSE_CHARS) }
                if (response.isNotBlank()) {
                    return response
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
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; PopLauncher)")
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
            out.append(buffer, 0, read)
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
        return if (result.isNotBlank()) result else t
    }
}
