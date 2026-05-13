package com.example.kaone.ui

import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.kaone.ml.IdolClassifier
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EncyclopediaScreen(
    initialGroup: String? = null,
    initialMember: String? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedGroup by rememberSaveable { mutableStateOf<String?>(initialGroup) }
    var selectedMember by rememberSaveable { mutableStateOf<String?>(initialMember) }

    // 搜尋狀態
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // --- AI 辨識功能實作 ---
    val classifier = remember { IdolClassifier(context) }
    val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val bitmap = if (Build.VERSION.SDK_INT < 28) {
                MediaStore.Images.Media.getBitmap(context.contentResolver, it)
            } else {
                val source = ImageDecoder.createSource(context.contentResolver, it)
                ImageDecoder.decodeBitmap(source) { decoder, _, _, -> decoder.isMutableRequired = true }
            }
            val result = classifier.classify(bitmap)
            if (result != null) {
                searchQuery = result.member
                Toast.makeText(context, "辨識成功：${result.group} ${result.member}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "無法辨識照片中的偶像", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 提升滾動狀態以保持位置
    val groupGridState = rememberLazyGridState()
    val memberGridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    // 搜尋過濾邏輯：直接在此實作以避免改動 KpopData.kt
    val searchResults = remember(searchQuery) {
        if (searchQuery.isBlank()) return@remember emptyList<EncyclopediaSearchResult>()
        
        val query = searchQuery.trim().lowercase()
        val results = mutableListOf<EncyclopediaSearchResult>()

        // 1. 搜尋團體
        KpopData.groupMembersData.keys.forEach { groupKey ->
            if (groupKey.lowercase().contains(query)) {
                results.add(EncyclopediaSearchResult.Group(groupKey))
            }
        }

        // 2. 搜尋成員
        KpopData.groupMembersData.forEach { (groupKey, members) ->
            members.forEach { memberKey ->
                if (memberKey.lowercase().contains(query)) {
                    results.add(EncyclopediaSearchResult.Member(groupKey, memberKey))
                }
            }
        }

        // 3. 搜尋專輯
        KpopData.groupAlbumData.forEach { (groupPureName, albums) ->
            val groupKey = KpopData.groupMembersData.keys.find { it.getCleanName() == groupPureName } ?: groupPureName
            albums.forEach { albumName ->
                if (albumName.lowercase().contains(query)) {
                    results.add(EncyclopediaSearchResult.Album(groupKey, albumName))
                }
            }
        }
        
        results.distinctBy { 
            when(it) {
                is EncyclopediaSearchResult.Group -> "group_${it.groupKey}"
                is EncyclopediaSearchResult.Member -> "member_${it.groupKey}_${it.memberKey}"
                is EncyclopediaSearchResult.Album -> "album_${it.groupKey}_${it.albumName}"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("搜尋團體、成員、專輯...", color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp) },
                            singleLine = true,
                            trailingIcon = {
                                if (searchQuery.isEmpty()) {
                                    IconButton(onClick = { photoPickerLauncher.launch("image/*") }) {
                                        Icon(Icons.Default.PhotoCamera, null, tint = Color.White.copy(alpha = 0.7f))
                                    }
                                } else {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, null, tint = Color.White.copy(alpha = 0.7f))
                                    }
                                }
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = Color.White,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                        )
                        LaunchedEffect(Unit) {
                            focusRequester.requestFocus()
                        }
                    } else {
                        val currentMember = selectedMember
                        val currentGroup = selectedGroup
                        val displayTitle = when {
                            currentMember != null -> currentMember.getCleanName()
                            currentGroup != null -> currentGroup.getCleanName()
                            else -> "探索圖鑑百科"
                        }
                        Text(
                            text = displayTitle,
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSearchActive) {
                            isSearchActive = false
                            searchQuery = ""
                        } else if (selectedMember != null) {
                            selectedMember = null
                        } else if (selectedGroup != null) {
                            selectedGroup = null
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        isSearchActive = !isSearchActive
                        if (!isSearchActive) searchQuery = ""
                    }) {
                        Icon(if (isSearchActive) Icons.Default.Close else Icons.Default.Search, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize().background(Color(0xFFECEFF4))) {
            if (isSearchActive) {
                if (searchQuery.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Search, null, modifier = Modifier.size(64.dp), tint = Color.LightGray.copy(alpha = 0.5f))
                            Spacer(Modifier.height(16.dp))
                            Text("想要探索哪個偶像 or 專輯？", color = Color.Gray, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                } else {
                    SearchListView(
                        results = searchResults,
                        onResultClick = { result ->
                            when(result) {
                                is EncyclopediaSearchResult.Group -> {
                                    selectedGroup = result.groupKey
                                    selectedMember = null
                                }
                                is EncyclopediaSearchResult.Member -> {
                                    selectedGroup = result.groupKey
                                    selectedMember = result.memberKey
                                }
                                is EncyclopediaSearchResult.Album -> {
                                    selectedGroup = result.groupKey
                                    selectedMember = null
                                }
                            }
                            isSearchActive = false
                            searchQuery = ""
                        }
                    )
                }
            } else {
                when {
                    selectedGroup == null -> {
                        GroupGrid(
                            state = groupGridState,
                            onGroupSelected = { group ->
                                if (selectedGroup != group) {
                                    scope.launch { memberGridState.scrollToItem(0) }
                                }
                                selectedGroup = group
                            }
                        )
                    }
                    selectedMember == null -> {
                        val currentGroup = selectedGroup
                        if (currentGroup != null) {
                            MemberGrid(
                                state = memberGridState,
                                groupName = currentGroup,
                                onMemberSelected = { selectedMember = it }
                            )
                        }
                    }
                    else -> {
                        val currentGroup = selectedGroup
                        val currentMember = selectedMember
                        if (currentGroup != null && currentMember != null) {
                            MemberChecklist(
                                groupName = currentGroup,
                                memberName = currentMember
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GroupGrid(state: LazyGridState, onGroupSelected: (String) -> Unit) {
    val groupedGroups = remember {
        KpopData.groupMembersData.keys
            .groupBy {
                val firstChar = it.first().uppercaseChar()
                if (firstChar.isLetter()) firstChar.toString() else "#"
            }
            .mapValues { it.value.sortedWith(String.CASE_INSENSITIVE_ORDER) }
            .toSortedMap()
    }
    
    LazyVerticalGrid(
        state = state,
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        groupedGroups.forEach { (letter, groupsInLetter) ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 20.dp, bottom = 12.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondary,
                        shape = CircleShape,
                        modifier = Modifier.size(36.dp),
                        shadowElevation = 4.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = letter, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Box(modifier = Modifier.height(3.dp).weight(1f).background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f), Color.Transparent))))
                }
            }
            items(groupsInLetter) { group ->
                val groupName = group.getCleanName()
                val groupImageUrl = group.split("|").find { it.startsWith("http") }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().clickable { onGroupSelected(group) }) {
                    Surface(modifier = Modifier.size(90.dp), shape = CircleShape, color = Color.White, shadowElevation = 8.dp, border = BorderStroke(3.dp, MaterialTheme.colorScheme.primaryContainer)) {
                        Box(contentAlignment = Alignment.Center) {
                            if (groupImageUrl != null) {
                                AsyncImage(model = groupImageUrl, contentDescription = groupName, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                            } else {
                                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape, modifier = Modifier.size(70.dp)) {
                                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Groups, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(45.dp)) }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(text = groupName, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, maxLines = 1, color = Color(0xFF2E3440))
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { Spacer(modifier = Modifier.height(40.dp)) }
    }
}

@Composable
fun MemberGrid(state: LazyGridState, groupName: String, onMemberSelected: (String) -> Unit) {
    val members = remember(groupName) { KpopData.groupMembersData[groupName] ?: emptyList() }
    LazyVerticalGrid(
        state = state,
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        items(members) { member ->
            val name = member.getCleanName()
            val imageUrl = member.split("|").find { it.startsWith("http") }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { onMemberSelected(member) }) {
                Surface(modifier = Modifier.size(90.dp), shape = CircleShape, color = Color.White, shadowElevation = 8.dp, border = BorderStroke(3.dp, MaterialTheme.colorScheme.primaryContainer)) {
                    Box(contentAlignment = Alignment.Center) {
                        if (imageUrl != null) {
                            AsyncImage(model = imageUrl, contentDescription = name, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                        } else {
                            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape, modifier = Modifier.size(70.dp)) {
                                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(45.dp)) }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(text = name, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, maxLines = 1, color = Color(0xFF2E3440))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemberChecklist(groupName: String, memberName: String) {
    val pureGroupName = groupName.getCleanName()
    val albums = remember(pureGroupName) {
        KpopData.groupAlbumData[pureGroupName]
            ?: KpopData.groupAlbumData.entries.find { it.key.getCleanName() == pureGroupName }?.value
            ?: emptyList()
    }
    val scrollState = rememberScrollState()
    
    // 清洗成員名稱
    val names = memberName.split("|").filter { it.isNotBlank() && !it.startsWith("http") }
    val imageUrl = memberName.split("|").find { it.startsWith("http") }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(16.dp)) {
        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primary, shadowElevation = 8.dp) {
            Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(100.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    shadowElevation = 4.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (imageUrl != null) {
                            AsyncImage(model = imageUrl, contentDescription = names.getOrNull(0) ?: "", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            Icon(Icons.Default.Stars, null, modifier = Modifier.size(70.dp), tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                        }
                    }
                }
                Spacer(Modifier.width(20.dp))
                Column {
                    Text(text = (names.getOrNull(0) ?: "").uppercase(), fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 1.sp)
                    if(names.size > 1) { Text(text = names[1], fontSize = 18.sp, color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Bold) }
                    Surface(color = Color.White.copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                        Text(text = pureGroupName, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusIndicator("HAVE", Color(0xFF81C784), textColor = Color.White)
                        StatusIndicator("WANT", Color(0xFFFFB74D), textColor = Color.White)
                        StatusIndicator("OTW", Color(0xFF64B5F6), textColor = Color.White)
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        val albumList = albums.filter { !it.contains("日") && !it.contains("美") }
        val specialList = albums.filter { it.contains("日") || it.contains("美") || it.contains("單曲") }
        if (albumList.isNotEmpty()) { ChecklistSection(title = "CORE ALBUM COLLECTION", items = albumList, accentColor = Color(0xFFC62828)) }
        Spacer(Modifier.height(32.dp))
        if (specialList.isNotEmpty()) { ChecklistSection(title = "SPECIAL / LIMITED EDITION", items = specialList, accentColor = Color(0xFF283593)) }
        Spacer(Modifier.height(60.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChecklistSection(title: String, items: List<String>, accentColor: Color) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(modifier = Modifier.fillMaxWidth(), color = accentColor, shape = RoundedCornerShape(8.dp), shadowElevation = 4.dp) {
            Text(text = title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 12.dp), letterSpacing = 2.sp)
        }
        Spacer(Modifier.height(20.dp))
        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(24.dp), maxItemsInEachRow = 4) {
            items.forEach { albumName ->
                Column(modifier = Modifier.width(78.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.65f)
                        .shadow(elevation = 8.dp, shape = RoundedCornerShape(10.dp))
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White)
                        .border(1.dp, Color.Black.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                    ) {
                        Box(modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(20.dp).clip(CircleShape).border(2.dp, accentColor.copy(alpha = 0.3f), CircleShape).background(Color.White.copy(alpha = 0.9f)))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(text = albumName, fontSize = 11.sp, lineHeight = 14.sp, textAlign = TextAlign.Center, maxLines = 2, fontWeight = FontWeight.Bold, color = Color(0xFF3B4252))
                }
            }
        }
    }
}

@Composable
fun StatusIndicator(label: String, color: Color, textColor: Color = Color.DarkGray) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(color).border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape))
        Spacer(Modifier.width(6.6.dp))
        Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Black, color = textColor)
    }
}
