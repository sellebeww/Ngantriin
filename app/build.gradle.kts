import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Kredensial ada di local.properties (tidak masuk git) atau environment,
// jangan ditaruh di source. URL boleh kosong, nanti aplikasi jalan di mode
// demo dengan backend in-memory bawaan.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun secret(key: String): String =
    (localProperties.getProperty(key) ?: System.getenv(key) ?: "").trim()

android {
    namespace = "org.umn.ngantriin"
    // Compose 1.12 dan core-ktx 1.19 sama-sama butuh compile ke API 37.
    // Dikunci di rilis minor .0 karena AGP 9.1.1 belum kenal 37.1 dan
    // memberi peringatan kalau compile ke level minor yang tidak dikenalnya.
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "org.umn.ngantriin"
        minSdk = 27
        targetSdk = 36
        // CI bisa mengubah nilai ini tanpa edit file:
        // ./gradlew assembleRelease -PversionCode=7 -PversionName=1.1.0
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("versionName") as String?) ?: "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL", "\"${secret("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${secret("SUPABASE_ANON_KEY")}\"")
    }

    // Signing rilis diambil dari local.properties atau environment, bukan
    // dari repo. Kalau keempat nilainya tidak lengkap, build rilis dibiarkan
    // unsigned (assembleRelease tetap jalan, tinggal di-sign manual).
    val releaseKeystore = secret("RELEASE_KEYSTORE_FILE")
    val hasReleaseSigning = releaseKeystore.isNotEmpty() &&
        secret("RELEASE_KEYSTORE_PASSWORD").isNotEmpty() &&
        secret("RELEASE_KEY_ALIAS").isNotEmpty() &&
        secret("RELEASE_KEY_PASSWORD").isNotEmpty()

    if (hasReleaseSigning) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(releaseKeystore)
                storePassword = secret("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = secret("RELEASE_KEY_ALIAS")
                keyPassword = secret("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            // ErrorMapper melakukan log sebelum memetakan error. Tanpa ini stub
            // android.util.Log melempar exception dan semua tes mapping gagal karena alasan yang salah.
            isReturnDefaultValues = true
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Ekspor skema Room supaya migrasi mudah ditinjau di version control.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    // --- AndroidX core -------------------------------------------------------
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    // Ganti bahasa per aplikasi: AppCompatDelegate bisa dipakai tanpa
    // MainActivity mewarisi AppCompatActivity, cukup library dan wiring di
    // manifest di bawah supaya pilihan bahasa tersimpan setelah restart.
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // --- Compose -------------------------------------------------------------
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // --- Persistence ---------------------------------------------------------
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    // --- Background work -----------------------------------------------------
    implementation(libs.androidx.work.runtime.ktx)

    // --- Kotlin --------------------------------------------------------------
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    // --- Supabase ------------------------------------------------------------
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.storage)
    implementation(libs.ktor.client.okhttp)

    // --- Firebase / location -------------------------------------------------
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.play.services.location)

    // --- Images --------------------------------------------------------------
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.svg)

    // --- Test ----------------------------------------------------------------
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Firebase opsional saat build: taruh google-services.json di app/
// dan plugin akan memakainya. Tanpa file itu aplikasi tetap bisa
// dibuild dan jalan, NotificationHelper hanya memakai notifikasi lokal.
if (rootProject.file("app/google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}
