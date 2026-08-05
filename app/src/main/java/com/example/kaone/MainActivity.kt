package com.example.kaone

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.cloudinary.android.MediaManager
import com.example.kaone.ui.HomeScreen
import com.example.kaone.ui.LoginScreen
import com.example.kaone.ui.SignUpScreen
import com.example.kaone.ui.createNotificationChannel
import com.example.kaone.ui.showLocalNotification
import com.example.kaone.ui.theme.KaOneTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var banListener: ListenerRegistration? = null
    private var notificationListener: ListenerRegistration? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            fetchAndStoreFcmToken()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        createNotificationChannel(this)
        askNotificationPermission()

        try {
            val config = mapOf(
                "cloud_name" to "ddlaenz5b",
                "api_key" to "391416459781695",
                "api_secret" to "HJsJPCFhhDRT18PX31yJHCsy0DU"
            )
            MediaManager.init(this, config)
        } catch (e: Exception) { }
        
        enableEdgeToEdge()
        setContent {
            KaOneTheme {
                var currentScreen by remember { 
                    mutableStateOf(if (auth.currentUser != null) "home" else "login") 
                }
                
                var isGuestMode by remember { mutableStateOf(false) }

                LaunchedEffect(currentScreen) {
                    val userId = auth.currentUser?.uid
                    if (currentScreen == "home" && userId != null && !isGuestMode) {
                        fetchAndStoreFcmToken()
                        setupBanListener(userId) {
                            currentScreen = "login"
                        }
                        setupNotificationListener(userId)
                    } else {
                        removeListeners()
                    }
                }
                
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    when (currentScreen) {
                        "login" -> {
                            LoginScreen(
                                modifier = Modifier.padding(innerPadding),
                                onLoginClick = { email, password ->
                                    val cleanEmail = email.trim()
                                    if (cleanEmail.isEmpty() || password.isEmpty()) {
                                        Toast.makeText(this, "請填寫完整帳號密碼", Toast.LENGTH_SHORT).show()
                                        return@LoginScreen
                                    }
                                    auth.signInWithEmailAndPassword(cleanEmail, password)
                                        .addOnCompleteListener(this) { task ->
                                            if (task.isSuccessful) {
                                                val userId = auth.currentUser?.uid
                                                if (userId != null) {
                                                    db.collection("users").document(userId).get().addOnSuccessListener { d ->
                                                        if (d.getBoolean("isBanned") == true) {
                                                            Toast.makeText(this, "此帳號已被停權", Toast.LENGTH_LONG).show()
                                                            auth.signOut()
                                                        } else {
                                                            Toast.makeText(this, "登入成功！", Toast.LENGTH_SHORT).show()
                                                            isGuestMode = false
                                                            currentScreen = "home"
                                                        }
                                                    }
                                                }
                                            } else {
                                                Toast.makeText(this, "登入失敗: ${task.exception?.localizedMessage ?: "請確認帳密"}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                },
                                onSignUpClick = { currentScreen = "signup" },
                                onGuestClick = { 
                                    isGuestMode = true
                                    currentScreen = "home" 
                                },
                                onForgotPasswordClick = { email ->
                                    val cleanEmail = email.trim()
                                    if (cleanEmail.isEmpty()) {
                                        Toast.makeText(this, "請先輸入電子郵件", Toast.LENGTH_SHORT).show()
                                    } else {
                                        auth.sendPasswordResetEmail(cleanEmail)
                                            .addOnCompleteListener { task ->
                                                if (task.isSuccessful) {
                                                    Toast.makeText(this, "重設密碼郵件已寄出", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(this, "寄送失敗: ${task.exception?.localizedMessage}", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                    }
                                }
                            )
                        }
                        "signup" -> {
                            SignUpScreen(
                                modifier = Modifier.padding(innerPadding),
                                onSignUpClick = { email, password, name, gender, nickname, location, idHash ->
                                    val cleanEmail = email.trim()

                                    // 1. 先檢查這個身分證 Hash 是否已經被註冊過
                                    db.collection("users")
                                        .whereEqualTo("idHash", idHash)
                                        .get()
                                        .addOnSuccessListener { documents ->
                                            if (!documents.isEmpty) {
                                                Toast.makeText(this, "此身分證已被其他帳號綁定，無法重複註冊", Toast.LENGTH_LONG).show()
                                            } else {
                                                // 2. 執行 Firebase Auth 註冊
                                                auth.createUserWithEmailAndPassword(cleanEmail, password)
                                                    .addOnCompleteListener(this) { task ->
                                                        if (task.isSuccessful) {
                                                            val userId = auth.currentUser?.uid
                                                            if (userId != null) {
                                                                val user = hashMapOf(
                                                                    "name" to name,
                                                                    "nickname" to nickname,
                                                                    "gender" to gender,
                                                                    "location" to location,
                                                                    "email" to cleanEmail,
                                                                    "idHash" to idHash,
                                                                    "isVerified" to true,
                                                                    "createdAt" to FieldValue.serverTimestamp(),
                                                                    "favoriteCardIds" to emptyList<String>(),
                                                                    "isAdmin" to false,
                                                                    "isBanned" to false
                                                                )
                                                                // 3. 寫入資料庫
                                                                db.collection("users").document(userId).set(user)
                                                                    .addOnSuccessListener {
                                                                        Toast.makeText(this, "實名認證註冊成功！", Toast.LENGTH_SHORT).show()
                                                                        currentScreen = "home"
                                                                    }
                                                                    .addOnFailureListener { e ->
                                                                        Toast.makeText(this, "資料庫寫入失敗: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                                                    }
                                                            }
                                                        } else {
                                                            Toast.makeText(this, "帳號創建失敗: ${task.exception?.localizedMessage}", Toast.LENGTH_LONG).show()
                                                        }
                                                    }
                                            }
                                        }
                                        .addOnFailureListener { e ->
                                            Toast.makeText(this, "網路錯誤: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                        }
                                },
                                onBackToLoginClick = { currentScreen = "login" }
                            )
                        }
                        "home" -> {
                            HomeScreen(
                                userId = if (isGuestMode) "" else (auth.currentUser?.uid ?: ""),
                                modifier = Modifier.padding(innerPadding),
                                onLogoutClick = {
                                    auth.signOut()
                                    isGuestMode = false
                                    currentScreen = "login"
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                fetchAndStoreFcmToken()
            }
        } else {
            fetchAndStoreFcmToken()
        }
    }

    private fun fetchAndStoreFcmToken() {
        val userId = auth.currentUser?.uid ?: return
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                db.collection("users").document(userId).update("fcmToken", token)
                    .addOnSuccessListener { Log.d("FCM", "Token updated: $token") }
            }
        }
    }

    private fun setupBanListener(userId: String, onBanned: () -> Unit) {
        banListener?.remove()
        banListener = db.collection("users").document(userId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    if (snapshot.getBoolean("isBanned") == true) {
                        Toast.makeText(this, "您的帳號已被停權", Toast.LENGTH_LONG).show()
                        auth.signOut()
                        onBanned()
                    }
                }
            }
    }

    private fun setupNotificationListener(userId: String) {
        notificationListener?.remove()
        notificationListener = db.collection("notifications")
            .whereEqualTo("userId", userId)
            .whereEqualTo("isRead", false)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snapshots, e ->
                if (e != null) return@addSnapshotListener
                
                for (dc in snapshots?.documentChanges ?: emptyList()) {
                    if (dc.type == DocumentChange.Type.ADDED) {
                        val title = dc.document.getString("title") ?: "新通知"
                        val content = dc.document.getString("content") ?: ""
                        showLocalNotification(this, title, content)
                    }
                }
            }
    }

    private fun removeListeners() {
        banListener?.remove()
        notificationListener?.remove()
    }

    override fun onDestroy() {
        super.onDestroy()
        removeListeners()
    }
}
