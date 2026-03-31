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
                    if (reporterId.isEmpty()) {
                        Toast.makeText(context, "請先登入後再進行檢舉", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isSubmitting = true
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
                                db.collection("users").document(targetId).get().addOnSuccessListener { userDoc ->
                                    val profileUrl = userDoc.getString("profileImageUrl") ?: ""
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

                            onDismiss()
                        }
                        .addOnFailureListener {
                            isSubmitting = false
                            Toast.makeText(context, "提交失敗，請重試", Toast.LENGTH_SHORT).show()
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
