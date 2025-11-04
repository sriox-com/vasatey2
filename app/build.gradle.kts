plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.sriox.vasatey"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.sriox.vasatey"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    androidResources {
        // Prevent compression of Picovoice model files
        noCompress += listOf("pv", "ppn")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    // --- Android Core ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)

    // --- Firebase (Only Messaging for push notifications) ---
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.messaging.ktx)
    implementation(libs.firebase.installations.ktx)

    // --- ✅ Supabase SDK ---
    implementation(libs.supabase.postgrest.kt)
    implementation(libs.supabase.storage.kt)
    implementation(libs.supabase.realtime.kt)
    implementation(libs.supabase.gotrue.kt)

    // --- Ktor & Serialization ---
    implementation(libs.ktor.client.android)
    implementation(libs.kotlinx.serialization.json)

    // --- Picovoice (Wake Word Engine) ---
    implementation(libs.picovoice.porcupine)

    // --- Lifecycle + Coroutines ---
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // --- Networking ---
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)

    // --- Location & Maps ---
    implementation(libs.play.services.location)
    implementation(libs.play.services.maps)
}
