package com.example.kaone.ui

import android.util.Log
import com.example.kaone.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * AI 面交地點推薦資料結構與 Gemini 整合工具
 */
data class GeminiMeetupRecommendation(
    val locationName: String,
    val reason: String,
    val category: String = "捷運站/交通樞紐"
)

object MeetupLocationHelper {

    // 從 BuildConfig (local.properties) 讀取 Gemini API Key
    var geminiApiKey: String = BuildConfig.GEMINI_API_KEY
    var lastErrorMsg: String? = null

    /**
     * 向 Google 伺服器動態查詢目前該 API Key 支援的有效 Gemini 模型名稱清單
     */
    private fun fetchAvailableModelNames(apiKey: String): List<String> {
        try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            if (conn.responseCode == 200) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(jsonStr)
                val modelsArr = root.optJSONArray("models") ?: return emptyList()
                val list = mutableListOf<String>()

                for (i in 0 until modelsArr.length()) {
                    val mObj = modelsArr.getJSONObject(i)
                    val name = mObj.optString("name", "") // 例如 "models/gemini-2.5-flash"
                    val cleanName = name.removePrefix("models/")
                    val methods = mObj.optJSONArray("supportedGenerationMethods")?.toString() ?: ""

                    if (methods.contains("generateContent")) {
                        if (cleanName.contains("flash")) {
                            list.add(0, cleanName) // 優先使用 flash 系列模型
                        } else {
                            list.add(cleanName)
                        }
                    }
                }
                Log.d("MeetupLocationHelper", "動態從 Google 查詢到的可用模型清單: $list")
                return list
            } else {
                Log.w("MeetupLocationHelper", "查詢模型清單失敗，HTTP 狀態碼: ${conn.responseCode}")
            }
        } catch (e: Exception) {
            Log.e("MeetupLocationHelper", "查詢可用模型失敗: ${e.message}")
        }
        return emptyList()
    }

    /**
     * 呼叫 Gemini AI 模型進行面交地點智慧推薦
     *
     * @param userALocation 使用者 A 的所在地區 (例如 "台北市 中正區")
     * @param userBLocation 使用者 B 的所在地區 (例如 "新北市 板橋區")
     * @param userAName 使用者 A 稱呼 (例如 "我")
     * @param userBName 使用者 B 稱呼 (例如 "對方")
     * @param customPrompt 額外需求（如 "要有停車場" 或 "雨天備案"）
     * @return Gemini 推薦的地點清單與說明理由
     */
    suspend fun getGeminiMeetupRecommendations(
        userALocation: String,
        userBLocation: String,
        userAName: String = "我",
        userBName: String = "對方",
        customPrompt: String = "",
        apiKey: String = geminiApiKey
    ): List<GeminiMeetupRecommendation> = withContext(Dispatchers.IO) {
        if (userALocation.isBlank() && userBLocation.isBlank()) {
            return@withContext defaultFallbackRecommendations()
        }

        val keyToUse = apiKey.ifEmpty { geminiApiKey }.trim()
        if (keyToUse.isNotBlank()) {
            // 優先使用已確認支援且速度最快的 gemini-3.6-flash
            val primaryModels = listOf("gemini-3.6-flash")
            val dynamicModels = fetchAvailableModelNames(keyToUse)
            val candidateModels = (primaryModels + dynamicModels).distinct()
            Log.d("MeetupLocationHelper", "嘗試使用模型佇列: $candidateModels")

            val prompt = """
                你是一個專業的台灣面交地點智慧推薦助手。
                使用者 1（$userAName）位於：${userALocation.ifEmpty { "未設定" }}
                使用者 2（$userBName）位於：${userBLocation.ifEmpty { "未設定" }}
                ${if (customPrompt.isNotBlank()) "特別需求：$customPrompt" else ""}

                請推薦 3 個最適合兩人的台灣面交地點。
                為加快生成速度，輸出文字請極度精簡，重點有到就好，理由只需簡短一句話（例如：位於兩人中心點，雙方搭車皆方便）。

                請嚴格按照以下 JSON 陣列格式回應，只輸出純 JSON 陣列，不要加入任何前導文字：
                [
                  {
                    "locationName": "地點名稱（例如：忠孝復興站 4號出口）",
                    "reason": "簡短理由（例如：雙方搭捷運均不需轉車）",
                    "category": "類型（例如：捷運站）"
                  }
                ]
            """.trimIndent()

            for (modelName in candidateModels) {
                try {
                    Log.d("MeetupLocationHelper", "嘗試使用模型: $modelName 呼叫 Gemini API...")
                    val generativeModel = GenerativeModel(
                        modelName = modelName,
                        apiKey = keyToUse
                    )

                    val response = generativeModel.generateContent(prompt)
                    val rawText = response.text?.trim() ?: ""
                    Log.d("MeetupLocationHelper", "Gemini [$modelName] 回應內容: $rawText")

                    // 強健解構 JSON Array 範圍
                    val startIdx = rawText.indexOf('[')
                    val endIdx = rawText.lastIndexOf(']')

                    if (startIdx != -1 && endIdx != -1 && endIdx > startIdx) {
                        val jsonText = rawText.substring(startIdx, endIdx + 1)
                        val results = mutableListOf<GeminiMeetupRecommendation>()
                        val jsonArray = JSONArray(jsonText)
                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(i)
                            results.add(
                                GeminiMeetupRecommendation(
                                    locationName = obj.optString("locationName", "推薦地點"),
                                    reason = obj.optString("reason", "交通方便且安全的公共場所"),
                                    category = obj.optString("category", "熱門地標")
                                )
                            )
                        }
                        if (results.isNotEmpty()) {
                            Log.d("MeetupLocationHelper", "成功取得 Gemini [$modelName] 推薦結果 ${results.size} 筆！")
                            lastErrorMsg = null
                            return@withContext results
                        }
                    }
                } catch (e: Exception) {
                    val msg = e.localizedMessage ?: e.message ?: "未知錯誤"
                    lastErrorMsg = "模型 [$modelName] 失敗: $msg"
                    Log.e("MeetupLocationHelper", "模型 [$modelName] 呼叫失敗，原因: $msg", e)
                }
            }
        }

        Log.w("MeetupLocationHelper", "Gemini 所有模型呼叫均失敗或未設定 Key，切換至備用規則引擎")
        return@withContext generateSmartRuleBasedRecommendations(userALocation, userBLocation)
    }

    /**
     * 當 API Key 尚未傳入或網路連線失敗時的智慧備用推薦 Engine
     */
    private fun generateSmartRuleBasedRecommendations(
        locA: String,
        locB: String
    ): List<GeminiMeetupRecommendation> {
        val list = mutableListOf<GeminiMeetupRecommendation>()

        val combine = "$locA $locB"
        if (combine.contains("台北") || combine.contains("新北") || combine.contains("基隆")) {
            list.add(GeminiMeetupRecommendation("捷運台北車站 / 地下街", "位於大台北交通心臟，火車與多條捷運線交會處", "交通樞紐"))
            list.add(GeminiMeetupRecommendation("捷運忠孝復興站 4號出口", "板南線與文湖線交會樞紐，人潮多且安全", "捷運站"))
            list.add(GeminiMeetupRecommendation("捷運板橋站 / 環球購物中心", "新北市核心交通大站，高鐵台鐵捷運共構", "交通樞紐"))
        } else if (combine.contains("台中") || combine.contains("彰化")) {
            list.add(GeminiMeetupRecommendation("台中火車站 / 大魯閣新時代", "台中鐵路核心樞紐，人潮眾多且明亮", "交通樞紐"))
            list.add(GeminiMeetupRecommendation("高鐵台中站 / 烏日捷運站", "中部交通轉乘樞紐，極適合跨縣市面交", "高鐵站"))
            list.add(GeminiMeetupRecommendation("台中市政府捷運站", "中捷綠線核心大站，周邊地標明顯", "捷運站"))
        } else if (combine.contains("高雄") || combine.contains("屏東")) {
            list.add(GeminiMeetupRecommendation("捷運美麗島站 / 光之穹頂", "高雄紅橘兩線唯一交會站，最便利的面交中點", "捷運站"))
            list.add(GeminiMeetupRecommendation("高鐵左營站 / 彩虹市集", "北高雄交通樞紐，高鐵台鐵捷運三鐵共構", "高鐵站"))
            list.add(GeminiMeetupRecommendation("捷運三多商圈站 1號出口", "南高雄核心商圈，周邊連鎖門市眾多", "捷運站"))
        } else {
            list.add(GeminiMeetupRecommendation("高鐵嘉義站 / 嘉義火車站", "台灣中南部主要交通轉乘大站，方便跨區交割", "交通樞紐"))
            list.add(GeminiMeetupRecommendation("台南火車站門口便利商店", "台南核心樞紐，連鎖店家多且明亮安全", "交通樞紐"))
            list.add(GeminiMeetupRecommendation("高鐵新竹站 / 竹北門市", "新竹縣市核心面交轉乘點", "高鐵站"))
        }
        return list
    }

    private fun defaultFallbackRecommendations(): List<GeminiMeetupRecommendation> {
        return listOf(
            GeminiMeetupRecommendation("台北車站 / 捷運大廳", "全國最大交通樞紐，最便利交割地點", "交通樞紐"),
            GeminiMeetupRecommendation("台中火車站", "中部鐵路交通中心，設施完善", "交通樞紐"),
            GeminiMeetupRecommendation("捷運美麗島站", "高雄捷運紅橘線交會點", "捷運站")
        )
    }
}
