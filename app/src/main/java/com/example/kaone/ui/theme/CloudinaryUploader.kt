package com.example.kaone.ui.theme

import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// /Users/linyijie/AndroidStudioProjects/KaOne/app/src/main/java/com/example/kaone/ui/theme/CloudinaryUploader.kt

// ... (省略其他 import)
object CloudinaryUploader {
    fun uploadMedia(
        context: Context,
        uri: Uri,
        scope: CoroutineScope,
        onSuccess: (String) -> Unit,
        onFailure: () -> Unit,
        onProgress: (Float) -> Unit = {} // 新增進度回傳
    ) {
        scope.launch {
            MediaManager.get().upload(uri)
                .unsigned("KaOne5566")
                .option("resource_type", "auto")
                .callback(object : UploadCallback {
                    override fun onStart(requestId: String) {
                        onProgress(0f)
                    }
                    override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {
                        val progress = bytes.toFloat() / totalBytes.toFloat()
                        onProgress(progress) // 回傳進度 (0.0 ~ 1.0)
                    }
                    override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                        val url = resultData["secure_url"] as? String ?: ""
                        onSuccess(url)
                    }
                    override fun onError(requestId: String, error: ErrorInfo) {
                        Toast.makeText(context, "上傳失敗: ${error.description}", Toast.LENGTH_SHORT).show()
                        onFailure()
                    }
                    override fun onReschedule(requestId: String, error: ErrorInfo) {}
                }).dispatch()
        }
    }
}
