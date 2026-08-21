package com.example.kaone.ui

import android.Manifest
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.common.InputImage
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(
    onSignUpClick: (String, String, String, String, String, String, String) -> Unit, // email, password, name, gender, nickname, location, idHash
    onBackToLoginClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }

    var idNumberInput by remember { mutableStateOf("") }
    var detectedIdByAI by remember { mutableStateOf("") }
    var idHash by remember { mutableStateOf("") }
    var isVerifying by remember { mutableStateOf(false) }
    var verificationStatus by remember { mutableStateOf("未驗證") }

    var showCamera by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // 自動比對邏輯：只比對身分證字號
    LaunchedEffect(idNumberInput, detectedIdByAI) {
        val userInputId = idNumberInput.trim().uppercase()

        if (detectedIdByAI.isNotEmpty() && userInputId.isNotEmpty()) {
            val idMatches = (detectedIdByAI == userInputId)

            if (idMatches) {
                idHash = IdCardValidator.hashIdNumber(detectedIdByAI)
                verificationStatus = "已通過 AI 核對 ✅"
            } else {
                idHash = ""
                verificationStatus = "核對失敗：輸入的號碼與證件不符"
            }
        } else if (detectedIdByAI.isNotEmpty()) {
            // 將「請填寫」改為「請確認」，增加使用者體驗
            verificationStatus = "已辨識證件，請確認身分證字號是否正確"
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showCamera = true
        } else {
            Toast.makeText(context, "需要相機權限才能掃描證件", Toast.LENGTH_SHORT).show()
        }
    }

    val genderOptions = listOf("男", "女", "不公開")
    var selectedGender by remember { mutableStateOf<String?>(null) }
    val cityDistricts = KpopData.cityDistricts
    var cityExpanded by remember { mutableStateOf(false) }
    var selectedCity by remember { mutableStateOf("") }
    var districtExpanded by remember { mutableStateOf(false) }
    var selectedDistrict by remember { mutableStateOf("") }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            Text(text = "註冊帳號", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(24.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (idHash.isNotEmpty()) Icons.Default.CheckCircle else Icons.Default.CreditCard,
                            contentDescription = null,
                            tint = if (idHash.isNotEmpty()) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "身分證實名認證", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "請點擊按鈕並將證件對準導覽框。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ClarificationText()
                    Spacer(modifier = Modifier.height(12.dp))

                    if (isVerifying) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Button(
                            onClick = {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            },
                            colors = if (idHash.isNotEmpty()) ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)) else ButtonDefaults.buttonColors()
                        ) {
                            Text(if (idHash.isEmpty()) "拍攝身份證核對" else "重新核對")
                        }
                    }

                    Text(
                        text = "狀態：$verificationStatus",
                        fontSize = 13.sp,
                        color = if (idHash.isNotEmpty()) Color(0xFF4CAF50) else Color.Red,
                        modifier = Modifier.padding(top = 4.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = idNumberInput,
                onValueChange = { idNumberInput = it },
                label = { Text("身分證字號") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("例：A123456789") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("姓名") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("請輸入真實姓名") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                label = { Text("暱稱") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(text = "性別：", fontSize = 16.sp)
                Row(modifier = Modifier.selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
                    genderOptions.forEach { text ->
                        Row(
                            Modifier.selectable(
                                selected = (text == selectedGender),
                                onClick = { selectedGender = text },
                                role = Role.RadioButton
                            ).padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = (text == selectedGender), onClick = null)
                            Text(text = text, modifier = Modifier.padding(start = 2.dp), fontSize = 14.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            ExposedDropdownMenuBox(
                expanded = cityExpanded,
                onExpandedChange = { cityExpanded = !cityExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedCity.ifEmpty { "請選擇縣市" },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("所在縣市") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cityExpanded) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = cityExpanded, onDismissRequest = { cityExpanded = false }) {
                    cityDistricts.keys.forEach { city ->
                        DropdownMenuItem(text = { Text(city) }, onClick = {
                            selectedCity = city
                            selectedDistrict = ""
                            cityExpanded = false
                        })
                    }
                }
            }

            if (selectedCity.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                ExposedDropdownMenuBox(
                    expanded = districtExpanded,
                    onExpandedChange = { districtExpanded = !districtExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedDistrict.ifEmpty { "請選擇行政區" },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("行政區") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = districtExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = districtExpanded, onDismissRequest = { districtExpanded = false }) {
                        cityDistricts[selectedCity]?.forEach { district ->
                            DropdownMenuItem(text = { Text(district) }, onClick = {
                                selectedDistrict = district
                                districtExpanded = false
                            })
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("電子郵件") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("設定密碼") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("確認密碼") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    val fieldCheck = ProfanityFilter.checkFields(mapOf("姓名" to name, "暱稱" to nickname))
                    if (fieldCheck != null) {
                        Toast.makeText(context, "「$fieldCheck」包含違禁詞，請修正後再試", Toast.LENGTH_SHORT).show()
                    } else if (idHash.isEmpty()) {
                        Toast.makeText(context, "請先完成 AI 實名認證並核對身分證號碼", Toast.LENGTH_SHORT).show()
                    } else if (name.isEmpty() || nickname.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty() || selectedGender == null || selectedCity.isEmpty() || selectedDistrict.isEmpty()) {
                        Toast.makeText(context, "請填寫完後再送出資料！", Toast.LENGTH_SHORT).show()
                    } else if (password.length < 6) {
                        Toast.makeText(context, "密碼長度至少六碼", Toast.LENGTH_SHORT).show()
                    } else if (password != confirmPassword) {
                        Toast.makeText(context, "兩次密碼輸入不一致", Toast.LENGTH_SHORT).show()
                    } else {
                        val finalLocation = "$selectedCity $selectedDistrict"
                        onSignUpClick(email, password, name, selectedGender!!, nickname, finalLocation, idHash)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("註冊", fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = onBackToLoginClick) {
                Text("已有帳號？返回登入")
            }
            Spacer(modifier = Modifier.height(32.dp))
        }

        // 當需要顯示相機時，覆蓋整個畫面
        if (showCamera) {
            IdCardCameraScreen(
                onImageCaptured = { uri ->
                    showCamera = false
                    isVerifying = true
                    try {
                        val image = InputImage.fromFilePath(context, uri)
                        IdCardValidator.scanIdCard(
                            image = image,
                            onSuccess = { detectedId, detectedName -> // 接收兩個參數
                                detectedIdByAI = detectedId
                                idNumberInput = detectedId // 自動填入身分證

                                if (detectedName.isNotEmpty()) {
                                    name = detectedName // ✨ 新增：自動填入姓名
                                }

                                isVerifying = false
                                Toast.makeText(context, "已自動辨識身分資訊", Toast.LENGTH_SHORT).show()
                            },
                            onFailure = { e ->
                                verificationStatus = e.message ?: "辨識失敗"
                                detectedIdByAI = ""
                                idHash = ""
                                isVerifying = false
                                Toast.makeText(context, verificationStatus, Toast.LENGTH_LONG).show()
                            }
                        )
                    } catch (e: Exception) {
                        isVerifying = false
                        Toast.makeText(context, "圖片處理出錯: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                },
                onDismiss = { showCamera = false }
            )
        }
    }
}

@Composable
fun ClarificationText() {
    Text(
        text = "注意：請將證件「橫拿」拍攝並避開強光。",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(top = 4.dp)
    )
}