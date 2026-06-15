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
    keystoreProperties.stringPropertyNames().forEach { key ->
        keystoreProperties.setProperty(key, keystoreProperties.getProperty(key).trim())
    }
}

fun propOrEnv(propKey: String, envKey: String): String? =
    System.getenv(envKey)?.takeIf { it.isNotBlank() }
        ?: keystoreProperties.getProperty(propKey)?.takeIf { it.isNotBlank() }

fun hasReleaseSigning(): Boolean =
    propOrEnv("storePassword", "KEYSTORE_PASSWORD") != null &&
        propOrEnv("keyAlias", "KEY_ALIAS") != null &&
        propOrEnv("keyPassword", "KEY_PASSWORD") != null &&
        propOrEnv("storeFile", "KEYSTORE_FILE")?.let { rootProject.file(it).exists() } == true

android {
    namespace = "com.kuroshimira.nyanpasu"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kuroshimira.nyanpasu"
        minSdk = 24
        targetSdk = 34
        versionCode = 3
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning()) {
            create("release") {
                keyAlias = propOrEnv("keyAlias", "KEY_ALIAS")!!
                keyPassword = propOrEnv("keyPassword", "KEY_PASSWORD")!!
                storeFile = rootProject.file(propOrEnv("storeFile", "KEYSTORE_FILE")!!)
                storePassword = propOrEnv("storePassword", "KEYSTORE_PASSWORD")!!
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
            if (hasReleaseSigning()) {
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
