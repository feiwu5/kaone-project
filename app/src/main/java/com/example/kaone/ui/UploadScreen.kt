package com.example.kaone.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.kaone.ui.theme.ImgbbUploader
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UploadScreen(
    userId: String,
    onUploadSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    existingCard: KpopCard? = null,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val scope = rememberCoroutineScope()
    
    var userNickname by remember { mutableStateOf("") }
    var userProfileImageUrl by remember { mutableStateOf("") }
    var userLocation by remember { mutableStateOf("") }
    
    LaunchedEffect(userId) {
        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                userNickname = document.getString("nickname") ?: "未知用戶"
                userProfileImageUrl = document.getString("profileImageUrl") ?: ""
                userLocation = document.getString("location") ?: ""
            }
    }

    var imageUrl by remember { mutableStateOf(existingCard?.imageUrl ?: "") }
    var isUploadingMain by remember { mutableStateOf(false) }

    val wishlistImageUrls = remember { 
        mutableStateListOf<String>().apply { 
            existingCard?.wishlistImageUrls?.let { addAll(it) } 
        } 
    }
    var isUploadingWishlist by remember { mutableStateOf(false) }

    val mainLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            isUploadingMain = true
            ImgbbUploader.uploadImage(context, it, scope,
                onSuccess = { url -> imageUrl = url; isUploadingMain = false },
                onFailure = { isUploadingMain = false }
            )
        }
    }

    val wishlistLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            isUploadingWishlist = true
            var uploadedCount = 0
            uris.forEach { uri ->
                ImgbbUploader.uploadImage(context, uri, scope,
                    onSuccess = { url -> 
                        wishlistImageUrls.add(url)
                        uploadedCount++
                        if (uploadedCount == uris.size) isUploadingWishlist = false
                    },
                    onFailure = { 
                        uploadedCount++
                        if (uploadedCount == uris.size) isUploadingWishlist = false
                    }
                )
            }
        }
    }

    fun formatDisplayName(rawName: String): String {
        return if (rawName.contains("|")) { rawName.replace("|", " (") + ")" } else { rawName }
    }

    val groupMembersData = KpopData.groupMembersData
    val allGroups = groupMembersData.keys.sorted()

    // --- 持有小卡資訊 ---
    var groupInput by remember { mutableStateOf(existingCard?.groupName ?: "") }
    var isGroupMenuExpanded by remember { mutableStateOf(false) }
    val selectedMembers = remember { 
        mutableStateListOf<String>().apply { 
            existingCard?.memberList?.let { addAll(it) } 
        } 
    }
    var memberInput by remember { mutableStateOf("") }
    var isMemberMenuExpanded by remember { mutableStateOf(false) }
    
    // --- 持有類型 (專輯/演唱會) ---
    var typeInput by remember { mutableStateOf(existingCard?.cardType ?: "") }
    var isTypeMenuExpanded by remember { mutableStateOf(false) }

    // --- 許願清單資訊 (智慧配對核心) ---
    val selectedWishGroups = remember { 
        mutableStateListOf<String>().apply { 
            existingCard?.wishGroupList?.let { addAll(it) } 
        } 
    }
    var wishGroupInput by remember { mutableStateOf("") }
    var isWishGroupMenuExpanded by remember { mutableStateOf(false) }
    
    val selectedWishMembers = remember { 
        mutableStateListOf<String>().apply { 
            existingCard?.wishMemberList?.let { addAll(it) } 
        } 
    }
    var wishMemberInput by remember { mutableStateOf("") }
    var isWishMemberMenuExpanded by remember { mutableStateOf(false) }

    var wishlist by remember { mutableStateOf(existingCard?.wishlist ?: "") }
    var remarks by remember { mutableStateOf(existingCard?.remarks ?: "") }
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp).verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            }
            Text(
                text = if (existingCard == null) "上傳你的小卡" else "編輯小卡資訊", 
                fontSize = 28.sp, 
                fontWeight = FontWeight.Bold, 
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { if (!isUploadingMain) mainLauncher.launch("image/*") },
            contentAlignment = Alignment.Center
        ) {
            if (imageUrl.isNotEmpty()) {
                AsyncImage(model = imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            } else if (isUploadingMain) {
                CircularProgressIndicator()
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(40.dp), tint = Color.Gray)
                    Text("點擊選取小卡照片 (必填)", color = Color.Gray)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 持有團體
        val filteredGroups = allGroups.filter { it.contains(groupInput, ignoreCase = true) }
        ExposedDropdownMenuBox(
            expanded = isGroupMenuExpanded, 
            onExpandedChange = { isGroupMenuExpanded = it }, 
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = groupInput, 
                onValueChange = { groupInput = it; isGroupMenuExpanded = true; typeInput = "" }, // 換團體時清空類型
                label = { Text("持有團體") }, 
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth(), 
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isGroupMenuExpanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
            )
            ExposedDropdownMenu(
                expanded = isGroupMenuExpanded, 
                onDismissRequest = { isGroupMenuExpanded = false }
            ) { 
                val displayGroups = if (groupInput.isEmpty()) allGroups.take(20) else filteredGroups
                displayGroups.forEach { group -> 
                    DropdownMenuItem(
                        text = { Text(formatDisplayName(group)) }, 
                        onClick = { groupInput = group; isGroupMenuExpanded = false; typeInput = "" }
                    ) 
                } 
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 持有成員
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("持有成員", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            FlowRow(modifier = Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                selectedMembers.forEach { member -> InputChip(selected = true, onClick = { selectedMembers.remove(member) }, label = { Text(formatDisplayName(member)) }, trailingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp)) } ) }
            }
            val availableMembers = groupMembersData[groupInput] ?: emptyList()
            val filteredMembers = availableMembers.filter { it.contains(memberInput, ignoreCase = true) && it !in selectedMembers }
            ExposedDropdownMenuBox(
                expanded = isMemberMenuExpanded, 
                onExpandedChange = { isMemberMenuExpanded = it }, 
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = memberInput, 
                    onValueChange = { memberInput = it; isMemberMenuExpanded = true }, 
                    label = { Text("選擇或搜尋成員") }, 
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth(), 
                    trailingIcon = { IconButton(onClick = { if (memberInput.isNotBlank()) { if (memberInput.trim() !in selectedMembers) selectedMembers.add(memberInput.trim()); memberInput = ""; isMemberMenuExpanded = false } }) { Icon(Icons.Default.Add, null) } },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                )
                ExposedDropdownMenu(
                    expanded = isMemberMenuExpanded, 
                    onDismissRequest = { isMemberMenuExpanded = false }
                ) { 
                    val displayMembers = filteredMembers.take(20)
                    displayMembers.forEach { member -> 
                        DropdownMenuItem(
                            text = { Text(formatDisplayName(member)) }, 
                            onClick = { if (member !in selectedMembers) selectedMembers.add(member); memberInput = ""; isMemberMenuExpanded = false }
                        ) 
                    } 
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        // 持有類型 (專輯/演唱會)
        val availableTypes = KpopData.groupAlbumData[groupInput] ?: emptyList()
        val filteredTypes = availableTypes.filter { it.contains(typeInput, ignoreCase = true) }
        
        ExposedDropdownMenuBox(
            expanded = isTypeMenuExpanded,
            onExpandedChange = { isTypeMenuExpanded = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = typeInput,
                onValueChange = { typeInput = it; isTypeMenuExpanded = true },
                label = { Text("小卡類型 (專輯/演唱會/週邊)") },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth(),
                placeholder = { Text("請先選擇團體，或直接輸入") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isTypeMenuExpanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
            )
            if (availableTypes.isNotEmpty()) {
                ExposedDropdownMenu(
                    expanded = isTypeMenuExpanded,
                    onDismissRequest = { isTypeMenuExpanded = false }
                ) {
                    filteredTypes.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type) },
                            onClick = { typeInput = type; isTypeMenuExpanded = false }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        HorizontalDivider(thickness = 1.dp, color = Color.LightGray.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(32.dp))

        Text("想換的小卡 (許願池)", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
        Spacer(modifier = Modifier.height(8.dp))
        
        LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Box(
                    modifier = Modifier.size(120.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { if (!isUploadingWishlist) wishlistLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (isUploadingWishlist) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AddPhotoAlternate, null, tint = Color.Gray)
                            Text("選取參考圖", fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                }
            }
            items(wishlistImageUrls) { url ->
                Box(modifier = Modifier.size(120.dp).clip(RoundedCornerShape(12.dp))) {
                    AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    IconButton(
                        onClick = { wishlistImageUrls.remove(url) },
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = wishlist, onValueChange = { wishlist = it }, label = { Text("想換的小卡描述 (必填)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)

        // --- 結構化許願區 ---
        Spacer(modifier = Modifier.height(24.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("智慧配對標籤 (選填，大幅增加成功率)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                
                Spacer(Modifier.height(12.dp))
                
                // 許願團體
                Text("許願團體", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(modifier = Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    selectedWishGroups.forEach { group -> InputChip(selected = true, onClick = { selectedWishGroups.remove(group) }, label = { Text(formatDisplayName(group), fontSize = 11.sp) }, trailingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(12.dp)) } ) }
                }
                val filteredWishGroups = allGroups.filter { it.contains(wishGroupInput, ignoreCase = true) && it !in selectedWishGroups }
                ExposedDropdownMenuBox(
                    expanded = isWishGroupMenuExpanded, 
                    onExpandedChange = { isWishGroupMenuExpanded = it }, 
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextField(
                        value = wishGroupInput, 
                        onValueChange = { wishGroupInput = it; isWishGroupMenuExpanded = true }, 
                        placeholder = { Text("搜尋團體...", fontSize = 13.sp) }, 
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth(), 
                        colors = ExposedDropdownMenuDefaults.textFieldColors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent), 
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isWishGroupMenuExpanded) }
                    )
                    ExposedDropdownMenu(
                        expanded = isWishGroupMenuExpanded, 
                        onDismissRequest = { isWishGroupMenuExpanded = false }
                    ) { 
                        val displayWishGroups = filteredWishGroups.take(10)
                        displayWishGroups.forEach { group -> 
                            DropdownMenuItem(
                                text = { Text(formatDisplayName(group)) }, 
                                onClick = { selectedWishGroups.add(group); wishGroupInput = ""; isWishGroupMenuExpanded = false }
                            ) 
                        } 
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 許願成員
                Text("許願成員", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(modifier = Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    selectedWishMembers.forEach { member -> InputChip(selected = true, onClick = { selectedWishMembers.remove(member) }, label = { Text(formatDisplayName(member), fontSize = 11.sp) }, trailingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(12.dp)) } ) }
                }
                val wishMemberPool = if (selectedWishGroups.isNotEmpty()) { selectedWishGroups.flatMap { groupMembersData[it] ?: emptyList() } } else { groupMembersData.values.flatten() }.distinct()
                val filteredWishMembers = wishMemberPool.filter { it.contains(wishMemberInput, ignoreCase = true) && it !in selectedWishMembers }
                
                ExposedDropdownMenuBox(
                    expanded = isWishMemberMenuExpanded, 
                    onExpandedChange = { isWishMemberMenuExpanded = it }, 
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextField(
                        value = wishMemberInput, 
                        onValueChange = { wishMemberInput = it; isWishMemberMenuExpanded = true }, 
                        placeholder = { Text("搜尋成員...", fontSize = 13.sp) }, 
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth(), 
                        colors = ExposedDropdownMenuDefaults.textFieldColors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent), 
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isWishMemberMenuExpanded) }
                    )
                    ExposedDropdownMenu(
                        expanded = isWishMemberMenuExpanded, 
                        onDismissRequest = { isWishMemberMenuExpanded = false }
                    ) { 
                        val displayWishMembers = filteredWishMembers.take(10)
                        displayWishMembers.forEach { member -> 
                            DropdownMenuItem(
                                text = { Text(formatDisplayName(member)) }, 
                                onClick = { selectedWishMembers.add(member); wishMemberInput = ""; isWishMemberMenuExpanded = false }
                            ) 
                        } 
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(value = remarks, onValueChange = { remarks = it }, label = { Text("備註 / 卡況描述") }, modifier = Modifier.fillMaxWidth(), minLines = 3)

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                if (imageUrl.isEmpty() || groupInput.isEmpty() || selectedMembers.isEmpty() || wishlist.trim().isEmpty()) {
                    Toast.makeText(context, "請填寫完必填欄位！", Toast.LENGTH_SHORT).show()
                } else {
                    val cardData = hashMapOf(
                        "userId" to userId,
                        "ownerNickname" to userNickname,
                        "ownerProfileImageUrl" to userProfileImageUrl,
                        "location" to userLocation,
                        "imageUrl" to imageUrl,
                        "groupName" to groupInput,
                        "memberName" to selectedMembers.joinToString(", "), 
                        "memberList" to selectedMembers.toList(),
                        "cardType" to typeInput, // 儲存小卡類型
                        "wishlist" to wishlist,
                        "wishlistImageUrls" to wishlistImageUrls.toList(),
                        "wishGroupList" to selectedWishGroups.toList(),
                        "wishMemberList" to selectedWishMembers.toList(),
                        "remarks" to remarks,
                        "status" to (existingCard?.status ?: "available"),
                        "createdAt" to (existingCard?.createdAt ?: FieldValue.serverTimestamp())
                    )
                    
                    val task = if (existingCard == null) {
                        db.collection("cards").add(cardData)
                    } else {
                        db.collection("cards").document(existingCard.id).set(cardData)
                    }
                    
                    task.addOnSuccessListener {
                        Toast.makeText(context, if (existingCard == null) "小卡上傳成功！" else "修改成功！", Toast.LENGTH_SHORT).show()
                        if (existingCard == null) {
                            checkForSmartMatches(userId, cardData)
                        }
                        onUploadSuccess()
                    }
                }
            },
            enabled = !isUploadingMain && !isUploadingWishlist,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(if (isUploadingMain || isUploadingWishlist) "圖片上傳中..." else if (existingCard == null) "確認上傳" else "儲存修改", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(40.dp))
    }
}

/**
 * 智慧配對核心邏輯：
 * 當我上傳一張新卡片後，檢查別人的卡片是否符合我的許願標籤。
 * 如果符合，就發送通知給我（目前上傳者）。
 */
private fun checkForSmartMatches(currentUserId: String, myCardData: Map<String, Any>) {
    val db = FirebaseFirestore.getInstance()
    @Suppress("UNCHECKED_CAST")
    val myWishGroups = myCardData["wishGroupList"] as? List<String> ?: emptyList()
    @Suppress("UNCHECKED_CAST")
    val myWishMembers = myCardData["wishMemberList"] as? List<String> ?: emptyList()

    if (myWishGroups.isEmpty() && myWishMembers.isEmpty()) return

    // 搜尋別人的卡片 (非自己上傳且狀態為可交換)
    db.collection("cards")
        .whereNotEqualTo("userId", currentUserId)
        .whereEqualTo("status", "available")
        .get()
        .addOnSuccessListener { snapshot ->
            snapshot.documents.forEach { doc ->
                val otherCard = doc.toKpopCard() ?: return@forEach
                
                // 檢查別人的持有卡片是否符合「我」剛剛上傳的許願標籤
                val matchByGroup = otherCard.groupName in myWishGroups
                val matchByMember = otherCard.memberList.any { it in myWishMembers }

                if (matchByGroup || matchByMember) {
                    // 發送匹配通知給「我」
                    sendNotification(
                        userId = currentUserId,
                        type = "match",
                        title = "找到潛在匹配！",
                        content = "有人持有你許願的《${otherCard.memberName}》，點擊去看看吧！",
                        relatedId = otherCard.id,
                        relatedImage = otherCard.imageUrl
                    )
                }
            }
        }
}
