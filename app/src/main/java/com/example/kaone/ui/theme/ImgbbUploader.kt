package com.example.kaone.ui.theme

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object ImgbbUploader {
    private val mainHandler = Handler(Looper.getMainLooper())

    // 將 Uri 複製到暫存檔案，解決背景上傳權限失效的問題
    private fun copyUriToCache(context: Context, uri: Uri): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val tempFile = File(context.cacheDir, "upload_${UUID.randomUUID()}.jpg")
            val outputStream = FileOutputStream(tempFile)
            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun uploadImage(
        context: Context,
        imageUri: Uri,
        scope: CoroutineScope,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        // 使用提供的 scope 在背景執行檔案複製
        scope.launch(Dispatchers.IO) {
            val localFile = copyUriToCache(context, imageUri)
            if (localFile == null) {
                mainHandler.post { 
                    Toast.makeText(context, "無法處理圖片檔案", Toast.LENGTH_SHORT).show()
                    onFailure("File copy failed") 
                }
                return@launch
            }

            try {
                MediaManager.get().upload(localFile.absolutePath)
                    .unsigned("KaOne5566")
                    .option("resource_type", "auto")
                    .callback(object : UploadCallback {
                        override fun onStart(requestId: String) {
                            Log.d("Cloudinary", "上傳開始: $requestId")
                        }
                        
                        override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}
                        
                        override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                            val url = resultData["secure_url"]?.toString() ?: resultData["url"]?.toString() ?: ""
                            Log.d("Cloudinary", "上傳成功: $url")
                            mainHandler.post {
                                if (localFile.exists()) localFile.delete()
                                if (url.isNotEmpty()) {
                                    onSuccess(url)
                                } else {
                                    onFailure("No URL returned")
                                }
                            }
                        }

                        override fun onError(requestId: String, error: ErrorInfo) {
                            Log.e("Cloudinary", "上傳失敗: ${error.description}")
                            mainHandler.post {
                                if (localFile.exists()) localFile.delete()
                                Toast.makeText(context, "圖片上傳失敗: ${error.description}", Toast.LENGTH_SHORT).show()
                                onFailure(error.description ?: "Unknown error")
                            }
                        }

                        override fun onReschedule(requestId: String, error: ErrorInfo) {}
                    })
                    .dispatch()
            } catch (e: Exception) {
                e.printStackTrace()
                if (localFile.exists()) localFile.delete()
                mainHandler.post { onFailure(e.message ?: "Dispatch failed") }
            }
        }
    }
}
