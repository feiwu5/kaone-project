package com.example.kaone

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.cloudinary.android.MediaManager
import com.example.kaone.ui.HomeScreen
import com.example.kaone.ui.LoginScreen
import com.example.kaone.ui.SignUpScreen
import com.example.kaone.ui.theme.KaOneTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class MainActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var banListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            val config = mapOf(
                "cloud_name" to "ddlaenz5b",
                "api_key" to "391416459781695",
                "api_secret" to "HJsJPCFhhDRT18PX31yJHCsy0DU"
            )
            MediaManager.init(this, config)
        } catch (e: Exception) { }

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        
        enableEdgeToEdge()
        setContent {
            KaOneTheme {
                var currentScreen by remember { 
                    mutableStateOf(if (auth.currentUser != null) "home" else "login") 
                }
                
                // 追蹤是否為訪客模式
                var isGuestMode by remember { mutableStateOf(false) }

                // 監聽用戶是否被停權 (訪客模式跳過)
                LaunchedEffect(currentScreen) {
                    val userId = auth.currentUser?.uid
                    if (currentScreen == "home" && userId != null && !isGuestMode) {
                        banListener?.remove()
                        banListener = db.collection("users").document(userId)
                            .addSnapshotListener { snapshot, _ ->
                                if (snapshot != null && snapshot.exists()) {
                                    val isBanned = snapshot.getBoolean("isBanned") ?: false
                                    if (isBanned) {
                                        Toast.makeText(this@MainActivity, "您的帳號已被停權，請聯繫管理員", Toast.LENGTH_LONG).show()
                                        auth.signOut()
                                        currentScreen = "login"
                                    }
                                }
                            }
                    } else {
                        banListener?.remove()
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
                                onSignUpClick = { email, password, name, gender, nickname, location ->
                                    val cleanEmail = email.trim()
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
                                                        "createdAt" to FieldValue.serverTimestamp(),
                                                        "favoriteCardIds" to emptyList<String>(),
                                                        "customDashboardTabs" to emptyList<String>(),
                                                        "profileImageUrl" to "",
                                                        "isAdmin" to false,
                                                        "isBanned" to false
                                                    )
                                                    db.collection("users").document(userId).set(user)
                                                        .addOnSuccessListener {
                                                            Toast.makeText(this, "註冊成功！", Toast.LENGTH_SHORT).show()
                                                            isGuestMode = false
                                                            currentScreen = "home"
                                                        }
                                                        .addOnFailureListener { e ->
                                                            Toast.makeText(this, "資料儲存失敗: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                                        }
                                                }
                                            } else {
                                                Toast.makeText(this, "註冊失敗: ${task.exception?.localizedMessage}", Toast.LENGTH_LONG).show()
                                            }
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

    override fun onDestroy() {
        super.onDestroy()
        banListener?.remove()
    }
}
