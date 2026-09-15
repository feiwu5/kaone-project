package com.example.kaone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class VerificationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                VerificationScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerificationScreen(onBack: () -> Unit) {

    val questions = listOf(
        "卡面印刷" to "圖案清楚嗎？有沒有模糊、重影或明顯的網點？",
        "對光觀察" to "轉動卡片看看，表面有沒有奇怪的刮痕或凹痕？",
        "邊緣切割" to "看看四個角，邊緣有沒有不平整或明顯毛邊？",
        "顏色飽和" to "和官方圖片比較，顏色有沒有明顯太深或太淺？",
        "材質紋理" to "對光看看表面的紋理和光澤，摸起來的厚度正常嗎？"
    )

    var currentIndex by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var showResult by remember { mutableStateOf(false) }
    var selectedOption by remember { mutableStateOf<Boolean?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "小卡真偽檢查",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        },
        containerColor = Color(0xFFF8F8F8)
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {

            if (!showResult) {

                // 題數
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "小卡檢查",
                        fontSize = 14.sp,
                        color = Color.DarkGray
                    )

                    Text(
                        text = "${currentIndex + 1} / ${questions.size}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 進度條
                LinearProgressIndicator(
                    progress = {
                        (currentIndex + 1).toFloat() / questions.size
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color(0xFFE4E4E4)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // 問題
                Text(
                    text = questions[currentIndex].first,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF222222)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = questions[currentIndex].second,
                    fontSize = 15.sp,
                    lineHeight = 23.sp,
                    color = Color(0xFF666666)
                )

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = "請選擇檢查結果",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF555555)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 選項
                CheckOption(
                    text = "正常",
                    selected = selectedOption == true
                ) {
                    selectedOption = true
                }

                Spacer(modifier = Modifier.height(10.dp))

                CheckOption(
                    text = "有點異常",
                    selected = selectedOption == false
                ) {
                    selectedOption = false
                }

                Spacer(modifier = Modifier.weight(1f))

                // 下一步
                Button(
                    onClick = {
                        if (selectedOption == true) {
                            score++
                        }

                        if (currentIndex < questions.size - 1) {
                            currentIndex++
                            selectedOption = null
                        } else {
                            showResult = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    enabled = selectedOption != null,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (currentIndex == questions.size - 1) {
                            "查看結果"
                        } else {
                            "下一題"
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {

                ResultView(
                    score = score,
                    total = questions.size,
                    onBack = onBack
                )
            }
        }
    }
}


@Composable
fun CheckOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        } else {
            Color.White
        },
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                Color(0xFFE0E0E0)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            RadioButton(
                selected = selected,
                onClick = null,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = text,
                fontSize = 15.sp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color(0xFF333333)
                },
                fontWeight = if (selected) {
                    FontWeight.Medium
                } else {
                    FontWeight.Normal
                }
            )
        }
    }
}


@Composable
fun ResultView(
    score: Int,
    total: Int,
    onBack: () -> Unit
) {

    val resultTitle: String
    val resultDescription: String
    val resultIcon: androidx.compose.ui.graphics.vector.ImageVector

    when {
        score >= 5 -> {
            resultTitle = "看起來是真卡"
            resultDescription = "這次檢查的項目大多符合，可以先放心。不過如果是高價小卡，還是建議再和官方卡面仔細比較。"
            resultIcon = Icons.Default.CheckCircle
        }

        score >= 3 -> {
            resultTitle = "有一些地方需要注意"
            resultDescription = "有幾個項目和正常卡面不太一樣，建議再確認卡況，也可以請賣家提供更多清楚的照片。"
            resultIcon = Icons.Default.Warning
        }

        else -> {
            resultTitle = "建議先不要購買"
            resultDescription = "這次檢查有多個項目不太符合，可能是假卡或有較嚴重的瑕疵，購買前建議再多確認。"
            resultIcon = Icons.Default.Warning
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(modifier = Modifier.height(50.dp))

        Icon(
            imageVector = resultIcon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = if (score >= 3) {
                MaterialTheme.colorScheme.primary
            } else {
                Color(0xFFD65A5A)
            }
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = resultTitle,
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF222222),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "符合項目 $score / $total",
            fontSize = 14.sp,
            color = Color(0xFF777777)
        )

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = resultDescription,
            modifier = Modifier.fillMaxWidth(),
            fontSize = 15.sp,
            lineHeight = 23.sp,
            color = Color(0xFF555555),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = "返回",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}