package com.example.kaone.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun ReportDialog(
    reporterId: String,
    targetId: String,
    targetType: String, // "card" or "user"
    onDismiss: () -> Unit
) {
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current
    val reportReasons = listOf("垃圾內容 / 廣告", "詐騙行為", "不當言論 or 圖片", "個人隱私洩露", "其他")
    var selectedReason by remember { mutableStateOf(reportReasons[0]) }
    var otherReason by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("檢舉此內容") },
        text = {
            Column(Modifier.selectableGroup()) {
                Text("請選擇檢舉原因：", fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
                reportReasons.forEach { reason ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .selectable(
                                selected = (selectedReason == reason),
                                onClick = { selectedReason = reason },
                                role = Role.RadioButton
                            )
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedReason == reason),
                            onClick = null
                        )
                        Text(
                            text = reason,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
                if (selectedReason == "其他") {
                    OutlinedTextField(
                        value = otherReason,
                        onValueChange = { otherReason = it },
                        label = { Text("詳細原因 (必填)") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        isError = otherReason.isBlank(),
                        supportingText = {
                            if (otherReason.isBlank()) {
                                Text("請輸入檢舉的詳細原因")
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {
            val isConfirmEnabled = !isSubmitting && (selectedReason != "其他" || otherReason.isNotBlank())

            Button(
                enabled = isConfirmEnabled,
                onClick = {
                    // 1. 檢查使用者是否手寫了違禁詞
                    if (selectedReason == "其他" && ProfanityFilter.containsProfanity(otherReason)) {
                        Toast.makeText(context, "檢舉原因包含違禁詞，請修正", Toast.LENGTH_SHORT).show()
                        return@Button // 攔截，不送出檢舉
                    }

                    if (reporterId.isEmpty()) {
                        Toast.makeText(context, "請先登入後再進行檢舉", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isSubmitting = true
                    
                    // 1. 檢查用戶是否處於「懲處暫停檢舉」狀態
                    db.collection("users").document(reporterId).get().addOnSuccessListener { userDoc ->
                        val reportBannedUntil = userDoc.getTimestamp("reportBannedUntil")
                        if (reportBannedUntil != null && reportBannedUntil.toDate().after(java.util.Date())) {
                            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                            isSubmitting = false
                            Toast.makeText(context, "您因濫用檢舉功能，檢舉權限已被暫停至 ${sdf.format(reportBannedUntil.toDate())}", Toast.LENGTH_LONG).show()
                            return@addOnSuccessListener
                        }

                        // 2. 防重複檢舉：檢查該用戶是否已經檢舉過同一目標
                        db.collection("reports")
                            .whereEqualTo("reporterId", reporterId)
                            .whereEqualTo("targetId", targetId)
                            .get()
                            .addOnSuccessListener { querySnapshot ->
                                if (!querySnapshot.isEmpty) {
                                    isSubmitting = false
                                    Toast.makeText(context, "您已經檢舉過此內容，請勿重複提交", Toast.LENGTH_SHORT).show()
                                    return@addOnSuccessListener
                                }

                                val finalReason = if (selectedReason == "其他") otherReason else selectedReason
                                val reportData = hashMapOf(
                                    "reporterId" to reporterId,
                                    "targetId" to targetId,
                                    "targetType" to targetType,
                                    "reason" to finalReason,
                                    "timestamp" to Timestamp.now(),
                                    "status" to "pending"
                                )
                                db.collection("reports").add(reportData)
                                    .addOnSuccessListener {
                                        Toast.makeText(context, "感謝您的檢舉，我們將盡快處理", Toast.LENGTH_SHORT).show()

                                        if (targetType == "user") {
                                            db.collection("users").document(targetId).get().addOnSuccessListener { targetUserDoc ->
                                                val profileUrl = targetUserDoc.getString("profileImageUrl") ?: ""
                                                sendNotification(
                                                    targetId,
                                                    "report",
                                                    "帳號檢舉通知",
                                                    "您的帳號因「$finalReason」收到一則檢舉。管理員將審核您的個人檔案。",
                                                    targetId,
                                                    profileUrl
                                                )
                                            }
                                        } else if (targetType == "card") {
                                            db.collection("cards").document(targetId).get().addOnSuccessListener { cardDoc ->
                                                val cardOwnerId = cardDoc.getString("userId") ?: ""
                                                val rawCardName = cardDoc.getString("memberName") ?: "未命名小卡"
                                                val cardName = rawCardName.split("|").first()
                                                val cardImageUrl = cardDoc.getString("imageUrl") ?: ""
                                                if (cardOwnerId.isNotEmpty()) {
                                                    sendNotification(
                                                        cardOwnerId,
                                                        "report",
                                                        "內容檢舉通知",
                                                        "您發佈的《$cardName》因「$finalReason」收到一則檢舉。請遵守社區規範。",
                                                        targetId,
                                                        cardImageUrl
                                                    )
                                                }
                                            }
                                        }

                                        // 自動檢查：若 24 小時內同一人檢舉達 5 次(含)以上，自動發送提醒通知給檢舉人
                                        db.collection("reports")
                                            .whereEqualTo("reporterId", reporterId)
                                            .get()
                                            .addOnSuccessListener { reportsSnapshot ->
                                                val twentyFourHoursAgoMs = System.currentTimeMillis() - (24 * 60 * 60 * 1000L)
                                                val count24h = reportsSnapshot.documents.count { doc ->
                                                    val ts = doc.getTimestamp("timestamp")
                                                    ts != null && ts.toDate().time >= twentyFourHoursAgoMs
                                                }
                                                if (count24h >= 5) {
                                                    sendNotification(
                                                        reporterId,
                                                        "system",
                                                        "檢舉提醒通知",
                                                        "您已檢舉數次，若違規屬實我們會刪除貼文，若是惡意檢舉，請停止您的動作以免被停權。"
                                                    )
                                                }
                                            }

                                        onDismiss()
                                    }
                                    .addOnFailureListener {
                                        isSubmitting = false
                                        Toast.makeText(context, "提交失敗，請重試", Toast.LENGTH_SHORT).show()
                                    }
                            }
                            .addOnFailureListener {
                                isSubmitting = false
                                Toast.makeText(context, "檢查檢舉紀錄失敗，請重試", Toast.LENGTH_SHORT).show()
                            }
                    }.addOnFailureListener {
                        isSubmitting = false
                        Toast.makeText(context, "檢查用戶狀態失敗，請重試", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                if (isSubmitting) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                else Text("提交檢舉")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}