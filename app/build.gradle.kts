import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    // Reads app/google-services.json (generates R.string.default_web_client_id)
    id("com.google.gms.google-services")
}

// Release signing. Reads keystore.properties in the project root (never committed):
//   storeFile=/absolute/path/to/scrub-upload.jks
//   storePassword=...
//   keyAlias=scrub-upload
//   keyPassword=...
// Without the file, release builds are left unsigned.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.scrub.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.scrub.app"
        minSdk = 30          // Android 11+: needed for the system trash / delete dialogs
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystoreProperties.getProperty("storeFile") != null) {
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
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }

    androidResources {
        noCompress += "tflite"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.05.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.ui:ui-tooling-preview")

    implementation("androidx.core:core-ktx:1.16.0")
    // Launch screen (Chalk / dark background + icon), backported to Android 11
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

    // Image loading for the photo grids
    implementation("io.coil-kt:coil-compose:2.7.0")

    // On-device face detection (bundled model, works offline)
    implementation("com.google.mlkit:face-detection:16.1.7")

    // On-device face recognition (FaceNet model in assets/). LiteRT is the renamed TensorFlow
    // Lite runtime: same org.tensorflow.lite.Interpreter API, but its native library is built
    // for 16 KB memory pages (Play requirement). tensorflow-lite 2.16.1 is 4 KB aligned.
    // Staying on 1.x: 2.x drops 32-bit x86 and moves to the CompiledModel API.
    implementation("com.google.ai.edge.litert:litert:1.4.2")

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")

    // Accounts. Firebase Authentication is the ONLY networked component in the app.
    // Never add Firestore, Storage, Analytics, Crashlytics or any other Firebase product:
    // photos, faces.bin and the vault must never leave the phone. See CLAUDE.md.
    // BoM 34.19.0 (firebase-auth 24.2.0) is the newest built with Kotlin metadata <= 2.3, which
    // Kotlin 2.2 can read. BoM 35.x needs Kotlin 2.3+. Same for googleid 1.2.0 vs 1.2.1.
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-auth")

    // "Continue with Google" through Credential Manager (not the deprecated GoogleSignInClient)
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.2.0")
}
