package com.example.kaone.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    userId: String,
    onBack: () -> Unit,
    onNavigateToChat: (String) -> Unit, // roomId
    onNavigateToCard: (String, String) -> Unit // cardId, ownerId
) {
    val db = FirebaseFirestore.getInstance()
    var notifications by remember { mutableStateOf<List<KaNotification>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedNotification by remember { mutableStateOf<KaNotification?>(null) }
    var reportedCard by remember { mutableStateOf<KpopCard?>(null) }

    fun cleanText(text: String): String {
        // 移除成員中文名字，支援半形 | 與全形 ｜ (例如: 《Liz|金智媛》 -> 《Liz》)
        return text.replace(Regex("[|｜][^》\\s,，。]+"), "")
    }

    LaunchedEffect(userId) {
        if (userId.isNotEmpty()) {
            db.collection("notifications")
                .whereEqualTo("userId", userId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("KaNotification", "監聽通知失敗: ${error.message}", error)
                        isLoading = false
                        return@addSnapshotListener
                    }
                    
                    val list = snapshot?.documents?.mapNotNull { doc ->
                        doc.toObject(KaNotification::class.java)?.copy(id = doc.id)
                    } ?: emptyList()
                    
                    notifications = list.sortedByDescending { it.timestamp }
                    isLoading = false
                }
        }
    }

    // 當選中檢舉通知時，嘗試獲取相關的小卡資訊
    LaunchedEffect(selectedNotification) {
        reportedCard = null
        if (selectedNotification?.type == "report" && selectedNotification?.relatedId?.isNotEmpty() == true) {
            val rid = selectedNotification!!.relatedId
            db.collection("cards").document(rid).get().addOnSuccessListener { doc ->
                if (doc.exists()) {
                    reportedCard = doc.toKpopCard()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("通知中心", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    val unreadNotifications = notifications.filter { !it.isRead }
                    if (unreadNotifications.isNotEmpty()) {
                        TextButton(onClick = {
                            val unreadIds = unreadNotifications.map { it.id }
                            notifications = notifications.map { if (it.id in unreadIds) it.copy(isRead = true) else it }
                            
                            val batch = db.batch()
                            unreadIds.forEach { id ->
                                batch.update(db.collection("notifications").document(id), "isRead", true)
                            }
                            batch.commit()
                        }) {
                            Text("全部標為已讀")
                        }
                    }
                }
            )
        }
    ) { p ->
        Box(Modifier.padding(p).fillMaxSize().background(Color(0xFFF7F8FA))) {
            if (isLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else if (notifications.isEmpty()) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.NotificationsNone, null, Modifier.size(64.dp), tint = Color.LightGray)
                    Spacer(Modifier.height(16.dp))
                    Text("目前沒有通知", color = Color.Gray)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(notifications, key = { it.id }) { notification ->
                        NotificationItem(notification, cleanText = ::cleanText, onClick = {
                            if (!notification.isRead) {
                                notifications = notifications.map {
                                    if (it.id == notification.id) it.copy(isRead = true) else it
                                }
                                db.collection("notifications").document(notification.id).update("isRead", true)
                            }
                            
                            if (notification.type == "report") {
                                selectedNotification = notification
                            } else if (notification.relatedId.isNotEmpty()) {
                                when (notification.type) {
                                    "chat", "trade_proposal" -> onNavigateToChat(notification.relatedId)
                                    "card", "match" -> onNavigateToCard(notification.relatedId, "")
                                    else -> selectedNotification = notification
                                }
                            } else {
                                selectedNotification = notification
                            }
                        })
                        HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFEEEEEE))
                    }
                }
            }
        }
    }

    if (selectedNotification != null) {
        val n = selectedNotification!!
        AlertDialog(
            onDismissRequest = { selectedNotification = null },
            title = { 
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when(n.type) {
                            "report" -> Icons.Default.Warning
                            "match" -> Icons.Default.AutoAwesome
                            "review" -> Icons.Default.Star
                            else -> Icons.Default.Notifications
                        },
                        contentDescription = null,
                        tint = when(n.type) {
                            "report" -> Color.Red
                            "match" -> Color(0xFFEC407A)
                            "review" -> Color(0xFFFFD700)
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(n.title, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(cleanText(n.content), fontSize = 16.sp, lineHeight = 24.sp)
                    
                    val displayImage = n.relatedImage.ifEmpty { reportedCard?.imageUrl ?: "" }
                    
                    if (n.type == "report" && displayImage.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text("相關內容：", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth().clickable {
                                if (reportedCard != null) {
                                    onNavigateToCard(reportedCard!!.id, reportedCard!!.userId)
                                    selectedNotification = null
                                }
                            }
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(
                                    model = displayImage,
                                    contentDescription = null,
                                    modifier = Modifier.size(70.dp).clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    if (reportedCard != null) {
                                        val displayMember = reportedCard!!.memberName.split(Regex("[|｜]")).first()
                                        val displayGroup = reportedCard!!.groupName.split(Regex("[|｜]")).first()
                                        Text(displayMember, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(displayGroup, fontSize = 12.sp, color = Color.Gray)
                                        Text("點擊可查看詳情 >", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                                    } else {
                                        Text("內容已被移除或無法預覽", fontSize = 14.sp, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    n.timestamp?.let {
                        Text(
                            text = "時間：" + SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(it.toDate()),
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    
                    if (n.type == "report") {
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            color = Color(0xFFFFEBEE),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "請遵守 KaOne! 社區規範。若內容確實違反規定，該上架內容將被移除，嚴重者將暫停帳號權限。",
                                modifier = Modifier.padding(12.dp),
                                fontSize = 12.sp,
                                color = Color(0xFFC62828),
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { selectedNotification = null }) {
                    Text("我了解了")
                }
            }
        )
    }
}

@Composable
fun NotificationItem(notification: KaNotification, cleanText: (String) -> String, onClick: () -> Unit) {
    val icon = when (notification.type) {
        "trade_proposal" -> Icons.Default.SwapHoriz
        "match" -> Icons.Default.AutoAwesome
        "chat" -> Icons.Default.ChatBubble
        "report" -> Icons.Default.Report
        "review" -> Icons.Default.Star
        else -> Icons.Default.Notifications
    }
    
    val iconColor = when (notification.type) {
        "trade_proposal" -> Color(0xFF5C6BC0)
        "match" -> Color(0xFFEC407A)
        "chat" -> Color(0xFF4CAF50)
        "report" -> Color(0xFFF44336)
        "review" -> Color(0xFFFFD700)
        else -> Color.Gray
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (notification.isRead) Color.White else Color(0xFFE3F2FD))
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = iconColor.copy(alpha = 0.1f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(24.dp))
            }
        }
        
        Spacer(Modifier.width(16.dp))
        
        Column(Modifier.weight(1f)) {
            Text(notification.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
            Text(cleanText(notification.content), fontSize = 14.sp, color = Color.DarkGray, maxLines = 2)
            
            if (notification.type == "report" && notification.relatedImage.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                AsyncImage(
                    model = notification.relatedImage,
                    contentDescription = "被檢舉內容",
                    modifier = Modifier.size(80.dp, 60.dp).clip(RoundedCornerShape(4.dp)).background(Color.LightGray),
                    contentScale = ContentScale.Crop
                )
            }
            
            Spacer(Modifier.height(8.dp))
            notification.timestamp?.let {
                Text(
                    SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(it.toDate()),
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}
