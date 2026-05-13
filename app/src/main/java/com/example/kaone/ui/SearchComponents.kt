package com.example.kaone.ui

import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.kaone.ml.IdolClassifier

@Composable
fun SearchCategoryHeader(title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.height(1.dp).weight(1f).background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)))
    }
}

/**
 * 通用的 Kpop 搜尋框 (支援文字搜尋與照片辨識)
 * 優化視覺效果：增加陰影、高度與互動回饋
 */
@Composable
fun KpopSearchField(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = "搜尋小卡、團體、成員...",
    modifier: Modifier = Modifier,
    contentColor: Color = Color.Gray
) {
    val context = LocalContext.current
    val classifier = remember { IdolClassifier(context) }
    
    // 組件卸載時關閉模型，節省記憶體
    DisposableEffect(Unit) {
        onDispose { classifier.close() }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            try {
                val bitmap = if (Build.VERSION.SDK_INT < 28) {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                } else {
                    val source = ImageDecoder.createSource(context.contentResolver, it)
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ -> decoder.isMutableRequired = true }
                }
                val result = classifier.classify(bitmap)
                if (result != null) {
                    onQueryChange(result.member)
                    Toast.makeText(context, "辨識成功：${result.group} ${result.member}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "無法辨識照片中的偶像", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "圖片處理失敗", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Surface(
        modifier = modifier
            .height(48.dp) // 增加高度提升點擊感
            .fillMaxWidth(),
        shape = RoundedCornerShape(24.dp), // 全圓角設計
        color = Color.White,
        shadowElevation = 3.dp, // 加入陰影讓層次更鮮明
        tonalElevation = 2.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = contentColor.copy(alpha = 0.6f)
            )
            
            Spacer(Modifier.width(12.dp))
            
            BasicTextField(
                value = searchQuery,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 15.sp,
                    color = Color(0xFF2E3440),
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = placeholder,
                                fontSize = 15.sp,
                                color = contentColor.copy(alpha = 0.5f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                }
            )
            
            if (searchQuery.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "清除",
                        modifier = Modifier.size(18.dp),
                        tint = contentColor
                    )
                }
            } else {
                IconButton(
                    onClick = { photoPickerLauncher.launch("image/*") },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = "相機搜尋",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
fun SearchListView(
    results: List<EncyclopediaSearchResult>,
    onResultClick: (EncyclopediaSearchResult) -> Unit
) {
    val groups = results.filterIsInstance<EncyclopediaSearchResult.Group>()
    val members = results.filterIsInstance<EncyclopediaSearchResult.Member>()
    val albums = results.filterIsInstance<EncyclopediaSearchResult.Album>()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (groups.isNotEmpty()) {
            item { SearchCategoryHeader("團體") }
            items(groups) { result -> SearchResultItem(result, onResultClick) }
        }
        
        if (members.isNotEmpty()) {
            item { SearchCategoryHeader("成員") }
            items(members) { result -> SearchResultItem(result, onResultClick) }
        }
        
        if (albums.isNotEmpty()) {
            item { SearchCategoryHeader("專輯") }
            items(albums) { result -> SearchResultItem(result, onResultClick) }
        }
        
        if (results.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(top = 100.dp), contentAlignment = Alignment.Center) {
                    Text("找不到匹配的結果", color = Color.Gray, fontSize = 14.sp)
                }
            }
        }
        
        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
fun SearchResultItem(
    result: EncyclopediaSearchResult,
    onResultClick: (EncyclopediaSearchResult) -> Unit
) {
    val title: String
    val subtitle: String
    val imageUrl: String?
    val icon: androidx.compose.ui.graphics.vector.ImageVector

    when(result) {
        is EncyclopediaSearchResult.Group -> {
            title = result.groupKey.getCleanName()
            subtitle = "團體"
            imageUrl = result.groupKey.split("|").find { it.startsWith("http") }
            icon = Icons.Default.Groups
        }
        is EncyclopediaSearchResult.Member -> {
            title = result.memberKey.getCleanName()
            subtitle = "成員 (${result.groupKey.getCleanName()})"
            imageUrl = result.memberKey.split("|").find { it.startsWith("http") }
            icon = Icons.Default.Person
        }
        is EncyclopediaSearchResult.Album -> {
            title = result.albumName
            subtitle = "專輯 (${result.groupKey.getCleanName()})"
            imageUrl = null
            icon = Icons.Default.Album
        }
    }

    Surface(
        onClick = { onResultClick(result) },
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    color = Color(0xFFF0F2F5)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = Color.LightGray, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF2E3440), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, fontSize = 12.sp, color = Color.Gray)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.LightGray.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
        }
    }
}
