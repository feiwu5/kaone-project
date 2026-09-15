package com.example.kaone.ui

import androidx.compose.runtime.Immutable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.PropertyName

sealed class EncyclopediaSearchResult {
    data class Group(val groupKey: String) : EncyclopediaSearchResult()
    data class Member(val groupKey: String, val memberKey: String) : EncyclopediaSearchResult()
    data class Album(val groupKey: String, val albumName: String) : EncyclopediaSearchResult()
}

// 輔助函式：從原始資料中安全提取名稱，精準過濾掉縮寫與網址
fun String.getCleanName(): String {
    return this.split("|")
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() && !it.startsWith("http") } 
        ?: this
}

@Immutable
data class KpopCard(
    val id: String = "",
    val memberName: String = "",
    val groupName: String = "",
    val ownerName: String = "",
    val imageUrl: String = "",
    val ownerProfileImageUrl: String = "",
    val wishlist: String = "",
    val remarks: String = "",
    val wishlistImageUrls: List<String> = emptyList(),
    val userId: String = "",
    val status: String = "available",
    val createdAt: Timestamp? = null,
    val location: String = "",
    val wishGroupList: List<String> = emptyList(),
    val wishMemberList: List<String> = emptyList(),
    val memberList: List<String> = emptyList(),
    val cardType: String = ""
)

@Immutable
data class KpopEvent(
    val id: String = "",
    val title: String = "",
    val type: String = "生日咖啡廳",
    val groupName: String = "",
    val location: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val imageUrl: String = "",
    val description: String = "",
    val userId: String = "",
    val createdAt: Timestamp? = null
)

@Immutable
data class KpopComment(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val userProfileImage: String = "",
    val text: String = "",
    val timestamp: Timestamp? = null
)

@Immutable
data class ChatMessage(
    val id: String = "", 
    val senderId: String = "", 
    val senderName: String = "", // 新增：發送者暱稱 (用於群組顯示)
    val senderImage: String = "", // 新增：發送者頭像
    val text: String = "", 
    val messageType: String = "text",
    val mediaUrl: String = "", 
    val timestamp: Timestamp? = null, 
    val cardImage: String = "",
    val cardMember: String = "", 
    val cardGroup: String = "", 
    val tradeTargetCardId: String = "",
    val tradeOfferCardId: String = "", 
    val tradeOfferImage: String = "", 
    val tradeOfferMember: String = "",
    val tradeTargetImage: String = "", 
    var tradeStatus: String = "pending", 
    val meetingLocation: String = "",
    val tradeNotes: String = "", 
    val recipientName: String = "", 
    val recipientPhone: String = "", 
    val meetingDate: String = "",
    val reviewedBy: List<String> = emptyList(),
    @get:PropertyName("isRead") @set:PropertyName("isRead") var isRead: Boolean = false
)

@Immutable
data class ChatRoom(
    val id: String = "", 
    val participantIds: List<String> = emptyList(), 
    val lastMessage: String = "",
    val lastMessageTime: Timestamp? = null, 
    val activeInquiryCardId: String = "", 
    val unreadCount: Map<String, Int> = emptyMap(),
    // --- 群組相關欄位 ---
    @get:PropertyName("isGroup") val isGroup: Boolean = false,
    val roomName: String = "", 
    val roomImage: String = "",
    val createdBy: String = "",
    // ------------------
    var otherNickname: String = "" // 保持用於私訊顯示
)

data class KaNotification(
    val id: String = "",
    val userId: String = "",
    val type: String = "", // trade_proposal, match, chat, report, review, friend_request, friend_accept
    val title: String = "",
    val content: String = "",
    val timestamp: Timestamp? = null,
    @get:PropertyName("isRead") @set:PropertyName("isRead") var isRead: Boolean = false,
    val relatedId: String = "",
    val relatedImage: String = ""
)

fun DocumentSnapshot.toKpopCard(): KpopCard? {
    return try {
        val multiUrls = (get("wishlistImageUrls") as? List<*>)?.mapNotNull { it.toString() } ?: emptyList()
        val singleUrl = getString("wishlistImageUrl")
        val finalUrls = if (multiUrls.isEmpty() && singleUrl != null) listOf(singleUrl) else multiUrls

        KpopCard(
            id = id,
            memberName = getString("memberName") ?: "",
            groupName = getString("groupName") ?: "",
            ownerName = getString("ownerNickname") ?: "未知用戶",
            imageUrl = getString("imageUrl") ?: "",
            ownerProfileImageUrl = getString("ownerProfileImageUrl") ?: "",
            wishlist = getString("wishlist") ?: "",
            remarks = getString("remarks") ?: "",
            wishlistImageUrls = finalUrls,
            userId = getString("userId") ?: "",
            status = getString("status") ?: "available",
            createdAt = getTimestamp("createdAt"),
            location = getString("location") ?: "",
            wishGroupList = (get("wishGroupList") as? List<*>)?.mapNotNull { it.toString() } ?: emptyList(),
            wishMemberList = (get("wishMemberList") as? List<*>)?.mapNotNull { it.toString() } ?: emptyList(),
            memberList = (get("memberList") as? List<*>)?.mapNotNull { it.toString() } ?: emptyList(),
            cardType = getString("cardType") ?: ""
        )
    } catch (_: Exception) {
        null
    }
}
