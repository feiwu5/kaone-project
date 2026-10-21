package com.example.kaone.ui.theme

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.*
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import id.zelory.compressor.Compressor
import id.zelory.compressor.constraint.format
import id.zelory.compressor.constraint.quality
import id.zelory.compressor.constraint.resolution
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object CloudinaryUploader {

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun toast(context: Context, msg: String) {
        mainHandler.post { Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
    }

    /** 把 Uri 複製成暫存檔 */
    private suspend fun uriToFile(context: Context, uri: Uri, suffix: String): File =
        withContext(Dispatchers.IO) {
            val f = File(context.cacheDir, "src_${System.currentTimeMillis()}$suffix")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(f).use { input.copyTo(it) }
            }
            f
        }

    /** 底層上傳：直接丟檔案 Uri 給 Cloudinary */
    private fun doUpload(
        context: Context,
        uri: Uri,
        resourceType: String,
        onSuccess: (String) -> Unit,
        onFailure: () -> Unit,
        onProgress: (Float) -> Unit
    ) {
        MediaManager.get().upload(uri)
            .unsigned("KaOne5566")
            .option("resource_type", resourceType)
            .callback(object : UploadCallback {
                override fun onStart(requestId: String) { mainHandler.post { onProgress(0f) } }
                override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {
                    val p = if (totalBytes > 0) bytes.toFloat() / totalBytes else 0f
                    mainHandler.post { onProgress(p) }
                }
                override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                    val url = resultData["secure_url"] as? String ?: ""
                    mainHandler.post { onSuccess(url) }
                }
                override fun onError(requestId: String, error: ErrorInfo) {
                    toast(context, "傳送失敗: ${error.description}")
                    mainHandler.post { onFailure() }
                }
                override fun onReschedule(requestId: String, error: ErrorInfo) {}
            }).dispatch()
    }

    /** 圖片：先壓縮成長邊 1600px、JPEG 80% 再上傳 */
    fun uploadMedia(
        context: Context,
        uri: Uri,
        scope: CoroutineScope,
        onSuccess: (String) -> Unit,
        onFailure: () -> Unit,
        onProgress: (Float) -> Unit = {}
    ) {
        scope.launch {
            try {
                val src = uriToFile(context, uri, ".jpg")
                val compressed = Compressor.compress(context, src) {
                    resolution(1600, 1600)
                    quality(80)
                    format(android.graphics.Bitmap.CompressFormat.JPEG)
                }
                src.delete()
                doUpload(context, Uri.fromFile(compressed), "image", onSuccess, onFailure, onProgress)
            } catch (e: Exception) {
                toast(context, "圖片處理失敗: ${e.message}")
                onFailure()
            }
        }
    }

    /** 影片：先壓縮到 720p 再上傳 */
    @OptIn(UnstableApi::class)
    fun compressAndUploadVideo(
        context: Context,
        uri: Uri,
        scope: CoroutineScope,
        onSuccess: (String) -> Unit,
        onFailure: () -> Unit,
        onProgress: (Float) -> Unit = {}
    ) {
        val outFile = File(context.cacheDir, "vid_${System.currentTimeMillis()}.mp4")

        val transformer = Transformer.Builder(context)
            .setVideoMimeType("video/avc")
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    doUpload(context, Uri.fromFile(outFile), "video", onSuccess, {
                        outFile.delete(); onFailure()
                    }, onProgress)
                }
                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    toast(context, "影片壓縮失敗，改用原檔上傳")
                    // 壓縮失敗就退回直接上傳
                    doUpload(context, uri, "video", onSuccess, onFailure, onProgress)
                }
            })
            .build()

        val edited = EditedMediaItem.Builder(MediaItem.fromUri(uri))
            .setEffects(
                Effects(
                    emptyList(),
                    listOf(Presentation.createForHeight(720))
                )
            )
            .build()

        // Transformer 必須在有 Looper 的執行緒（主執行緒）啟動
        mainHandler.post {
            transformer.start(edited, outFile.absolutePath)
        }
    }
}