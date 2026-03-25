package com.example.kaone.ui.theme

import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

object CloudinaryUploader {
    // 支援圖片與影片上傳
    fun uploadMedia(
        context: Context,
        uri: Uri,
        scope: CoroutineScope,
        onSuccess: (String) -> Unit,
        onFailure: () -> Unit
    ) {
        scope.launch {
            MediaManager.get().upload(uri)
                .unsigned("KaOne5566") // 必須加上這個才能在實體機正常上傳
                .option("resource_type", "auto")
                .callback(object : UploadCallback {
                    override fun onStart(requestId: String) {}
                    override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}
                    override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                        val url = resultData["secure_url"] as? String ?: ""
                        onSuccess(url)
                    }
                    override fun onError(requestId: String, error: ErrorInfo) {
                        Toast.makeText(context, "圖片上傳失敗: ${error.description}", Toast.LENGTH_SHORT).show()
                        onFailure()
                    }
                    override fun onReschedule(requestId: String, error: ErrorInfo) {}
                }).dispatch()
        }
    }
}
