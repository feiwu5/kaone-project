package com.example.kaone.ui

import android.util.Log
import android.util.Patterns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

object UrlSafetyFilter {

    private const val TAG = "UrlSafetyFilter"

    // 優先從 BuildConfig (local.properties) 讀取 API Key
    private val API_KEY: String
        get() = com.example.kaone.BuildConfig.SAFE_BROWSING_API_KEY.ifBlank { "AIzaSyBC6JvcbmsENuaUyn_h-qOknd4KYaWIwlE" }

    private const val SAFE_BROWSING_ENDPOINT =
        "https://safebrowsing.googleapis.com/v4/threatMatches:find"

    // 網址 -> 是否為威脅（快取，避免重複呼叫 API）
    private val cache = ConcurrentHashMap<String, Boolean>()

    private fun normalize(url: String): String =
        if (url.startsWith("http://", true) || url.startsWith("https://", true)) url
        else "http://$url"

    /**
     * 從文字中提取所有網址
     */
    fun extractUrls(text: String): List<String> {
        val urls = mutableListOf<String>()
        val matcher = Patterns.WEB_URL.matcher(text)
        while (matcher.find()) {
            var u = matcher.group()
            // 只剝除貼在網址最末端的句子標點符號 (句點、逗點、驚嘆號、分號)，保留網址內部的標點符號與括號
            while (u.endsWith('.') || u.endsWith(',') || u.endsWith('!') || u.endsWith(';')) {
                u = u.dropLast(1)
            }
            if (u.isNotEmpty()) urls.add(u)
        }
        return urls.distinct()
    }

    /**
     * 使用 Google Safe Browsing API v4 檢查網址清單
     * @return 被 Google 判定為威脅的網址
     */
    suspend fun checkGoogleSafeBrowsing(urls: List<String>): List<String> =
        withContext(Dispatchers.IO) {
            if (urls.isEmpty() || API_KEY.isBlank()) return@withContext emptyList()

            val formatted = urls.map { normalize(it) }.distinct()
            val uncached = formatted.filter { !cache.containsKey(it) }

            if (uncached.isNotEmpty()) {
                val threats = queryApi(uncached)
                // API 失敗時 threats 為 null，不寫入快取，下次會重查
                if (threats != null) {
                    uncached.forEach { cache[it] = threats.contains(it) }
                }
            }

            formatted.filter { cache[it] == true }
        }

    /**
     * 實際呼叫 API。成功回傳威脅網址集合（可為空），失敗回傳 null。
     */
    private fun queryApi(urls: List<String>): Set<String>? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL("$SAFE_BROWSING_ENDPOINT?key=$API_KEY")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 5000
                readTimeout = 5000
            }

            val threatEntries = JSONArray()
            urls.forEach { threatEntries.put(JSONObject().put("url", it)) }

            val jsonBody = JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientId", "kaone-app")
                    put("clientVersion", "1.0.0")
                })
                put("threatInfo", JSONObject().apply {
                    put("threatTypes", JSONArray(listOf(
                        "MALWARE",
                        "SOCIAL_ENGINEERING",
                        "UNWANTED_SOFTWARE",
                        "POTENTIALLY_HARMFUL_APPLICATION"
                    )))
                    put("platformTypes", JSONArray(listOf("ANY_PLATFORM")))
                    put("threatEntryTypes", JSONArray(listOf("URL")))
                    put("threatEntries", threatEntries)
                })
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use {
                it.write(jsonBody.toString())
                it.flush()
            }

            val code = conn.responseCode
            Log.d(TAG, "API 回應狀態碼: $code")

            if (code != 200) {
                val err = conn.errorStream?.bufferedReader()?.use { r -> r.readText() }
                Log.e(TAG, "API 錯誤回應: $err")
                return null
            }

            val resp = conn.inputStream.bufferedReader().use { it.readText() }
            val matches = JSONObject(resp).optJSONArray("matches")
            val threatUrls = mutableSetOf<String>()
            if (matches != null) {
                for (i in 0 until matches.length()) {
                    val u = matches.getJSONObject(i)
                        .optJSONObject("threat")?.optString("url").orEmpty()
                    if (u.isNotEmpty()) threatUrls.add(u)
                }
            }
            Log.d(TAG, "偵測到威脅網址: $threatUrls")
            threatUrls
        } catch (e: Exception) {
            Log.e(TAG, "API 呼叫例外: ${e.localizedMessage}", e)
            null
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * 單一網址檢查
     */
    suspend fun isSuspiciousAsync(url: String): Boolean =
        checkGoogleSafeBrowsing(listOf(url)).isNotEmpty()

    /**
     * 檢查文字中的所有網址是否有威脅
     */
    suspend fun hasSuspiciousUrlAsync(text: String): Boolean {
        val urls = extractUrls(text)
        if (urls.isEmpty()) return false
        return checkGoogleSafeBrowsing(urls).isNotEmpty()
    }
}