package com.example.kaone.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(onBack: () -> Unit) {
    val db = FirebaseFirestore.getInstance()
    var userCount by remember { mutableIntStateOf(0) }
    var cardCount by remember { mutableIntStateOf(0) }
    var reportCount by remember { mutableIntStateOf(0) }
    var currentView by remember { mutableStateOf("menu") } // "menu", "users", "reports"

    LaunchedEffect(Unit) {
        db.collection("users").get().addOnSuccessListener { userCount = it.size() }
        db.collection("cards").get().addOnSuccessListener { cardCount = it.size() }
        db.collection("reports").whereEqualTo("status", "pending").addSnapshotListener { s, _ ->
            reportCount = s?.size() ?: 0
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (currentView) {
                            "users" -> "用戶權限管理"
                            "reports" -> "檢舉內容處理"
                            else -> "管理員後台"
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (currentView == "menu") onBack() else currentView = "menu" }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (currentView) {
                "menu" -> AdminMenuView(userCount, cardCount, reportCount,
                    onNavigateToUsers = { currentView = "users" },
                    onNavigateToReports = { currentView = "reports" }
                )
                "users" -> AdminUserListView()
                "reports" -> AdminReportListView()
            }
        }
    }
}

@Composable
fun AdminMenuView(userCount: Int, cardCount: Int, reportCount: Int, onNavigateToUsers: () -> Unit, onNavigateToReports: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AdminStatCard("用戶總數", userCount.toString(), Modifier.weight(1f), Icons.Default.People)
            AdminStatCard("未處理檢舉", reportCount.toString(), Modifier.weight(1f), Icons.Default.Report, if (reportCount > 0) Color.Red else Color.Gray)
        }

        Text(
            "快速管理項目",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            fontWeight = FontWeight.Bold,
            color = Color.Gray
        )

        Card(
            modifier = Modifier.padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column {
                AdminMenuItem("用戶權限管理", "封鎖違規用戶或調整權限") { onNavigateToUsers() }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                AdminMenuItem("檢舉內容處理", if (reportCount > 0) "有 $reportCount 則待處理檢舉" else "查看用戶檢舉的小卡或用戶") { onNavigateToReports() }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                AdminMenuItem("公告發布系統", "發布全系統廣播通知") { /* 實作邏輯 */ }
            }
        }
    }
}

@Composable
fun AdminUserListView() {
    val db = FirebaseFirestore.getInstance()
    var userList by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        db.collection("users").addSnapshotListener { snapshot, _ ->
            userList = snapshot?.documents?.map { it.data?.plus("id" to it.id) ?: emptyMap() } ?: emptyList()
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().background(Color.White)) {
        items(userList) { user ->
            val userId = user["id"] as String
            val nickname = user["nickname"] as? String ?: "未知用戶"
            val email = user["email"] as? String ?: ""
            val isBanned = user["isBanned"] as? Boolean ?: false
            val profileUrl = user["profileImageUrl"] as? String ?: ""

            ListItem(
                headlineContent = { Text(nickname, fontWeight = FontWeight.Bold) },
                supportingContent = { Text(email, fontSize = 12.sp, color = Color.Gray) },
                leadingContent = {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Color.LightGray)) {
                        if (profileUrl.isNotEmpty()) {
                            AsyncImage(model = profileUrl, contentDescription = null, contentScale = ContentScale.Crop)
                        }
                    }
                },
                trailingContent = {
                    TextButton(
                        onClick = {
                            db.collection("users").document(userId).update("isBanned", !isBanned)
                                .addOnSuccessListener {
                                    Toast.makeText(context, if (isBanned) "已解除封鎖" else "已封鎖該用戶", Toast.LENGTH_SHORT).show()
                                }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = if (isBanned) Color.Green else Color.Red)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (isBanned) Icons.Default.CheckCircle else Icons.Default.Block, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (isBanned) "解封" else "封鎖")
                        }
                    }
                }
            )
            HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFEEEEEE))
        }
    }
}

@Composable
fun AdminReportListView() {
    val db = FirebaseFirestore.getInstance()
    var reportList by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    val context = LocalContext.current
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    var inspectingCardId by remember { mutableStateOf<String?>(null) }
    var inspectingCardData by remember { mutableStateOf<KpopCard?>(null) }

    LaunchedEffect(Unit) {
        db.collection("reports")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                reportList = snapshot?.documents?.map { it.data?.plus("id" to it.id) ?: emptyMap() } ?: emptyList()
            }
    }

    LaunchedEffect(inspectingCardId) {
        if (inspectingCardId != null) {
            db.collection("cards").document(inspectingCardId!!).get().addOnSuccessListener { doc ->
                inspectingCardData = doc.toKpopCard()
            }
        } else {
            inspectingCardData = null
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().background(Color.White)) {
        if (reportList.isEmpty()) {
            item {
                Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                    Text("目前沒有檢舉記錄", color = Color.Gray)
                }
            }
        }
        items(reportList) { report ->
            val reportId = report["id"] as String
            val targetType = report["targetType"] as? String ?: ""
            val reason = report["reason"] as? String ?: ""
            val status = report["status"] as? String ?: "pending"
            val timestamp = report["timestamp"] as? Timestamp
            val targetId = report["targetId"] as? String ?: ""

            Card(
                modifier = Modifier.padding(8.dp).fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (status == "pending") Color(0xFFFFF9C4) else Color(0xFFF5F5F5)
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = if (targetType == "user") Color.Blue else Color.Magenta,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                if (targetType == "user") "用戶" else "小卡",
                                color = Color.White,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("狀態: ${if (status == "pending") "待處理" else "已結案"}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text(timestamp?.let { sdf.format(it.toDate()) } ?: "", fontSize = 12.sp, color = Color.Gray)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("原因: $reason", fontWeight = FontWeight.Medium)
                    Text("目標ID: $targetId", fontSize = 11.sp, color = Color.Gray)
                    
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (targetType == "card") {
                            FilledTonalButton(
                                onClick = { inspectingCardId = targetId },
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.Visibility, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("查看內容", fontSize = 12.sp)
                            }
                        }

                        if (status == "pending") {
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = {
                                db.collection("reports").document(reportId).update("status", "resolved")
                                    .addOnSuccessListener { Toast.makeText(context, "已標記為已處理", Toast.LENGTH_SHORT).show() }
                            }, modifier = Modifier.height(32.dp)) {
                                Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("忽略/標記處理", fontSize = 12.sp)
                            }
                            
                            if (targetType == "card") {
                                TextButton(
                                    onClick = {
                                        db.collection("cards").document(targetId).delete()
                                            .addOnSuccessListener {
                                                db.collection("reports").document(reportId).update("status", "resolved_deleted")
                                                Toast.makeText(context, "已刪除該內容", Toast.LENGTH_SHORT).show()
                                            }
                                            .addOnFailureListener {
                                                Toast.makeText(context, "刪除失敗", Toast.LENGTH_SHORT).show()
                                            }
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("刪除", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (inspectingCardData != null) {
        val card = inspectingCardData!!
        Dialog(onDismissRequest = { inspectingCardId = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Card(modifier = Modifier.fillMaxWidth(0.9f).wrapContentHeight().padding(vertical = 20.dp), shape = RoundedCornerShape(28.dp)) {
                Box(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("小卡詳細資訊", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { inspectingCardId = null }) { Icon(Icons.Default.Close, null) }
                        }
                        
                        Spacer(Modifier.height(12.dp))
                        Box(Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(16.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                            AsyncImage(model = card.imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = Color(0xFF586795), shape = RoundedCornerShape(8.dp)) {
                                Text(card.groupName.split("|").first(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(card.memberName, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        }
                        
                        if (card.cardType.isNotEmpty()) {
                            Text(card.cardType, fontSize = 14.sp, color = Color.Gray)
                        }

                        Spacer(Modifier.height(16.dp))
                        AdminDetailSection(label = "許願清單", icon = Icons.Default.Favorite) {
                            Text(card.wishlist, fontSize = 14.sp)
                            if (card.wishlistImageUrls.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(card.wishlistImageUrls) { url ->
                                        AsyncImage(model = url, contentDescription = null, modifier = Modifier.size(80.dp, 110.dp).clip(RoundedCornerShape(8.dp)).background(Color.White), contentScale = ContentScale.Crop)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        AdminDetailSection(label = "備註說明", icon = Icons.AutoMirrored.Filled.Notes) {
                            Text(card.remarks.ifBlank { "無備註" }, fontSize = 14.sp)
                        }

                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Event, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                            Text(" 上架時間: ${card.createdAt?.let { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(it.toDate()) } ?: "未知"}", fontSize = 12.sp, color = Color.Gray)
                        }
                        Text("上傳用戶 ID: ${card.userId}", fontSize = 10.sp, color = Color.Gray)
                        Spacer(Modifier.height(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun AdminDetailSection(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(0.05f)).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color(0xFF586795), modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF586795))
        }
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
fun AdminStatCard(title: String, value: String, modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, valueColor: Color = Color.Unspecified) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = if (valueColor != Color.Unspecified) valueColor else MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(title, fontSize = 12.sp, color = Color.Gray)
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = if (valueColor != Color.Unspecified) valueColor else Color.Black)
        }
    }
}

@Composable
fun AdminMenuItem(title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        headlineContent = { Text(title, fontWeight = FontWeight.Bold) },
        supportingContent = { Text(subtitle, fontSize = 12.sp) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.rotate(180f)) }
    )
}
