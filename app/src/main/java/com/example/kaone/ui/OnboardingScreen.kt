package com.example.kaone.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.kaone.R
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

// 自定義平滑過渡函數
fun lerp(start: Float, stop: Float, fraction: Float): Float = start + fraction * (stop - start)

data class OnboardingPage(
    val title: String,
    val description: String,
    val featureTag: String,
    val tagIcon: ImageVector,
    val imageRes: Int,
    val idolUrl1: String, // 第一張背景圖
    val idolUrl2: String, // 第二張背景圖
    val themeColor: Color
)

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val scope = rememberCoroutineScope()

    val pages = listOf(
        OnboardingPage(
            "找尋你的本命卡", "探索全台最豐富的小卡清單，\n精準搜尋，收藏你的最愛。",
            "PREMIUM COLLECT", Icons.Default.Star, R.drawable.onboarding_1,
            "https://drive.google.com/uc?export=view&id=1Qg744y7iIfTF8Bl2tVS8CmPsFSvYzlQh",
            "https://drive.google.com/uc?export=view&id=1_OOLf82hD18_HuWAncmh_MHbvlid5KEJ",
            Color(0xFF5C6BC0)
        ),
        OnboardingPage(
            "智慧配對系統", "一鍵媒合你的心願與重複小卡，\n讓換卡變得簡單又快速。",
            "AI SMART MATCH", Icons.Default.AutoAwesome, R.drawable.onboarding_2,
            "https://drive.google.com/uc?export=view&id=13h4f2GDnLZHwF-8JhX7xGjx6O2fA8S7h",
            "https://drive.google.com/uc?export=view&id=1JMTOvD3Z6HWs-hJ9558rMHZ2WpOZAi_2",
            Color(0xFFEC407A)
        ),
        OnboardingPage(
            "應援活動地圖", "即時查看全台應援與生咖資訊，\n追星行程一手掌握。",
            "LIVE EVENTS", Icons.Default.LocationOn, R.drawable.onboarding_3,
            "https://drive.google.com/uc?export=view&id=1m2DO55438_-gpdWxllCJN1ZAok_vBgie",
            "https://drive.google.com/uc?export=view&id=1SS3qNRw558s9tr3rdRG2dL_7oS39HI2j",
            Color(0xFF66BB6A)
        ),
        OnboardingPage(
            "快速上傳小卡", "拍照即辨識，輕鬆填寫欄位及備註，\n並寫下想換到的小卡。",
            "QUICK UPLOAD", Icons.Default.CloudUpload, R.drawable.onboarding_4,
            "https://drive.google.com/uc?export=view&id=1jWyzkBdsgRXSQDVo5jsYBTJHkxblsNWP",
            "https://drive.google.com/uc?export=view&id=1UZpS_Xg_XgLG_UeyJruvadNj5uxXtZCx",
            Color(0xFFFFA726)
        ),
        OnboardingPage(
            "即時聊天社群", "與同好交換心得，即時溝通換卡細節，\n安全又便利的聊天環境。",
            "SECURE CHAT", Icons.Default.Chat, R.drawable.onboarding_5,
            "https://drive.google.com/uc?export=view&id=1-eAq7etXNC21L3sXP1PBADdv9T0J7WHo",
            "https://drive.google.com/uc?export=view&id=1FzrvGbnd9bSvqi1FInfaxH71fQla0df1",
            Color(0xFF26C6DA)
        ),
        OnboardingPage(
            "個人化收藏櫃", "展示你的追星歷程，自定義個人首頁，\n讓全世界看到你的追星成果。",
            "PROFILE HUB", Icons.Default.Person, R.drawable.onboarding_6,
            "https://drive.google.com/uc?export=view&id=1IyQrs07ih8HjWgJup_eSpBDnJTRWsCF3",
            "https://drive.google.com/uc?export=view&id=1RRpJszSztb1DJS0ULGi2YadDuEZZwEI2",
            Color(0xFF7E57C2)
        )
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { position ->
            val page = pages[position]
            val pageOffset = ((pagerState.currentPage - position) + pagerState.currentPageOffsetFraction).absoluteValue

            Box(modifier = Modifier.fillMaxSize().padding(top = 80.dp), contentAlignment = Alignment.TopCenter) {

                // 2. 左上背景裝飾
                AsyncImage(
                    model = page.idolUrl1,
                    contentDescription = null,
                    modifier = Modifier
                        .offset(x = (-110).dp, y = (-20).dp)
                        .size(130.dp, 180.dp)
                        .graphicsLayer {
                            rotationZ = -12f
                            alpha = lerp(0f, 0.5f, 1f - pageOffset)
                            translationX = pageOffset * (-150f)
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .shadow(8.dp),
                    contentScale = ContentScale.Crop
                )

                // 2.5 右下背景裝飾
                AsyncImage(
                    model = page.idolUrl2,
                    contentDescription = null,
                    modifier = Modifier
                        .offset(x = 135.dp, y = 290.dp)
                        .size(110.dp, 150.dp)
                        .graphicsLayer {
                            rotationZ = 18f
                            alpha = lerp(0f, 0.45f, 1f - pageOffset)
                            translationX = pageOffset * 180f
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .shadow(6.dp),
                    contentScale = ContentScale.Crop
                )

                // 3. 手機主體展示
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.60f)
                        .aspectRatio(0.48f)
                        .graphicsLayer {
                            val scale = lerp(0.95f, 1f, 1f - pageOffset)
                            scaleX = scale
                            scaleY = scale
                            alpha = lerp(0.7f, 1f, 1f - pageOffset)
                        },
                    shape = RoundedCornerShape(32.dp),
                    color = Color.White,
                    shadowElevation = 16.dp,
                    border = BorderStroke(4.dp, Color(0xFF2D3436))
                ) {
                    Image(
                        painter = painterResource(id = page.imageRes),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }

        // 5. 底部資訊面板
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.White, Color.White),
                        startY = 0f, endY = 80f
                    )
                )
                .navigationBarsPadding()
                .padding(horizontal = 32.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val currentPage = pages[pagerState.currentPage]

            AnimatedContent(
                targetState = currentPage,
                transitionSpec = { fadeIn() + slideInVertically { it / 3 } togetherWith fadeOut() },
                label = ""
            ) { page ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = page.title,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2D3436),
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = page.description,
                        fontSize = 15.sp,
                        color = Color(0xFF636E72),
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }

            Spacer(Modifier.height(36.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    repeat(pages.size) { i ->
                        val isSelected = pagerState.currentPage == i
                        val width by animateDpAsState(if (isSelected) 18.dp else 6.dp, label = "")
                        Box(Modifier.padding(2.dp).size(height = 6.dp, width = width).clip(CircleShape).background(if (isSelected) currentPage.themeColor else Color(0xFFDFE6E9)))
                    }
                }

                Button(
                    onClick = {
                        if (pagerState.currentPage < pages.size - 1) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        } else {
                            onFinished()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D3436)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(48.dp).padding(horizontal = 4.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Text(
                        text = if (pagerState.currentPage < pages.size - 1) "下一步" else "開始體驗",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }
            }
        }

        TextButton(onClick = onFinished, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).statusBarsPadding()) {
            Text("跳過", color = Color(0xFFB2BEC3), fontWeight = FontWeight.Medium, fontSize = 14.sp)
        }
    }
}