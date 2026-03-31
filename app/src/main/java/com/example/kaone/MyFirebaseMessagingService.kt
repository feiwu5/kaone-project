package com.example.kaone

import com.example.kaone.ui.showLocalNotification
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    /**
     * 當 FCM 令牌更新時（例如首次安裝或清除資料後），將新令牌存入 Firestore 的用戶資料中
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            FirebaseFirestore.getInstance().collection("users").document(userId)
                .update("fcmToken", token)
        }
    }

    /**
     * 當 App 在前台收到通知，或收到資料訊息（Data Message）時觸發
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // 處理通知內容
        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: "新通知"
        val body = remoteMessage.notification?.body ?: remoteMessage.data["content"] ?: ""

        // 使用我們之前寫好的 NotificationUtils 彈出通知
        showLocalNotification(applicationContext, title, body)
    }
}
