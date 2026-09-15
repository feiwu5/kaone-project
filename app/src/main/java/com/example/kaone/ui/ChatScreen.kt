package com.example.kaone.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.widget.VideoView
import android.widget.MediaController
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.*
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.kaone.VerificationActivity
import com.example.kaone.ui.theme.CloudinaryUploader
import com.google.firebase.Timestamp
import com.google.firebase.firestore.*
import java.io.File
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
    var friendUids by remember { mutableStateOf<Set<String>>(emptySet()) }
    var searchQuery by remember { mutableStateOf("") }
    var roomToDelete by remember { mutableStateOf<ChatRoom?>(null) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    LaunchedEffect(userId) {
        db.collection("friendships")
            .whereArrayContains("uids", userId)
            .whereEqualTo("status", "friends")
            .addSnapshotListener { s, _ ->
                friendUids = s?.documents?.flatMap {
                    @Suppress("UNCHECKED_CAST") (it.get("uids") as? List<String>) ?: emptyList()
                }?.filter { it != userId }?.toSet() ?: emptySet()
            }

        db.collection("chatRooms").whereArrayContains("participantIds", userId).addSnapshotListener { s, _ ->
            s?.let { snapshot ->
                val fetchedRooms = snapshot.documents.map { doc ->
                    val raw = doc.get("unreadCount") as? Map<*, *>
                    val mapped = mutableMapOf<String, Int>()
                    raw?.forEach { (k, v) -> if (k is String) mapped[k] = (v as? Long)?.toInt() ?: 0 }
                    ChatRoom(
                        id = doc.id,
                        participantIds = @Suppress("UNCHECKED_CAST") (doc.get("participantIds") as? List<String> ?: emptyList()),
                        lastMessage = doc.getString("lastMessage") ?: "點擊開始聊天",
                        lastMessageTime = doc.getTimestamp("lastMessageTime", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
                        activeInquiryCardId = doc.getString("activeInquiryCardId") ?: "",
                        unreadCount = mapped,
                        isGroup = doc.getBoolean("isGroup") ?: false,
                        roomName = doc.getString("roomName") ?: "",
                        roomImage = doc.getString("roomImage") ?: ""
                    )
                }

                fetchedRooms.forEach { room ->
                    if (room.isGroup) {
                        room.otherNickname = room.roomName.ifEmpty { "未命名群組" }
                    } else {
                        val otherId = room.participantIds.firstOrNull { it != userId } ?: ""
                        if (otherId.isNotEmpty()) {
                            db.collection("users").document(otherId).get().addOnSuccessListener { userDoc ->
                                room.otherNickname = userDoc.getString("nickname") ?: "用戶"
                                rooms = fetchedRooms.toList()
                            }
                        }
                    }
                }
                rooms = fetchedRooms.sortedByDescending { r -> r.lastMessageTime }
            }
        }
    }

    val filteredRooms = rooms.filter { room ->
        val matchesSearch = searchQuery.isEmpty() || room.otherNickname.contains(searchQuery, ignoreCase = true)
        val otherId = room.participantIds.firstOrNull { it != userId } ?: ""
        val isFriend = friendUids.contains(otherId)

        val matchesTab = if (selectedTab == 0) {
            !room.isGroup && !isFriend
        } else {
            room.isGroup || isFriend
        }
        matchesSearch && matchesTab
    }

    val allDisplayRooms = remember(filteredRooms, friendUids, selectedTab, rooms) {
        if (selectedTab == 1) {
            val friendsWithNoRooms = friendUids.filter { fid ->
                !rooms.any { !it.isGroup && it.participantIds.contains(fid) }
            }
            val syntheticRooms = friendsWithNoRooms.map { fid ->
                ChatRoom(
                    id = "FRIEND_$fid",
                    participantIds = listOf(userId, fid),
                    lastMessage = "我們已成為好友，開始聊天吧！",
                    lastMessageTime = null,
                    isGroup = false
                )
            }
            (filteredRooms + syntheticRooms).sortedByDescending { it.lastMessageTime }
        } else {
            filteredRooms
        }
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

    if (showCreateGroupDialog) {
        var groupName by remember { mutableStateOf("") }
        var selectedFriendIds by remember { mutableStateOf(setOf<String>()) }
        var friendsList by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

        LaunchedEffect(friendUids) {
            val list = mutableListOf<Pair<String, String>>()
            friendUids.forEach { uid ->
                db.collection("users").document(uid).get().addOnSuccessListener { d ->
                    list.add(uid to (d.getString("nickname") ?: "用戶"))
                    if (list.size == friendUids.size) friendsList = list
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showCreateGroupDialog = false },
            title = { Text("建立新群組", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        label = { Text("群組名稱") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("選擇成員:", fontSize = 14.sp, color = Color.Gray)
                    LazyColumn(modifier = Modifier.heightIn(max = 250.dp).padding(top = 8.dp)) {
                        items(friendsList) { friend ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable {
                                        selectedFriendIds = if (selectedFriendIds.contains(friend.first))
                                            selectedFriendIds - friend.first else selectedFriendIds + friend.first
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = selectedFriendIds.contains(friend.first), onCheckedChange = null)
                                Spacer(Modifier.width(8.dp))
                                Text(friend.second)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val allParticipants = selectedFriendIds.toList() + userId
                        val newGroup = hashMapOf(
                            "isGroup" to true,
                            "roomName" to groupName,
                            "roomImage" to "",
                            "participantIds" to allParticipants,
                            "lastMessage" to "群組已建立，開始聊天吧！",
                            "lastMessageTime" to FieldValue.serverTimestamp(),
                            "unreadCount" to allParticipants.associateWith { 0 },
                            "createdBy" to userId
                        )
                        db.collection("chatRooms").add(newGroup).addOnSuccessListener {
                            showCreateGroupDialog = false
                        }
                    },
                    enabled = groupName.isNotBlank() && selectedFriendIds.isNotEmpty()
                ) { Text("建立") }
            },
            dismissButton = { TextButton(onClick = { showCreateGroupDialog = false }) { Text("取消") } }
        )
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                TopAppBar(title = { Text("我的對話", fontWeight = FontWeight.Bold) })
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
                                        Text(text = "搜尋聯絡人...", fontSize = 15.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                SecondaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.White,
                    indicator = { TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(selectedTab)) }
                ) {
                    val displayTabs = listOf("交換訊息", "卡友群組")
                    displayTabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                            }
                        )
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFEEEEEE))
            }
        },
        floatingActionButton = {
            if (selectedTab == 1) {
                FloatingActionButton(
                    onClick = { showCreateGroupDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.GroupAdd, contentDescription = "建立群組")
                }
            }
        }
    ) { p ->
        if (allDisplayRooms.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(p), Alignment.Center) {
                Text(if(searchQuery.isEmpty()) "尚無聊天紀錄" else "找不到相關聯絡人", color = Color.Gray)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(p)) {
                items(allDisplayRooms.size) { i ->
                    val room = allDisplayRooms[i]
                    val otherId = room.participantIds.firstOrNull { it != userId } ?: ""
                    val isFriend = friendUids.contains(otherId)

                    ChatListItem(
                        userId = userId,
                        room = room,
                        isFriend = isFriend,
                        onRoomClick = { id, name ->
                            if (id.startsWith("FRIEND_")) {
                                val targetId = id.removePrefix("FRIEND_")
                                createChatRoomAndNavigate(db, userId, targetId, onRoomClick)
                            } else {
                                onRoomClick(id, name)
                            }
                        },
                        onDelete = { if (!room.id.startsWith("FRIEND_")) roomToDelete = room }
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), 0.5.dp, Color(0xFFF0F0F0))
                }
            }
        }
    }
}

private fun createChatRoomAndNavigate(db: FirebaseFirestore, myId: String, otherId: String, onNavigate: (String, String) -> Unit) {
    val participants = listOf(myId, otherId).sorted()
    db.collection("chatRooms")
        .whereEqualTo("isGroup", false)
        .whereEqualTo("participantIds", participants)
        .get()
        .addOnSuccessListener { s ->
            if (!s.isEmpty) {
                val doc = s.documents[0]
                onNavigate(doc.id, "好友")
            } else {
                val newRoom = hashMapOf(
                    "participantIds" to participants,
                    "isGroup" to false,
                    "lastMessage" to "我們已成為好友，開始聊天吧！",
                    "lastMessageTime" to FieldValue.serverTimestamp(),
                    "unreadCount" to mapOf(myId to 0, otherId to 0)
                )
                db.collection("chatRooms").add(newRoom).addOnSuccessListener { 
                    onNavigate(it.id, "好友")
                }
            }
        }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatListItem(userId: String, room: ChatRoom, isFriend: Boolean = false, onRoomClick: (String, String) -> Unit, onDelete: () -> Unit) {
    val db = FirebaseFirestore.getInstance()
    var otherName by remember { mutableStateOf(room.otherNickname) }
    var otherImageUrl by remember { mutableStateOf("") }
    val otherId = room.participantIds.firstOrNull { it != userId } ?: ""
    val unread = room.unreadCount[userId] ?: 0

    LaunchedEffect(room.id, otherId) {
        if (room.isGroup) {
            otherName = room.roomName.ifEmpty { "群組聊天" }
            otherImageUrl = room.roomImage
        } else {
            if (otherId.isNotEmpty()) {
                db.collection("users").document(otherId).get().addOnSuccessListener {
                    otherName = it.getString("nickname") ?: "用戶"
                    otherImageUrl = it.getString("profileImageUrl") ?: ""
                }
            }
        }
    }

    Row(Modifier.fillMaxWidth().combinedClickable(onClick = { onRoomClick(room.id, otherName) }, onLongClick = onDelete).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(54.dp).clip(CircleShape).background(Color.LightGray)) {
            if (otherImageUrl.isNotEmpty()) AsyncImage(model = otherImageUrl, contentDescription = null, contentScale = ContentScale.Crop)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(otherName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (isFriend && !room.isGroup) {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "好友",
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Text(room.lastMessage, fontSize = 14.sp, color = if(unread > 0) Color.Black else Color.Gray, maxLines = 1, fontWeight = if(unread > 0) FontWeight.Medium else FontWeight.Normal)
        }
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
    val db = FirebaseFirestore.getInstance()
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var inputText by remember { mutableStateOf("") }
    var inquiryCard by remember { mutableStateOf(initialCard) }

    var showInquiry by rememberSaveable(roomId) { mutableStateOf(true) }
    var lastInquiryCardId by rememberSaveable(roomId) { mutableStateOf("") }

    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    var previewVideoUrl by remember { mutableStateOf<String?>(null) }
    val otherId = remember { mutableStateOf("") }
    var otherImageUrl by remember { mutableStateOf("") }
    var otherUserName by remember { mutableStateOf(initialOtherUserName) }

    var myNickname by remember { mutableStateOf("") }
    var myProfileImage by remember { mutableStateOf("") }

    var isGroup by remember { mutableStateOf(false) }
    var roomName by remember { mutableStateOf("") }
    var roomImage by remember { mutableStateOf("") }
    var participantIds by remember { mutableStateOf<List<String>>(emptyList()) }

    var showEditGroupDialog by remember { mutableStateOf(false) }
    var showMembersDialog by remember { mutableStateOf(false) }
    var showAddMemberDialog by remember { mutableStateOf(false) }
    var showUnfriendConfirm by remember { mutableStateOf(false) }
    var friendsToAdd by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var groupMembersInfo by remember { mutableStateOf<List<Triple<String, String, String>>>(emptyList()) }

    var showMyCardsDialog by remember { mutableStateOf(false) }
    var showTradeFormDialog by remember { mutableStateOf(false) }
    var selectedOfferCard by remember { mutableStateOf<KpopCard?>(null) }
    var myAvailableCards by remember { mutableStateOf<List<KpopCard>>(emptyList()) }
    var tradeMethod by remember { mutableStateOf("面交") }
    var mAddr by remember { mutableStateOf("") }
    var mDate by remember { mutableStateOf("") }
    var mTime by remember { mutableStateOf("") }
    var sInfo by remember { mutableStateOf("") }
    var rName by remember { mutableStateOf("") }
    var rPhone by remember { mutableStateOf("") }
    var oMethod by remember { mutableStateOf("") }
    var tNotes by remember { mutableStateOf("") }
    var cMember by remember { mutableStateOf("") }
    var isUploading by remember { mutableStateOf(false) }
    var isUploadingTrade by remember { mutableStateOf(false) }
    var reviewMsgId by remember { mutableStateOf<String?>(null) }

    val datePickerState = rememberDatePickerState()
    var showDatePicker by remember { mutableStateOf(false) }
    val timePickerState = rememberTimePickerState()
    var showTimePicker by remember { mutableStateOf(false) }

    var chatBgColor by rememberSaveable { mutableLongStateOf(0xFF8BA2B5L) }
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(userId) {
        db.collection("users").document(userId).get().addOnSuccessListener {
            myNickname = it.getString("nickname") ?: "用戶"
            myProfileImage = it.getString("profileImageUrl") ?: ""
        }
    }

    LaunchedEffect(roomId) {
        db.collection("chatRooms").document(roomId).update("unreadCount.$userId", 0)

        db.collection("chatRooms").document(roomId).get().addOnSuccessListener { d ->
            participantIds = (d.get("participantIds") as? List<*>)?.mapNotNull { it.toString() } ?: emptyList()
            isGroup = d.getBoolean("isGroup") ?: false
            roomName = d.getString("roomName") ?: ""
            roomImage = d.getString("roomImage") ?: ""

            if (isGroup) {
                otherUserName = roomName.ifEmpty { "群組聊天" }
            } else {
                val oid = participantIds.firstOrNull { it != userId } ?: ""
                otherId.value = oid
                if (oid.isNotEmpty()) {
                    db.collection("users").document(oid).get().addOnSuccessListener {
                        otherImageUrl = it.getString("profileImageUrl") ?: ""
                        if (otherUserName.isEmpty() || otherUserName == "讀取中...") {
                            otherUserName = it.getString("nickname") ?: "用戶"
                        }
                    }
                }
            }

            val customBg = (d.get("backgrounds") as? Map<*, *>)?.get(userId) as? Long
            if (customBg != null) {
                chatBgColor = customBg
            }
        }

        db.collection("chatRooms").document(roomId).addSnapshotListener { s, _ ->
            s?.let {
                isGroup = it.getBoolean("isGroup") ?: false
                roomName = it.getString("roomName") ?: ""
                roomImage = it.getString("roomImage") ?: ""
                participantIds = (it.get("participantIds") as? List<*>)?.mapNotNull { it.toString() } ?: emptyList()
            }
            val cid = s?.getString("activeInquiryCardId") ?: ""
            if (cid.isNotEmpty()) {
                if (cid != lastInquiryCardId) {
                    showInquiry = true
                    lastInquiryCardId = cid
                }
                db.collection("cards").document(cid).get().addOnSuccessListener { d ->
                    if (d.exists()) {
                        inquiryCard = d.toKpopCard()
                    }
                }
            } else {
                inquiryCard = null
                showInquiry = false
                lastInquiryCardId = ""
            }
        }

        db.collection("chatRooms").document(roomId).collection("messages").orderBy("timestamp", Query.Direction.ASCENDING).addSnapshotListener { snapshot, _ ->
            snapshot?.let {
                messages = it.documents.mapNotNull { d -> d.toObject(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.copy(id = d.id) }
            }
        }
    }

    LaunchedEffect(roomId, messages.size) {
        if (messages.isNotEmpty()) {
            messages.filter { it.senderId != userId && !it.isRead }.forEach { msg ->
                db.collection("chatRooms").document(roomId)
                    .collection("messages").document(msg.id)
                    .update("isRead", true)
            }
        }
    }

    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }

    fun sendMsg(t: String, type: String = "text", url: String = "", extra: Map<String, Any>? = null) {
        if (type == "text" && ProfanityFilter.containsProfanity(t)) {
            Toast.makeText(context, "訊息包含違禁詞，請修改後再傳送", Toast.LENGTH_SHORT).show()
            return
        }

        val data = mutableMapOf(
            "senderId" to userId,
            "senderName" to myNickname,
            "senderImage" to myProfileImage,
            "messageType" to type,
            "timestamp" to FieldValue.serverTimestamp(),
            "isRead" to false
        )
        if (t.isNotEmpty()) data["text"] = t
        if (url.isNotEmpty()) data["mediaUrl"] = url
        extra?.let { data.putAll(it) }

        db.collection("chatRooms").document(roomId).collection("messages").add(data)

        val lastMsgText = when(type) {
            "text" -> t
            "trade_proposal" -> "[交換提案]"
            "video" -> "[影片]"
            "video_request" -> "[要求對光影片]"
            else -> "[媒體]"
        }

        val roomUpdate = mutableMapOf<String, Any>(
            "lastMessage" to lastMsgText,
            "lastMessageTime" to FieldValue.serverTimestamp()
        )
        participantIds.filter { it != userId }.forEach { id ->
            roomUpdate["unreadCount.$id"] = FieldValue.increment(1)
        }
        db.collection("chatRooms").document(roomId).update(roomUpdate)

        val notifTitle: String = if (isGroup) roomName.ifEmpty { "群組訊息" } else otherUserName
        val notifContent: String = when (type) {
            "trade_proposal" -> "收到新的交換提案"
            "image" -> "傳送了一張圖片"
            "video" -> "傳送了一段影片"
            "video_request" -> "傳送了對光影片請求"
            "card" -> "分享了一張小卡"
            else -> t
        }
        participantIds.filter { it != userId }.forEach { id ->
            sendNotification(id, "chat_silent", notifTitle, notifContent, roomId)
        }
    }

    val mediaLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        u?.let { uri ->
            isUploading = true
            val isVideo = context.contentResolver.getType(uri)?.startsWith("video") == true
            CloudinaryUploader.uploadMedia(context, uri, scope, onSuccess = { url ->
                sendMsg("", if(isVideo) "video" else "image", url)
                isUploading = false
            }, onFailure = { isUploading = false })
        }
    }

    val groupImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        u?.let { uri ->
            isUploading = true
            CloudinaryUploader.uploadMedia(context, uri, scope, onSuccess = { url ->
                db.collection("chatRooms").document(roomId).update("roomImage", url)
                isUploading = false
            }, onFailure = { isUploading = false })
        }
    }

    fun handleTradeAction(msg: ChatMessage, newStatus: String) {
        db.collection("chatRooms").document(roomId).collection("messages").document(msg.id).update("tradeStatus", newStatus).addOnSuccessListener {
            val lastMsgText = when(newStatus) { "accepted" -> "交換進行中..."; "completed" -> "交換已完成 🎉"; else -> "提案狀態更新" }
            if (newStatus == "accepted") { if (msg.tradeTargetCardId.isNotEmpty()) db.collection("cards").document(msg.tradeTargetCardId).update("status", "trading"); if (msg.tradeOfferCardId != "custom" && msg.tradeOfferCardId.isNotEmpty()) db.collection("cards").document(msg.tradeOfferCardId).update("status", "trading") }
            else if (newStatus == "completed") { if (msg.tradeTargetCardId.isNotEmpty()) db.collection("cards").document(msg.tradeTargetCardId).update("status", "exchanged"); if (msg.tradeOfferCardId != "custom" && msg.tradeOfferCardId.isNotEmpty()) db.collection("cards").document(msg.tradeOfferCardId).update("status", "exchanged") }

            val roomUpdate = mutableMapOf<String, Any>(
                "lastMessage" to lastMsgText,
                "lastMessageTime" to FieldValue.serverTimestamp()
            )
            participantIds.filter { it != userId }.forEach { id ->
                roomUpdate["unreadCount.$id"] = FieldValue.increment(1)
            }
            db.collection("chatRooms").document(roomId).update(roomUpdate)

            val statusTitle = when(newStatus) {
                "accepted" -> "交換提案已接受"
                "completed" -> "交換已順利完成"
                "declined" -> "交換提案已被拒絕"
                "cancelled" -> "交換提案已取消"
                else -> "交換狀態更新"
            }
            participantIds.filter { it != userId }.forEach { id ->
                sendNotification(id, "chat_silent", statusTitle, "您的交換提案狀態已變更為：${lastMsgText}", roomId)
            }
        }
    }

    if (showAddMemberDialog) {
        var selectedFriendIds by remember { mutableStateOf(setOf<String>()) }
        LaunchedEffect(Unit) {
            db.collection("friendships")
                .whereArrayContains("uids", userId)
                .whereEqualTo("status", "friends")
                .get()
                .addOnSuccessListener { snapshot ->
                    val allFriendIds = snapshot.documents.flatMap { 
                        @Suppress("UNCHECKED_CAST") (it.get("uids") as? List<String>) ?: emptyList() 
                    }.filter { it != userId && !participantIds.contains(it) }.toSet()
                    
                    val list = mutableListOf<Pair<String, String>>()
                    if (allFriendIds.isEmpty()) { friendsToAdd = emptyList(); return@addOnSuccessListener }
                    
                    var count = 0
                    allFriendIds.forEach { fid ->
                        db.collection("users").document(fid).get().addOnSuccessListener { d ->
                            list.add(fid to (d.getString("nickname") ?: "用戶"))
                            count++
                            if (count == allFriendIds.size) friendsToAdd = list
                        }
                    }
                }
        }
        AlertDialog(
            onDismissRequest = { showAddMemberDialog = false },
            title = { Text("邀請好友加入群組", fontWeight = FontWeight.Bold) },
            text = {
                if (friendsToAdd.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), Alignment.Center) { Text("所有好友都已在群組中", color = Color.Gray) }
                } else {
                    LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        items(friendsToAdd) { friend ->
                            Row(Modifier.fillMaxWidth().clickable { selectedFriendIds = if (selectedFriendIds.contains(friend.first)) selectedFriendIds - friend.first else selectedFriendIds + friend.first }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = selectedFriendIds.contains(friend.first), onCheckedChange = null)
                                Spacer(Modifier.width(12.dp))
                                Text(friend.second)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val batch = db.batch(); val roomRef = db.collection("chatRooms").document(roomId)
                    selectedFriendIds.forEach { fid -> batch.update(roomRef, "participantIds", FieldValue.arrayUnion(fid)); batch.update(roomRef, "unreadCount.$fid", 0) }
                    batch.commit().addOnSuccessListener { showAddMemberDialog = false; Toast.makeText(context, "已成功邀請 ${selectedFriendIds.size} 位成員", Toast.LENGTH_SHORT).show(); sendMsg("${myNickname} 邀請了新成員加入群組", "text") }
                }, enabled = selectedFriendIds.isNotEmpty()) { Text("確認邀請") }
            },
            dismissButton = { TextButton(onClick = { showAddMemberDialog = false }) { Text("取消") } }
        )
    }

    if (showEditGroupDialog) {
        var newName by remember { mutableStateOf(roomName) }
        AlertDialog(
            onDismissRequest = { showEditGroupDialog = false },
            title = { Text("修改群組資訊", fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(80.dp).clip(CircleShape).background(Color.LightGray).clickable { groupImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        if (roomImage.isNotEmpty()) AsyncImage(model = roomImage, contentDescription = null, contentScale = ContentScale.Crop)
                        else Icon(Icons.Default.CameraAlt, null, modifier = Modifier.align(Alignment.Center), tint = Color.White)
                    }
                    Text("點擊更換頭像", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("群組名稱") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    db.collection("chatRooms").document(roomId).update("roomName", newName).addOnSuccessListener {
                        showEditGroupDialog = false
                    }
                }) { Text("儲存") }
            },
            dismissButton = { TextButton(onClick = { showEditGroupDialog = false }) { Text("取消") } }
        )
    }

    if (showMembersDialog) {
        LaunchedEffect(participantIds) {
            val list = mutableListOf<Triple<String, String, String>>()
            participantIds.forEach { pid ->
                db.collection("users").document(pid).get().addOnSuccessListener { d ->
                    list.add(Triple(pid, d.getString("nickname") ?: "用戶", d.getString("profileImageUrl") ?: ""))
                    if (list.size == participantIds.size) groupMembersInfo = list
                }
            }
        }
        AlertDialog(
            onDismissRequest = { showMembersDialog = false },
            title = { Text("群組成員 (${participantIds.size})", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                    items(groupMembersInfo) { member ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { showMembersDialog = false; onViewProfile(member.first) }, verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(Color.LightGray)) {
                                if (member.third.isNotEmpty()) AsyncImage(model = member.third, contentDescription = null, contentScale = ContentScale.Crop)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(member.second, fontWeight = FontWeight.Medium)
                            if (member.first == userId) Text(" (我)", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showMembersDialog = false }) { Text("關閉") } }
        )
    }

    if (showUnfriendConfirm) {
        AlertDialog(
            onDismissRequest = { showUnfriendConfirm = false },
            title = { Text("解除好友關係") },
            text = { Text("確定要解除與 $otherUserName 的好友關係嗎？解除後對方將不再出現在你的好友清單中。") },
            confirmButton = {
                TextButton(onClick = {
                    db.collection("friendships")
                        .whereArrayContains("uids", userId)
                        .whereEqualTo("status", "friends")
                        .get()
                        .addOnSuccessListener { snapshot ->
                            val doc = snapshot.documents.find { 
                                @Suppress("UNCHECKED_CAST") val uids = it.get("uids") as? List<String>
                                uids?.contains(otherId.value) == true
                            }
                            doc?.reference?.delete()?.addOnSuccessListener {
                                Toast.makeText(context, "已解除好友關係", Toast.LENGTH_SHORT).show()
                                showUnfriendConfirm = false
                            }
                        }
                }) { Text("確定解除好友", color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { showUnfriendConfirm = false }) { Text("取消") } }
        )
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
                title = { Text(if (isGroup) roomName.ifEmpty { "群組聊天" } else otherUserName.ifEmpty { "聊天" }, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = {
                        val intent = Intent(context, VerificationActivity::class.java)
                        context.startActivity(intent)
                    }) {
                        Icon(Icons.Default.Verified, "辨識真偽", tint = MaterialTheme.colorScheme.primary)
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "選單") }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            if (!isGroup) {
                                DropdownMenuItem(
                                    text = { Text("查看個人主頁") },
                                    onClick = { showMenu = false; if (otherId.value.isNotEmpty()) onViewProfile(otherId.value) },
                                    leadingIcon = { Icon(Icons.Default.Person, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("解除好友關係", color = Color.Red) },
                                    onClick = { showMenu = false; showUnfriendConfirm = true },
                                    leadingIcon = { Icon(Icons.Default.PersonRemove, null, tint = Color.Red) }
                                )
                                HorizontalDivider()
                                val bgOptions = listOf(
                                    "預設藍" to 0xFF8BA2B5L, "柔和白" to 0xFFF5F5F5L, "薄荷綠" to 0xFFC8E6C9L, "經典灰" to 0xFFCFD8DCL, "質感黑" to 0xFF263238L
                                )
                                bgOptions.forEach { (name, color) ->
                                    DropdownMenuItem(
                                        text = { Text("  • $name") },
                                        onClick = {
                                            chatBgColor = color
                                            showMenu = false
                                            db.collection("chatRooms").document(roomId).update("backgrounds.$userId", color)
                                        }
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("刪除對話紀錄", color = Color.Red) },
                                    onClick = { showMenu = false; showDeleteConfirm = true },
                                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("查看群組成員") },
                                    onClick = { showMenu = false; showMembersDialog = true },
                                    leadingIcon = { Icon(Icons.Default.Group, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("邀請成員") },
                                    onClick = { showMenu = false; showAddMemberDialog = true },
                                    leadingIcon = { Icon(Icons.Default.PersonAdd, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("修改群組資訊") },
                                    onClick = { showMenu = false; showEditGroupDialog = true },
                                    leadingIcon = { Icon(Icons.Default.Edit, null) }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("退出群組", color = Color.Red) },
                                    onClick = {
                                        showMenu = false
                                        db.collection("chatRooms").document(roomId).update("participantIds", FieldValue.arrayRemove(userId)).addOnSuccessListener {
                                            Toast.makeText(context, "已退出群組", Toast.LENGTH_SHORT).show()
                                            onBack()
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Default.ExitToApp, null, tint = Color.Red) }
                                )
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 4.dp) {
                Column(modifier = Modifier.padding(bottom = 6.dp)) {
                    if (isUploading || isUploadingTrade) LinearProgressIndicator(Modifier.fillMaxWidth())

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 0.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = { sendMsg("希望能看看這張小卡的對光影片，確認一下卡況和真偽，謝謝！", type = "video_request") },
                            label = { Text("要求對光影片", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.VideoCameraFront, null, Modifier.size(12.dp)) }
                        )
                        AssistChip(
                            onClick = { context.startActivity(Intent(context, VerificationActivity::class.java)) },
                            label = { Text("鑑定指南", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.FactCheck, null, Modifier.size(14.dp)) }
                        )
                    }

                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { mediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }) {
                            Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Surface(
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, Color.LightGray),
                            color = Color.White
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (inputText.isEmpty()) {
                                    Text("訊息", color = Color.Gray, fontSize = 14.sp)
                                }
                                BasicTextField(
                                    value = inputText,
                                    onValueChange = { inputText = it },
                                    textStyle = TextStyle(fontSize = 14.sp, color = Color.Black),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                        IconButton(onClick = { if (inputText.isNotBlank()) { val t = inputText.trim(); inputText = ""; sendMsg(t) } }, colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White)) {
                            Icon(Icons.AutoMirrored.Filled.Send, null)
                        }
                    }
                }
            }
        }
    ) { p ->
        Column(
            Modifier.fillMaxSize().padding(p).background(Color(chatBgColor))
        ) {
            val cardOwnerId = inquiryCard?.userId ?: ""
            val isMeOwner = cardOwnerId == userId
            val lastVideoRequest = messages.lastOrNull { it.messageType == "video_request" }
            val isWaitingForVideo = lastVideoRequest != null && !messages.any {
                it.messageType == "video" && it.senderId == cardOwnerId &&
                        (it.timestamp?.seconds ?: 0L) >= (lastVideoRequest.timestamp?.seconds ?: 0L)
            }

            if (showInquiry && inquiryCard != null) Surface(modifier = Modifier.fillMaxWidth(), color = Color.White.copy(alpha = 0.9f)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "目前詢問商品", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = {
                                showInquiry = false
                                db.collection("chatRooms").document(roomId).update("activeInquiryCardId", "")
                            },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(Icons.Default.Close, null, tint = Color.LightGray, modifier = Modifier.size(14.dp))
                        }
                    }
                    Surface(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), color = Color.White, shape = RoundedCornerShape(8.dp), border = BorderStroke(0.5.dp, Color.LightGray)) {
                        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(model = inquiryCard!!.imageUrl, contentDescription = null, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)).clickable { previewImageUrl = inquiryCard!!.imageUrl }, contentScale = ContentScale.Crop)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(text = inquiryCard!!.memberName.split(", ").joinToString(", ") { it.split("|").first() }, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                                Text(text = inquiryCard!!.groupName.split("|").first(), fontSize = 11.sp, color = Color.Gray)
                            }

                            if (isMeOwner && isWaitingForVideo) {
                                Button(
                                    onClick = { mediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.height(30.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) { Text("上傳影片(必填)", fontSize = 11.sp) }

                                Spacer(Modifier.width(4.dp))

                                OutlinedButton(
                                    onClick = {
                                        db.collection("chatRooms").document(roomId).update("activeInquiryCardId", "")
                                        sendMsg("抱歉，我目前無法提供對光影片，暫不考慮此次交換。", type = "text")
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.height(30.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) { Text("拒絕", fontSize = 11.sp) }

                            } else if (!isMeOwner) {
                                Button(
                                    onClick = { db.collection("cards").whereEqualTo("userId", userId).whereEqualTo("status", "available").get().addOnSuccessListener { myAvailableCards = it.documents.mapNotNull { d -> d.toKpopCard() }; showMyCardsDialog = true } },
                                    enabled = !isWaitingForVideo,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.height(30.dp)
                                ) { Text(text = if (isWaitingForVideo) "等待對方影片" else "交換", fontSize = 11.sp) }
                            }

                            if (!isWaitingForVideo || !isMeOwner) {
                                Spacer(Modifier.width(8.dp))
                                OutlinedButton(onClick = { sendMsg("", "card", "", mapOf("cardImage" to inquiryCard!!.imageUrl, "cardMember" to inquiryCard!!.memberName, "cardGroup" to inquiryCard!!.groupName)) }, shape = RoundedCornerShape(16.dp), modifier = Modifier.height(30.dp)) { Text("傳送", fontSize = 11.sp) }
                            }
                        }
                    }
                }
            }

            LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
                itemsIndexed(messages) { index, m ->
                    val isMe = m.senderId == userId
                    val timeStr = m.timestamp?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(it.toDate()) } ?: ""

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
                            Column {
                                if (isGroup) {
                                    Text(
                                        text = m.senderName.ifEmpty { "用戶" },
                                        fontSize = 11.sp,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(start = 44.dp, bottom = 2.dp)
                                    )
                                }
                                Row(verticalAlignment = Alignment.Top) {
                                    Box(Modifier.size(36.dp).clip(CircleShape).background(Color.LightGray)) {
                                        if (m.senderImage.isNotEmpty()) {
                                            AsyncImage(model = m.senderImage, contentDescription = null, contentScale = ContentScale.Crop)
                                        } else if (otherImageUrl.isNotEmpty()) {
                                            AsyncImage(model = otherImageUrl, contentDescription = null, contentScale = ContentScale.Crop)
                                        }
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        MessageContent(m, isMe, chatBgColor, onPreviewImage = { previewImageUrl = it }, onPreviewVideo = { previewVideoUrl = it }, onAccept = {
                                            if (isWaitingForVideo && !isMe) {
                                                Toast.makeText(context, "請先上傳對光影片後才能接受提案", Toast.LENGTH_SHORT).show()
                                            } else {
                                                handleTradeAction(m, "accepted")
                                            }
                                        }, onDecline = { handleTradeAction(m, "declined") }, onCancel = { handleTradeAction(m, "cancelled") }, onComplete = { handleTradeAction(m, "completed") }, onReview = { reviewMsgId = m.id }, userId = userId)
                                    }
                                }
                            }
                        } else {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 4.dp)) {
                                    if (m.isRead) {
                                        Text(
                                            text = "已讀",
                                            fontSize = 9.sp,
                                            color = (if(chatBgColor == 0xFF263238L) Color.LightGray else Color.White).copy(alpha = 0.6f)
                                        )
                                    }
                                    Text(
                                        text = timeStr,
                                        fontSize = 10.sp,
                                        color = (if(chatBgColor == 0xFF263238L) Color.LightGray else Color.White).copy(alpha = 0.8f)
                                    )
                                }
                                MessageContent(m, isMe, chatBgColor, onPreviewImage = { previewImageUrl = it }, onPreviewVideo = { previewVideoUrl = it }, onAccept = { handleTradeAction(m, "accepted") }, onDecline = { handleTradeAction(m, "declined") }, onCancel = { handleTradeAction(m, "cancelled") }, onComplete = { handleTradeAction(m, "completed") }, onReview = { reviewMsgId = m.id }, userId = userId)
                            }
                        }
                    }
                }
            }
        }
    }

    if (previewVideoUrl != null) {
        Dialog(onDismissRequest = { previewVideoUrl = null }) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setVideoURI(Uri.parse(previewVideoUrl))
                            val mc = MediaController(ctx)
                            mc.setAnchorView(this)
                            setMediaController(mc)

                            setOnPreparedListener {
                                it.isLooping = true
                                start()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().aspectRatio(9f / 16f)
                )

                IconButton(
                    onClick = { previewVideoUrl = null },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "關閉", tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun MessageContent(
    m: ChatMessage,
    isMe: Boolean,
    chatBgColor: Long,
    onPreviewImage: (String) -> Unit,
    onPreviewVideo: (String) -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onCancel: () -> Unit,
    onComplete: () -> Unit,
    onReview: () -> Unit,
    userId: String
) {
    when (m.messageType) {
        "trade_proposal" -> TradeProposalItem(userId, m, isMe, onAccept, onDecline, onCancel, onComplete, onReview)
        "card" -> Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.width(200.dp).clickable { onPreviewImage(m.cardImage) }, colors = CardDefaults.cardColors(containerColor = Color.White)) { Column { AsyncImage(model = m.cardImage, contentDescription = null, modifier = Modifier.fillMaxWidth().height(150.dp), contentScale = ContentScale.Crop); Column(Modifier.padding(12.dp)) { Text(text = m.cardMember.split(", ").joinToString(", ") { it.split("|").first() }, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text(text = m.cardGroup.split("|").first(), fontSize = 12.sp, color = Color.Gray) } } }
        "video" -> Surface(
            color = Color.Black,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.size(200.dp).clickable { onPreviewVideo(m.mediaUrl) }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PlayCircleFilled, null, tint = Color.White, modifier = Modifier.size(48.dp))
            }
        }
        else -> Surface(
            color = if (isMe) Color(0xFF95EC69) else Color.White,
            shape = RoundedCornerShape(12.dp).copy(
                topStart = if (isMe) CornerSize(12.dp) else CornerSize(2.dp),
                topEnd = if (isMe) CornerSize(2.dp) else CornerSize(12.dp)
            )
        ) {
            when (m.messageType) {
                "text", "video_request" -> Text(text = m.text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = Color.Black, fontSize = 15.sp)
                "image" -> AsyncImage(model = m.mediaUrl, contentDescription = null, modifier = Modifier.sizeIn(maxWidth = 200.dp, maxHeight = 300.dp).clip(RoundedCornerShape(8.dp)).clickable { onPreviewImage(m.mediaUrl) }, contentScale = ContentScale.Crop)
            }
        }
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
