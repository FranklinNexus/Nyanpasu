import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// 构建产物统一写到 .out/，避免 Windows 上 R.jar 被多进程锁住
// Android Studio（Run/Sync）→ .out/app-ide
// gradlew / CI           → .out/app
// 终端 build.ps1         → .out/app-<时间戳>
val nyanpasuOutSuffix =
    providers.gradleProperty("nyanpasuOutSuffix").orNull?.takeIf { it.isNotBlank() }
        ?: System.getenv("NYANPASU_OUT_SUFFIX")?.takeIf { it.isNotBlank() }
val fromAndroidStudio =
    providers.gradleProperty("android.injected.invoked.from.ide").orNull == "true"
val outDirName =
    when {
        nyanpasuOutSuffix != null -> "app-$nyanpasuOutSuffix"
        fromAndroidStudio -> "app-ide"
        else -> "app"
    }
layout.buildDirectory.set(
    rootProject.layout.projectDirectory.dir(".out/$outDirName"),
)

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    val raw = keystorePropertiesFile.readText(Charsets.UTF_8).removePrefix("\uFEFF")
    keystoreProperties.load(raw.reader())
}

android {
    namespace = "com.kuroshimira.nyanpasu"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kuroshimira.nyanpasu"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
            }
        }
    }

    buildFeatures {
        viewBinding = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation(libs.coil)
    implementation(libs.okhttp)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.photoview)
}
