package com.example.kaone.ui.theme

import android.content.Context
import android.net.Uri
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import kotlinx.coroutines.CoroutineScope

object ImgbbUploader {
    // 雖然名稱仍叫 ImgbbUploader (為了不改動其他頁面程式碼)，
    // 但我們底層已更換為更穩定、快速的 Cloudinary。
    fun uploadImage(
        context: Context,
        imageUri: Uri,
        scope: CoroutineScope,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        MediaManager.get().upload(imageUri)
            .unsigned("KaOne5566") // 使用您提供的 Unsigned Upload Preset 名稱
            .callback(object : UploadCallback {
                override fun onStart(requestId: String) {
                    // 開始上傳
                }
                
                override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {
                    // 上傳進度
                }
                
                override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                    // 成功拿到 URL (Cloudinary 返回的是 secure_url)
                    val url = resultData["secure_url"] as String
                    onSuccess(url)
                }

                override fun onError(requestId: String, error: ErrorInfo) {
                    // 失敗
                    onFailure(error.description ?: "Cloudinary 上傳失敗")
                }

                override fun onReschedule(requestId: String, error: ErrorInfo) {
                    // 重新調度
                }
            })
            .dispatch()
    }
}
