plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.kaone"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.kaone"
        minSdk = 26
        targetSdk = 35 // 升級至 35 並搭配支援 16KB 的庫
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        mlModelBinding = true
    }

    packaging {
        resources {
            excludes += "/META-INF/AL2.0"
            excludes += "/META-INF/LGPL2.1"
        }
        jniLibs {
            // 讓系統在安裝時處理庫的對齊
            useLegacyPackaging = false 
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.9.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-messaging")

    // 圖片載入與處理
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.cloudinary:cloudinary-android:3.1.2")
    implementation("androidx.compose.material:material-icons-extended:1.6.7")
    
    // 強制更新 graphics-path 以修正 16KB 警告
    implementation("androidx.graphics:graphics-path:1.0.1")

    // ML Kit 文字辨識 (繁體中文版)
    implementation("com.google.mlkit:text-recognition-chinese:16.0.0")
    implementation(libs.androidx.appcompat)

    // CameraX 核心庫
    val cameraVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraVersion")
    implementation("androidx.camera:camera-camera2:$cameraVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraVersion")
    implementation("androidx.camera:camera-view:$cameraVersion")

    // TensorFlow Lite
    implementation("org.tensorflow:tensorflow-lite:2.17.0")

    // 💡 解決 Duplicate class 衝突的關鍵：從 Support 和 Metadata 庫中排除掉舊版的 API 庫
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4") {
        exclude(group = "org.tensorflow", module = "tensorflow-lite-api")
    }
    implementation("org.tensorflow:tensorflow-lite-metadata:0.4.4") {
        exclude(group = "org.tensorflow", module = "tensorflow-lite-api")
    }
    implementation("org.tensorflow:tensorflow-lite-gpu:2.17.0")




    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
