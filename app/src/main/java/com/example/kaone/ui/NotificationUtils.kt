package com.example.kaone.ui

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

fun sendNotification(
    userId: String,
    type: String,
    title: String,
    content: String,
    relatedId: String = "",
    relatedImage: String = ""
) {
    if (userId.isEmpty()) return
    val db = FirebaseFirestore.getInstance()
    val notification = hashMapOf(
        "userId" to userId,
        "type" to type,
        "title" to title,
        "content" to content,
        "timestamp" to FieldValue.serverTimestamp(),
        "isRead" to false,
        "relatedId" to relatedId,
        "relatedImage" to relatedImage
    )
    db.collection("notifications").add(notification)
}
