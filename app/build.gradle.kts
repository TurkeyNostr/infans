plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.turkbot.babytracker"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.turkbot.babytracker"
        minSdk = 26
        targetSdk = 34
        versionCode = 21
        versionName = "1.4.15"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Debug build: debuggable is true by default (expected)
            // Explicitly set for clarity
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // OkHttp for WebSocket relay connections
    implementation(libs.okhttp)

    // Secp256k1 for Nostr cryptography
    implementation(libs.secp256k1.kmp)
    implementation(libs.secp256k1.jni.android)

    // BouncyCastle for HKDF/PBKDF2 used in NIP-44
    implementation(libs.bouncy.castle)

    // EncryptedSharedPreferences for secure key storage
    implementation(libs.security.crypto)

    // DataStore for preferences
    implementation(libs.datastore.preferences)

    // WorkManager for background sync
    implementation(libs.work.runtime.ktx)
}
