import java.io.FileInputStream
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
}

// ── Release signing ─────────────────────────────────────────────────────────
// Credentials are read from `keystore.properties` at the project root, which is
// gitignored and never committed. If the file is absent (fresh clone / CI with
// no secrets), the release build falls back to DEBUG signing so the project
// still opens and `assembleRelease` still produces an installable — though NOT
// distributable — APK. See RELEASE_GUIDE.md for setup.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val hasReleaseKeystore = keystorePropertiesFile.exists()
val keystoreProperties = Properties().apply {
    if (hasReleaseKeystore) FileInputStream(keystorePropertiesFile).use { load(it) }
}

android {
    namespace = "com.example.hunterxmusic"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.example.hunterxmusic"
        minSdk = 24
        targetSdk = 36
        versionCode = 16
        versionName = "10.2.0"
        // The local AI server address. Debug points at the Android emulator
        // loopback; before shipping a release build, set this to your hosted
        // server (e.g. https://your-domain.com/) — otherwise AI chat can only
        // reach the emulator's localhost.
        buildConfigField("String", "AI_SERVER_URL", "\"https://cyrosonic.com/\"")
    }

    signingConfigs {
        // Only declared when keystore.properties is present, so the module
        // still configures cleanly on a machine without the signing secrets.
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Real release key when creds exist; otherwise debug key so the
            // build still succeeds (that APK is for local testing only).
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = true
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // Media3 Audio Engine
  implementation("androidx.media3:media3-exoplayer:1.5.1")
  implementation("androidx.media3:media3-session:1.5.1")
  implementation("androidx.media3:media3-datasource:1.5.1")
  implementation("androidx.media3:media3-datasource-okhttp:1.5.1")
  implementation("androidx.media3:media3-database:1.5.1")

  // Room Local Cache Database
  implementation("androidx.room:room-runtime:2.7.1")
  implementation("androidx.room:room-ktx:2.7.1")
  ksp("androidx.room:room-compiler:2.7.1")

  // Retrofit + OkHttp + Gson
  implementation("com.squareup.retrofit2:retrofit:2.9.0")
  implementation("com.squareup.retrofit2:converter-gson:2.9.0")
  implementation("com.squareup.okhttp3:okhttp:4.12.0")

  // Coil Image Loader
  implementation("io.coil-kt:coil-compose:2.5.0")

  // Material Icons
  implementation("androidx.compose.material:material-icons-core")
  implementation("androidx.compose.material:material-icons-extended")

  // YouTube Stream Extractor (local deobfuscation and signature decryption)
  implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.25.2")

  // AndroidX Palette & Splashscreen for dynamic theme & animated entrance
  implementation("androidx.palette:palette-ktx:1.0.0")
  implementation("androidx.core:core-splashscreen:1.0.1")

  // Baseline Profile: ahead-of-time compiled hot paths for a faster cold start
  implementation(libs.androidx.profileinstaller)
}
