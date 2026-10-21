package com.example.kaone.ui

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.kaone.ml.IdolClassifier
import com.example.kaone.ui.theme.ImgbbUploader
import com.google.android.gms.location.LocationServices
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

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
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

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
    var isVerified by remember { mutableStateOf(false) }

    var currentLatitude by remember { mutableStateOf<Double?>(existingCard?.latitude) }
    var currentLongitude by remember { mutableStateOf<Double?>(existingCard?.longitude) }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            fusedLocationClient.getCurrentLocation(
                com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                null
            ).addOnSuccessListener { location ->
                if (location != null) {
                    currentLatitude = location.latitude
                    currentLongitude = location.longitude
                    Toast.makeText(context, "成功獲取 GPS 位置！", Toast.LENGTH_SHORT).show()
                } else {
                    fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                        if (lastLoc != null) {
                            currentLatitude = lastLoc.latitude
                            currentLongitude = lastLoc.longitude
                            Toast.makeText(context, "已獲取最後已知位置", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "抓不到位置，請確認手機 GPS 已開啟且非室內死角", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        } else {
            Toast.makeText(context, "需要授權定位權限才能在地圖上顯示小卡", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(userId) {
        db.collection("users").document(userId).get().addOnSuccessListener { document ->
            userNickname = document.getString("nickname") ?: "未知用戶"
            userProfileImageUrl = document.getString("profileImageUrl") ?: ""
            userLocation = document.getString("location") ?: ""
            isVerified = document.getBoolean("isVerified") ?: false
        }

        if (existingCard == null) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.getCurrentLocation(
                    com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                    null
                ).addOnSuccessListener { location ->
                    if (location != null) {
                        currentLatitude = location.latitude
                        currentLongitude = location.longitude
                    }
                }
            }
        }
    }

    var imageUrl by remember(existingCard?.id) { mutableStateOf(existingCard?.imageUrl ?: "") }
    var isUploadingMain by remember { mutableStateOf(false) }
    var showImageSourceDialog by remember { mutableStateOf(false) }
    var isTargetingWishlist by remember { mutableStateOf(false) }
    var showCustomCamera by remember { mutableStateOf(false) }

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

    fun getBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ -> decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
        } catch (e: Exception) { e.printStackTrace(); null }
    }

    fun handleSelectedImage(uri: Uri, isWishlist: Boolean) {
        if (isWishlist) isUploadingWishlist = true else { isUploadingMain = true; imageUrl = "" }

        scope.launch(Dispatchers.Default) {
            val bitmap = getBitmapFromUri(uri)
            if (bitmap != null) {
                val result = classifier.classify(bitmap)
                withContext(Dispatchers.Main) {
                    result?.let { res ->
                        val matchedGroupKey = allGroups.find { it.getCleanName().equals(res.group.trim(), ignoreCase = true) } ?: res.group
                        val memberList = groupMembersData[matchedGroupKey] ?: emptyList()
                        val matchedMemberKey = memberList.find { dbMember ->
                            val cleanDbMember = dbMember.replace("|", " ").lowercase()
                            val cleanAiMember = res.member.lowercase()
                            cleanDbMember.contains(cleanAiMember) || cleanAiMember.contains(dbMember.getCleanName().lowercase())
                        } ?: res.member

                        if (isWishlist) {
                            if (matchedGroupKey !in selectedWishGroups) selectedWishGroups.add(matchedGroupKey)
                            if (matchedMemberKey !in selectedWishMembers) selectedWishMembers.add(matchedMemberKey)
                        } else {
                            groupInput = formatDisplayName(matchedGroupKey)
                            selectedMembers.clear()
                            selectedMembers.add(matchedMemberKey)
                            Toast.makeText(context, "✅辨識成功", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            ImgbbUploader.uploadImage(context, uri, scope, onSuccess = { url ->
                if (isWishlist) {
                    wishlistImageUrls.add(url)
                    isUploadingWishlist = false
                } else {
                    imageUrl = url
                    isUploadingMain = false
                }
            }, onFailure = {
                if (isWishlist) isUploadingWishlist = false else isUploadingMain = false
                Toast.makeText(context, "圖片上傳失敗，請檢查網路", Toast.LENGTH_SHORT).show()
            })
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let { handleSelectedImage(it, false) } }
    val wishlistLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris -> uris.forEach { handleSelectedImage(it, true) } }

    val scrollState = rememberScrollState()

    if (showImageSourceDialog) {
        AlertDialog(
            onDismissRequest = { showImageSourceDialog = false },
            title = { Text("選取照片來源") },
            confirmButton = {
                TextButton(onClick = {
                    showImageSourceDialog = false
                    showCustomCamera = true
                }) { Text("相機拍照") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImageSourceDialog = false
                    if (isTargetingWishlist) wishlistLauncher.launch("image/*") else galleryLauncher.launch("image/*")
                }) { Text("相簿選取") }
            }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(scrollState), horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                Text(text = if (existingCard == null) "上傳你的小卡" else "編輯小卡資訊", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(24.dp))
            Box(
                modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable {
                    if (!isUploadingMain) { isTargetingWishlist = false; showImageSourceDialog = true }
                }, contentAlignment = Alignment.Center
            ) {
                if (imageUrl.isNotEmpty()) AsyncImage(model = imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                else if (isUploadingMain) CircularProgressIndicator()
                else Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.AddAPhoto, null, modifier = Modifier.size(40.dp), tint = Color.Gray); Text("點擊選取或拍攝照片 (必填)", color = Color.Gray) }
            }

            Spacer(Modifier.height(24.dp))

            // 地理位置
            if (currentLatitude == null) {
                OutlinedButton(onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        fusedLocationClient.getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, null).addOnSuccessListener { loc ->
                            if (loc != null) { currentLatitude = loc.latitude; currentLongitude = loc.longitude }
                        }
                    } else requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.LocationOn, null); Spacer(Modifier.width(8.dp)); Text("點擊獲取 GPS 位置 (地圖顯示必備)")
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                    Icon(Icons.Default.GpsFixed, null, tint = Color.Green, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp)); Text("位置已獲取", fontSize = 12.sp, color = Color.Gray); Spacer(Modifier.weight(1f))
                    TextButton(onClick = { requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }) { Text("重新獲取", fontSize = 12.sp) }
                }
            }

            Spacer(Modifier.height(16.dp))
            // 團體
            ExposedDropdownMenuBox(expanded = isGroupMenuExpanded, onExpandedChange = { isGroupMenuExpanded = it }, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(value = groupInput, onValueChange = { groupInput = it; isGroupMenuExpanded = true; typeInput = "" }, label = { Text("持有團體") }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth(), singleLine = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(isGroupMenuExpanded) })
                ExposedDropdownMenu(expanded = isGroupMenuExpanded, onDismissRequest = { isGroupMenuExpanded = false }, modifier = Modifier.heightIn(max = 300.dp)) {
                    allGroups.filter { it.getCleanName().contains(groupInput, true) || formatDisplayName(it).contains(groupInput, true) }.forEach {
                        DropdownMenuItem(text = { Text(formatDisplayName(it)) }, onClick = { groupInput = formatDisplayName(it); isGroupMenuExpanded = false; typeInput = "" })
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            // 成員
            Column(Modifier.fillMaxWidth()) {
                Text("持有成員", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    selectedMembers.forEach { member -> InputChip(selected = true, onClick = { selectedMembers.remove(member) }, label = { Text(formatDisplayName(member)) }, trailingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp)) }) }
                }
                ExposedDropdownMenuBox(expanded = isMemberMenuExpanded, onExpandedChange = { isMemberMenuExpanded = it }, modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = memberInput, onValueChange = { memberInput = it; isMemberMenuExpanded = true }, label = { Text("選擇或搜尋成員") }, singleLine = true, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth(), trailingIcon = {
                        IconButton(onClick = { if (memberInput.isNotBlank()) { selectedMembers.add(memberInput); memberInput = ""; isMemberMenuExpanded = false } }) { Icon(Icons.Default.Add, null) }
                    })
                    ExposedDropdownMenu(expanded = isMemberMenuExpanded, onDismissRequest = { isMemberMenuExpanded = false }, modifier = Modifier.heightIn(max = 300.dp)) {
                        (groupMembersData[currentMatchedGroupKey] ?: emptyList()).filter { it.getCleanName().contains(memberInput, true) && it !in selectedMembers }.forEach { DropdownMenuItem(text = { Text(formatDisplayName(it)) }, onClick = { selectedMembers.add(it); memberInput = ""; isMemberMenuExpanded = false }) }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            // 專輯 / 小卡類型
            val avTypes = (KpopData.groupAlbumData[currentMatchedGroupKey]
                ?: KpopData.groupAlbumData[currentMatchedGroupKey?.getCleanName()]
                ?: emptyList()).filter { it.contains(typeInput, true) }
            ExposedDropdownMenuBox(
                expanded = isTypeMenuExpanded,
                onExpandedChange = { isTypeMenuExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = typeInput,
                    onValueChange = { typeInput = it; isTypeMenuExpanded = true },
                    label = { Text("小卡類型 / 專輯") },
                    singleLine = true,
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryEditable)
                        .fillMaxWidth(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(isTypeMenuExpanded) }
                )
                if (avTypes.isNotEmpty()) ExposedDropdownMenu(
                    expanded = isTypeMenuExpanded,
                    onDismissRequest = { isTypeMenuExpanded = false },
                    modifier = Modifier.heightIn(max = 300.dp)
                ) {
                    avTypes.forEach {
                        DropdownMenuItem(
                            text = { Text(it) },
                            onClick = { typeInput = it; isTypeMenuExpanded = false }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp)); HorizontalDivider(thickness = 1.dp, color = Color.LightGray.copy(alpha = 0.5f)); Spacer(modifier = Modifier.height(32.dp))

            Text("想換的小卡 (許願池)", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
            LazyRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Box(Modifier.size(120.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { isTargetingWishlist = true; showImageSourceDialog = true }, Alignment.Center) { if (isUploadingWishlist) CircularProgressIndicator() else Icon(Icons.Default.AddPhotoAlternate, null, tint = Color.Gray) } }
                items(wishlistImageUrls) { url -> Box(Modifier.size(120.dp).clip(RoundedCornerShape(12.dp))) { AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop); IconButton(onClick = { wishlistImageUrls.remove(url) }, Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp).background(Color.Black.copy(0.5f), CircleShape)) { Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(16.dp)) } } }
            }
            OutlinedTextField(value = wishlist, onValueChange = { wishlist = it }, label = { Text("想換的小卡描述 (必填)") }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp), minLines = 2)

            Spacer(Modifier.height(24.dp))

            // 智慧配對標籤 (藍色底框)
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            " 智慧配對標籤 (選填)",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // ---- 許願團體 ----
                    Spacer(Modifier.height(12.dp))
                    Text("許願團體", fontSize = 12.sp)
                    FlowRow(
                        Modifier.padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        selectedWishGroups.forEach { g ->
                            InputChip(
                                selected = true,
                                onClick = { selectedWishGroups.remove(g) },
                                label = { Text(formatDisplayName(g), fontSize = 11.sp) },
                                trailingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(12.dp)) }
                            )
                        }
                    }
                    ExposedDropdownMenuBox(
                        expanded = isWishGroupMenuExpanded,
                        onExpandedChange = { isWishGroupMenuExpanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextField(
                            value = wishGroupInput,
                            onValueChange = { wishGroupInput = it; isWishGroupMenuExpanded = true },
                            placeholder = { Text("搜尋團體...") },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryEditable)
                                .fillMaxWidth(),
                            colors = ExposedDropdownMenuDefaults.textFieldColors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            ),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(isWishGroupMenuExpanded) }
                        )
                        ExposedDropdownMenu(
                            expanded = isWishGroupMenuExpanded,
                            onDismissRequest = { isWishGroupMenuExpanded = false },
                            modifier = Modifier.heightIn(max = 300.dp)
                        ) {
                            allGroups.filter {
                                it.getCleanName().contains(wishGroupInput, true) && it !in selectedWishGroups
                            }.forEach { g ->
                                DropdownMenuItem(
                                    text = { Text(formatDisplayName(g)) },
                                    onClick = {
                                        selectedWishGroups.add(g)
                                        wishGroupInput = ""
                                        isWishGroupMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // ---- 許願成員 ----
                    Spacer(Modifier.height(12.dp))
                    Text("許願成員", fontSize = 12.sp)
                    FlowRow(
                        Modifier.padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        selectedWishMembers.forEach { m ->
                            InputChip(
                                selected = true,
                                onClick = { selectedWishMembers.remove(m) },
                                label = { Text(formatDisplayName(m), fontSize = 11.sp) },
                                trailingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(12.dp)) }
                            )
                        }
                    }
                    val wPool = if (selectedWishGroups.isNotEmpty()) {
                        selectedWishGroups.flatMap { groupMembersData[it] ?: emptyList() }
                    } else {
                        groupMembersData.values.flatten().distinct()
                    }
                    ExposedDropdownMenuBox(
                        expanded = isWishMemberMenuExpanded,
                        onExpandedChange = { isWishMemberMenuExpanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextField(
                            value = wishMemberInput,
                            onValueChange = { wishMemberInput = it; isWishMemberMenuExpanded = true },
                            placeholder = { Text("搜尋成員...") },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryEditable)
                                .fillMaxWidth(),
                            colors = ExposedDropdownMenuDefaults.textFieldColors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            ),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(isWishMemberMenuExpanded) }
                        )
                        ExposedDropdownMenu(
                            expanded = isWishMemberMenuExpanded,
                            onDismissRequest = { isWishMemberMenuExpanded = false },
                            modifier = Modifier.heightIn(max = 300.dp)
                        ) {
                            wPool.filter {
                                it.getCleanName().contains(wishMemberInput, true) && it !in selectedWishMembers
                            }.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(formatDisplayName(m)) },
                                    onClick = {
                                        selectedWishMembers.add(m)
                                        wishMemberInput = ""
                                        isWishMemberMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            OutlinedTextField(value = remarks, onValueChange = { remarks = it }, label = { Text("備註 / 卡況描述") }, modifier = Modifier.fillMaxWidth().padding(top = 24.dp), minLines = 3)

            Spacer(Modifier.height(32.dp))
            Button(
                onClick = {
                    // 1. 違禁詞檢查
                    val fieldCheck = ProfanityFilter.checkFields(mapOf("想換的小卡描述" to wishlist, "備註" to remarks))
                    if (fieldCheck != null) {
                        Toast.makeText(context, "「$fieldCheck」包含違禁詞，請修正", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    // 2. 釣魚網址檢查
                    val suspiciousUrlField = mapOf("想換的小卡描述" to wishlist, "備註" to remarks)
                        .entries.find { UrlSafetyFilter.extractUrls(it.value).any { url -> UrlSafetyFilter.isSuspicious(url) } }

                    if (suspiciousUrlField != null) {
                        Toast.makeText(context, "「${suspiciousUrlField.key}」包含可疑連結，請移除以保護交易安全", Toast.LENGTH_LONG).show()
                        return@Button
                    }

                    if (!isVerified) {
                        Toast.makeText(context, "請先完成 AI 實名認證再發布作品！", Toast.LENGTH_LONG).show()
                        return@Button
                    }
                    if (imageUrl.isEmpty() || groupInput.isEmpty() || selectedMembers.isEmpty() || wishlist.trim().isEmpty()) {
                        Toast.makeText(context, "請填完必填欄位！", Toast.LENGTH_SHORT).show()
                    } else {
                        val data = hashMapOf(
                            "userId" to userId, "ownerNickname" to userNickname, "ownerProfileImageUrl" to userProfileImageUrl,
                            "location" to userLocation, "latitude" to currentLatitude, "longitude" to currentLongitude,
                            "imageUrl" to imageUrl, "groupName" to (currentMatchedGroupKey ?: groupInput),
                            "memberName" to selectedMembers.joinToString(", "), "memberList" to selectedMembers.toList(),
                            "cardType" to typeInput, "wishlist" to wishlist, "wishlistImageUrls" to wishlistImageUrls.toList(),
                            "wishGroupList" to selectedWishGroups.toList(), "wishMemberList" to selectedWishMembers.toList(),
                            "remarks" to remarks, "status" to (existingCard?.status ?: "available"),
                            "createdAt" to (existingCard?.createdAt ?: FieldValue.serverTimestamp())
                        )
                        val task = if (existingCard == null) db.collection("cards").add(data) else db.collection("cards").document(existingCard.id).set(data)
                        task.addOnSuccessListener { Toast.makeText(context, "上傳成功！", Toast.LENGTH_SHORT).show(); onUploadSuccess() }
                    }
                },
                enabled = !isUploadingMain && !isUploadingWishlist,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (isUploadingMain || isUploadingWishlist) "圖片上傳中..." else if (existingCard == null) "確認上傳" else "儲存修改", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(40.dp))
        }

        if (showCustomCamera) {
            Dialog(onDismissRequest = { showCustomCamera = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
                PhotoCardCameraScreen(onImageCaptured = { uri -> showCustomCamera = false; handleSelectedImage(uri, isTargetingWishlist) }, onDismiss = { showCustomCamera = false })
            }
        }
    }
}