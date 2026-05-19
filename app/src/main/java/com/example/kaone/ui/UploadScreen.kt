package com.example.kaone.ui

import android.Manifest
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.kaone.ml.IdolClassifier
import com.example.kaone.ui.theme.ImgbbUploader
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

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

    val classifier = remember { IdolClassifier(context) }
    DisposableEffect(Unit) {
        onDispose { classifier.close() }
    }

    // 輔助函式
    fun String.getCleanName(): String {
        return this.split("|").map { it.trim() }.firstOrNull { it.isNotBlank() && !it.startsWith("http") } ?: this.trim()
    }

    fun formatDisplayName(rawName: String): String {
        val parts = rawName.split("|").map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("http") }
        return parts.joinToString(" ")
    }

    var userNickname by remember { mutableStateOf("") }
    var userProfileImageUrl by remember { mutableStateOf("") }
    var userLocation by remember { mutableStateOf("") }

    LaunchedEffect(userId) {
        db.collection("users").document(userId).get().addOnSuccessListener { document ->
            userNickname = document.getString("nickname") ?: "未知用戶"
            userProfileImageUrl = document.getString("profileImageUrl") ?: ""
            userLocation = document.getString("location") ?: ""
        }
    }

    // --- 關鍵修正：加入 Key 確保編輯時狀態正確載入 ---
    var imageUrl by remember(existingCard?.id) { mutableStateOf(existingCard?.imageUrl ?: "") }
    var isUploadingMain by remember { mutableStateOf(false) }
    var showImageSourceDialog by remember { mutableStateOf(false) }
    var activeUploadUri by remember { mutableStateOf<Uri?>(null) }

    var groupInput by remember(existingCard?.id) { mutableStateOf(existingCard?.groupName?.let { formatDisplayName(it) } ?: "") }
    val selectedMembers = remember(existingCard?.id) { mutableStateListOf<String>().apply { existingCard?.memberList?.let { addAll(it) } } }
    var memberInput by remember { mutableStateOf("") }
    var isGroupMenuExpanded by remember { mutableStateOf(false) }
    var isMemberMenuExpanded by remember { mutableStateOf(false) }
    var typeInput by remember(existingCard?.id) { mutableStateOf(existingCard?.cardType ?: "") }
    var isTypeMenuExpanded by remember { mutableStateOf(false) }

    var wishlist by remember(existingCard?.id) { mutableStateOf(existingCard?.wishlist ?: "") }
    var remarks by remember(existingCard?.id) { mutableStateOf(existingCard?.remarks ?: "") }
    val wishlistImageUrls = remember(existingCard?.id) { mutableStateListOf<String>().apply { existingCard?.wishlistImageUrls?.let { addAll(it) } } }
    var isUploadingWishlist by remember { mutableStateOf(false) }

    val selectedWishGroups = remember(existingCard?.id) { mutableStateListOf<String>().apply { existingCard?.wishGroupList?.let { addAll(it) } } }
    var wishGroupInput by remember { mutableStateOf("") }
    var isWishGroupMenuExpanded by remember { mutableStateOf(false) }

    val selectedWishMembers = remember(existingCard?.id) { mutableStateListOf<String>().apply { existingCard?.wishMemberList?.let { addAll(it) } } }
    var wishMemberInput by remember { mutableStateOf("") }
    var isWishMemberMenuExpanded by remember { mutableStateOf(false) }

    val groupMembersData = KpopData.groupMembersData
    val allGroups = groupMembersData.keys.sorted()
    val currentMatchedGroupKey = remember(groupInput) { allGroups.find { it.getCleanName().equals(groupInput.trim(), ignoreCase = true) || formatDisplayName(it).equals(groupInput.trim(), ignoreCase = true) } }

    fun handleSelectedImage(uri: Uri) {
        val thisRequestUri = uri
        activeUploadUri = thisRequestUri
        isUploadingMain = true
        imageUrl = ""
        if (existingCard == null) { groupInput = ""; selectedMembers.clear(); typeInput = "" }

        scope.launch(Dispatchers.Default) {
            try {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                val bitmap = ImageDecoder.decodeBitmap(source) { decoder, _, _ -> decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE }
                val result = classifier.classify(bitmap)
                withContext(Dispatchers.Main) {
                    if (activeUploadUri == thisRequestUri && result != null) {
                        val matchedGroup = allGroups.find { it.getCleanName().equals(result.group.trim(), ignoreCase = true) } ?: result.group
                        groupInput = formatDisplayName(matchedGroup)
                        val matchedMember = (groupMembersData[matchedGroup] ?: emptyList()).find { it.getCleanName().equals(result.member.trim(), ignoreCase = true) } ?: result.member
                        selectedMembers.clear()
                        selectedMembers.add(matchedMember)
                        Toast.makeText(context, "AI 辨識成功！", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
        ImgbbUploader.uploadImage(context, uri, scope, onSuccess = { if (activeUploadUri == thisRequestUri) { imageUrl = it; isUploadingMain = false } }, onFailure = { if (activeUploadUri == thisRequestUri) isUploadingMain = false })
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let { handleSelectedImage(it) } }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { if (it) activeUploadUri?.let { handleSelectedImage(it) } else isUploadingMain = false }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) {
            val tempFile = File(context.cacheDir, "camera_${UUID.randomUUID()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
            activeUploadUri = uri
            cameraLauncher.launch(uri)
        }
    }
    
    // --- 關鍵修改：讓許願池上傳也能觸發 AI 辨識自動帶入標籤 ---
    val wishlistLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            isUploadingWishlist = true
            uris.forEach { uri ->
                // AI 辨識許願卡
                scope.launch(Dispatchers.Default) {
                    try {
                        val source = ImageDecoder.createSource(context.contentResolver, uri)
                        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, _, _ -> decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE }
                        val result = classifier.classify(bitmap)
                        withContext(Dispatchers.Main) {
                            result?.let { res ->
                                val matchedGroup = allGroups.find { it.getCleanName().equals(res.group.trim(), ignoreCase = true) } ?: res.group
                                if (matchedGroup !in selectedWishGroups) selectedWishGroups.add(matchedGroup)
                                
                                val matchedMember = (groupMembersData[matchedGroup] ?: emptyList()).find { it.getCleanName().equals(res.member.trim(), ignoreCase = true) } ?: res.member
                                if (matchedMember !in selectedWishMembers) selectedWishMembers.add(matchedMember)
                                Toast.makeText(context, "已自動帶入許願標籤！", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }
                
                // 上傳圖片
                ImgbbUploader.uploadImage(context, uri, scope, onSuccess = { wishlistImageUrls.add(it); if (wishlistImageUrls.size >= uris.size) isUploadingWishlist = false }, onFailure = { isUploadingWishlist = false })
            }
        }
    }

    val scrollState = rememberScrollState()

    if (showImageSourceDialog) {
        AlertDialog(
            onDismissRequest = { showImageSourceDialog = false },
            title = { Text("選取照片來源") },
            confirmButton = { TextButton(onClick = { showImageSourceDialog = false; permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("相機拍照") } },
            dismissButton = { TextButton(onClick = { showImageSourceDialog = false; galleryLauncher.launch("image/*") }) { Text("相簿選取") } }
        )
    }

    Column(modifier = modifier.fillMaxSize().padding(24.dp).verticalScroll(scrollState), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            Text(text = if (existingCard == null) "上傳你的小卡" else "編輯小卡資訊", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(24.dp))
        Box(modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { if (!isUploadingMain) showImageSourceDialog = true }, contentAlignment = Alignment.Center) {
            if (imageUrl.isNotEmpty()) AsyncImage(model = imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            else if (isUploadingMain) CircularProgressIndicator()
            else Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.AddAPhoto, null, modifier = Modifier.size(40.dp), tint = Color.Gray); Text("點擊選取或拍攝照片 (必填)", color = Color.Gray) }
        }

        Spacer(Modifier.height(24.dp))
        // 持有團體
        ExposedDropdownMenuBox(expanded = isGroupMenuExpanded, onExpandedChange = { isGroupMenuExpanded = it }, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(value = groupInput, onValueChange = { groupInput = it; isGroupMenuExpanded = true; typeInput = "" }, label = { Text("持有團體") }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth(), singleLine = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(isGroupMenuExpanded) })
            ExposedDropdownMenu(expanded = isGroupMenuExpanded, onDismissRequest = { isGroupMenuExpanded = false }, modifier = Modifier.heightIn(max = 300.dp)) {
                allGroups.filter { it.getCleanName().contains(groupInput, true) || formatDisplayName(it).contains(groupInput, true) }.forEach {
                    DropdownMenuItem(text = { Text(formatDisplayName(it)) }, onClick = { groupInput = formatDisplayName(it); isGroupMenuExpanded = false; typeInput = "" })
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        // 持有成員
        Column(Modifier.fillMaxWidth()) {
            Text("持有成員", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                selectedMembers.forEach { member ->
                    InputChip(selected = true, onClick = { selectedMembers.remove(member) }, label = { Text(formatDisplayName(member)) }, trailingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp)) })
                }
            }
            ExposedDropdownMenuBox(expanded = isMemberMenuExpanded, onExpandedChange = { isMemberMenuExpanded = it }, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(value = memberInput, onValueChange = { memberInput = it; isMemberMenuExpanded = true }, label = { Text("選擇或搜尋成員") }, singleLine = true, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth(), trailingIcon = { IconButton(onClick = { if (memberInput.isNotBlank()) { selectedMembers.add(memberInput); memberInput = ""; isMemberMenuExpanded = false } }) { Icon(Icons.Default.Add, null) } })
                ExposedDropdownMenu(expanded = isMemberMenuExpanded, onDismissRequest = { isMemberMenuExpanded = false }, modifier = Modifier.heightIn(max = 300.dp)) {
                    (groupMembersData[currentMatchedGroupKey] ?: emptyList()).filter { it.getCleanName().contains(memberInput, true) && it !in selectedMembers }.forEach {
                        DropdownMenuItem(text = { Text(formatDisplayName(it)) }, onClick = { selectedMembers.add(it); memberInput = ""; isMemberMenuExpanded = false })
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        // 持有類型
        val avTypes = (KpopData.groupAlbumData[currentMatchedGroupKey] ?: KpopData.groupAlbumData[currentMatchedGroupKey?.getCleanName()] ?: emptyList()).filter { it.contains(typeInput, true) }
        ExposedDropdownMenuBox(expanded = isTypeMenuExpanded, onExpandedChange = { isTypeMenuExpanded = it }, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(value = typeInput, onValueChange = { typeInput = it; isTypeMenuExpanded = true }, label = { Text("小卡類型") }, singleLine = true, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(isTypeMenuExpanded) })
            if (avTypes.isNotEmpty()) ExposedDropdownMenu(expanded = isTypeMenuExpanded, onDismissRequest = { isTypeMenuExpanded = false }, modifier = Modifier.heightIn(max = 300.dp)) {
                avTypes.forEach { DropdownMenuItem(text = { Text(it) }, onClick = { typeInput = it; isTypeMenuExpanded = false }) }
            }
        }

        Spacer(modifier = Modifier.height(32.dp)); HorizontalDivider(thickness = 1.dp, color = Color.LightGray.copy(alpha = 0.5f)); Spacer(modifier = Modifier.height(32.dp))

        Text("想換的小卡 (許願池)", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
        LazyRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Box(Modifier.size(120.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { wishlistLauncher.launch("image/*") }, Alignment.Center) { if(isUploadingWishlist) CircularProgressIndicator() else Icon(Icons.Default.AddPhotoAlternate, null, tint = Color.Gray) } }
            items(wishlistImageUrls) { url -> Box(Modifier.size(120.dp).clip(RoundedCornerShape(12.dp))) { AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop); IconButton(onClick = { wishlistImageUrls.remove(url) }, Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp).background(Color.Black.copy(0.5f), CircleShape)) { Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(16.dp)) } } }
        }
        OutlinedTextField(value = wishlist, onValueChange = { wishlist = it }, label = { Text("想換的小卡描述 (必填)") }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp), minLines = 2)

        Spacer(Modifier.height(24.dp))
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)); Text(" 智慧配對標籤 (選填)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.height(12.dp)); Text("許願團體", fontSize = 12.sp); FlowRow(Modifier.padding(vertical = 4.dp), Arrangement.spacedBy(6.dp)) { selectedWishGroups.forEach { g -> InputChip(selected = true, onClick = { selectedWishGroups.remove(g) }, label = { Text(formatDisplayName(g), fontSize = 11.sp) }, trailingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(12.dp)) }) } }
                ExposedDropdownMenuBox(expanded = isWishGroupMenuExpanded, onExpandedChange = { isWishGroupMenuExpanded = it }, Modifier.fillMaxWidth()) {
                    TextField(value = wishGroupInput, onValueChange = { wishGroupInput = it; isWishGroupMenuExpanded = true }, placeholder = { Text("搜尋團體...") }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth(), colors = ExposedDropdownMenuDefaults.textFieldColors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(isWishGroupMenuExpanded) })
                    ExposedDropdownMenu(expanded = isWishGroupMenuExpanded, onDismissRequest = { isWishGroupMenuExpanded = false }, modifier = Modifier.heightIn(max = 300.dp)) {
                        allGroups.filter { it.getCleanName().contains(wishGroupInput, true) && it !in selectedWishGroups }.forEach { g -> DropdownMenuItem(text = { Text(formatDisplayName(g)) }, onClick = { selectedWishGroups.add(g); wishGroupInput = ""; isWishGroupMenuExpanded = false }) }
                    }
                }
                Spacer(Modifier.height(12.dp)); Text("許願成員", fontSize = 12.sp); FlowRow(Modifier.padding(vertical = 4.dp), Arrangement.spacedBy(6.dp)) { selectedWishMembers.forEach { m -> InputChip(selected = true, onClick = { selectedWishMembers.remove(m) }, label = { Text(formatDisplayName(m), fontSize = 11.sp) }, trailingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(12.dp)) }) } }
                val wPool = if (selectedWishGroups.isNotEmpty()) selectedWishGroups.flatMap { groupMembersData[it] ?: emptyList() } else groupMembersData.values.flatten().distinct()
                ExposedDropdownMenuBox(expanded = isWishMemberMenuExpanded, onExpandedChange = { isWishMemberMenuExpanded = it }, Modifier.fillMaxWidth()) {
                    TextField(value = wishMemberInput, onValueChange = { wishMemberInput = it; isWishMemberMenuExpanded = true }, placeholder = { Text("搜尋成員...") }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth(), colors = ExposedDropdownMenuDefaults.textFieldColors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(isWishMemberMenuExpanded) })
                    ExposedDropdownMenu(expanded = isWishMemberMenuExpanded, onDismissRequest = { isWishMemberMenuExpanded = false }, modifier = Modifier.heightIn(max = 300.dp)) {
                        wPool.filter { it.getCleanName().contains(wishMemberInput, true) && it !in selectedWishMembers }.forEach { m -> DropdownMenuItem(text = { Text(formatDisplayName(m)) }, onClick = { selectedWishMembers.add(m); wishMemberInput = ""; isWishMemberMenuExpanded = false }) }
                    }
                }
            }
        }
        OutlinedTextField(value = remarks, onValueChange = { remarks = it }, label = { Text("備註 / 卡況描述") }, modifier = Modifier.fillMaxWidth().padding(top = 24.dp), minLines = 3)
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = {
                if (imageUrl.isEmpty() || groupInput.isEmpty() || selectedMembers.isEmpty() || wishlist.trim().isEmpty()) { Toast.makeText(context, "請填完必填欄位！", Toast.LENGTH_SHORT).show() }
                else {
                    val data = hashMapOf("userId" to userId, "ownerNickname" to userNickname, "ownerProfileImageUrl" to userProfileImageUrl, "location" to userLocation, "imageUrl" to imageUrl, "groupName" to (currentMatchedGroupKey ?: groupInput), "memberName" to selectedMembers.joinToString(", "), "memberList" to selectedMembers.toList(), "cardType" to typeInput, "wishlist" to wishlist, "wishlistImageUrls" to wishlistImageUrls.toList(), "wishGroupList" to selectedWishGroups.toList(), "wishMemberList" to selectedWishMembers.toList(), "remarks" to remarks, "status" to (existingCard?.status ?: "available"), "createdAt" to (existingCard?.createdAt ?: FieldValue.serverTimestamp()))
                    val task = if (existingCard == null) db.collection("cards").add(data) else db.collection("cards").document(existingCard.id).set(data)
                    task.addOnSuccessListener { Toast.makeText(context, "上傳成功！", Toast.LENGTH_SHORT).show(); onUploadSuccess() }
                }
            },
            enabled = !isUploadingMain && !isUploadingWishlist,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp)
        ) { Text(if (isUploadingMain || isUploadingWishlist) "圖片上傳中..." else if (existingCard == null) "確認上傳" else "儲存修改", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(40.dp))
    }
}
