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
        onSuccess: (id: String, name: String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val rawText = visionText.text
                val noSpaceText = rawText.replace("\\s".toRegex(), "")
                
                if (noSpaceText.contains("全民健康保險") || noSpaceText.contains("NATIONALHEALTHINSURANCE")) {
                    onFailure(Exception("檢測到健保卡。請改用「身分證」正面拍攝。"))
                    return@addOnSuccessListener
                }

                val keywords = listOf("中華", "民國", "國民身分證")
                val foundKeywordsCount = keywords.count { noSpaceText.contains(it) }

                if (foundKeywordsCount < 2) {
                    onFailure(Exception("未偵測到身分證。請確保證件完全放進「導覽框」內，並避開反光。"))
                    return@addOnSuccessListener
                }

                // --- ✨ 優化：辨識姓名邏輯 ---
                var foundName = ""
                
                // 策略 1: 使用 Regex 在清理過的全文中尋找
                val cleanFullText = rawText.replace(" ", "").replace(":", "").replace("：", "")
                val nameRegex = Regex("姓名([\\u4e00-\\u9fa5]{2,4})")
                val match = nameRegex.find(cleanFullText)
                if (match != null) {
                    foundName = match.groupValues[1]
                }
                
                // 策略 2: 尋找包含「姓名」的 Block，並檢查其下方或同 Block 的行
                if (foundName.isEmpty()) {
                    val blocks = visionText.textBlocks
                    outer@for (i in blocks.indices) {
                        val block = blocks[i]
                        val blockText = block.text.replace(" ", "")
                        if (blockText.contains("姓名")) {
                            // 檢查同 Block 的行
                            val lines = block.lines
                            for (j in lines.indices) {
                                val lineText = lines[j].text.replace(" ", "").replace(":", "").replace("：", "")
                                if (lineText.contains("姓名")) {
                                    val potential = lineText.substringAfter("姓名").filter { it.code in 0x4E00..0x9FFF }
                                    if (potential.length in 2..4) {
                                        foundName = potential
                                        break@outer
                                    }
                                    // 檢查下一行
                                    if (j + 1 < lines.size) {
                                        val nextLineText = lines[j+1].text.replace(" ", "").filter { it.code in 0x4E00..0x9FFF }
                                        if (nextLineText.length in 2..4 && !nextLineText.contains("姓名")) {
                                            foundName = nextLineText
                                            break@outer
                                        }
                                    }
                                }
                            }
                            
                            // 檢查物理位置在下方的 Block (ML Kit 通常按順序)
                            if (i + 1 < blocks.size) {
                                val nextBlockText = blocks[i+1].text.replace(" ", "").filter { it.code in 0x4E00..0x9FFF }
                                if (nextBlockText.length in 2..4 && !nextBlockText.contains("出生") && !nextBlockText.contains("民國")) {
                                    foundName = nextBlockText
                                    break@outer
                                }
                            }
                        }
                    }
                }

                // 3. 辨識字號
                val idRegex = Regex("[A-Z][12]\\d{8}")
                var foundId: String? = null

                val fullCleanedTextForId = cleanId(noSpaceText)
                val matchFull = idRegex.find(fullCleanedTextForId)
                if (matchFull != null && isValidTaiwanID(matchFull.value)) {
                    foundId = matchFull.value
                }

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
                    onSuccess(foundId, foundName)
                } else {
                    onFailure(Exception("未辨識到有效字號。請將證件對準在框框內。"))
                }
            }
            .addOnFailureListener { onFailure(it) }
    }
}