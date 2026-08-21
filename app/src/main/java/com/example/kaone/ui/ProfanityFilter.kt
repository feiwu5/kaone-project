package com.example.kaone.ui

object ProfanityFilter {
    // 這裡可以定義違禁詞清單，之後您可以自行修改或增加
    private val BANNED_WORDS = listOf(
        "幹你娘", "幹您娘", "你娘", "你媽", "他媽的", "他娘的", "他媽", "他娘", "三小", "啥小", "有病",
        "去你的", "去你媽的", "你媽逼", "妳媽", "妳媽的", "嗎的", "媽的", "馬的", "衝三小", "衝啥小", "塞拎娘",
        "30678", "87", "78", "北七", "八七", "白痴", "白癡", "白吃", "賤人", "婊子", "臭婊", "bitch",
        "Bitch", "笨蛋", "腦袋有問題", "腦子有問題", "頭殼有問題", "腦袋有洞", "腦子有洞", "雞巴", "雞掰", "雞拜", "雞敗",
        "機掰", "機敗", "機拜", "機八", "擊巴", "擊敗", "擊拜", "擊掰", "吉掰", "靠北", "靠杯", "靠邀", "哇靠", "哇幹",
        "啊幹", "啊靠", "阿幹", "阿靠", "靠背", "塞你娘", "吃大便", "大便", "吉掰", "靠北", "靠杯", "靠邀", "哇靠", "哇幹",

    )

    /**
     * 檢查文字是否包含違禁詞
     * @return 如果包含違禁詞返回 true，否則返回 false
     */
    fun containsProfanity(text: String): Boolean {
        val lowerText = text.lowercase()
        return BANNED_WORDS.any { lowerText.contains(it.lowercase()) }
    }

    /**
     * 檢查多個欄位是否包含違禁詞
     * @return 第一個發現違禁詞的欄位名稱，若無則返回 null
     */
    fun checkFields(fields: Map<String, String>): String? {
        for ((name, value) in fields) {
            if (containsProfanity(value)) return name
        }
        return null
    }
}
