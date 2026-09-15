package com.example.kaone.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.kaone.ui.theme.ImgbbUploader
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    userId: String,
    modifier: Modifier = Modifier,
    onLogoutClick: () -> Unit = {},
    onStartChat: (String, KpopCard?) -> Unit = { _, _ -> },
    onBack: (() -> Unit)? = null,
    isAdmin: Boolean = false,
    onAdminClick: () -> Unit = {},
    onEditCard: (KpopCard) -> Unit = {}
) {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    val isOwnProfile = userId == currentUserId
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val scope = rememberCoroutineScope()

    var nickname by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var profileImageUrl by remember { mutableStateOf("") }
    var fandoms by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var concerts by remember { mutableStateOf("") }
    var preference by remember { mutableStateOf("") }
    var uploadedCards by remember { mutableStateOf<List<KpopCard>>(emptyList()) }
    var favoriteCardIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var avgRating by remember { mutableDoubleStateOf(0.0) }
    var reviewCount by remember { mutableIntStateOf(0) }
    var showEdit by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showFavs by remember { mutableStateOf(false) }
    var showTabSet by remember { mutableStateOf(false) }
    var showReviews by remember { mutableStateOf(false) }
    var showFriends by remember { mutableStateOf(false) } // 新增：好友對話框狀態
    var selectedCard by remember { mutableStateOf<KpopCard?>(null) }
    var cardToDelete by remember { mutableStateOf<KpopCard?>(null) }
    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    var selectedStatusTab by remember { mutableIntStateOf(0) }
    val statusTabs = listOf("全部", "在架上", "交換中", "已成交")

    var friendsCount by remember { mutableIntStateOf(0) }
    var friendStatus by remember { mutableStateOf("none") }
    var friendRequestSenderId by remember { mutableStateOf("") }

    var currentUserNickname by remember { mutableStateOf("") }
    var currentUserProfileImageUrl by remember { mutableStateOf("") }

    var isVerified by remember { mutableStateOf(false) }
    var currentUserVerified by remember { mutableStateOf(false) }
    var isVerifyingId by remember { mutableStateOf(false) }
    var showIdCamera by remember { mutableStateOf(false) } 

    var showDeleteAccountConfirm by remember { mutableStateOf(false) }
    var isUploadingProfileImage by remember { mutableStateOf(false) }

    var reportingTargetId by remember { mutableStateOf<String?>(null) }
    var reportingType by remember { mutableStateOf("card") }

    val allGroups = listOf("&TEAM", "aespa", "AHOF", "ALLDAY PROJECT", "ALPHA DRIVE ONE", "Apink", "ARrC", "ASTRO", "ATEEZ", "BSS", "BABYMONSTER", "Billlie", "BOYNEXTDOOR", "BTS", "BIGBANG", "CLASS:y", "CLOSE YOUR EYES", "CNBLUE", "CORTIS", "CRAVITY", "Dreamcatcher", "ENHYPEN", "EXO", "fromis_9", "G-Dragon", "Girls' Generation", "GFRIEND", "GOT the beat", "GOT7", "Heart2Hearts", "I-DLE", "IDID", "ITZY", "IVE", "izna", "IZ*ONE", "KiiiKiii", "KISS OF LIFE", "LE SSERAFIM", "LIGHTSUM", "MAMAMOO", "MAMAMOO+", "MEOVV", "MONSTA X", "NCT 127", "NCT DOJAEJUNG", "NCT DREAM", "NCT WISH", "NewJeans", "NEXZ", "NiziU", "NMIXX", "P1Harmony", "PURPLE K!SS", "QWER", "Red Velvet", "Red Velvet - IRENE & SEULGI", "SAY MY NAME", "SEVENTEEN", "SF9", "STAYC", "Stray Kids", "SuperM", "TEMPEST", "THE BOYZ", "TOMORROW X TOGETHER", "tripleS", "TWICE", "TWS", "VERIVERY", "VIVIZ", "WANNA ONE", "WayV", "WEi", "WJSN", "ZEROBASEONE").sorted()

    val cityDistricts = KpopData.cityDistricts

    val profileImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            isUploadingProfileImage = true
            ImgbbUploader.uploadImage(context, it, scope,
                onSuccess = { url ->
                    profileImageUrl = url
                    isUploadingProfileImage = false
                },
                onFailure = {
                    isUploadingProfileImage = false
                    Toast.makeText(context, "圖片上傳失敗", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    LaunchedEffect(userId, currentUserId) {
        db.collection("users").document(userId).addSnapshotListener { s, _ ->
            s?.let { d ->
                nickname = d.getString("nickname") ?: "未設定"
                name = d.getString("name") ?: ""
                location = d.getString("location") ?: "未設定"
                profileImageUrl = d.getString("profileImageUrl") ?: ""
                fandoms = d.getString("fandoms") ?: ""
                bio = d.getString("bio") ?: ""
                concerts = d.getString("concerts") ?: ""
                preference = d.getString("preference") ?: ""
                isVerified = d.getBoolean("isVerified") ?: false
                @Suppress("UNCHECKED_CAST")
                favoriteCardIds = (d.get("favoriteCardIds") as? List<*>)?.mapNotNull { it.toString() } ?: emptyList()
            }
        }

        if (currentUserId != null) {
            db.collection("users").document(currentUserId).addSnapshotListener { s, _ ->
                currentUserVerified = s?.getBoolean("isVerified") ?: false
                currentUserNickname = s?.getString("nickname") ?: "卡友"
                currentUserProfileImageUrl = s?.getString("profileImageUrl") ?: ""
            }
        }

        db.collection("friendships")
            .whereArrayContains("uids", userId)
            .whereEqualTo("status", "friends")
            .addSnapshotListener { s, _ ->
                friendsCount = s?.size() ?: 0
            }

        if (currentUserId != null && !isOwnProfile) {
            val uids = listOf(currentUserId, userId).sorted()
            val friendDocId = "${uids[0]}_${uids[1]}"
            db.collection("friendships").document(friendDocId).addSnapshotListener { s, _ ->
                if (s != null && s.exists()) {
                    friendStatus = s.getString("status") ?: "none"
                    friendRequestSenderId = s.getString("senderId") ?: ""
                } else {
                    friendStatus = "none"
                    friendRequestSenderId = ""
                }
            }
        }

        db.collection("reviews").whereEqualTo("revieweeId", userId).addSnapshotListener { s, _ ->
            s?.let {
                reviewCount = it.size()
                avgRating = if (reviewCount > 0) {
                    it.documents.sumOf { d -> d.getLong("rating")?.toDouble() ?: 0.0 } / reviewCount
                } else { 0.0 }
            }
        }
        db.collection("cards").whereEqualTo("userId", userId).addSnapshotListener { s, _ ->
            uploadedCards = s?.documents?.mapNotNull { d -> d.toKpopCard() } ?: emptyList()
        }
    }

    val filteredCards = when (selectedStatusTab) {
        1 -> uploadedCards.filter { it.status == "available" }
        2 -> uploadedCards.filter { it.status == "trading" }
        3 -> uploadedCards.filter { it.status == "exchanged" || it.status == "completed" }
        else -> uploadedCards
    }

    Scaffold(topBar = { if (!isOwnProfile && onBack != null) TopAppBar(title = { Text(nickname) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }) }) { padding ->
        Column(modifier = modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).background(Color(0xFFF8F9FA))) {
            Box(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(100.dp).clip(CircleShape).background(Color.White).shadow(4.dp, CircleShape), Alignment.Center) {
                        if (profileImageUrl.isNotEmpty()) AsyncImage(model = profileImageUrl, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                        else Icon(Icons.Default.Person, null, Modifier.size(60.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(16.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(nickname, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        if (isVerified) {
                            Spacer(Modifier.width(6.6.dp))
                            Icon(Icons.Default.CheckCircle, "已認證", tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { if (reviewCount > 0) showReviews = true }) {
                        Icon(Icons.Default.Star, null, tint = Color(0xFFFFD700), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (reviewCount > 0) String.format(Locale.getDefault(), "%.1f", avgRating) else "尚無評價",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            textDecoration = if (reviewCount > 0) TextDecoration.Underline else null
                        )
                    }
                    Text("@$name • $location", fontSize = 14.sp, color = Color.Gray)

                    if (!isOwnProfile) {
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = {
                                    if (!currentUserVerified) {
                                        Toast.makeText(context, "為了交易安全，請先完成實名認證後再發起聊天！", Toast.LENGTH_LONG).show()
                                    } else {
                                        scope.launch { findOrCreateChatRoomInProfile(currentUserId ?: "", userId, null) { roomId, _ -> onStartChat(roomId, null) } }
                                    }
                                },
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Chat, null)
                                Spacer(Modifier.width(8.dp))
                                Text("私訊他")
                            }

                            if (currentUserId != null) {
                                Button(
                                    onClick = {
                                        val uids = listOf(currentUserId, userId).sorted()
                                        val friendDocId = "${uids[0]}_${uids[1]}"
                                        when (friendStatus) {
                                            "none" -> {
                                                db.collection("friendships").document(friendDocId).set(mapOf(
                                                    "uids" to uids,
                                                    "status" to "pending",
                                                    "senderId" to currentUserId,
                                                    "timestamp" to FieldValue.serverTimestamp()
                                                )).addOnSuccessListener {
                                                    sendNotification(
                                                        userId = userId,
                                                        type = "friend_request",
                                                        title = "新的好友申請",
                                                        content = "$currentUserNickname 想要加你為好友",
                                                        relatedId = currentUserId,
                                                        relatedImage = currentUserProfileImageUrl
                                                    )
                                                }
                                            }
                                            "pending" -> {
                                                if (friendRequestSenderId != currentUserId) {
                                                    db.collection("friendships").document(friendDocId).update("status", "friends")
                                                        .addOnSuccessListener {
                                                            Toast.makeText(context, "已成為好友！", Toast.LENGTH_SHORT).show()
                                                            sendNotification(
                                                                userId = friendRequestSenderId,
                                                                type = "friend_accept",
                                                                title = "好友申請已通過",
                                                                content = "$currentUserNickname 已接受你的好友申請！",
                                                                relatedId = currentUserId,
                                                                relatedImage = currentUserProfileImageUrl
                                                            )
                                                        }
                                                }
                                            }
                                            "friends" -> { Toast.makeText(context, "你們已經是好友了！", Toast.LENGTH_SHORT).show() }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (friendStatus == "friends") Color(0xFF4CAF50) else MaterialTheme.colorScheme.secondary
                                    ),
                                    shape = RoundedCornerShape(24.dp)
                                ) {
                                    val btnText = when (friendStatus) {
                                        "pending" -> if (friendRequestSenderId == currentUserId) "已發送申請" else "接受好友"
                                        "friends" -> "好友"
                                        else -> "加好友"
                                    }
                                    val btnIcon = if (friendStatus == "friends") Icons.Default.People else Icons.Default.PersonAdd
                                    Icon(btnIcon, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(btnText)
                                }

                                IconButton(onClick = { reportingTargetId = userId; reportingType = "user" }, modifier = Modifier.background(Color.LightGray.copy(0.3f), CircleShape)) {
                                    Icon(Icons.Default.Report, "檢舉用戶", tint = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
            Row(Modifier.padding(horizontal = 24.dp, vertical = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(16.dp), Arrangement.SpaceEvenly) {
                ProfileStatItem("已上架", uploadedCards.count { it.status == "available" }.toString())
                ProfileStatItem("評價", reviewCount.toString(), onClick = { if (reviewCount > 0) showReviews = true })
                ProfileStatItem("好友", friendsCount.toString(), onClick = { if (friendsCount > 0 && isOwnProfile) showFriends = true }) // 更新：加入點擊事件
                ProfileStatItem("收藏", favoriteCardIds.size.toString())
            }
            Card(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFF5C6BC0), modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("追星名片", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color(0xFF2D3436)) }
                    Spacer(Modifier.height(16.dp))
                    StanningSection(label = "追蹤團體", icon = "💖") { if (fandoms.isBlank()) Text("尚未填寫", color = Color.LightGray, fontSize = 14.sp) else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { fandoms.split(Regex("[,\\s|]+")).filter { it.isNotBlank() }.forEach { tag -> Surface(color = Color(0xFFE8EAF6), shape = RoundedCornerShape(8.dp)) { Text(text = tag, color = Color(0xFF5C6BC0), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) } } } }
                    StanningSection(label = "追蹤歷程", icon = "⏳") { if (bio.isBlank()) Text("尚未填寫", color = Color.LightGray, fontSize = 14.sp) else Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF5F7FA)).padding(12.dp)) { Text(text = bio, fontSize = 14.sp, fontStyle = FontStyle.Italic, color = Color(0xFF636E72)) } }
                    StanningSection(label = "演唱會紀錄", icon = "🎤") { if (concerts.isBlank()) Text("尚未填寫", color = Color.LightGray, fontSize = 14.sp) else Text(text = concerts, fontSize = 14.sp, color = Color(0xFF2D3436), lineHeight = 20.sp) }
                    StanningSection(label = "小卡偏好", icon = "💎", isLast = true) { if (preference.isBlank()) Text("尚未填寫", color = Color.LightGray, fontSize = 14.sp) else Surface(color = Color(0xFFFCE4EC), shape = RoundedCornerShape(8.dp)) { Text(text = preference, color = Color(0xFFD81B60), fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) } }
                }
            }
            if (isOwnProfile) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    if (isAdmin) {
                        ProfileMenuItem(Icons.Default.AdminPanelSettings, "管理員後台", "管理用戶、內容與公告", containerColor = MaterialTheme.colorScheme.secondaryContainer) { onAdminClick() }
                        HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFEEEEEE))
                    }

                    if (!isVerified) {
                        ProfileMenuItem(
                            Icons.Default.VerifiedUser, "進行 AI 實名認證",
                            if (isVerifyingId) "正在辨識中..." else "認證後可獲得信任勾勾 ✅ 並開啟私訊功能",
                            containerColor = Color(0xFFFFEBEE)
                        ) {
                            if (!isVerifyingId) {
                                showIdCamera = true 
                            }
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFEEEEEE))
                    }

                    ProfileMenuItem(Icons.Default.Edit, "編輯追星名片", "修改追星歷程、團體等資訊") { showEdit = true }
                    HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFEEEEEE))
                    ProfileMenuItem(Icons.Default.Settings, "帳號設定", "修改暱稱、地區、安全設定") { showSettings = true }
                    HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFEEEEEE))
                    ProfileMenuItem(Icons.Default.Tune, "標籤順序設定", "自訂首頁團體顯示順序") { showTabSet = true }
                    HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFEEEEEE))
                    ProfileMenuItem(Icons.Default.FavoriteBorder, "我的收藏", "儲存的小卡清單") { showFavs = true }
                }
            }
            Text(if (isOwnProfile) "我的作品牆" else "全部作品", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            TabRow(selectedTabIndex = selectedStatusTab, containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.primary, divider = {}, modifier = Modifier.padding(horizontal = 8.dp)) { statusTabs.forEachIndexed { index, title -> Tab(selected = selectedStatusTab == index, onClick = { selectedStatusTab = index }, text = { Text(title, fontSize = 14.sp, fontWeight = if (selectedStatusTab == index) FontWeight.Bold else FontWeight.Normal) }) } }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                filteredCards.chunked(2).forEach { row -> Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) { row.forEach { card -> Box(Modifier.weight(1f).padding(vertical = 6.dp)) { ProfileCardItem(card = card, onClick = { selectedCard = card }) } }; if (row.size == 1) Spacer(Modifier.weight(1f)) } }
                if (filteredCards.isEmpty()) { Box(Modifier.fillMaxWidth().padding(48.dp), Alignment.Center) { Text("尚無${statusTabs[selectedStatusTab]}作品", color = Color.Gray) } }
            }
            if (isOwnProfile) { TextButton(onClick = onLogoutClick, Modifier.align(Alignment.CenterHorizontally), colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.Red.copy(alpha = 0.7f))) { Text("登出帳號") } }
            Spacer(Modifier.height(80.dp))
        }
    }

    if (showFriends) { FriendsListDialog(userId, onDismiss = { showFriends = false }) }
    if (showIdCamera) {
        Dialog(onDismissRequest = { showIdCamera = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            IdCardCameraScreen(
                onImageCaptured = { uri ->
                    showIdCamera = false
                    isVerifyingId = true
                    try {
                        val image = InputImage.fromFilePath(context, uri)
                        IdCardValidator.scanIdCard(
                            image = image,
                            onSuccess = { idNumber, detectedName ->
                                val newHash = IdCardValidator.hashIdNumber(idNumber)
                                db.collection("users").whereEqualTo("idHash", newHash).get()
                                    .addOnSuccessListener { docs ->
                                        if (!docs.isEmpty) {
                                            Toast.makeText(context, "此身分證已被其他帳號綁定", Toast.LENGTH_LONG).show()
                                            isVerifyingId = false
                                        } else {
                                            db.collection("users").document(currentUserId!!).update(mapOf("idHash" to newHash, "isVerified" to true, "name" to detectedName))
                                                .addOnSuccessListener {
                                                    name = detectedName
                                                    Toast.makeText(context, "實名認證成功！姓名：$detectedName ✅", Toast.LENGTH_SHORT).show()
                                                    isVerifyingId = false
                                                    currentUserVerified = true
                                                }
                                        }
                                    }
                                    .addOnFailureListener { isVerifyingId = false; Toast.makeText(context, "網路錯誤，請稍後再試", Toast.LENGTH_SHORT).show() }
                            },
                            onFailure = { e -> isVerifyingId = false; Toast.makeText(context, "辨識失敗：${e.message}", Toast.LENGTH_LONG).show() }
                        )
                    } catch (e: Exception) { isVerifyingId = false; Toast.makeText(context, "圖片處理出錯: ${e.message}", Toast.LENGTH_SHORT).show() }
                },
                onDismiss = { showIdCamera = false }
            )
        }
    }

    if (cardToDelete != null) { AlertDialog(onDismissRequest = { cardToDelete = null }, title = { Text("刪除作品") }, text = { Text("確定要刪除這張小卡嗎？此動作無法復原。") }, confirmButton = { TextButton(onClick = { db.collection("cards").document(cardToDelete!!.id).delete().addOnSuccessListener { Toast.makeText(context, "已刪除", Toast.LENGTH_SHORT).show(); cardToDelete = null; selectedCard = null } }) { Text("確定刪除", color = Color.Red) } }, dismissButton = { TextButton(onClick = { cardToDelete = null }) { Text("取消") } }) }

    if (selectedCard != null) {
        val card = selectedCard!!
        Dialog(onDismissRequest = { selectedCard = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Card(modifier = Modifier.fillMaxWidth(0.82f).wrapContentHeight().padding(vertical = 20.dp), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F3F9))) {
                Box(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp).verticalScroll(rememberScrollState())) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(38.dp).clip(CircleShape).background(Color.White)) { if (card.ownerProfileImageUrl.isNotEmpty()) AsyncImage(model = card.ownerProfileImageUrl, contentDescription = null, contentScale = ContentScale.Crop) else Icon(Icons.Default.Person, null, tint = Color.LightGray) }
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(card.ownerName, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color.Black)
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Default.Star, null, tint = Color(0xFFFFD700), modifier = Modifier.size(12.dp))
                                    Text(if (reviewCount > 0) String.format(Locale.getDefault(), " %.1f(%d)", avgRating, reviewCount) else " 尚無評價", fontSize = 11.sp, color = Color(0xFF4A5568))
                                }
                                Text("查看個人主頁 >", fontSize = 10.sp, color = Color(0xFF5C6BC0), fontWeight = FontWeight.ExtraBold, modifier = Modifier.clickable { selectedCard = null })
                            }
                            IconButton(onClick = { selectedCard = null }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Close, null, tint = Color.Gray.copy(0.5f), modifier = Modifier.size(18.dp)) }
                        }
                        Spacer(Modifier.height(12.dp))
                        Box(Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(16.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                            AsyncImage(model = card.imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize().clickable { previewImageUrl = card.imageUrl }, contentScale = ContentScale.Crop)
                            if (card.status != "available") { Surface(color = if(card.status == "trading") Color(0xFF1976D2) else Color(0xFF2E7D32), shape = RoundedCornerShape(bottomEnd = 12.dp), modifier = Modifier.align(Alignment.TopStart)) { Text(if(card.status == "trading") "🤝 交換中" else "✅ 已成交", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) } }
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Surface(color = Color(0xFF586795), shape = RoundedCornerShape(8.dp)) { Text(card.groupName.split("|").first(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) }
                            Spacer(Modifier.width(8.dp))
                            Text(card.memberName.split(", ").joinToString(", ") { it.split("|").first() }, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color(0xFF1A202C))
                        }
                        Spacer(Modifier.height(16.dp))
                        DetailSection(label = "WISH LIST", icon = Icons.Default.Favorite) { Text(card.wishlist, fontSize = 14.sp, color = Color(0xFF2D3748), lineHeight = 20.sp) }
                        DetailSection(label = "REMARKS", icon = Icons.AutoMirrored.Filled.Notes) { Text(card.remarks.ifBlank { "無備註" }, fontSize = 14.sp, color = Color(0xFF4A5568)) }
                        Spacer(Modifier.height(60.dp))
                    }
                    if (card.userId != currentUserId) {
                        Button(
                            onClick = {
                                if (!currentUserVerified) { Toast.makeText(context, "為了交易安全，請先完成實名認證後再與卡友聊天！", Toast.LENGTH_LONG).show() }
                                else { scope.launch { findOrCreateChatRoomInProfile(currentUserId ?: "", card.userId, card.id) { roomId, _ -> onStartChat(roomId, card); selectedCard = null } } }
                            },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).height(44.dp),
                            shape = RoundedCornerShape(22.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF586795))
                        ) { Icon(Icons.AutoMirrored.Filled.Chat, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.6.dp)); Text("與他聊聊", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold) }
                    } else {
                        Row(modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onEditCard(card); selectedCard = null }, modifier = Modifier.size(42.dp).background(Color(0xFFE8EAF6), CircleShape)) { Icon(Icons.Default.Edit, "編輯", tint = Color(0xFF5C6BC0), modifier = Modifier.size(20.dp)) }
                            Spacer(Modifier.width(8.dp))
                            IconButton(onClick = { cardToDelete = card }, modifier = Modifier.size(42.dp).background(Color(0xFFFFEBEE), CircleShape)) { Icon(Icons.Default.Delete, null, tint = Color.Red, modifier = Modifier.size(20.dp)) }
                        }
                    }
                }
            }
        }
    }

    if (reportingTargetId != null) { ReportDialog(reporterId = currentUserId ?: "", targetId = reportingTargetId!!, targetType = reportingType, onDismiss = { reportingTargetId = null }) }
    if (previewImageUrl != null) { Dialog(onDismissRequest = { previewImageUrl = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) { Box(modifier = Modifier.fillMaxSize().background(Color.Black).clickable { previewImageUrl = null }, contentAlignment = Alignment.Center) { AsyncImage(model = previewImageUrl, contentDescription = "預覽", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit) } } }

    if (showEdit) {
        var tFandoms by remember { mutableStateOf(fandoms) }; var tBio by remember { mutableStateOf(bio) }
        var tConcerts by remember { mutableStateOf(concerts) }; var tPreference by remember { mutableStateOf(preference) }
        AlertDialog(onDismissRequest = { showEdit = false }, properties = DialogProperties(usePlatformDefaultWidth = false), modifier = Modifier.padding(24.dp).fillMaxWidth(), shape = RoundedCornerShape(32.dp), containerColor = Color.White, title = { Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { Text("編輯追星名片", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp); Spacer(Modifier.height(4.dp)); HorizontalDivider(Modifier.width(40.dp), 3.dp, MaterialTheme.colorScheme.primary.copy(0.3f)) } }, text = { Column(Modifier.verticalScroll(rememberScrollState()).fillMaxWidth()) { Spacer(Modifier.height(8.dp)); EditField(tFandoms, { tFandoms = it }, "追蹤團體", Icons.Default.Groups, placeholder = "例：BTS, NewJeans"); EditField(tBio, { tBio = it }, "追星歷程", Icons.Default.AutoAwesome, singleLine = false); EditField(tConcerts, { tConcerts = it }, "演唱會紀錄", Icons.Default.ConfirmationNumber); EditField(tPreference, { tPreference = it }, "小卡偏好", Icons.Default.Diamond) } }, confirmButton = { Button(onClick = {
            val fieldCheck = ProfanityFilter.checkFields(mapOf("追蹤團體" to tFandoms, "追星歷程" to tBio, "演唱會紀錄" to tConcerts, "小卡偏好" to tPreference))
            if (fieldCheck != null) { Toast.makeText(context, "「$fieldCheck」包含違禁詞，請修正後再試", Toast.LENGTH_SHORT).show(); return@Button }
            db.collection("users").document(userId).update(mapOf("fandoms" to tFandoms, "bio" to tBio, "concerts" to tConcerts, "preference" to tPreference)).addOnSuccessListener { Toast.makeText(context, "名片已更新 ✨", Toast.LENGTH_SHORT).show(); showEdit = false }
        }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) { Text("儲存變更", fontWeight = FontWeight.Bold) } }, dismissButton = { TextButton(onClick = { showEdit = false }, Modifier.fillMaxWidth()) { Text("取消修改", color = Color.Gray) } })
    }

    if (showSettings) {
        var tNickname by remember { mutableStateOf(nickname) }
        val locationParts = location.split(" "); var tCity by remember { mutableStateOf(locationParts.firstOrNull() ?: "") }; var tDistrict by remember { mutableStateOf(if (locationParts.size > 1) locationParts[1] else "") }
        var cityExpanded by remember { mutableStateOf(false) }; var districtExpanded by remember { mutableStateOf(false) }
        var pushEnabled by remember { mutableStateOf(true) }

        AlertDialog(onDismissRequest = { showSettings = false }, properties = DialogProperties(usePlatformDefaultWidth = false), modifier = Modifier.padding(24.dp).fillMaxWidth(), shape = RoundedCornerShape(32.dp), containerColor = Color.White, title = { Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { Text("帳號設定", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp); Spacer(Modifier.height(4.dp)); HorizontalDivider(Modifier.width(40.dp), 3.dp, MaterialTheme.colorScheme.primary.copy(0.3f)) } }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.size(80.dp).clip(CircleShape).background(Color.LightGray).clickable { if (!isUploadingProfileImage) profileImageLauncher.launch("image/*") }, contentAlignment = Alignment.Center) {
                    if (profileImageUrl.isNotEmpty()) AsyncImage(model = profileImageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Icon(Icons.Default.AddAPhoto, null, tint = Color.White)
                    if (isUploadingProfileImage) CircularProgressIndicator(modifier = Modifier.size(30.dp), color = MaterialTheme.colorScheme.primary)
                }
                Text("點擊更換頭像", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(16.dp))
                Column(Modifier.fillMaxWidth()) {
                    Text("個人資訊", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 8.dp))
                    EditField(tNickname, { tNickname = it }, "暱稱", Icons.Default.Badge)
                    ExposedDropdownMenuBox(expanded = cityExpanded, onExpandedChange = { cityExpanded = it }) { EditField(tCity, {}, "所在縣市", Icons.Default.Place, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable), readOnly = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cityExpanded) }); ExposedDropdownMenu(expanded = cityExpanded, onDismissRequest = { cityExpanded = false }) { cityDistricts.keys.forEach { city -> DropdownMenuItem(text = { Text(city) }, onClick = { tCity = city; tDistrict = ""; cityExpanded = false }) } } }
                    if (tCity.isNotEmpty()) { ExposedDropdownMenuBox(expanded = districtExpanded, onExpandedChange = { districtExpanded = it }) { EditField(tDistrict, {}, "行政區", Icons.Default.Map, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable), readOnly = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = districtExpanded) }); ExposedDropdownMenu(expanded = districtExpanded, onDismissRequest = { districtExpanded = false }) { cityDistricts[tCity]?.forEach { d -> DropdownMenuItem(text = { Text(d) }, onClick = { tDistrict = d; districtExpanded = false }) } } } }
                    Spacer(Modifier.height(16.dp)); Text("安全與通知", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 8.dp))
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Notifications, null, tint = Color.Gray, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(12.dp)); Text("推播通知", Modifier.weight(1f)); Switch(checked = pushEnabled, onCheckedChange = { pushEnabled = it }) }
                    OutlinedButton(onClick = { FirebaseAuth.getInstance().sendPasswordResetEmail(FirebaseAuth.getInstance().currentUser?.email ?: "").addOnSuccessListener { Toast.makeText(context, "重設郵件已寄出", Toast.LENGTH_LONG).show() } }, Modifier.fillMaxWidth().padding(vertical = 8.dp), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.LockReset, null); Spacer(Modifier.width(8.dp)); Text("重設登入密碼") }
                    Text("電子信箱：${FirebaseAuth.getInstance().currentUser?.email ?: "未知"}", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
                    Spacer(Modifier.height(24.dp))
                    TextButton(onClick = { showDeleteAccountConfirm = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.Red.copy(alpha = 0.6f))) { Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("永久刪除帳號", fontSize = 14.sp) }
                }
            }
        }, confirmButton = {
            Button(onClick = {
                if (ProfanityFilter.containsProfanity(tNickname)) { Toast.makeText(context, "暱稱包含違禁詞，請修改後再試", Toast.LENGTH_SHORT).show(); return@Button }
                val finalLocation = if (tDistrict.isNotEmpty()) "$tCity $tDistrict" else tCity
                scope.launch {
                    db.collection("users").document(userId).update(mapOf("nickname" to tNickname, "location" to finalLocation, "profileImageUrl" to profileImageUrl)).await()
                    val cardUpdates = db.collection("cards").whereEqualTo("userId", userId).get().await()
                    val batch = db.batch()
                    cardUpdates.documents.forEach { doc -> batch.update(doc.reference, mapOf("ownerNickname" to tNickname, "ownerProfileImageUrl" to profileImageUrl, "location" to finalLocation)) }
                    batch.commit().await()
                    Toast.makeText(context, "設定已儲存 ✅", Toast.LENGTH_SHORT).show()
                    showSettings = false
                }
            }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp), enabled = !isUploadingProfileImage) { Text("儲存設定", fontWeight = FontWeight.Bold) }
        }, dismissButton = { TextButton(onClick = { showSettings = false }, Modifier.fillMaxWidth()) { Text("關閉", color = Color.Gray) } })
    }

    if (showDeleteAccountConfirm) {
        AlertDialog(onDismissRequest = { showDeleteAccountConfirm = false }, title = { Text("刪除帳號？", fontWeight = FontWeight.Bold, color = Color.Red) }, text = { Text("這將會永久刪除您的個人資料、上傳的小卡及所有對話記錄。此動作無法復原，您確定要繼續嗎？") }, confirmButton = { Button(onClick = { val user = FirebaseAuth.getInstance().currentUser; if (user != null) { scope.launch { try { db.collection("users").document(userId).delete().await(); val cards = db.collection("cards").whereEqualTo("userId", userId).get().await(); cards.documents.forEach { doc -> doc.reference.delete() }; user.delete().await(); Toast.makeText(context, "帳號已成功刪除", Toast.LENGTH_LONG).show(); onLogoutClick() } catch (e: Exception) { Toast.makeText(context, "刪除失敗：請重新登入後再試一次 (${e.localizedMessage})", Toast.LENGTH_LONG).show() }; showDeleteAccountConfirm = false; showSettings = false } } }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("確定刪除", color = Color.White) } }, dismissButton = { TextButton(onClick = { showDeleteAccountConfirm = false }) { Text("取消") } })
    }

    if (showFavs) {
        var favCards by remember { mutableStateOf<List<KpopCard>>(emptyList()) }
        LaunchedEffect(favoriteCardIds) { if (favoriteCardIds.isNotEmpty()) { db.collection("cards").whereIn(FieldPath.documentId(), favoriteCardIds).get().addOnSuccessListener { s -> favCards = s.documents.mapNotNull { d -> d.toKpopCard() } } } }
        AlertDialog(onDismissRequest = { showFavs = false }, title = { Text("我的收藏", fontWeight = FontWeight.Bold) }, text = { Box(Modifier.heightIn(max = 450.dp)) { if (favCards.isEmpty()) Text("尚無收藏", color = Color.Gray, modifier = Modifier.padding(20.dp)) else LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(favCards) { card -> ProfileCardItem(card = card, onClick = { selectedCard = card; showFavs = false }) } } } }, confirmButton = { TextButton(onClick = { showFavs = false }) { Text("關閉") } })
    }

    if (showTabSet) {
        var tempTabs by remember { mutableStateOf(emptyList<String>()) }
        LaunchedEffect(Unit) { db.collection("users").document(userId).get().addOnSuccessListener { d -> tempTabs = (d.get("customDashboardTabs") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList() } }
        AlertDialog(onDismissRequest = { showTabSet = false }, title = { Text("標籤順序設定") }, text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text("目前排列 (點擊移除)：", fontSize = 12.sp, color = Color.Gray); FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { tempTabs.forEach { g -> FilterChip(selected = true, onClick = { tempTabs = tempTabs - g }, label = { Text(g) }) } }; HorizontalDivider(Modifier.padding(vertical = 8.dp)); Text("可加入團體：", fontSize = 12.sp, color = Color.Gray); FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { allGroups.filter { it !in tempTabs }.forEach { g -> FilterChip(selected = false, onClick = { tempTabs = tempTabs + g }, label = { Text(g) }) } } } }, confirmButton = { Button(onClick = { db.collection("users").document(userId).update("customDashboardTabs", tempTabs).addOnSuccessListener { Toast.makeText(context, "已儲存", Toast.LENGTH_SHORT).show(); showTabSet = false } }) { Text("儲存") } }, dismissButton = { TextButton(onClick = { showTabSet = false }) { Text("取消") } })
    }

    if (showReviews) { ReviewDetailsDialog(userId, currentUserId ?: "", onDismiss = { showReviews = false }) }
}

@Composable
fun FriendsListDialog(userId: String, onDismiss: () -> Unit) {
    val db = FirebaseFirestore.getInstance()
    var friends by remember { mutableStateOf<List<Triple<String, String, String>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(userId) {
        db.collection("friendships")
            .whereArrayContains("uids", userId)
            .whereEqualTo("status", "friends")
            .addSnapshotListener { snapshot, _ ->
                snapshot?.let { s ->
                    scope.launch {
                        val list = mutableListOf<Triple<String, String, String>>()
                        s.documents.forEach { doc ->
                            val uids = @Suppress("UNCHECKED_CAST") (doc.get("uids") as? List<String>)
                            val otherId = uids?.firstOrNull { it != userId } ?: ""
                            if (otherId.isNotEmpty()) {
                                try {
                                    val userDoc = db.collection("users").document(otherId).get().await()
                                    list.add(Triple(
                                        otherId,
                                        userDoc.getString("nickname") ?: "用戶",
                                        userDoc.getString("profileImageUrl") ?: ""
                                    ))
                                } catch (_: Exception) {}
                            }
                        }
                        friends = list
                        isLoading = false
                    }
                }
            }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("好友名單", fontWeight = FontWeight.Bold) },
        text = {
            Box(Modifier.heightIn(max = 450.dp).fillMaxWidth()) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (friends.isEmpty()) {
                    Text("目前尚無好友", color = Color.Gray, modifier = Modifier.padding(20.dp))
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(friends) { friend ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(40.dp).clip(CircleShape).background(Color.LightGray)) {
                                    if (friend.third.isNotEmpty()) AsyncImage(model = friend.third, contentDescription = null, contentScale = ContentScale.Crop)
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(friend.second, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                IconButton(onClick = {
                                    val uids = listOf(userId, friend.first).sorted()
                                    val docId = "${uids[0]}_${uids[1]}"
                                    db.collection("friendships").document(docId).delete().addOnSuccessListener {
                                        Toast.makeText(context, "已解除好友關係", Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Icon(Icons.Default.PersonRemove, "解除好友", tint = Color.Red.copy(alpha = 0.6f))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("關閉") } }
    )
}

@Composable
fun DetailSection(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(0.5f)).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color(0xFF586795), modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.6.dp))
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF586795), letterSpacing = 1.sp)
        }
        Spacer(Modifier.height(6.6.dp))
        content()
    }
}

@Composable
fun ReviewDetailsDialog(revieweeId: String, currentUserId: String, onDismiss: () -> Unit) {
    val db = FirebaseFirestore.getInstance()
    var reviews by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var editingReview by remember { mutableStateOf<Map<String, Any>?>(null) }
    var deletingReviewId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(revieweeId) { db.collection("reviews").whereEqualTo("revieweeId", revieweeId).addSnapshotListener { s, _ -> s?.let { snapshot -> scope.launch { val list = mutableListOf<Map<String, Any>>(); snapshot.documents.forEach { d ->
        val data = d.data ?: return@forEach; val reviewerId = data["reviewerId"] as? String ?: ""; val updatedData = data.toMutableMap(); updatedData["id"] = d.id
        if (reviewerId.isNotEmpty()) { try { val userDoc = db.collection("users").document(reviewerId).get().await(); updatedData["reviewerNickname"] = userDoc.getString("nickname") ?: "用戶"; updatedData["reviewerImageUrl"] = userDoc.getString("profileImageUrl") ?: "" } catch (_: Exception) { } }
        list.add(updatedData)
    }; reviews = list.sortedByDescending { it["timestamp"] as? Timestamp }; isLoading = false } } } }

    AlertDialog(onDismissRequest = onDismiss, title = { Text("評價內容", fontWeight = FontWeight.Bold) }, text = { Box(Modifier.heightIn(max = 450.dp).fillMaxWidth()) { if (isLoading) { CircularProgressIndicator(modifier = Modifier.align(Alignment.Center)) } else if (reviews.isEmpty()) { Text("尚無詳細評價內容", color = Color.Gray, modifier = Modifier.padding(20.dp)) } else { LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) { items(reviews) { r ->
        val id = r["id"] as String; val reviewerId = r["reviewerId"] as String; val rating = (r["rating"] as? Long)?.toInt() ?: 0; val comment = r["comment"] as? String ?: ""; val nickname = r["reviewerNickname"] as? String ?: "用戶"; val imageUrl = r["reviewerImageUrl"] as? String ?: ""; val timestamp = r["timestamp"] as? Timestamp; val isMe = reviewerId == currentUserId
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFBFBFC)), shape = RoundedCornerShape(12.dp), border = if(isMe) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.3f)) else null) { Column(Modifier.padding(12.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(32.dp).clip(CircleShape).background(Color.LightGray)) { if (imageUrl.isNotEmpty()) AsyncImage(model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop) }; Spacer(Modifier.width(8.dp)); Column(Modifier.weight(1f)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(nickname, fontWeight = FontWeight.Bold, fontSize = 14.sp); if (isMe) Surface(color = MaterialTheme.colorScheme.primary.copy(0.1f), shape = RoundedCornerShape(4.dp), modifier = Modifier.padding(start = 4.dp)) { Text("我", color = MaterialTheme.colorScheme.primary, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)) } }; timestamp?.let { ts -> Text(SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(ts.toDate()), fontSize = 10.sp, color = Color.Gray) } }; Row { for (i in 1..5) { Icon(Icons.Default.Star, null, tint = if (i <= rating) Color(0xFFFFD700) else Color.LightGray, modifier = Modifier.size(14.dp)) } } }; if (comment.isNotEmpty()) { Spacer(Modifier.height(8.dp)); Text(comment, fontSize = 14.sp, color = Color.DarkGray) }; if (isMe) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { IconButton(onClick = { editingReview = r }, Modifier.size(32.dp)) { Icon(Icons.Default.Edit, "編輯", tint = Color.Gray, modifier = Modifier.size(16.dp)) }; IconButton(onClick = { deletingReviewId = id }, Modifier.size(32.dp)) { Icon(Icons.Default.Delete, "刪除", tint = Color.Red.copy(0.6f), modifier = Modifier.size(16.dp)) } } } } }
    } } } } }, confirmButton = { TextButton(onClick = onDismiss) { Text("關閉") } })

    if (deletingReviewId != null) { AlertDialog(onDismissRequest = { deletingReviewId = null }, title = { Text("刪除評價") }, text = { Text("確定要刪除這筆評價嗎？此動作無法復原。") }, confirmButton = { TextButton(onClick = { db.collection("reviews").document(deletingReviewId!!).delete().addOnSuccessListener { Toast.makeText(context, "已刪除", Toast.LENGTH_SHORT).show(); deletingReviewId = null } }) { Text("確定刪除", color = Color.Red) } }, dismissButton = { TextButton(onClick = { deletingReviewId = null }) { Text("取消") } }) }
    if (editingReview != null) {
        var tRating by remember { mutableIntStateOf((editingReview!!["rating"] as? Long)?.toInt() ?: 0) }; var tComment by remember { mutableStateOf(editingReview!!["comment"] as? String ?: "") }
        AlertDialog(onDismissRequest = { editingReview = null }, title = { Text("編輯評價", fontWeight = FontWeight.Bold) }, text = { Column { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { for (i in 1..5) { Icon(imageVector = if (i <= tRating) Icons.Default.Star else Icons.Default.StarBorder, contentDescription = null, tint = if (i <= tRating) Color(0xFFFFD700) else Color.Gray, modifier = Modifier.size(32.dp).clickable { tRating = i }) } }; Spacer(Modifier.height(16.dp)); OutlinedTextField(value = tComment, onValueChange = { tComment = it }, label = { Text("修改評價內容...") }, modifier = Modifier.fillMaxWidth().height(100.dp)) } }, confirmButton = {
            Button(onClick = {
                if (ProfanityFilter.containsProfanity(tComment)) { Toast.makeText(context, "評價包含違禁詞，請修改後再試", Toast.LENGTH_SHORT).show(); return@Button }
                db.collection("reviews").document(editingReview!!["id"] as String).update(mapOf("rating" to tRating, "comment" to tComment, "timestamp" to FieldValue.serverTimestamp())).addOnSuccessListener { Toast.makeText(context, "已更新", Toast.LENGTH_SHORT).show(); editingReview = null }
            }) { Text("儲存修改") }
        }, dismissButton = { TextButton(onClick = { editingReview = null }) { Text("取消") } })
    }
}

@Composable
fun EditField(value: String, onValueChange: (String) -> Unit, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, placeholder: String = "", singleLine: Boolean = true, readOnly: Boolean = false, trailingIcon: @Composable (() -> Unit)? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 14.sp) },
        placeholder = { if (placeholder.isNotEmpty()) Text(placeholder, fontSize = 12.sp, color = Color.Gray) },
        leadingIcon = { Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) },
        trailingIcon = trailingIcon,
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        singleLine = singleLine,
        readOnly = readOnly,
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = Color.LightGray.copy(alpha = 0.5f), focusedLabelColor = MaterialTheme.colorScheme.primary, focusedContainerColor = Color(0xFFFBFBFC), unfocusedContainerColor = Color(0xFFFBFBFC))
    )
}

@Composable
fun ProfileStatItem(label: String, value: String, onClick: (() -> Unit)? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = if (onClick != null) Modifier.clickable { onClick() } else Modifier) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2D3436))
        Text(label, fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
fun StanningSection(label: String, icon: String, isLast: Boolean = false, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text(icon, fontSize = 14.sp); Spacer(Modifier.width(8.dp)); Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF636E72)) }
        Spacer(Modifier.height(8.dp)); content()
        if (!isLast) { Spacer(Modifier.height(16.dp)); HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFF1F3F5)); Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
fun ProfileMenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, containerColor: Color = Color.Transparent, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(containerColor, RoundedCornerShape(12.dp)).clickable { onClick() }.padding(vertical = 16.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)), Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp); Text(subtitle, fontSize = 12.sp, color = Color.Gray) }
        Icon(Icons.Default.ChevronRight, null, tint = Color.LightGray)
    }
}

@Composable
fun ProfileCardItem(card: KpopCard, onClick: () -> Unit) {
    Card(shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Column {
            Box {
                AsyncImage(model = card.imageUrl, contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(0.95f).clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)), contentScale = ContentScale.Crop)
                if (card.status != "available") Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.4f)), Alignment.Center) {
                    Surface(color = if(card.status == "trading") Color(0xFF1976D2) else Color(0xFF2E7D32), shape = RoundedCornerShape(4.dp)) { Text(if(card.status == "trading") "🤝 交換中" else "✅ 已成交", color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontWeight = FontWeight.Bold) }
                }
            }
            Column(Modifier.padding(12.dp)) {
                Text(card.memberName.split(", ").joinToString(", ") { it.split("|").first() }, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black, maxLines = 1)
                Text(card.groupName.split("|").first(), fontSize = 12.sp, color = Color.Gray, maxLines = 1)
                if (card.cardType.isNotEmpty()) { Spacer(Modifier.height(4.dp)); CardTypeBadge(card.cardType) }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(16.dp).clip(CircleShape).background(Color.LightGray)) { if (card.ownerProfileImageUrl.isNotEmpty()) AsyncImage(model = card.ownerProfileImageUrl, contentDescription = null, contentScale = ContentScale.Crop) }
                    Text(" " + card.ownerName, fontSize = 11.sp, color = Color.Gray, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun CardTypeBadge(type: String) { Surface(color = Color(0xFFF0F2F8), shape = RoundedCornerShape(4.dp)) { Text(text = type, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5C6BC0)) } }

suspend fun findOrCreateChatRoomInProfile(u1: String, u2: String, cid: String?, onComplete: (String, KpopCard?) -> Unit) {
    if (u1 == u2 || u1.isEmpty()) return
    val participants = listOf(u1, u2).sorted()
    val db = FirebaseFirestore.getInstance()
    val res = db.collection("chatRooms").whereEqualTo("participantIds", participants).get().await()
    if (res.documents.isNotEmpty()) { val roomId = res.documents.first().id; onComplete(roomId, null) }
    else {
        val nr = hashMapOf("participantIds" to participants, "createdAt" to FieldValue.serverTimestamp(), "lastMessage" to "", "lastMessageTime" to FieldValue.serverTimestamp(), "activeInquiryCardId" to (cid ?: ""), "unreadCount" to mapOf(u1 to 0, u2 to 0))
        val ar = db.collection("chatRooms").add(nr).await()
        onComplete(ar.id, null)
    }
}
