package com.example.kaone.ui

import android.content.Context
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Composable
fun IdCardCameraScreen(
    onImageCaptured: (Uri) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { 
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build() 
    }
    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    LaunchedEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (ex: Exception) {
                Log.e("IdCardCamera", "Use case binding failed", ex)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // 導覽框遮罩 (橫式身分證框)
        IdCardOverlayGuide()

        // 控制與提示
        Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.padding(16.dp).align(Alignment.TopStart)
            ) {
                Icon(Icons.Default.Close, contentDescription = "關閉", tint = Color.White)
            }

            Text(
                text = "請將身分證對齊框內並橫向拍攝",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 150.dp)
            )
        }

        // 底部拍照按鈕
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp)
        ) {
            Button(
                onClick = {
                    captureAndCropImage(context, imageCapture, previewView, cameraExecutor, onImageCaptured)
                },
                shape = CircleShape,
                modifier = Modifier.size(75.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(65.dp)
                        .drawBehind {
                            drawCircle(
                                color = Color.Black.copy(0.2f), 
                                radius = size.minDimension / 2, 
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                )
            }
        }
    }
}

@Composable
fun IdCardOverlayGuide() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        
        // 身分證框 (橫式 1.58 : 1)，佔螢幕寬度 90%
        val rectWidth = canvasWidth * 0.9f
        val rectHeight = rectWidth / 1.58f
        val left = (canvasWidth - rectWidth) / 2
        val top = (canvasHeight - rectHeight) / 2

        val combinedPath = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(0f, 0f, canvasWidth, canvasHeight))
            addRoundRect(
                RoundRect(
                    rect = Rect(left, top, left + rectWidth, top + rectHeight),
                    cornerRadius = CornerRadius(16.dp.toPx())
                )
            )
        }

        drawPath(path = combinedPath, color = Color.Black.copy(alpha = 0.7f))
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(left, top),
            size = Size(rectWidth, rectHeight),
            cornerRadius = CornerRadius(16.dp.toPx()),
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

private fun captureAndCropImage(
    context: Context,
    imageCapture: ImageCapture,
    previewView: PreviewView,
    executor: ExecutorService,
    onImageCaptured: (Uri) -> Unit
) {
    imageCapture.takePicture(
        executor,
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bitmap = imageProxyToBitmap(image)
                image.close()

                // 核心：這裡會根據 PreviewView 的實際尺寸進行精確裁剪，AI 只會看到框框內的內容
                val croppedBitmap = cropToIdCardRatio(bitmap, previewView.width, previewView.height)
                
                val file = File(context.cacheDir, "id_cropped_${System.currentTimeMillis()}.jpg")
                FileOutputStream(file).use { out ->
                    croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                
                onImageCaptured(Uri.fromFile(file))
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("IdCardCamera", "Capture failed", exception)
            }
        }
    )
}

private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
    val buffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    
    val matrix = Matrix()
    matrix.postRotate(image.imageInfo.rotationDegrees.toFloat())
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

/**
 * 將原始 Bitmap 裁剪為對應 UI 導覽框的部分
 */
private fun cropToIdCardRatio(bitmap: Bitmap, viewWidth: Int, viewHeight: Int): Bitmap {
    val bitmapWidth = bitmap.width.toFloat()
    val bitmapHeight = bitmap.height.toFloat()
    val viewWidthF = viewWidth.toFloat()
    val viewHeightF = viewHeight.toFloat()

    // 判斷 PreviewView 是如何縮放/裁剪 Bitmap 的 (假設為 FILL_CENTER)
    val scale: Float
    val offsetX: Float
    val offsetY: Float

    if (bitmapWidth / bitmapHeight > viewWidthF / viewHeightF) {
        // Bitmap 較寬，裁切左右
        scale = viewHeightF / bitmapHeight
        offsetX = (bitmapWidth * scale - viewWidthF) / 2f
        offsetY = 0f
    } else {
        // Bitmap 較高，裁切上下
        scale = viewWidthF / bitmapWidth
        offsetX = 0f
        offsetY = (bitmapHeight * scale - viewHeightF) / 2f
    }

    // 計算 UI 導覽框在螢幕上的座標 (0.9f 寬度, 1.58f 比例)
    val rectWidthUI = viewWidthF * 0.9f
    val rectHeightUI = rectWidthUI / 1.58f
    val leftUI = (viewWidthF - rectWidthUI) / 2f
    val topUI = (viewHeightF - rectHeightUI) / 2f

    // 將 UI 座標對應回 Bitmap 原始座標
    val leftBitmap = (leftUI + offsetX) / scale
    val topBitmap = (topUI + offsetY) / scale
    val widthBitmap = rectWidthUI / scale
    val heightBitmap = rectHeightUI / scale

    return Bitmap.createBitmap(
        bitmap,
        leftBitmap.toInt().coerceAtLeast(0),
        topBitmap.toInt().coerceAtLeast(0),
        widthBitmap.toInt().coerceAtMost(bitmap.width - leftBitmap.toInt()),
        heightBitmap.toInt().coerceAtMost(bitmap.height - topBitmap.toInt())
    )
}
