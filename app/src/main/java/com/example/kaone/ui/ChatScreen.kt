package com.example.kaone.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import coil.compose.AsyncImage
import com.example.kaone.ui.theme.CloudinaryUploader
import com.google.firebase.Timestamp
import com.google.firebase.firestore.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(userId: String, targetRoomId: String? = null, inquiryCard: KpopCard? = null, onViewProfile: (String) -> Unit = {}, modifier: Modifier = Modifier) {
    var activeChatRoomId by remember { mutableStateOf(targetRoomId) }
    var otherUserName by remember { mutableStateOf("") }
    LaunchedEffect(targetRoomId) { if (targetRoomId != null) activeChatRoomId = targetRoomId }
    if (activeChatRoomId == null) ChatList(userId, onRoomClick = { id, name -> activeChatRoomId = id; otherUserName = name })
    else ChatRoomView(userId, activeChatRoomId!!, otherUserName, inquiryCard, onViewProfile = onViewProfile, onBack = { activeChatRoomId = null })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatList(userId: String, onRoomClick: (String, String) -> Unit) {
    val db = FirebaseFirestore.getInstance()
    var rooms by remember { mutableStateOf<List<ChatRoom>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var roomToDelete by remember { mutableStateOf<ChatRoom?>(null) }
    
    LaunchedEffect(userId) { 
        db.collection("chatRooms").whereArrayContains("participantIds", userId).addSnapshotListener { s, _ -> 
            s?.let { snapshot ->
                val fetchedRooms = snapshot.documents.map { doc ->
                    val raw = doc.get("unreadCount") as? Map<*, *>
                    val mapped = mutableMapOf<String, Int>()
                    raw?.forEach { (k, v) -> if (k is String) mapped[k] = (v as? Long)?.toInt() ?: 0 }
                    ChatRoom(
                        id = doc.id,
                        participantIds = @Suppress("UNCHECKED_CAST") (doc.get("participantIds") as? List<String> ?: emptyList()),
                        lastMessage = doc.getString("lastMessage") ?: "聊天",
                        lastMessageTime = doc.getTimestamp("lastMessageTime", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
                        activeInquiryCardId = doc.getString("activeInquiryCardId") ?: "",
                        unreadCount = mapped
                    )
                }
                
                // 異步獲取每個聊天室對對方的暱稱
                fetchedRooms.forEach { room ->
                    val otherId = room.participantIds.firstOrNull { it != userId } ?: ""
                    if (otherId.isNotEmpty()) {
                        db.collection("users").document(otherId).get().addOnSuccessListener { userDoc ->
                            room.otherNickname = userDoc.getString("nickname") ?: "用戶"
                            // 強制觸發重繪 (如果需要)
                            rooms = rooms.toList() 
                        }
                    }
                }
                rooms = fetchedRooms.sortedByDescending { r -> r.lastMessageTime }
            } 
        } 
    }

    val filteredRooms = rooms.filter { room ->
        searchQuery.isEmpty() || room.otherNickname.contains(searchQuery, ignoreCase = true)
    }

    if (roomToDelete != null) {
        AlertDialog(
            onDismissRequest = { roomToDelete = null },
            title = { Text("刪除對話紀錄") },
            text = { Text("確定要刪除與 ${roomToDelete?.otherNickname} 的所有對話嗎？此動作將永久移除雙方的對話歷史。") },
            confirmButton = {
                TextButton(onClick = {
                    roomToDelete?.let { room ->
                        db.collection("chatRooms").document(room.id).delete().addOnSuccessListener {
                            roomToDelete = null
                        }
                    }
                }) { Text("確定刪除", color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { roomToDelete = null }) { Text("取消") } }
        )
    }

    Scaffold(
        topBar = { 
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                TopAppBar(title = { Text("我的對話", fontWeight = FontWeight.Bold) })
                // 搜尋框
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(44.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFFF1F3F4)).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Search, null, modifier = Modifier.size(20.dp), tint = Color.Gray)
                        Spacer(Modifier.width(12.dp))
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 15.sp, color = Color.Black),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { innerTextField ->
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = "搜尋聯絡人...",
                                            fontSize = 15.sp,
                                            color = Color.Gray,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp), tint = Color.Gray)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFEEEEEE))
            }
        }
    ) { p ->
        if (filteredRooms.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(p), Alignment.Center) { 
                Text(if(searchQuery.isEmpty()) "尚無聊天紀錄" else "找不到相關聯絡人", color = Color.Gray) 
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(p)) {
                items(filteredRooms.size) { i ->
                    ChatListItem(userId, filteredRooms[i], onRoomClick, onDelete = { roomToDelete = filteredRooms[i] })
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), 0.5.dp, Color(0xFFF0F0F0))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatListItem(userId: String, room: ChatRoom, onRoomClick: (String, String) -> Unit, onDelete: () -> Unit) {
    val db = FirebaseFirestore.getInstance(); var otherName by remember { mutableStateOf(room.otherNickname) }; var otherImageUrl by remember { mutableStateOf("") }
    val otherId = room.participantIds.firstOrNull { it != userId } ?: ""
    val unread = room.unreadCount[userId] ?: 0
    
    LaunchedEffect(otherId) { 
        if (otherId.isNotEmpty()) db.collection("users").document(otherId).get().addOnSuccessListener { 
            otherName = it.getString("nickname") ?: "用戶"
            otherImageUrl = it.getString("profileImageUrl") ?: "" 
        } 
    }
    
    Row(Modifier.fillMaxWidth().combinedClickable(onClick = { onRoomClick(room.id, otherName) }, onLongClick = onDelete).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(54.dp).clip(CircleShape).background(Color.LightGray)) { if (otherImageUrl.isNotEmpty()) AsyncImage(model = otherImageUrl, contentDescription = null, contentScale = ContentScale.Crop) }
        Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(otherName, fontWeight = FontWeight.Bold, fontSize = 16.sp); Text(room.lastMessage, fontSize = 14.sp, color = if(unread > 0) Color.Black else Color.Gray, maxLines = 1, fontWeight = if(unread > 0) FontWeight.Medium else FontWeight.Normal) }
        Column(horizontalAlignment = Alignment.End) { 
            room.lastMessageTime?.let { Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(it.toDate()), fontSize = 12.sp, color = Color.LightGray) }
            if (unread > 0) {
                Surface(color = Color.Red, shape = CircleShape, modifier = Modifier.padding(top = 4.dp)) {
                    Text(unread.toString(), color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatRoomView(userId: String, roomId: String, initialOtherUserName: String, initialCard: KpopCard? = null, onViewProfile: (String) -> Unit, onBack: () -> Unit) {
    val db = FirebaseFirestore.getInstance(); var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }; var inputText by remember { mutableStateOf("") }
    var inquiryCard by remember { mutableStateOf(initialCard) }; var showInquiry by remember { mutableStateOf(true) }; var previewImageUrl by remember { mutableStateOf<String?>(null) }
    val otherId = remember { mutableStateOf("") }; var otherImageUrl by remember { mutableStateOf("") }
    var otherUserName by remember { mutableStateOf(initialOtherUserName) }
    var showMyCardsDialog by remember { mutableStateOf(false) }; var showTradeFormDialog by remember { mutableStateOf(false) }
    var selectedOfferCard by remember { mutableStateOf<KpopCard?>(null) }; var myAvailableCards by remember { mutableStateOf<List<KpopCard>>(emptyList()) }
    var tradeMethod by remember { mutableStateOf("面交") }; var mAddr by remember { mutableStateOf("") }; var mDate by remember { mutableStateOf("") }; var sInfo by remember { mutableStateOf("") }
    var rName by remember { mutableStateOf("") }; var rPhone by remember { mutableStateOf("") }; var oMethod by remember { mutableStateOf("") }; var tNotes by remember { mutableStateOf("") }; var cMember by remember { mutableStateOf("") }
    var isUploading by remember { mutableStateOf(false) }; var isUploadingTrade by remember { mutableStateOf(false) }
    var reviewMsgId by remember { mutableStateOf<String?>(null) }
    
    var chatBgColor by rememberSaveable { mutableLongStateOf(0xFF8BA2B5L) }
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    val listState = rememberLazyListState(); val scope = rememberCoroutineScope(); val context = LocalContext.current

    LaunchedEffect(roomId) {
        db.collection("chatRooms").document(roomId).update("unreadCount.$userId", 0)
        db.collection("chatRooms").document(roomId).get().addOnSuccessListener { d -> 
            val oid = (d.get("participantIds") as? List<*>)?.mapNotNull { it.toString() }?.firstOrNull { it != userId } ?: ""
            otherId.value = oid
            if (oid.isNotEmpty()) db.collection("users").document(oid).get().addOnSuccessListener { 
                otherImageUrl = it.getString("profileImageUrl") ?: "" 
                if (otherUserName.isEmpty() || otherUserName == "讀取中...") {
                    otherUserName = it.getString("nickname") ?: "用戶"
                }
            }
        }
        db.collection("chatRooms").document(roomId).addSnapshotListener { s, _ -> val cid = s?.getString("activeInquiryCardId") ?: ""; if (cid.isNotEmpty()) db.collection("cards").document(cid).get().addOnSuccessListener { d -> if (d.exists()) {
            inquiryCard = d.toKpopCard()
        } } }
        db.collection("chatRooms").document(roomId).collection("messages").orderBy("timestamp", Query.Direction.ASCENDING).addSnapshotListener { snapshot, _ -> snapshot?.let { messages = it.documents.mapNotNull { d -> d.toObject(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.copy(id = d.id) } } }
    }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }

    fun sendMsg(t: String, type: String = "text", url: String = "", extra: Map<String, Any>? = null) {
        val data = mutableMapOf("senderId" to userId, "messageType" to type, "timestamp" to FieldValue.serverTimestamp()); if (t.isNotEmpty()) data["text"] = t; if (url.isNotEmpty()) data["mediaUrl"] = url; extra?.let { data.putAll(it) }
        db.collection("chatRooms").document(roomId).collection("messages").add(data)
        db.collection("chatRooms").document(roomId).update(mapOf(
            "lastMessage" to (if(type=="text") t else if(type=="trade_proposal") "[交換提案]" else "[媒體]"), 
            "lastMessageTime" to FieldValue.serverTimestamp(),
            "unreadCount.${otherId.value}" to FieldValue.increment(1)
        ))
        
        // 發送通知 (FCM / 本地橫幅)
        val notifTitle: String
        val notifContent: String
        when (type) {
            "trade_proposal" -> {
                notifTitle = "收到新的交換提案"
                notifContent = "有人向您發起了交換提案，快去查看吧！"
            }
            "image" -> {
                notifTitle = otherUserName
                notifContent = "傳送了一張圖片"
            }
            "card" -> {
                notifTitle = otherUserName
                notifContent = "分享了一張小卡"
            }
            else -> {
                notifTitle = otherUserName
                notifContent = t
            }
        }
        // 傳送通知，類別標註為 chat_silent 以便在 NotificationScreen 排除
        sendNotification(otherId.value, "chat_silent", notifTitle, notifContent, roomId)
    }

    val mediaLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u -> u?.let { isUploading = true; CloudinaryUploader.uploadMedia(context, it, scope, onSuccess = { url -> sendMsg("", "image", url); isUploading = false }, onFailure = { isUploading = false }) } }
    val tradeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u -> u?.let { isUploadingTrade = true; CloudinaryUploader.uploadMedia(context, it, scope, onSuccess = { url -> selectedOfferCard = KpopCard(id = "custom", imageUrl = url); showMyCardsDialog = false; showTradeFormDialog = true; isUploadingTrade = false }, onFailure = { isUploadingTrade = false }) } }

    fun handleTradeAction(msg: ChatMessage, newStatus: String) {
        db.collection("chatRooms").document(roomId).collection("messages").document(msg.id).update("tradeStatus", newStatus).addOnSuccessListener {
            val lastMsgText = when(newStatus) { "accepted" -> "交換進行中..."; "completed" -> "交換已完成 🎉"; else -> "提案狀態更新" }
            if (newStatus == "accepted") { if (msg.tradeTargetCardId.isNotEmpty()) db.collection("cards").document(msg.tradeTargetCardId).update("status", "trading"); if (msg.tradeOfferCardId != "custom" && msg.tradeOfferCardId.isNotEmpty()) db.collection("cards").document(msg.tradeOfferCardId).update("status", "trading") }
            else if (newStatus == "completed") { if (msg.tradeTargetCardId.isNotEmpty()) db.collection("cards").document(msg.tradeTargetCardId).update("status", "exchanged"); if (msg.tradeOfferCardId != "custom" && msg.tradeOfferCardId.isNotEmpty()) db.collection("cards").document(msg.tradeOfferCardId).update("status", "exchanged") }
            db.collection("chatRooms").document(roomId).update(mapOf("lastMessage" to lastMsgText, "lastMessageTime" to FieldValue.serverTimestamp(), "unreadCount.${otherId.value}" to FieldValue.increment(1)))
            
            // 狀態更新通知
            val statusTitle = when(newStatus) {
                "accepted" -> "交換提案已接受"
                "completed" -> "交換已順利完成"
                "declined" -> "交換提案已被拒絕"
                "cancelled" -> "交換提案已取消"
                else -> "交換狀態更新"
            }
            sendNotification(otherId.value, "chat_silent", statusTitle, "您的交換提案狀態已變更為：${lastMsgText}", roomId)
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("刪除對話") },
            text = { Text("確定要刪除與 $otherUserName 的所有對話紀錄嗎？此動作將永久移除雙方的對話歷史，且無法復原。") },
            confirmButton = {
                TextButton(onClick = {
                    db.collection("chatRooms").document(roomId).delete().addOnSuccessListener {
                        Toast.makeText(context, "對話已刪除", Toast.LENGTH_SHORT).show()
                        onBack()
                    }
                }) { Text("確定刪除", color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") } }
        )
    }

    Scaffold(
        topBar = { 
            TopAppBar(
                title = { Text(if(otherUserName.isEmpty()) "聊天" else otherUserName, fontWeight = FontWeight.Bold) }, 
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "選單") }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("查看個人主頁") },
                                onClick = { showMenu = false; if (otherId.value.isNotEmpty()) onViewProfile(otherId.value) },
                                leadingIcon = { Icon(Icons.Default.Person, null) }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("更換背景顏色") },
                                onClick = { },
                                leadingIcon = { Icon(Icons.Default.Palette, null) },
                                enabled = false
                            )
                            val bgOptions = listOf(
                                "預設藍" to 0xFF8BA2B5L, "柔和白" to 0xFFF5F5F5L, "薄荷綠" to 0xFFC8E6C9L, "經典灰" to 0xFFCFD8DCL, "質感黑" to 0xFF263238L
                            )
                            bgOptions.forEach { (name, color) ->
                                DropdownMenuItem(
                                    text = { Text("  • $name") },
                                    onClick = { chatBgColor = color; showMenu = false }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("刪除對話紀錄", color = Color.Red) },
                                onClick = { showMenu = false; showDeleteConfirm = true },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                            )
                        }
                    }
                }
            ) 
        },
        bottomBar = { Surface(tonalElevation = 4.dp) { Column { if (isUploading || isUploadingTrade) LinearProgressIndicator(Modifier.fillMaxWidth()); Row(modifier = Modifier.padding(8.dp).fillMaxWidth().navigationBarsPadding(), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = { mediaLauncher.launch("image/*") }) { Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary) }; OutlinedTextField(value = inputText, onValueChange = { inputText = it }, modifier = Modifier.weight(1f), placeholder = { Text("訊息") }, shape = RoundedCornerShape(24.dp), maxLines = 3); IconButton(onClick = { if (inputText.isNotBlank()) { val t = inputText.trim(); inputText = ""; sendMsg(t) } }, colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White)) { Icon(Icons.AutoMirrored.Filled.Send, null) } } } } }
    ) { p ->
        Column(Modifier.fillMaxSize().padding(p).background(Color(chatBgColor))) {
            if (showInquiry && inquiryCard != null) Surface(modifier = Modifier.fillMaxWidth(), color = Color.White.copy(alpha = 0.9f)) { Column(Modifier.padding(12.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(text = "目前詢問商品", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.weight(1f)); IconButton(onClick = { showInquiry = false }, Modifier.size(20.dp)) { Icon(Icons.Default.Close, null, tint = Color.LightGray, modifier = Modifier.size(14.dp)) } }; Surface(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), color = Color.White, shape = RoundedCornerShape(8.dp), border = BorderStroke(0.5.dp, Color.LightGray)) { Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) { AsyncImage(model = inquiryCard!!.imageUrl, contentDescription = null, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)).clickable { previewImageUrl = inquiryCard!!.imageUrl }, contentScale = ContentScale.Crop); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(text = inquiryCard!!.memberName.split(", ").joinToString(", ") { it.split("|").first() }, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1); Text(text = inquiryCard!!.groupName.split("|").first(), fontSize = 11.sp, color = Color.Gray) }; if (inquiryCard!!.userId != userId) { Button(onClick = { db.collection("cards").whereEqualTo("userId", userId).whereEqualTo("status", "available").get().addOnSuccessListener { myAvailableCards = it.documents.mapNotNull { d -> d.toKpopCard() }; showMyCardsDialog = true } }, shape = RoundedCornerShape(16.dp), modifier = Modifier.height(30.dp), contentPadding = PaddingValues(horizontal = 12.dp)) { Text("交換", fontSize = 11.sp) }; Spacer(Modifier.width(8.dp)) }; OutlinedButton(onClick = { sendMsg("", "card", "", mapOf("cardImage" to inquiryCard!!.imageUrl, "cardMember" to inquiryCard!!.memberName, "cardGroup" to inquiryCard!!.groupName)) }, shape = RoundedCornerShape(16.dp), modifier = Modifier.height(30.dp), contentPadding = PaddingValues(horizontal = 12.dp)) { Text("傳送", fontSize = 11.sp) } } } } }
            LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
                itemsIndexed(messages) { index, m ->
                    val isMe = m.senderId == userId; val timeStr = m.timestamp?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(it.toDate()) } ?: ""
                    
                    // 日期顯示邏輯
                    val showDate = if (index == 0) {
                        m.timestamp != null
                    } else {
                        val prevMsg = messages[index - 1]
                        if (m.timestamp != null && prevMsg.timestamp != null) {
                            val cal1 = Calendar.getInstance().apply { setTime(m.timestamp.toDate()) }
                            val cal2 = Calendar.getInstance().apply { setTime(prevMsg.timestamp.toDate()) }
                            cal1.get(Calendar.YEAR) != cal2.get(Calendar.YEAR) || cal1.get(Calendar.DAY_OF_YEAR) != cal2.get(Calendar.DAY_OF_YEAR)
                        } else false
                    }

                    if (showDate && m.timestamp != null) {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                            Surface(color = Color.Black.copy(alpha = 0.1f), shape = RoundedCornerShape(12.dp)) {
                                Text(
                                    text = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault()).format(m.timestamp.toDate()),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                                    fontSize = 11.sp,
                                    color = if(chatBgColor == 0xFF263238L) Color.LightGray else Color.White
                                )
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Top) {
                        if (!isMe) {
                            Box(Modifier.size(36.dp).clip(CircleShape).background(Color.LightGray)) {
                                if (otherImageUrl.isNotEmpty()) AsyncImage(model = otherImageUrl, contentDescription = null, contentScale = ContentScale.Crop)
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        Row(verticalAlignment = Alignment.Bottom) {
                            if (isMe) Text(text = timeStr, fontSize = 10.sp, color = (if(chatBgColor == 0xFF263238L) Color.LightGray else Color.White).copy(alpha = 0.8f), modifier = Modifier.padding(end = 4.dp))
                            when (m.messageType) {
                                "trade_proposal" -> TradeProposalItem(userId, m, isMe, onAccept = { handleTradeAction(m, "accepted") }, onDecline = { handleTradeAction(m, "declined") }, onCancel = { handleTradeAction(m, "cancelled") }, onComplete = { handleTradeAction(m, "completed") }, onReview = { reviewMsgId = m.id })
                                "card" -> Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.width(200.dp).clickable { previewImageUrl = m.cardImage }, colors = CardDefaults.cardColors(containerColor = Color.White)) { Column { AsyncImage(model = m.cardImage, contentDescription = null, modifier = Modifier.fillMaxWidth().height(150.dp), contentScale = ContentScale.Crop); Column(Modifier.padding(12.dp)) { Text(text = m.cardMember.split(", ").joinToString(", ") { it.split("|").first() }, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text(text = m.cardGroup.split("|").first(), fontSize = 12.sp, color = Color.Gray) } } }
                                else -> Surface(
                                    color = if (isMe) Color(0xFF95EC69) else Color.White,
                                    shape = RoundedCornerShape(12.dp).copy(
                                        topStart = if (isMe) CornerSize(12.dp) else CornerSize(2.dp),
                                        topEnd = if (isMe) CornerSize(2.dp) else CornerSize(12.dp)
                                    )
                                ) { 
                                    when (m.messageType) { 
                                        "text" -> Text(text = m.text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = Color.Black, fontSize = 15.sp)
                                        "image" -> AsyncImage(model = m.mediaUrl, contentDescription = null, modifier = Modifier.sizeIn(maxWidth = 200.dp, maxHeight = 300.dp).clip(RoundedCornerShape(8.dp)).clickable { previewImageUrl = m.mediaUrl }, contentScale = ContentScale.Crop) 
                                    } 
                                }
                            }
                            if (!isMe) Text(text = timeStr, fontSize = 10.sp, color = (if(chatBgColor == 0xFF263238L) Color.LightGray else Color.White).copy(alpha = 0.8f), modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }
        }
    }

    if (showMyCardsDialog) { 
        AlertDialog(
            onDismissRequest = { showMyCardsDialog = false }, 
            title = { Text("選擇提案小卡", fontWeight = FontWeight.Bold) }, 
            text = { 
                LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.height(350.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { 
                    item { Card(modifier = Modifier.height(130.dp).clickable { tradeLauncher.launch("image/*") }, border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))) { Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Icon(Icons.Default.AddAPhoto, null, tint = MaterialTheme.colorScheme.primary); Text(text = "上傳新圖片", fontSize = 12.sp) } } }
                    items(myAvailableCards) { c -> Card(modifier = Modifier.clickable { selectedOfferCard = c; showMyCardsDialog = false; showTradeFormDialog = true }) { Column { AsyncImage(model = c.imageUrl, contentDescription = null, modifier = Modifier.fillMaxWidth().height(100.dp), contentScale = ContentScale.Crop); Text(text = c.memberName.split(", ").joinToString(", ") { it.split("|").first() }, fontSize = 12.sp, modifier = Modifier.padding(4.dp), maxLines = 1) } } } 
                } 
            }, 
            confirmButton = { TextButton(onClick = { showMyCardsDialog = false }) { Text("取消") } }
        ) 
    }
    
    if (showTradeFormDialog && selectedOfferCard != null) { 
        AlertDialog(
            onDismissRequest = { showTradeFormDialog = false }, 
            title = { Text("提案表", fontWeight = FontWeight.Bold) }, 
            text = { 
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) { 
                    if (selectedOfferCard!!.id == "custom") OutlinedTextField(cMember, { cMember = it }, label = { Text("成員名") }, modifier = Modifier.fillMaxWidth()) 
                    else Text(text = "卡片：${selectedOfferCard!!.memberName.split(", ").joinToString(", ") { it.split("|").first() }}", fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("面交", "郵寄", "其他").forEach { m -> FilterChip(selected = tradeMethod == m, onClick = { tradeMethod = m }, label = { Text(m) }) } }
                    when (tradeMethod) { 
                        "面交" -> { OutlinedTextField(mAddr, { mAddr = it }, label = { Text("地點") }); OutlinedTextField(mDate, { mDate = it }, label = { Text("時間") }) }
                        "郵寄" -> { OutlinedTextField(sInfo, { sInfo = it }, label = { Text("地址/門市") }); OutlinedTextField(rName, { rName = it }, label = { Text("姓名") }); OutlinedTextField(rPhone, { rPhone = it }, label = { Text("電話") }) }
                        "其他" -> { OutlinedTextField(oMethod, { oMethod = it }, label = { Text("方式") }); OutlinedTextField(mAddr, { mAddr = it }, label = { Text("詳情") }); OutlinedTextField(rName, { rName = it }, label = { Text("姓名") }); OutlinedTextField(rPhone, { rPhone = it }, label = { Text("電話") }) } 
                    }
                    OutlinedTextField(tNotes, { tNotes = it }, label = { Text("備註") }, modifier = Modifier.fillMaxWidth()) 
                } 
            }, 
            confirmButton = { 
                Button(onClick = { 
                    val fn = if (selectedOfferCard!!.id == "custom") cMember else selectedOfferCard!!.memberName
                    val loc = when(tradeMethod) { "面交" -> "面交: $mAddr"; "郵寄" -> "郵寄: $sInfo"; else -> "$oMethod: $mAddr" }
                    if (fn.isBlank() || (tradeMethod == "面交" && mAddr.isBlank()) || (tradeMethod == "郵寄" && sInfo.isBlank())) { Toast.makeText(context, "請填寫完整資訊", Toast.LENGTH_SHORT).show(); return@Button }
                    sendMsg("", "trade_proposal", "", mapOf("tradeTargetCardId" to (inquiryCard?.id ?: ""), "tradeTargetImage" to (inquiryCard?.imageUrl ?: ""), "tradeOfferCardId" to selectedOfferCard!!.id, "tradeOfferImage" to selectedOfferCard!!.imageUrl, "tradeOfferMember" to fn, "meetingLocation" to loc, "meetingDate" to mDate, "tradeNotes" to tNotes, "recipientName" to rName, "recipientPhone" to rPhone, "tradeStatus" to "pending"))
                    showTradeFormDialog = false 
                }) { Text("發起提案") } 
            }
        ) 
    }
    
    if (previewImageUrl != null) {
        Dialog(onDismissRequest = { previewImageUrl = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) { 
            Box(Modifier.fillMaxSize().background(Color.Black)) { 
                AsyncImage(model = previewImageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                IconButton(onClick = { previewImageUrl = null }, modifier = Alignment.TopEnd.let { Modifier.align(it) }.padding(16.dp)) { Icon(Icons.Default.Close, null, tint = Color.White) }
            } 
        }
    }
    
    if (reviewMsgId != null) {
        var rating by remember { mutableStateOf(0) }
        var comment by remember { mutableStateOf("") }
        var isSubmitting by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!isSubmitting) reviewMsgId = null },
            title = { Text("給予評價", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("為這次交換評分", fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
                    Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                        for (i in 1..5) {
                            Icon(
                                imageVector = if (i <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = null,
                                tint = if (i <= rating) Color(0xFFFFD700) else Color.Gray,
                                modifier = Modifier.size(36.dp).clickable(enabled = !isSubmitting) { rating = i }
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(value = comment, onValueChange = { comment = it }, label = { Text("分享你的交換體驗...") }, modifier = Modifier.fillMaxWidth().height(100.dp), enabled = !isSubmitting)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (rating == 0) { Toast.makeText(context, "請選擇評分", Toast.LENGTH_SHORT).show(); return@Button }
                        isSubmitting = true
                        val reviewData = hashMapOf("reviewerId" to userId, "revieweeId" to otherId.value, "rating" to rating, "comment" to comment, "timestamp" to FieldValue.serverTimestamp())
                        db.collection("reviews").add(reviewData).addOnSuccessListener {
                            db.collection("chatRooms").document(roomId).collection("messages").document(reviewMsgId!!).update("reviewedBy", FieldValue.arrayUnion(userId))
                            
                            // 發送通知
                            sendNotification(otherId.value, "chat_silent", "收到新的評價", "有人對您的交換進行了評價！", roomId)

                            Toast.makeText(context, "評價已送出", Toast.LENGTH_SHORT).show()
                            isSubmitting = false
                            reviewMsgId = null
                        }.addOnFailureListener {
                            isSubmitting = false
                            Toast.makeText(context, "評價送出失敗", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isSubmitting
                ) { if (isSubmitting) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Text("送出評價") }
            },
            dismissButton = { if (!isSubmitting) TextButton(onClick = { reviewMsgId = null }) { Text("取消") } }
        )
    }
}

@Composable
fun TradeProposalItem(userId: String, m: ChatMessage, isMe: Boolean, onAccept: () -> Unit, onDecline: () -> Unit, onCancel: () -> Unit, onComplete: () -> Unit, onReview: () -> Unit) {
    Card(modifier = Modifier.width(260.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE0E0E0))) {
        Column(Modifier.padding(12.dp)) {
            Text(text = if (isMe) "📋 您的提案" else "📋 收到提案", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { AsyncImage(model = m.tradeTargetImage, contentDescription = null, modifier = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop); Text(text = "他的卡", fontSize = 9.sp, color = Color.Gray) }; Icon(Icons.Default.Sync, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 4.dp)); Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { AsyncImage(model = m.tradeOfferImage, contentDescription = null, modifier = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop); Text(text = m.tradeOfferMember.split(", ").joinToString(", ") { it.split("|").first() }, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1) } }
            HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFF0F0F0)); Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(vertical = 6.dp)) { Row { Text(text = "📍 方式：", fontSize = 11.sp, fontWeight = FontWeight.Bold); Text(text = m.meetingLocation, fontSize = 11.sp, color = Color.Gray) }; if (m.meetingDate.isNotEmpty()) Row { Text(text = "📅 時間：", fontSize = 11.sp, fontWeight = FontWeight.Bold); Text(text = m.meetingDate, fontSize = 11.sp, color = Color.Gray) }; if (m.recipientName.isNotEmpty()) Row { Text(text = "👤 收件：", fontSize = 11.sp, fontWeight = FontWeight.Bold); Text(text = m.recipientName, fontSize = 11.sp, color = Color.Gray) } }
            
            if (m.tradeStatus == "pending") { 
                if (!isMe) Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = onAccept, modifier = Modifier.weight(1f).height(30.dp), shape = RoundedCornerShape(15.dp), contentPadding = PaddingValues(0.dp)) { Text(text = "接受", fontSize = 11.sp) }; OutlinedButton(onClick = onDecline, modifier = Modifier.weight(1f).height(30.dp), shape = RoundedCornerShape(15.dp), contentPadding = PaddingValues(0.dp)) { Text(text = "拒絕", fontSize = 11.sp) } } 
                else Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { Box(modifier = Modifier.fillMaxWidth().background(Color(0xFFF9F9F9), RoundedCornerShape(6.dp)).padding(6.dp), contentAlignment = Alignment.Center) { Text(text = "⏳ 等待確認...", fontSize = 11.sp, color = Color.Gray) }; TextButton(onClick = onCancel, modifier = Modifier.height(30.dp)) { Text(text = "取消提案", color = Color.Red, fontSize = 11.sp) } } 
            } else if (m.tradeStatus == "accepted") {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { Surface(color = Color(0xFFE3F2FD), shape = RoundedCornerShape(4.dp), modifier = Modifier.fillMaxWidth()) { Text(text = "🤝 交換進行中", modifier = Modifier.padding(6.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF1976D2), textAlign = TextAlign.Center) }; Spacer(Modifier.height(8.dp)); Button(onClick = onComplete, modifier = Modifier.fillMaxWidth().height(32.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) { Text(text = "結束交換 (已成交)", fontSize = 12.sp) } }
            } else if (m.tradeStatus == "completed") {
                Column(Modifier.fillMaxWidth()) {
                    val hasIReviewed = m.reviewedBy.contains(userId)
                    if (hasIReviewed) {
                        Surface(color = Color(0xFFE8F5E9), shape = RoundedCornerShape(4.dp), modifier = Modifier.fillMaxWidth()) { 
                            Text(text = "✅ 交換已完成", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF2E7D32), textAlign = TextAlign.Center)
                        }
                    } else {
                        Surface(color = Color(0xFFE8F5E9), shape = RoundedCornerShape(4.dp), modifier = Modifier.fillMaxWidth()) { 
                            Text(text = "✅ 已成交", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF2E7D32), textAlign = TextAlign.Center) 
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onReview, modifier = Modifier.fillMaxWidth().height(32.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) { 
                            Text("給予評價", fontSize = 12.sp) 
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.CenterEnd) { Surface(color = Color(0xFFF5F5F5), shape = RoundedCornerShape(4.dp)) { Text(text = if (m.tradeStatus == "declined") "❌ 已拒絕" else "⚪ 已取消", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray) } }
            }
        }
    }
}
