import java.util.Properties
import java.io.FileInputStream

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

// Load signing properties from .env file
val signingProps = Properties()
val envFile = file("${rootDir}/.env")
if (envFile.exists()) {
    signingProps.load(FileInputStream(envFile))
}

// Gunakan satu password yang konsisten untuk menghindari error padding pada PKCS12
val RELEASE_PASSWORD = signingProps.getProperty("STORE_PASSWORD") ?: "b7a8f9c2d3e4f5a6b7c8d9e0f1a2b3c4"
val RELEASE_ALIAS = signingProps.getProperty("KEY_ALIAS") ?: "upload"

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.example"
    minSdk = 24
    targetSdk = 36
    versionCode = 2
    versionName = "1.1"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    ndk {
      abiFilters += "arm64-v8a"
    }
  }

  signingConfigs {
    create("release") {
      val keystorePath = signingProps.getProperty("KEYSTORE_PATH") ?: "my-upload-key.jks"
      storeFile = file(if (File(keystorePath).isAbsolute) keystorePath else "${rootDir}/$keystorePath")
      storePassword = RELEASE_PASSWORD
      keyAlias = RELEASE_ALIAS
      keyPassword = RELEASE_PASSWORD
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  packaging {
    jniLibs {
      useLegacyPackaging = true
    }
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Task untuk membuat Keystore secara otomatis (HANYA JIKA BELUM ADA)
val generateReleaseKeystore = tasks.register<Exec>("generateReleaseKeystore") {
    val keystoreFile = file("${rootDir}/my-upload-key.jks")
    
    // Syarat: Tugas ini hanya dijalankan jika file JKS belum ada.
    // Sekali dibuat, JKS ini tidak akan pernah berubah atau dibuat ulang secara otomatis.
    onlyIf { !keystoreFile.exists() }

    val javaHome = System.getProperty("java.home")
    val keytoolPath = if (System.getProperty("os.name").lowercase().contains("win")) {
        "$javaHome/bin/keytool.exe"
    } else {
        "$javaHome/bin/keytool"
    }
    
    executable(keytoolPath)
    args(
        "-genkey", "-v",
        "-keystore", keystoreFile.absolutePath,
        "-alias", RELEASE_ALIAS,
        "-keyalg", "RSA",
        "-keysize", "2048",
        "-validity", "10000",
        "-storepass", RELEASE_PASSWORD,
        "-keypass", RELEASE_PASSWORD,
        "-dname", "CN=My App, OU=Dev, O=My Company, L=Jakarta, S=Jakarta, C=ID"
    )
    
    doLast {
        println("Keystore baru berhasil dibuat di: ${keystoreFile.absolutePath}")
        println("PERINGATAN: Simpan file ini baik-baik. Jangan sampai hilang atau berubah!")
    }
}

// Pastikan keystore dibuat sebelum proses signing divalidasi
tasks.matching { it.name.startsWith("validateSigning") || it.name.startsWith("package") }.configureEach {
    dependsOn(generateReleaseKeystore)
}

secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.converter.moshi)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.retrofit)
  implementation(libs.mediapipe.tasks.genai)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen.ksp)
}
