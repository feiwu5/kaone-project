package com.example.kaone.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.kaone.ui.theme.ImgbbUploader
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun UserAvatar(userId: String, size: Int = 16, defaultUrl: String = "") {
    val db = FirebaseFirestore.getInstance()
    var latestImageUrl by remember { mutableStateOf(defaultUrl) }

    DisposableEffect(userId) {
        var listenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null
        if (userId.isNotEmpty()) {
            listenerRegistration = db.collection("users").document(userId).addSnapshotListener { snapshot, _ ->
                val url = snapshot?.getString("profileImageUrl")
                if (url != null) latestImageUrl = url
            }
        }
        onDispose {
            listenerRegistration?.remove()
        }
    }

    Box(Modifier.size(size.dp).clip(CircleShape).background(Color.LightGray)) {
        if (latestImageUrl.isNotEmpty()) {
            AsyncImage(model = latestImageUrl, contentDescription = null, contentScale = ContentScale.Crop)
        }
    }
}

@Composable
fun GuestModePlaceholder(onLoginClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(80.dp), tint = Color.LightGray)
        Spacer(Modifier.height(24.dp))
        Text("此功能需要登入", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "登入後即可開始交換小卡、參與應援活動以及與其他卡友聊天！",
            fontSize = 14.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onLoginClick,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("前往登入 / 註冊", fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    userId: String,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current
    var selectedBottomTab by rememberSaveable { mutableIntStateOf(0) }
    var targetChatRoomId by remember { mutableStateOf<String?>(null) }
    var targetInquiryCard by remember { mutableStateOf<KpopCard?>(null) }
    var viewingOtherUserId by remember { mutableStateOf<String?>(null) }
    var viewingAdmin by remember { mutableStateOf(false) }
    var isAdmin by remember { mutableStateOf(false) }

    var activeExploreView by rememberSaveable { mutableStateOf("menu") }
    var homeSelectedTab by rememberSaveable { mutableIntStateOf(0) }

    var totalUnreadCount by remember { mutableIntStateOf(0) }
    val isGuest = userId.isEmpty()

    var editingCard by remember { mutableStateOf<KpopCard?>(null) }

    LaunchedEffect(userId) {
        if (!isGuest) {
            db.collection("users").document(userId).get().addOnSuccessListener { document ->
                isAdmin = document.getBoolean("isAdmin") ?: false
                val loc = document.getString("location") ?: ""

                // --- 全域自動修復舊卡片地點 ---
                if (loc.isNotEmpty()) {
                    db.collection("cards")
                        .whereEqualTo("userId", userId)
                        .get()
                        .addOnSuccessListener { snapshot ->
                            var fixCount = 0
                            snapshot.documents.forEach { doc ->
                                if (doc.getString("location").isNullOrEmpty()) {
                                    doc.reference.update("location", loc)
                                    fixCount++
                                }
                            }
                            if (fixCount > 0) {
                                Toast.makeText(context, "已自動為 $fixCount 張舊小卡同步地區資訊", Toast.LENGTH_SHORT).show()
                            }
                        }
                }
            }

            db.collection("chatRooms")
                .whereArrayContains("participantIds", userId)
                .addSnapshotListener { snapshot, _ ->
                    var count = 0
                    snapshot?.documents?.forEach { doc ->
                        val unreadMap = doc.get("unreadCount") as? Map<*, *>
                        count += (unreadMap?.get(userId) as? Long)?.toInt() ?: 0
                    }
                    totalUnreadCount = count
                }
        }
    }

    if (viewingAdmin) {
        AdminScreen(onBack = { viewingAdmin = false })
    } else {
        Scaffold(
            modifier = modifier,
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(selected = selectedBottomTab == 0, onClick = { selectedBottomTab = 0; viewingOtherUserId = null }, icon = { Icon(if (selectedBottomTab == 0) Icons.Default.Home else Icons.Outlined.Home, "首頁") }, label = { Text("首頁") })
                    NavigationBarItem(selected = selectedBottomTab == 1, onClick = { selectedBottomTab = 1; viewingOtherUserId = null }, icon = { Icon(if (selectedBottomTab == 1) Icons.Default.Explore else Icons.Outlined.Explore, "探索") }, label = { Text("探索") })
                    NavigationBarItem(selected = selectedBottomTab == 2, onClick = { selectedBottomTab = 2; viewingOtherUserId = null; editingCard = null }, icon = { Icon(Icons.Default.AddCircle, "上傳", modifier = Modifier.size(32.dp)) }, label = { Text("上傳") })

                    NavigationBarItem(
                        selected = selectedBottomTab == 3,
                        onClick = {
                            selectedBottomTab = 3
                            viewingOtherUserId = null
                            targetChatRoomId = null
                            targetInquiryCard = null
                        },
                        icon = {
                            BadgedBox(badge = { if (totalUnreadCount > 0) { Badge { Text(if (totalUnreadCount > 99) "99+" else totalUnreadCount.toString()) } } }) {
                                Icon(if (selectedBottomTab == 3) Icons.Default.ChatBubble else Icons.Outlined.ChatBubbleOutline, "聊天")
                            }
                        },
                        label = { Text("聊天") }
                    )
                    NavigationBarItem(selected = selectedBottomTab == 4, onClick = { selectedBottomTab = 4; viewingOtherUserId = null }, icon = { Icon(if (selectedBottomTab == 4) Icons.Default.Person else Icons.Outlined.PersonOutline, "個人") }, label = { Text("個人") })
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())) {
                if (viewingOtherUserId != null) {
                    ProfileScreen(
                        userId = viewingOtherUserId!!,
                        onStartChat = { roomId, card ->
                            if (isGuest) {
                                Toast.makeText(context, "請先登入後再聊天", Toast.LENGTH_SHORT).show()
                            } else {
                                targetChatRoomId = roomId; targetInquiryCard = card; selectedBottomTab = 3; viewingOtherUserId = null
                            }
                        },
                        onBack = { viewingOtherUserId = null },
                        isAdmin = isAdmin,
                        onEditCard = { editingCard = it; selectedBottomTab = 2 }
                    )
                } else {
                    when (selectedBottomTab) {
                        0 -> MainDashboard(
                            userId = userId,
                            isAdmin = isAdmin,
                            onStartChat = { roomId, card -> targetChatRoomId = roomId; targetInquiryCard = card; selectedBottomTab = 3 },
                            onViewProfile = { viewingOtherUserId = it },
                            selectedTab = homeSelectedTab,
                            onTabChange = { homeSelectedTab = it },
                            onEditCard = { editingCard = it; selectedBottomTab = 2 }
                        )
                        1 -> ExploreScreen(userId = userId, onViewProfile = { viewingOtherUserId = it }, activeView = activeExploreView, onActiveViewChange = { activeExploreView = it }, onStartChat = { roomId, card -> targetChatRoomId = roomId; targetInquiryCard = card; selectedBottomTab = 3 })
                        2 -> if (isGuest) GuestModePlaceholder(onLogoutClick) else UploadScreen(
                            userId = userId,
                            existingCard = editingCard,
                            onUploadSuccess = {
                                val prevTab = if (editingCard != null) (if (viewingOtherUserId != null || selectedBottomTab == 4) 4 else 0) else 0
                                selectedBottomTab = prevTab
                                editingCard = null
                            },
                            onBack = {
                                editingCard = null
                                selectedBottomTab = 0 // 或者回到之前的頁面
                            }
                        )
                        3 -> if (isGuest) GuestModePlaceholder(onLogoutClick) else ChatScreen(userId = userId, targetRoomId = targetChatRoomId, inquiryCard = targetInquiryCard, onViewProfile = { viewingOtherUserId = it })
                        4 -> if (isGuest) GuestModePlaceholder(onLogoutClick) else ProfileScreen(
                            userId = userId,
                            onLogoutClick = onLogoutClick,
                            onStartChat = { roomId, card -> targetChatRoomId = roomId; targetInquiryCard = card; selectedBottomTab = 3 },
                            isAdmin = isAdmin,
                            onAdminClick = { viewingAdmin = true },
                            onEditCard = { editingCard = it; selectedBottomTab = 2 }
                        )
                        else -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text("開發中...") }
                    }
                }
            }
        }
    }
}

@Composable
fun ExploreScreen(userId: String, onViewProfile: (String) -> Unit, activeView: String, onActiveViewChange: (String) -> Unit, onStartChat: (String, KpopCard?) -> Unit) {
    var editingEvent by remember { mutableStateOf<KpopEvent?>(null) }
    var userLocation by remember { mutableStateOf("") }
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current

    LaunchedEffect(userId) {
        if (userId.isNotEmpty()) {
            db.collection("users").document(userId).get().addOnSuccessListener { document ->
                userLocation = document.getString("location") ?: ""
            }
        }
    }

    when (activeView) {
        "menu" -> ExploreMenuView(onNavigate = {
            if ((it == "smartMatch" || it == "nearby") && userId.isEmpty()) {
                Toast.makeText(context, "此功能需要登入後使用", Toast.LENGTH_SHORT).show()
            } else {
                onActiveViewChange(it)
            }
        })
        "events" -> ExploreEventsView(currentUserId = userId, onBack = { onActiveViewChange("menu") }, onAddClick = { editingEvent = null; onActiveViewChange("upload") }, onEditClick = { editingEvent = it; onActiveViewChange("upload") }, onViewProfile = onViewProfile)
        "upload" -> if (userId.isEmpty()) GuestModePlaceholder { /* 由 HomeScreen 統一導回 */ } else ExploreEventUploadView(userId = userId, existingEvent = editingEvent, onBack = { onActiveViewChange("events") }, onSuccess = { onActiveViewChange("events") })
        "smartMatch" -> SmartMatchView(currentUserId = userId, onBack = { onActiveViewChange("menu") }, onStartChat = onStartChat, onViewProfile = onViewProfile)
        "nearby" -> NearbyExchangeView(currentUserId = userId, initialLocation = userLocation, onBack = { onActiveViewChange("menu") }, onStartChat = onStartChat, onViewProfile = onViewProfile)
    }
}

@Composable
fun ExploreMenuView(onNavigate: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF8F9FA))) {
        Surface(modifier = Modifier.fillMaxWidth(), color = Color.White, shadowElevation = 1.dp) {
            Text("探索專區", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color(0xFF333333), modifier = Modifier.statusBarsPadding().padding(20.dp))
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(span = { GridItemSpan(2) }) {
                ExploreBentoCard(
                    title = "應援活動資訊",
                    subtitle = "全台生咖、領應援地圖",
                    icon = Icons.Default.Celebration,
                    backgroundColor = Color(0xFFE8EAF6),
                    iconColor = Color(0xFF5C6BC0),
                    isLarge = true,
                    onClick = { onNavigate("events") }
                )
            }
            item {
                ExploreBentoCard(
                    title = "智慧配對",
                    subtitle = "自動匹配心願",
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    backgroundColor = Color(0xFFFCE4EC),
                    iconColor = Color(0xFFEC407A),
                    isLarge = false,
                    onClick = { onNavigate("smartMatch") }
                )
            }
            item {
                ExploreBentoCard(
                    title = "鄰近交換",
                    subtitle = "身邊的換卡友",
                    icon = Icons.Default.Map,
                    backgroundColor = Color(0xFFE8F5E9),
                    iconColor = Color(0xFF66BB6A),
                    isLarge = false,
                    onClick = { onNavigate("nearby") }
                )
            }
            item {
                ExploreBentoCard(
                    title = "熱門趨勢",
                    subtitle = "大家都在找什麼",
                    icon = Icons.Default.AutoAwesome,
                    backgroundColor = Color(0xFFFFF3E0),
                    iconColor = Color(0xFFFFA726),
                    isLarge = false,
                    onClick = { /* 開發中 */ }
                )
            }
            item {
                ExploreBentoCard(
                    title = "圖鑑百科",
                    subtitle = "官方卡片整理",
                    icon = Icons.Default.AutoStories,
                    backgroundColor = Color(0xFFE1F5FE),
                    iconColor = Color(0xFF29B6F6),
                    isLarge = false,
                    onClick = { /* 開發中 */ }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartMatchView(currentUserId: String, onBack: () -> Unit, onStartChat: (String, KpopCard?) -> Unit, onViewProfile: (String) -> Unit) {
    val db = FirebaseFirestore.getInstance()
    var myCards by remember { mutableStateOf<List<KpopCard>>(emptyList()) }
    var allOtherCards by remember { mutableStateOf<List<KpopCard>>(emptyList()) }
    var matches by remember { mutableStateOf<List<MatchResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(currentUserId) {
        isLoading = true
        // 1. 抓取我的卡片
        val mySnapshot = db.collection("cards").whereEqualTo("userId", currentUserId).get().await()
        myCards = mySnapshot.documents.mapNotNull { it.toKpopCard() }

        // 2. 抓取別人的卡片
        val otherSnapshot = db.collection("cards").whereNotEqualTo("userId", currentUserId).get().await()
        allOtherCards = otherSnapshot.documents.mapNotNull { it.toKpopCard() }

        // 3. 進行比對
        val results = mutableListOf<MatchResult>()
        allOtherCards.forEach { otherCard ->
            var score = 0f
            val reasons = mutableListOf<String>()

            myCards.forEach { myCard ->
                val heHasWhatIWant = otherCard.memberList.any { it in myCard.wishMemberList } || otherCard.groupName in myCard.wishGroupList
                val iHaveWhatHeWants = myCard.memberList.any { it in otherCard.wishMemberList } || myCard.groupName in otherCard.wishGroupList

                if (heHasWhatIWant && iHaveWhatHeWants) {
                    score = 1.0f
                    reasons.add("完美配對：雙向奔赴！")
                } else if (heHasWhatIWant) {
                    score = maxOf(score, 0.6f)
                    reasons.add("他剛好有你想要的！")
                } else if (iHaveWhatHeWants) {
                    score = maxOf(score, 0.4f)
                    reasons.add("你剛好有他想要的！")
                }
            }

            if (score > 0) {
                results.add(MatchResult(otherCard, score, reasons.distinct()))
            }
        }
        matches = results.sortedByDescending { it.score }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text("智慧配對", fontWeight = FontWeight.Bold, fontSize = 18.sp); Text("根據你的上傳標籤自動匹配", fontSize = 12.sp, color = Color.Gray) } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { p ->
        Box(Modifier.padding(p).fillMaxSize().background(Color(0xFFF7F8FA))) {
            if (isLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else if (matches.isEmpty()) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.AutoMirrored.Filled.ManageSearch, null, Modifier.size(64.dp), tint = Color.LightGray)
                    Spacer(Modifier.height(16.dp))
                    Text("目前尚無匹配對象", fontWeight = FontWeight.Bold, color = Color.Gray)
                    Text("試著多上傳幾張小卡並填寫許願標籤吧！", fontSize = 13.sp, color = Color.Gray)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(matches) { match ->
                        MatchCardItem(match, onStartChat, onViewProfile, currentUserId)
                    }
                }
            }
        }
    }
}

@Composable
fun MatchCardItem(match: MatchResult, onStartChat: (String, KpopCard?) -> Unit, onViewProfile: (String) -> Unit, currentUserId: String) {
    val card = match.card
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(userId = card.userId, size = 32, defaultUrl = card.ownerProfileImageUrl)
                Spacer(Modifier.width(8.dp))
                Text(card.ownerName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                Surface(
                    color = when {
                        match.score >= 0.9f -> Color(0xFFFFEBEE)
                        match.score >= 0.5f -> Color(0xFFE3F2FD)
                        else -> Color(0xFFF5F5F5)
                    },
                    shape = CircleShape
                ) {
                    Text(
                        text = match.reasons.firstOrNull() ?: "初步匹配",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            match.score >= 0.9f -> Color(0xFFE91E63)
                            match.score >= 0.5f -> Color(0xFF1976D2)
                            else -> Color.Gray
                        }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.height(120.dp)) {
                AsyncImage(
                    model = card.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.width(90.dp).fillMaxHeight().clip(RoundedCornerShape(8.dp)).background(Color(0xFFF0F0F0)),
                    contentScale = ContentScale.Crop)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(card.groupName.split("|").first(), fontSize = 11.sp, color = Color.Gray)
                    Text(card.memberName, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
                    if (card.cardType.isNotEmpty()) {
                        Text(card.cardType, fontSize = 12.sp, color = Color.DarkGray)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("他想要：", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEC407A))
                    Text(card.wishlist.ifBlank { "未指定" }, fontSize = 12.sp, maxLines = 2, color = Color.DarkGray)
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onViewProfile(card.userId) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("查看個人", fontSize = 13.sp)
                }
                Button(
                    onClick = {
                        scope.launch { findOrCreateChatRoom(currentUserId, card.userId, card.id) { roomId -> onStartChat(roomId, card) } }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5C6BC0))
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("立即聊聊", fontSize = 13.sp)
                }
            }
        }
    }
}

data class MatchResult(val card: KpopCard, val score: Float, val reasons: List<String>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyExchangeView(currentUserId: String, initialLocation: String, onBack: () -> Unit, onStartChat: (String, KpopCard?) -> Unit, onViewProfile: (String) -> Unit) {
    val db = FirebaseFirestore.getInstance()
    val scope = rememberCoroutineScope()
    val locationParts = initialLocation.split(" ")
    var selectedCity by remember { mutableStateOf(locationParts.getOrNull(0) ?: "台北市") }
    var selectedDistrict by remember { mutableStateOf(locationParts.getOrNull(1) ?: "全部") }

    var nearbyCards by remember { mutableStateOf<List<KpopCard>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    var selectedCard by remember { mutableStateOf<KpopCard?>(null) }
    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    var ownerAvgRating by remember { mutableDoubleStateOf(0.0) }
    var ownerReviewCount by remember { mutableIntStateOf(0) }

    val cities = KpopData.cityDistricts.keys.toList()
    val districts = listOf("全部") + (KpopData.cityDistricts[selectedCity] ?: emptyList())

    LaunchedEffect(selectedCity, selectedDistrict) {
        isLoading = true
        val queryLocation = if (selectedDistrict != "全部") "$selectedCity $selectedDistrict" else selectedCity

        db.collection("cards")
            .whereGreaterThanOrEqualTo("location", queryLocation)
            .whereLessThanOrEqualTo("location", queryLocation + "\uf8ff")
            .get()
            .addOnSuccessListener { snapshot ->
                nearbyCards = snapshot.documents.mapNotNull { it.toKpopCard() }
                    .filter { it.userId != currentUserId && it.status == "available" }
                isLoading = false
            }
    }

    LaunchedEffect(selectedCard) {
        selectedCard?.let { card ->
            db.collection("reviews").whereEqualTo("revieweeId", card.userId).addSnapshotListener { s, _ ->
                s?.let {
                    val count = it.size()
                    ownerReviewCount = count
                    ownerAvgRating = if (count > 0) it.documents.sumOf { doc -> doc.getLong("rating")?.toDouble() ?: 0.0 } / count else 0.0
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text("鄰近交換", fontWeight = FontWeight.Bold, fontSize = 18.sp); Text("尋找身邊的卡友面交", fontSize = 12.sp, color = Color.Gray) } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { p ->
        Column(Modifier.padding(p).fillMaxSize().background(Color(0xFFF7F8FA))) {
            Surface(color = Color.White, shadowElevation = 1.dp) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    var cityExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = cityExpanded, onExpandedChange = { cityExpanded = it }, modifier = Modifier.weight(1f)) {
                        OutlinedTextField(value = selectedCity, onValueChange = {}, readOnly = true, label = { Text("縣市") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cityExpanded) }, modifier = Modifier.menuAnchor(), textStyle = TextStyle(fontSize = 14.sp), shape = RoundedCornerShape(12.dp))
                        ExposedDropdownMenu(expanded = cityExpanded, onDismissRequest = { cityExpanded = false }) {
                            cities.forEach { city -> DropdownMenuItem(text = { Text(city) }, onClick = { selectedCity = city; selectedDistrict = "全部"; cityExpanded = false }) }
                        }
                    }
                    var distExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = distExpanded, onExpandedChange = { distExpanded = it }, modifier = Modifier.weight(1f)) {
                        OutlinedTextField(value = selectedDistrict, onValueChange = {}, readOnly = true, label = { Text("區域") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = distExpanded) }, modifier = Modifier.menuAnchor(), textStyle = TextStyle(fontSize = 14.sp), shape = RoundedCornerShape(12.dp))
                        ExposedDropdownMenu(expanded = distExpanded, onDismissRequest = { distExpanded = false }) {
                            districts.forEach { dist -> DropdownMenuItem(text = { Text(dist) }, onClick = { selectedDistrict = dist; distExpanded = false }) }
                        }
                    }
                }
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (nearbyCards.isEmpty()) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.LocationOff, null, Modifier.size(64.dp), tint = Color.LightGray)
                    Spacer(Modifier.height(16.dp))
                    Text("此地區目前尚無小卡", fontWeight = FontWeight.Bold, color = Color.Gray)
                    Text("換個地區看看，或上傳你的小卡吧！", fontSize = 13.sp, color = Color.Gray)
                }
            } else {
                LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(nearbyCards) { card ->
                        NearbyCardItem(card, onStartChat, onViewProfile, { selectedCard = card }, currentUserId)
                    }
                }
            }
        }
    }

    if (selectedCard != null) {
        val card = selectedCard!!
        Dialog(onDismissRequest = { selectedCard = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Card(modifier = Modifier.fillMaxWidth(0.82f).wrapContentHeight().padding(vertical = 20.dp), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F3F9))) {
                Box(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp).verticalScroll(rememberScrollState())) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(38.dp).clip(CircleShape).background(Color.White)) {
                                UserAvatar(userId = card.userId, size = 38, defaultUrl = card.ownerProfileImageUrl)
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(card.ownerName, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color.Black)
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Default.Star, null, tint = Color(0xFFFFD700), modifier = Modifier.size(12.dp))
                                    Text(if (ownerReviewCount > 0) String.format(Locale.getDefault(), " %.1f(%d)", ownerAvgRating, ownerReviewCount) else " 尚無評價", fontSize = 11.sp, color = Color(0xFF4A5568))
                                }
                                Text("查看個人主頁 >", fontSize = 10.sp, color = Color(0xFF5C6BC0), fontWeight = FontWeight.ExtraBold, modifier = Modifier.clickable { onViewProfile(card.userId); selectedCard = null })
                            }
                            IconButton(onClick = { selectedCard = null }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Close, null, tint = Color.Gray.copy(0.5f), modifier = Modifier.size(18.dp)) }
                        }

                        Spacer(Modifier.height(12.dp))
                        Box(Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(16.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                            AsyncImage(model = card.imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize().clickable { previewImageUrl = card.imageUrl }, contentScale = ContentScale.Fit)
                        }

                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Surface(color = Color(0xFF586795), shape = RoundedCornerShape(8.dp)) { Text(card.groupName.split("|").first(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) }
                            Spacer(Modifier.width(8.dp))
                            Text(card.memberName, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color(0xFF1A202C))
                        }

                        if (card.cardType.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(card.cardType, fontSize = 14.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                        }

                        Spacer(Modifier.height(16.dp))
                        DetailSection(label = "WISH LIST", icon = Icons.Default.Favorite) {
                            Text(card.wishlist, fontSize = 14.sp, color = Color(0xFF2D3748), lineHeight = 20.sp)
                            if (card.wishlistImageUrls.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(card.wishlistImageUrls) { url ->
                                        AsyncImage(model = url, contentDescription = null, modifier = Modifier.size(90.dp, 120.dp).clip(RoundedCornerShape(8.dp)).background(Color.White).clickable { previewImageUrl = url }, contentScale = ContentScale.Crop)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        DetailSection(label = "REMARKS", icon = Icons.AutoMirrored.Filled.Notes) {
                            Text(card.remarks.ifBlank { "無備註" }, fontSize = 14.sp, color = Color(0xFF4A5568), lineHeight = 20.sp)
                        }

                        Spacer(Modifier.height(20.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            if (card.location.isNotEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.LocationOn, null, tint = Color(0xFF5C6BC0), modifier = Modifier.size(14.dp))
                                    Text(" ${card.location}", fontSize = 13.sp, color = Color(0xFF5C6BC0), fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Spacer(Modifier.width(1.dp))
                            }

                            if (card.createdAt != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Event, null, tint = Color.LightGray, modifier = Modifier.size(14.dp))
                                    Text(" ${SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(card.createdAt.toDate())}", fontSize = 13.sp, color = Color.Gray)
                                }
                            }
                        }
                        Spacer(Modifier.height(60.dp))
                    }
                    if (card.userId != currentUserId) {
                        Button(onClick = {
                            scope.launch { findOrCreateChatRoom(currentUserId, card.userId, card.id) { roomId -> onStartChat(roomId, card); selectedCard = null } }
                        }, modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).height(44.dp), shape = RoundedCornerShape(22.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF586795))) {
                            Icon(Icons.AutoMirrored.Filled.Chat, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.6.dp))
                            Text("與他聊聊", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }
    }

    if (previewImageUrl != null) {
        Dialog(onDismissRequest = { previewImageUrl = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black).clickable { previewImageUrl = null }, contentAlignment = Alignment.Center) {
                AsyncImage(model = previewImageUrl, contentDescription = "預覽圖片", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
        }
    }
}

@Composable
fun NearbyCardItem(card: KpopCard, onStartChat: (String, KpopCard?) -> Unit, onViewProfile: (String) -> Unit, onCardClick: () -> Unit, currentUserId: String) {
    val scope = rememberCoroutineScope()
    Card(shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), modifier = Modifier.fillMaxWidth().clickable { onCardClick() }, colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column {
            Box(Modifier.height(160.dp).fillMaxWidth()) {
                AsyncImage(model = card.imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Surface(color = Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(bottomEnd = 12.dp), modifier = Modifier.align(Alignment.TopStart)) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, null, tint = Color.White, modifier = Modifier.size(10.dp))
                        Text(card.location.split(" ").lastOrNull() ?: "", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Column(Modifier.padding(12.dp)) {
                Text(card.memberName, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                Text(card.groupName.split("|").first(), fontSize = 11.sp, color = Color.Gray)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onViewProfile(card.userId) },
                        modifier = Modifier.weight(1f).height(32.dp),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("查看主頁", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { scope.launch { findOrCreateChatRoom(currentUserId, card.userId, card.id) { roomId -> onStartChat(roomId, card) } } },
                        modifier = Modifier.weight(1f).height(32.dp),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5C6BC0))
                    ) {
                        Text("私訊面交", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Suppress("UNCHECKED_CAST")
fun DocumentSnapshot.toKpopCard(): KpopCard? {
    return try {
        KpopCard(
            id = id,
            memberName = getString("memberName") ?: "",
            groupName = getString("groupName") ?: "",
            ownerName = getString("ownerNickname") ?: "未知用戶",
            imageUrl = getString("imageUrl") ?: "",
            ownerProfileImageUrl = getString("ownerProfileImageUrl") ?: "",
            wishlist = getString("wishlist") ?: "",
            remarks = getString("remarks") ?: "",
            wishlistImageUrls = (get("wishlistImageUrls") as? List<String>) ?: emptyList(),
            userId = getString("userId") ?: "",
            status = getString("status") ?: "available",
            createdAt = getTimestamp("createdAt"),
            location = getString("location") ?: "",
            wishGroupList = (get("wishGroupList") as? List<String>) ?: emptyList(),
            wishMemberList = (get("wishMemberList") as? List<String>) ?: emptyList(),
            memberList = (get("memberList") as? List<String>) ?: emptyList(),
            cardType = getString("cardType") ?: ""
        )
    } catch (_: Exception) {
        null
    }
}

@Composable
fun ExploreBentoCard(title: String, subtitle: String, icon: ImageVector, backgroundColor: Color, iconColor: Color, isLarge: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isLarge) 160.dp else 145.dp)
            .clip(RoundedCornerShape(28.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor.copy(alpha = 0.1f),
                modifier = Modifier.align(Alignment.BottomEnd).size(if(isLarge) 110.dp else 80.dp).offset(x = 15.dp, y = 15.dp)
            )
            Column(modifier = Modifier.align(Alignment.TopStart)) {
                Surface(color = Color.White.copy(alpha = 0.6f), shape = CircleShape, modifier = Modifier.size(if(isLarge) 44.dp else 36.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(if(isLarge) 22.dp else 18.dp)) }
                }
                Spacer(Modifier.height(if(isLarge) 16.dp else 12.dp))
                Text(text = title, color = Color(0xFF2D3436), fontSize = if(isLarge) 20.sp else 16.sp, fontWeight = FontWeight.Bold)
                Text(text = subtitle, color = Color(0xFF636E72), fontSize = if(isLarge) 13.sp else 11.sp, maxLines = 1)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreEventsView(currentUserId: String, onBack: () -> Unit, onAddClick: () -> Unit, onEditClick: (KpopEvent) -> Unit, onViewProfile: (String) -> Unit) {
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current
    var events by remember { mutableStateOf<List<KpopEvent>>(emptyList()) }
    val isGuest = currentUserId.isEmpty()

    LaunchedEffect(Unit) {
        db.collection("events").orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { value, _ ->
                events = value?.documents?.mapNotNull { doc -> doc.toObject(KpopEvent::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.copy(id = doc.id) } ?: emptyList()
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("最新應援活動", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = {
                        if (isGuest) {
                            Toast.makeText(context, "請先登入後再發佈活動", Toast.LENGTH_SHORT).show()
                        } else {
                            onAddClick()
                        }
                    }) { Icon(Icons.Default.AddBox, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White, titleContentColor = Color.Black, navigationIconContentColor = Color.Black)
            )
        }
    ) { p ->
        if (events.isEmpty()) { Box(Modifier.fillMaxSize().padding(p), Alignment.Center) { Text("尚無相關活動資訊", color = Color.Gray) } }
        else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(p).background(Color(0xFFFAFAFA)), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(events) { event ->
                    EventPostItem(event = event, currentUserId = currentUserId, onEdit = { onEditClick(event) }, onDelete = { db.collection("events").document(event.id).delete() }, onViewProfile = onViewProfile)
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreEventUploadView(userId: String, existingEvent: KpopEvent? = null, onBack: () -> Unit, onSuccess: () -> Unit) {
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf(existingEvent?.title ?: "") }
    var type by remember { mutableStateOf(existingEvent?.type ?: "生日咖啡廳") }
    var groupName by remember { mutableStateOf(existingEvent?.groupName ?: "") }
    var location by remember { mutableStateOf(existingEvent?.location ?: "") }
    var startDate by remember { mutableStateOf(existingEvent?.startDate ?: "") }
    var endDate by remember { mutableStateOf(existingEvent?.endDate ?: "") }
    var description by remember { mutableStateOf(existingEvent?.description ?: "") }
    var imageUrl by remember { mutableStateOf(existingEvent?.imageUrl ?: "") }
    var isUploading by remember { mutableStateOf(false) }

    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            isUploading = true
            ImgbbUploader.uploadImage(context, it, scope, onSuccess = { url -> imageUrl = url; isUploading = false }, onFailure = { isUploading = false })
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text(if(existingEvent == null) "上傳應援活動" else "編輯活動", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }) }) { p ->
        Column(modifier = Modifier.fillMaxSize().padding(p).padding(20.dp).verticalScroll(rememberScrollState())) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF0F0F0)).clickable { imageLauncher.launch("image/*") }, contentAlignment = Alignment.Center) {
                if (imageUrl.isNotEmpty()) AsyncImage(model = imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else if (isUploading) CircularProgressIndicator()
                else Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.AddPhotoAlternate, null, Modifier.size(40.dp), tint = Color.Gray); Text("上傳活動海報", color = Color.Gray) }
            }
            Spacer(Modifier.height(20.dp)); OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("活動名稱") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp)); OutlinedTextField(value = groupName, onValueChange = { groupName = it }, label = { Text("所屬團體") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp)); Text("活動類型", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("生日咖啡廳", "領取應援", "打卡燈箱").forEach { t -> FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t) }) } }
            OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("地點") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { OutlinedTextField(value = startDate, onValueChange = { startDate = it }, label = { Text("開始日期") }, modifier = Modifier.weight(1f), placeholder = { Text("YYYY/MM/DD") }); OutlinedTextField(value = endDate, onValueChange = { endDate = it }, label = { Text("結束日期") }, modifier = Modifier.weight(1f), placeholder = { Text("YYYY/MM/DD") }) }
            Spacer(Modifier.height(12.dp)); OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("活動詳情描述") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            Spacer(Modifier.height(32.dp)); Button(onClick = { if (title.isBlank() || imageUrl.isEmpty()) { Toast.makeText(context, "請填寫名稱並上傳圖片", Toast.LENGTH_SHORT).show(); return@Button }; val eventData = mutableMapOf("title" to title, "type" to type, "groupName" to groupName, "location" to location, "startDate" to startDate, "endDate" to endDate, "description" to description, "imageUrl" to imageUrl, "userId" to userId, "createdAt" to (existingEvent?.createdAt ?: FieldValue.serverTimestamp())); val task = if (existingEvent == null) db.collection("events").add(eventData) else db.collection("events").document(existingEvent.id).set(eventData); task.addOnSuccessListener { Toast.makeText(context, if(existingEvent == null) "發佈成功！" else "更新成功！", Toast.LENGTH_SHORT).show(); onSuccess() } }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp), enabled = !isUploading) { Text(if(existingEvent == null) "確認發佈活動" else "儲存修改", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
fun EventPostItem(event: KpopEvent, currentUserId: String, onEdit: () -> Unit, onDelete: () -> Unit, onViewProfile: (String) -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    var publisherName by remember { mutableStateOf("載入中...") }
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current

    LaunchedEffect(event.userId) {
        db.collection("users").document(event.userId).get().addOnSuccessListener { publisherName = it.getString("nickname") ?: "用戶" }
    }

    Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clickable { onViewProfile(event.userId) }) { UserAvatar(userId = event.userId, size = 36) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.clickable { onViewProfile(event.userId) }) { Text(text = publisherName, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text(text = event.location, fontSize = 12.sp, color = Color.Gray) }
            Spacer(Modifier.weight(1f))
            if (event.userId == currentUserId && currentUserId.isNotEmpty()) {
                Box {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreHoriz, null, tint = Color.Gray) }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("編輯活動") }, onClick = { showMenu = false; onEdit() }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                        DropdownMenuItem(text = { Text("刪除活動", color = Color.Red) }, onClick = { showMenu = false; onDelete(); Toast.makeText(context, "活動已刪除", Toast.LENGTH_SHORT).show() }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) })
                    }
                }
            }
        }
        AsyncImage(model = event.imageUrl, contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(1f).background(Color(0xFFF0F0F0)), contentScale = ContentScale.Crop)
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Color(0xFFE3F2FD), shape = RoundedCornerShape(4.dp)) { Text(text = event.type, color = Color(0xFF1976D2), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) }
                Spacer(Modifier.width(8.dp)); Text(text = "📅 ${event.startDate} ~ ${event.endDate}", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(12.dp)); Text(text = event.title, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp)); Text(text = event.description, fontSize = 14.sp, lineHeight = 20.sp)
        }
        HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFEEEEEE))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MainDashboard(
    userId: String,
    isAdmin: Boolean,
    onStartChat: (String, KpopCard?) -> Unit,
    onViewProfile: (String) -> Unit,
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
    onEditCard: (KpopCard) -> Unit
) {
    val db = FirebaseFirestore.getInstance(); val context = LocalContext.current; val scope = rememberCoroutineScope()
    var cardList by remember { mutableStateOf<List<KpopCard>>(emptyList()) }; var favoriteIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var tabs by remember { mutableStateOf(listOf("全部")) }; var selectedCard by remember { mutableStateOf<KpopCard?>(null) }; var cardToDelete by remember { mutableStateOf<KpopCard?>(null) }
    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    var showTabSettingsDialog by remember { mutableStateOf(false) }; var searchQuery by remember { mutableStateOf("") }
    val isGuest = userId.isEmpty()
    val allGroups = listOf("&TEAM", "aespa", "AHOF", "ALLDAY PROJECT", "ALPHA DRIVE ONE", "Apink", "ARrC", "ASTRO", "ATEEZ", "BSS", "BABYMONSTER", "Billlie", "BOYNEXTDOOR", "BTS", "BIGBANG", "CLASS:y", "CLOSE YOUR EYES", "CNBLUE", "CORTIS", "CRAVITY", "Dreamcatcher", "ENHYPEN", "EXO", "fromis_9", "G-Dragon", "Girls' Generation", "GFRIEND", "GOT the beat", "GOT7", "Heart2Hearts", "I-DLE", "IDID", "ITZY", "IVE", "izna", "IZ*ONE", "KiiiKiii", "KISS OF LIFE", "LE SSERAFIM", "LIGHTSUM", "MAMAMOO", "MAMAMOO+", "MEOVV", "MONSTA X", "NCT 127", "NCT DOJAEJUNG", "NCT DREAM", "NCT WISH", "NewJeans", "NEXZ", "NiziU", "NMIXX", "P1Harmony", "PURPLE K!SS", "QWER", "Red Velvet", "Red Velvet - IRENE & SEULGI", "SAY MY NAME", "SEVENTEEN", "SF9", "STAYC", "Stray Kids", "SuperM", "TEMPEST", "THE BOYZ", "TOMORROW X TOGETHER", "tripleS", "TWICE", "TWS", "VERIVERY", "VIVIZ", "WANNA ONE", "WayV", "WEi", "WJSN", "ZEROBASEONE").sorted()

    var ownerAvgRating by remember { mutableDoubleStateOf(0.0) }
    var ownerReviewCount by remember { mutableIntStateOf(0) }

    var showChineseName by rememberSaveable { mutableStateOf(false) }

    var reportingCardId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(userId) { if (!isGuest) db.collection("users").document(userId).addSnapshotListener { snapshot, _ -> val customTabs = snapshot?.get("customDashboardTabs") as? List<*>; if (customTabs != null) tabs = listOf("全部") + customTabs.mapNotNull { it?.toString() }; val favs = snapshot?.get("favoriteCardIds") as? List<*>; favoriteIds = favs?.mapNotNull { it?.toString() }?.toSet() ?: emptySet() } }
    LaunchedEffect(Unit) {
        db.collection("cards").orderBy("createdAt", Query.Direction.DESCENDING).addSnapshotListener { value, error ->
            if (error == null && value != null) {
                cardList = value.documents.mapNotNull { doc -> doc.toKpopCard() }
            }
        }
    }

    LaunchedEffect(selectedCard) {
        selectedCard?.let { card ->
            db.collection("reviews").whereEqualTo("revieweeId", card.userId).addSnapshotListener { s, _ ->
                s?.let {
                    val count = it.size()
                    ownerReviewCount = count
                    ownerAvgRating = if (count > 0) it.documents.sumOf { doc -> doc.getLong("rating")?.toDouble() ?: 0.0 } / count else 0.0
                }
            }
        }
    }

    val filteredCards = cardList.filter { card -> (if (selectedTab == 0 || selectedTab >= tabs.size) true else card.groupName.contains(tabs[selectedTab], ignoreCase = true)) && (if (searchQuery.isEmpty()) true else card.memberName.lowercase().contains(searchQuery.trim().lowercase()) || card.groupName.lowercase().contains(searchQuery.trim().lowercase())) }

    fun formatMemberName(raw: String): String {
        return raw.split(", ").joinToString(", ") { m ->
            val parts = m.split("|")
            if (showChineseName && parts.size > 1) parts[1] else parts[0]
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF7F8FA))) {
        Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary, tonalElevation = 4.dp) {
            Row(modifier = Modifier.statusBarsPadding().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("KaOne!", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White, modifier = Modifier.padding(end = 12.dp))
                Box(modifier = Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(19.dp)).background(Color.White).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp), tint = Color.Gray)
                        Spacer(Modifier.width(8.dp))
                        BasicTextField(value = searchQuery, onValueChange = { searchQuery = it }, modifier = Modifier.weight(1f), singleLine = true, textStyle = TextStyle(fontSize = 14.sp, color = Color.Black), cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), decorationBox = { if (searchQuery.isEmpty()) Text("搜尋小卡或團體...", fontSize = 14.sp, color = Color.Gray); it() })
                        if (searchQuery.isNotEmpty()) Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp).clickable { searchQuery = "" }, tint = Color.Gray)
                    }
                }
                IconButton(onClick = { showChineseName = !showChineseName }) {
                    Surface(color = Color.White.copy(alpha = 0.2f), shape = CircleShape, modifier = Modifier.size(32.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(if (showChineseName) "中" else "En", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                IconButton(onClick = { if (isGuest) Toast.makeText(context, "請先登入後再設定標籤", Toast.LENGTH_SHORT).show() else showTabSettingsDialog = true }) { Icon(Icons.Default.Tune, "設定", tint = Color.White) }
            }
        }
        ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 16.dp, divider = {}, containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.primary) { tabs.forEachIndexed { index, title -> Tab(selected = selectedTab == index, onClick = { onTabChange(index) }, text = { Text(title, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) }) } }
        LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(filteredCards) { card ->
                CardItem(card = card, isFavorite = card.id in favoriteIds, showFavorite = card.userId != userId, onClick = { selectedCard = card }, onFavoriteClick = {
                    if (isGuest) {
                        Toast.makeText(context, "請先登入後再收藏", Toast.LENGTH_SHORT).show()
                    } else {
                        val update = if (card.id in favoriteIds) FieldValue.arrayRemove(card.id) else FieldValue.arrayUnion(card.id)
                        db.collection("users").document(userId).update("favoriteCardIds", update)
                    }
                }, memberNameDisplay = formatMemberName(card.memberName))
            }
        }
    }

    if (selectedCard != null) {
        val card = selectedCard!!
        Dialog(onDismissRequest = { selectedCard = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Card(modifier = Modifier.fillMaxWidth(0.82f).wrapContentHeight().padding(vertical = 20.dp), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F3F9))) {
                Box(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp).verticalScroll(rememberScrollState())) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(38.dp).clip(CircleShape).background(Color.White)) {
                                UserAvatar(userId = card.userId, size = 38, defaultUrl = card.ownerProfileImageUrl)
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(card.ownerName, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color.Black)
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Default.Star, null, tint = Color(0xFFFFD700), modifier = Modifier.size(12.dp))
                                    Text(if (ownerReviewCount > 0) String.format(Locale.getDefault(), " %.1f(%d)", ownerAvgRating, ownerReviewCount) else " 尚無評價", fontSize = 11.sp, color = Color(0xFF4A5568))
                                }
                                Text("查看個人主頁 >", fontSize = 10.sp, color = Color(0xFF5C6BC0), fontWeight = FontWeight.ExtraBold, modifier = Modifier.clickable { onViewProfile(card.userId); selectedCard = null })
                            }
                            IconButton(onClick = { selectedCard = null }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Close, null, tint = Color.Gray.copy(0.5f), modifier = Modifier.size(18.dp)) }
                        }
                        
                        Spacer(Modifier.height(12.dp))
                        Box(Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(16.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                            AsyncImage(model = card.imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize().clickable { previewImageUrl = card.imageUrl }, contentScale = ContentScale.Fit)
                        }
                        
                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Surface(color = Color(0xFF586795), shape = RoundedCornerShape(8.dp)) { Text(card.groupName.split("|").first(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) }
                            Spacer(Modifier.width(8.dp))
                            Text(formatMemberName(card.memberName), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color(0xFF1A202C))
                        }
                        
                        if (card.cardType.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(card.cardType, fontSize = 14.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                        }

                        Spacer(Modifier.height(16.dp))
                        DetailSection(label = "WISH LIST", icon = Icons.Default.Favorite) {
                            Text(card.wishlist, fontSize = 14.sp, color = Color(0xFF2D3748), lineHeight = 20.sp)
                            if (card.wishlistImageUrls.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(card.wishlistImageUrls) { url ->
                                        AsyncImage(model = url, contentDescription = null, modifier = Modifier.size(90.dp, 120.dp).clip(RoundedCornerShape(8.dp)).background(Color.White).clickable { previewImageUrl = url }, contentScale = ContentScale.Crop)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        DetailSection(label = "REMARKS", icon = Icons.AutoMirrored.Filled.Notes) {
                            Text(card.remarks.ifBlank { "無備註" }, fontSize = 14.sp, color = Color(0xFF4A5568), lineHeight = 20.sp)
                        }

                        Spacer(Modifier.height(20.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            if (card.location.isNotEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.LocationOn, null, tint = Color(0xFF5C6BC0), modifier = Modifier.size(14.dp))
                                    Text(" ${card.location}", fontSize = 13.sp, color = Color(0xFF5C6BC0), fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Spacer(Modifier.width(1.dp))
                            }
                            
                            if (card.createdAt != null) { 
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Event, null, tint = Color.LightGray, modifier = Modifier.size(14.dp))
                                    Text(" ${SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(card.createdAt.toDate())}", fontSize = 13.sp, color = Color.Gray)
                                }
                            }
                        }
                        Spacer(Modifier.height(60.dp))
                    }
                    if (card.userId != userId) {
                        Row(modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { reportingCardId = card.id }, modifier = Modifier.size(42.dp).background(Color(0xFFF5F5F5), CircleShape)) { Icon(Icons.Default.Report, "檢舉", tint = Color.Gray, modifier = Modifier.size(20.dp)) }
                            Spacer(Modifier.width(8.dp))
                            if (isAdmin && !isGuest) {
                                IconButton(onClick = { cardToDelete = card }, modifier = Modifier.size(42.dp).background(Color(0xFFFFEBEE), CircleShape)) { Icon(Icons.Default.Delete, "管理員刪除", tint = Color.Red, modifier = Modifier.size(20.dp)) }
                                Spacer(Modifier.width(8.dp))
                            }
                            Button(onClick = { 
                                if (isGuest) {
                                    Toast.makeText(context, "請先登入後再聊天", Toast.LENGTH_SHORT).show()
                                } else {
                                    scope.launch { findOrCreateChatRoom(userId, card.userId, card.id) { roomId -> onStartChat(roomId, card); selectedCard = null } } 
                                }
                            }, modifier = Modifier.height(44.dp), shape = RoundedCornerShape(22.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF586795))) {
                                Icon(Icons.AutoMirrored.Filled.Chat, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("與他聊聊", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    } else if (userId.isNotEmpty()) {
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

    if (reportingCardId != null) {
        ReportDialog(reporterId = userId, targetId = reportingCardId!!, targetType = "card", onDismiss = { reportingCardId = null })
    }

    if (cardToDelete != null) AlertDialog(onDismissRequest = { cardToDelete = null }, title = { Text("刪除作品") }, text = { Text("確定要刪除這張小卡嗎？此動作無法復原。") }, confirmButton = { TextButton(onClick = { db.collection("cards").document(cardToDelete!!.id).delete().addOnSuccessListener { Toast.makeText(context, "已刪除", Toast.LENGTH_SHORT).show(); cardToDelete = null; selectedCard = null } }) { Text("確定刪除", color = Color.Red) } }, dismissButton = { TextButton(onClick = { cardToDelete = null }) { Text("取消") } })
    if (showTabSettingsDialog) { var tempSelectedList by remember { mutableStateOf(tabs.filter { it != "全部" }) }; AlertDialog(onDismissRequest = { showTabSettingsDialog = false }, title = { Text("標籤順序") }, text = { Column(modifier = Modifier.verticalScroll(rememberScrollState())) { if (tempSelectedList.isNotEmpty()) { Text("目前排列：", fontWeight = FontWeight.Bold); FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { tempSelectedList.forEach { group -> FilterChip(selected = true, onClick = { tempSelectedList = tempSelectedList - group }, label = { Text(group) }) } } }; Spacer(Modifier.height(16.dp)); Text("可加入團體 (字母分類)：", fontWeight = FontWeight.Bold); val grouped = allGroups.filter { it !in tempSelectedList }.groupBy { val c = it.first().uppercaseChar(); if (c.isLetter()) c.toString() else "#" }; grouped.keys.sorted().forEach { key -> Text(text = key, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)); FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { grouped[key]?.forEach { group -> FilterChip(selected = false, onClick = { tempSelectedList = tempSelectedList + group }, label = { Text(group) }) } }; HorizontalDivider(modifier = Modifier.padding(top = 12.dp), thickness = 0.5.dp, color = Color.LightGray) } } }, confirmButton = { Button(onClick = { db.collection("users").document(userId).update("customDashboardTabs", tempSelectedList).addOnSuccessListener { showTabSettingsDialog = false; onTabChange(0) } }) { Text("儲存排列") } }) }

    if (previewImageUrl != null) {
        Dialog(onDismissRequest = { previewImageUrl = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black).clickable { previewImageUrl = null }, contentAlignment = Alignment.Center) {
                AsyncImage(model = previewImageUrl, contentDescription = "預覽圖片", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
        }
    }
}

suspend fun findOrCreateChatRoom(user1: String, user2: String, cardId: String? = null, onComplete: (String) -> Unit) {
    if (user1 == user2 || user1.isEmpty()) return
    val db = FirebaseFirestore.getInstance(); val participants = listOf(user1, user2).sorted()
    val existingRoom = db.collection("chatRooms").whereEqualTo("participantIds", participants).get().await()
    if (existingRoom.documents.isNotEmpty()) { val roomId = existingRoom.documents.first().id; db.collection("chatRooms").document(roomId).update("activeInquiryCardId", cardId ?: ""); onComplete(roomId) }
    else { val newRoom = hashMapOf("participantIds" to participants, "createdAt" to FieldValue.serverTimestamp(), "lastMessage" to "", "lastMessageTime" to FieldValue.serverTimestamp(), "activeInquiryCardId" to (cardId ?: ""), "unreadCount" to mapOf(user1 to 0, user2 to 0)); val addedRoom = db.collection("chatRooms").add(newRoom).await(); onComplete(addedRoom.id) }
}

@Composable
fun CardItem(card: KpopCard, isFavorite: Boolean, showFavorite: Boolean = true, onClick: () -> Unit, onFavoriteClick: () -> Unit, memberNameDisplay: String) {
    Card(
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(185.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 0.dp, bottomEnd = 0.dp))
                    .background(Color(0xFFF5F5F5))
            ) {
                if (card.imageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = card.imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.Image,
                        null,
                        tint = Color.Gray,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            
            Column(Modifier.padding(horizontal = 13.dp, vertical = 8.dp)) {
                Text(
                    text = memberNameDisplay,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.Black,
                    maxLines = 1
                )
                Text(
                    text = card.groupName.split("|").first(),
                    fontSize = 13.sp,
                    color = Color.Gray,
                    maxLines = 1
                )
                
                Spacer(Modifier.height(8.dp))
                
                if (card.cardType.isNotEmpty()) {
                    Surface(
                        color = Color(0xFFF0F4FF),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = card.cardType,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF5C6BC0)
                        )
                    }
                }
                
                Spacer(Modifier.height(10.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        UserAvatar(userId = card.userId, size = 18, defaultUrl = card.ownerProfileImageUrl)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = card.ownerName,
                            fontSize = 11.sp,
                            color = Color.Gray,
                            maxLines = 1
                        )
                    }
                    
                    if (showFavorite) {
                        IconButton(
                            onClick = onFavoriteClick,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                                null,
                                tint = if (isFavorite) Color.Red else Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

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
