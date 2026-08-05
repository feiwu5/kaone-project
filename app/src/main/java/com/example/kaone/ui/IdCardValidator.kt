package com.example.kaone.ui

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.security.MessageDigest

object IdCardValidator {

    fun isValidTaiwanID(id: String): Boolean {
        if (id.length != 10) return false
        val regex = Regex("^[A-Z][12]\\d{8}$")
        if (!regex.matches(id)) return false
        val alphabet = "ABCDEFGHJKLMNPQRSTUVXYWZIO"
        val firstChar = id[0].uppercaseChar()
        val n1 = alphabet.indexOf(firstChar) + 10
        val digits = IntArray(11)
        digits[0] = n1 / 10
        digits[1] = n1 % 10
        for (i in 1 until 10) digits[i + 1] = id[i] - '0'
        val weights = intArrayOf(1, 9, 8, 7, 6, 5, 4, 3, 2, 1, 1)
        var sum = 0
        for (i in 0 until 11) sum += digits[i] * weights[i]
        return sum % 10 == 0
    }

    fun hashIdNumber(id: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(id.uppercase().toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun cleanId(input: String): String {
        val s = input.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }
        if (s.length < 2) return s
        val first = s[0]
        val rest = s.substring(1)
            .replace('I', '1').replace('L', '1').replace('O', '0')
            .replace('S', '5').replace('B', '8').replace('G', '6')
        return first + rest
    }

    fun scanIdCard(
        image: InputImage,
        onSuccess: (id: String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                // 移除所有空白以便進行關鍵字比對
                val allText = visionText.text.replace("\\s".toRegex(), "")

                // 1. 偵測健保卡 (維持排除)
                if (allText.contains("全民健康保險") || allText.contains("NATIONALHEALTHINSURANCE")) {
                    onFailure(Exception("檢測到健保卡。請改用「身分證」正面拍攝。"))
                    return@addOnSuccessListener
                }

                // 2. 更寬鬆的關鍵字組合判斷 (確保影像在框框內)
                val keywords = listOf("中華", "民國", "國民身分證",)
                val foundKeywordsCount = keywords.count { allText.contains(it) }

                if (foundKeywordsCount < 2) {
                    // 當 AI 在裁切後的圖中找不到足夠關鍵字時，提示用戶放進框內
                    onFailure(Exception("未偵測到身分證。請確保證件完全放進「導覽框」內，並避開反光。"))
                    return@addOnSuccessListener
                }

                // 3. 解決字號被切斷的問題
                val idRegex = Regex("[A-Z][12]\\d{8}")
                var foundId: String? = null

                // 方法 A：在整張圖清理過後的文字裡找
                val fullCleanedText = cleanId(allText)
                val matchFull = idRegex.find(fullCleanedText)
                if (matchFull != null && isValidTaiwanID(matchFull.value)) {
                    foundId = matchFull.value
                }

                // 方法 B：如果方法 A 沒找到，再逐行檢查
                if (foundId == null) {
                    val allLines = visionText.textBlocks.flatMap { it.lines }
                    for (line in allLines) {
                        val cleaned = cleanId(line.text)
                        val match = idRegex.find(cleaned)
                        if (match != null && isValidTaiwanID(match.value)) {
                            foundId = match.value
                            break
                        }
                    }
                }

                if (foundId != null) {
                    onSuccess(foundId)
                } else {
                    // 提示用戶對準框內的字號區域
                    onFailure(Exception("未辨識到有效字號。請將證件對準在框框內。"))
                }
            }
            .addOnFailureListener { onFailure(it) }
    }
}