package com.example.kaone.ui

import android.util.Patterns
import java.net.URI

object UrlSafetyFilter {

    /**
     * 從文字中提取所有網址
     */
    fun extractUrls(text: String): List<String> {
        val urls = mutableListOf<String>()
        val matcher = Patterns.WEB_URL.matcher(text)
        while (matcher.find()) {
            urls.add(matcher.group())
        }
        return urls
    }

    /**
     * 辨識是否為可疑網址 (初步過濾)
     */
    fun isSuspicious(url: String): Boolean {
        return try {
            val uri = URI(if (url.startsWith("http")) url else "http://$url")
            val host = uri.host?.lowercase() ?: ""
            
            // 1. 檢查是否為 IP 地址 (釣魚網站常見手法)
            val ipPattern = "^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$".toRegex()
            if (ipPattern.matches(host)) return true

            // 2. 檢查是否包含過多混淆符號
            if (host.count { it == '-' || it == '_' } > 3) return true
            
            // 3. 檢查常見詐騙關鍵字 (針對登入或驗證)
            val suspiciousKeywords = listOf("login", "verify", "secure", "signin", "update-password", "free-gift")
            if (suspiciousKeywords.any { host.contains(it) }) return true

            false
        } catch (e: Exception) {
            true
        }
    }
}
