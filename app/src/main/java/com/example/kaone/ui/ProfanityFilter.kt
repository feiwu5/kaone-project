package com.example.kaone.ui

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

object ProfanityFilter {

    private const val TAG = "ProfanityFilter"

    // 優先從 BuildConfig (local.properties) 讀取，亦可於程式碼中動態指定 customOpenAiKey
    var customOpenAiKey: String = ""

    val OPENAI_API_KEY: String
        get() = customOpenAiKey.ifBlank { com.example.kaone.BuildConfig.OPENAI_API_KEY }

    private const val OPENAI_MODERATION_ENDPOINT = "https://api.openai.com/v1/moderations"

    // 記憶體快取 (文字 -> 是否包含違禁/敏感內容)，避免重複傳送相同內容
    private val cache = ConcurrentHashMap<String, Boolean>()

    // 本地違禁詞清單
    private val BANNED_WORDS = listOf(
        "幹你娘", "幹您娘", "你娘", "你媽", "他媽的", "他娘的", "他媽", "他娘", "三小", "啥小", "有病",
        "去你的", "去你媽的", "你媽逼", "妳媽", "妳媽的", "嗎的", "媽的", "馬的", "衝三小", "衝啥小", "塞拎娘",
        "30678", "北七", "八七", "白痴", "白癡", "白吃", "賤人", "婊子", "臭婊", "bitch",
        "Bitch", "笨蛋", "腦袋有問題", "腦子有問題", "頭殼有問題", "腦袋有洞", "腦子有洞", "雞巴", "雞掰", "雞拜", "雞敗",
        "機掰", "機敗", "機拜", "機八", "擊巴", "擊敗", "擊拜", "擊掰", "吉掰", "靠北", "靠杯", "靠邀", "哇靠", "哇幹",
        "啊幹", "啊靠", "阿幹", "阿靠", "靠背", "塞你娘", "吃大便", "大便", "吉掰", "靠北", "靠", "靠邀", "你媽死了", "你爸死了",
        "fuck", "Fuck", "FUCK", "fuck you", "尼哥",
    )

    /**
     * 檢查文字是否包含本地違禁詞 (同步，離線快速檢查)
     */
    fun containsProfanity(text: String): Boolean {
        if (text.isBlank()) return false
        val lowerText = text.lowercase()
        val hit = BANNED_WORDS.any { lowerText.contains(it.lowercase()) }
        if (hit) {
            Log.d(TAG, "🚩 本地詞庫命中違禁詞: $text")
        }
        return hit
    }

    /**
     * 檢查多個欄位是否包含本地違禁詞 (同步)
     */
    fun checkFields(fields: Map<String, String>): String? {
        for ((name, value) in fields) {
            if (containsProfanity(value)) return name
        }
        return null
    }

    /**
     * 使用 OpenAI Moderation API 檢測文字是否包含髒話/敏感內容
     */
    suspend fun checkOpenAIModeration(text: String, apiKey: String = OPENAI_API_KEY): Boolean =
        withContext(Dispatchers.IO) {
            if (text.isBlank()) return@withContext false
            val keyToUse = apiKey.ifBlank { OPENAI_API_KEY }

            // 1. 若無 API Key，直接跳過 OpenAI 檢查
            if (keyToUse.isBlank()) {
                Log.d(TAG, "ℹ️ 未設定 OpenAI API Key，僅使用本地違禁詞庫檢查。")
                return@withContext false
            }

            // 2. 檢查快取
            cache[text]?.let {
                Log.d(TAG, "⚡ 使用快取檢測結果: $text -> $it")
                return@withContext it
            }

            Log.d(TAG, "🔍 [OpenAI Moderation API] 開始檢測文本: $text")

            var conn: HttpURLConnection? = null
            try {
                val url = URL(OPENAI_MODERATION_ENDPOINT)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $keyToUse")
                    doOutput = true
                    connectTimeout = 5000
                    readTimeout = 5000
                }

                val jsonBody = JSONObject().apply {
                    put("input", text)
                }

                OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                    writer.write(jsonBody.toString())
                    writer.flush()
                }

                val code = conn.responseCode
                Log.d(TAG, "📥 [OpenAI Moderation API] 回應狀態碼: $code")

                if (code == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
                    val responseStr = reader.readText()
                    reader.close()

                    Log.d(TAG, "📥 [OpenAI Moderation API] 回應內容: $responseStr")

                    val responseJson = JSONObject(responseStr)
                    val results = responseJson.optJSONArray("results")
                    if (results != null && results.length() > 0) {
                        val firstResult = results.getJSONObject(0)
                        val isFlagged = firstResult.optBoolean("flagged", false)

                        // 寫入快取
                        cache[text] = isFlagged

                        if (isFlagged) {
                            Log.w(TAG, "⚠️ [OpenAI Moderation API] 偵測到違禁/敏感內容: $text")
                        } else {
                            Log.d(TAG, "✅ [OpenAI Moderation API] 文本通過審查: $text")
                        }
                        return@withContext isFlagged
                    }
                } else {
                    val errReader = BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream, "UTF-8"))
                    val errStr = errReader.readText()
                    errReader.close()
                    Log.e(TAG, "❌ [OpenAI Moderation API] 請求錯誤: $errStr")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ [OpenAI Moderation API] 呼叫例外: ${e.localizedMessage}", e)
            } finally {
                conn?.disconnect()
            }

            false
        }

    /**
     * 綜合檢查：先查本地詞庫，若通過再送 OpenAI Moderation API 檢測
     */
    suspend fun containsProfanityAsync(text: String, apiKey: String = OPENAI_API_KEY): Boolean {
        if (text.isBlank()) return false

        // 1. 先查本地違禁詞
        if (containsProfanity(text)) return true

        // 2. 本地沒命中，再送 OpenAI Moderation API 檢測
        return checkOpenAIModeration(text, apiKey)
    }

    /**
     * 綜合檢查多個欄位
     */
    suspend fun checkFieldsAsync(fields: Map<String, String>, apiKey: String = OPENAI_API_KEY): String? {
        for ((name, value) in fields) {
            if (containsProfanityAsync(value, apiKey)) return name
        }
        return null
    }
}
