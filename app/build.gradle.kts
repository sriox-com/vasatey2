plugins {plugins {

    alias(libs.plugins.android.application)    alias(libs.plugins.android.application)

    alias(libs.plugins.kotlin.android)    alias(libs.plugins.kotlin.android)

    alias(libs.plugins.kotlin.serialization)    alias(libs.plugins.kotlin.serialization)

    alias(libs.plugins.kotlin.kapt)    alias(libs.plugins.google.gms.google.services)

    alias(libs.plugins.google.services)}

}

android {

android {    namespace = "com.sriox.vasatey"

    namespace = "com.sriox.vasatey"    compileSdk = 34

    compileSdk = 34

    defaultConfig {

    defaultConfig {        applicationId = "com.sriox.vasatey"

        applicationId = "com.sriox.vasatey"        minSdk = 26

        minSdk = 24        targetSdk = 34

        targetSdk = 34        versionCode = 1

        versionCode = 1        versionName = "1.0"

        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"    }

        

        // Picovoice configuration    buildTypes {

        ndk {        getByName("release") {

            abiFilters += listOf("arm64-v8a", "armeabi-v7a")            isMinifyEnabled = false

        }            isShrinkResources = false

                    proguardFiles(

        // BuildConfig fields                getDefaultProguardFile("proguard-android-optimize.txt"),

        buildConfigField("String", "SUPABASE_URL", "\"\"")                "proguard-rules.pro"

        buildConfigField("String", "SUPABASE_ANON_KEY", "\"\"")            )

        buildConfigField("String", "VERCEL_NOTIFICATION_URL", "\"https://vasatey-notify-msg.vercel.app/api/sendNotification\"")        }

        buildConfigField("String", "PICOVOICE_ACCESS_KEY", "\"\"")    }

    }

    androidResources {

    buildTypes {        // Prevent compression of Picovoice model files

        debug {        noCompress += listOf("pv", "ppn")

            isMinifyEnabled = false    }

            isDebuggable = true

            applicationIdSuffix = ".debug"    compileOptions {

            versionNameSuffix = "-debug"        sourceCompatibility = JavaVersion.VERSION_17

                    targetCompatibility = JavaVersion.VERSION_17

            buildConfigField("Boolean", "DEBUG_MODE", "true")    }

            buildConfigField("String", "LOG_LEVEL", "\"DEBUG\"")

        }    kotlinOptions {

                jvmTarget = "17"

        release {    }

            isMinifyEnabled = true

            isShrinkResources = true    buildFeatures {

            isDebuggable = false        viewBinding = true

                }

            buildConfigField("Boolean", "DEBUG_MODE", "false")}

            buildConfigField("String", "LOG_LEVEL", "\"INFO\"")

            dependencies {

            proguardFiles(    // --- Android Core ---

                getDefaultProguardFile("proguard-android-optimize.txt"),    implementation(libs.androidx.core.ktx)

                "proguard-rules.pro"    implementation(libs.androidx.appcompat)

            )    implementation(libs.material)

                implementation(libs.androidx.constraintlayout)

            signingConfig = signingConfigs.getByName("debug")

        }    // --- Firebase (Only Messaging for push notifications) ---

    }    implementation(platform(libs.firebase.bom))

        implementation(libs.firebase.messaging)

    compileOptions {    implementation("com.google.firebase:firebase-messaging-ktx")

        sourceCompatibility = JavaVersion.VERSION_1_8    implementation("com.google.firebase:firebase-installations-ktx")

        targetCompatibility = JavaVersion.VERSION_1_8

    }    // --- ✅ Supabase SDK ---

        implementation("io.github.jan-tennert.supabase:postgrest-kt:2.5.2")

    kotlinOptions {    implementation("io.github.jan-tennert.supabase:storage-kt:2.5.2")

        jvmTarget = "1.8"    implementation("io.github.jan-tennert.supabase:realtime-kt:2.5.2")

        freeCompilerArgs += listOf(    implementation("io.github.jan-tennert.supabase:gotrue-kt:2.5.2")

            "-Xopt-in=kotlin.RequiresOptIn",

            "-Xopt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",    // --- Ktor & Serialization ---

            "-Xopt-in=kotlinx.serialization.ExperimentalSerializationApi"    implementation("io.ktor:ktor-client-android:2.3.5")

        )    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    }

        // --- Picovoice (Wake Word Engine) ---

    buildFeatures {    implementation("ai.picovoice:porcupine-android:3.0.2")

        buildConfig = true

        viewBinding = true    // --- Lifecycle + Coroutines ---

        dataBinding = false    implementation(libs.androidx.lifecycle.runtime.ktx)

    }

        // --- Networking ---

    packaging {    implementation(libs.retrofit)

        resources {    implementation(libs.retrofit.converter.gson)

            excludes += "/META-INF/{AL2.0,LGPL2.1}"

            excludes += "/META-INF/INDEX.LIST"    // --- Location & Maps ---

            excludes += "/META-INF/DEPENDENCIES"    implementation(libs.play.services.location)

        }    implementation(libs.play.services.maps)

    }}

    
    lint {
        disable += "MissingTranslation"
        disable += "VectorPath"
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    // Core Android dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    
    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    
    // Serialization
    implementation(libs.kotlinx.serialization.json)
    
    // Supabase
    implementation(libs.supabase.postgrest.kt)
    implementation(libs.supabase.gotrue.kt)
    implementation(libs.supabase.storage.kt)
    implementation(libs.supabase.realtime.kt)
    
    // Ktor (for Supabase)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)
    
    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging.ktx)
    implementation(libs.firebase.analytics.ktx)
    implementation(libs.firebase.crashlytics.ktx)
    
    // Google Play Services
    implementation(libs.play.services.location)
    implementation(libs.play.services.maps)
    
    // Picovoice
    implementation(libs.picovoice.android)
    implementation(libs.porcupine.android)
    implementation(libs.rhino.android)
    
    // Network & HTTP
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    
    // Image Loading
    implementation(libs.glide)
    kapt(libs.glide.compiler)
    
    // Permissions
    implementation(libs.permissionx)
    
    // Date & Time
    implementation(libs.threetenabp)
    
    // Logging
    implementation(libs.timber)
    
    // UI Components
    implementation(libs.lottie)
    implementation(libs.swiperefreshlayout)
    
    // Security
    implementation(libs.androidx.security.crypto)
    
    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.truth)
    
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.runner)
}

// Google Services plugin configuration
apply(plugin = "com.google.gms.google-services")