plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android) // ✅ 修正：去掉了多余的 jetbrains
}

android {
    namespace = "com.kuroshimira.nyanpasu"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kuroshimira.nyanpasu"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // ✅ 修正：现在它正确地在 android {} 内部了
    buildFeatures {
        viewBinding = true
    }

    buildTypes {
        release {
            // 启用代码混淆和资源压缩，保护源代码并减小 APK 体积
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Debug 版本关闭混淆，便于调试
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation("io.coil-kt:coil:2.4.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.work:work-runtime-ktx:2.8.1")
    
    // 📸 PhotoView: 图片手势拖拽和缩放
    implementation("com.github.chrisbanes:PhotoView:2.3.0")
}