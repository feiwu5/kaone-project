package com.example.kaone.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.ui.draw.drawBehind
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

@Composable
fun PhotoCardCameraScreen(
    onImageCaptured: (Uri) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    LaunchedEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture)
            } catch (ex: Exception) { Log.e("PhotoCardCamera", "Binding failed", ex) }
        }, ContextCompat.getMainExecutor(context))
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // --- 直式導覽框遮罩與臉部對準框 ---
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // 1. 小卡外框 (調降高度比例至 0.55f)
            val rectHeight = canvasHeight * 0.60f
            val rectWidth = rectHeight / 1.58f
            val left = (canvasWidth - rectWidth) / 2
            // 將框框位置調低：設為 0.25f
            val top = (canvasHeight - rectHeight) * 0.35f

            // 2. 繪製半透明遮罩
            val combinedPath = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(0f, 0f, canvasWidth, canvasHeight))
                addRoundRect(RoundRect(Rect(left, top, left + rectWidth, top + rectHeight), CornerRadius(16.dp.toPx())))
            }
            drawPath(path = combinedPath, color = Color.Black.copy(alpha = 0.7f))

            // 3. 繪製小卡白色邊框
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(left, top),
                size = Size(rectWidth, rectHeight),
                cornerRadius = CornerRadius(16.dp.toPx()),
                style = Stroke(width = 2.dp.toPx())
            )

            // 4. 偶像臉部對準框 (橢圓形虛線)
            val faceWidth = rectWidth * 0.5f
            val faceHeight = faceWidth * 1.3f
            val faceLeft = left + (rectWidth - faceWidth) / 2
            val faceTop = top + (rectHeight * 0.15f)

            drawOval(
                color = Color.White.copy(alpha = 0.5f),
                topLeft = Offset(faceLeft, faceTop),
                size = Size(faceWidth, faceHeight),
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
            )
        }

        // 關閉按鈕
        IconButton(onClick = onDismiss, modifier = Modifier.statusBarsPadding().padding(16.dp)) {
            Icon(Icons.Default.Close, contentDescription = "關閉", tint = Color.White)
        }

        // 提示文字
        Text(
            text = "請將偶像臉部對準虛線框內",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 130.dp)
        )

        // 拍照按鈕
        Button(
            onClick = {
                imageCapture.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val bitmap = imageProxyToBitmap(image)
                        image.close()

                        val cropped = cropToPhotoCardRatio(bitmap)

                        val file = File(context.cacheDir, "pc_${System.currentTimeMillis()}.jpg")
                        FileOutputStream(file).use { out ->
                            cropped.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        }
                        onImageCaptured(Uri.fromFile(file))
                    }
                    override fun onError(exc: ImageCaptureException) { Log.e("PhotoCardCamera", "Error", exc) }
                })
            },
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
                .size(75.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(1.dp)
                    .background(Color.Transparent, CircleShape)
                    .drawBehind {
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.2f),
                            radius = size.minDimension / 2,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
            )
        }
    }
}

// 依照畫面的導覽框比例裁切圖片
private fun cropToPhotoCardRatio(bitmap: Bitmap): Bitmap {
    val w = bitmap.width
    val h = bitmap.height

    val targetH = (h * 0.55f).toInt()
    val targetW = (targetH / 1.58f).toInt()

    val left = (w - targetW) / 2
    // 這裡的 0.25f 必須與 UI 繪製的 top 邏輯完全一致，裁切才會準確
    val top = (h - targetH) * 0.25f

    return Bitmap.createBitmap(
        bitmap,
        left.coerceAtLeast(0),
        top.coerceAtLeast(0f).toInt(),
        targetW.coerceAtMost(w - left),
        targetH.coerceAtMost(h - top.toInt())
    )
}

// 將 ImageProxy 轉為 Bitmap 並旋轉至正確方向
private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
    val buffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    val matrix = Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}