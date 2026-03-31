package com.example.kaone.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.kaone.MainActivity
import com.example.kaone.R
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

private const val CHANNEL_ID = "kaone_notifications"
private const val CHANNEL_NAME = "KaOne 通知"

fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
            description = "用於顯示應用程式內的各種通知"
            enableLights(true)
            enableVibration(true)
            setShowBadge(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}

fun showLocalNotification(context: Context, title: String, content: String) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    // 建立點擊通知後要開啟的 Intent (跳轉到 MainActivity)
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    
    val pendingIntent = PendingIntent.getActivity(
        context, 
        0, 
        intent, 
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // 獲取 App Launcher 圖示作為大圖示
    val largeIcon = BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
    
    val builder = NotificationCompat.Builder(context, CHANNEL_ID)
        // 小圖示：必須是去背透明的圖示，否則在某些手機會顯示白色方塊
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setLargeIcon(largeIcon)
        .setContentTitle(title)
        .setContentText(content)
        // 設定優先權為最高，這在小米手機上非常重要
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setDefaults(NotificationCompat.DEFAULT_ALL)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)

    // 使用當前時間戳作為 ID，確保多條通知不會互相覆蓋
    notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
}

fun sendNotification(
    userId: String,
    type: String,
    title: String,
    content: String,
    relatedId: String = "",
    relatedImage: String = ""
) {
    if (userId.isEmpty()) return
    val db = FirebaseFirestore.getInstance()
    val notification = hashMapOf(
        "userId" to userId,
        "type" to type,
        "title" to title,
        "content" to content,
        "timestamp" to FieldValue.serverTimestamp(),
        "isRead" to false,
        "relatedId" to relatedId,
        "relatedImage" to relatedImage
    )
    db.collection("notifications").add(notification)
}
