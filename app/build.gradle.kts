import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
}

val keystoreProperties = Properties()
val keystoreFileExists = rootProject.file("keystore.properties").exists()
if (keystoreFileExists) {
    keystoreProperties.load(rootProject.file("keystore.properties").inputStream())
}

android {
    compileSdk = 36

    defaultConfig {
        applicationId = "com.github.libretube"
        minSdk = 26
        targetSdk = 36
        versionCode = 72
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resValue("string", "app_name", "HyperTube")
    }

    signingConfigs {
        create("debugConfig") {
            storeFile = file("${rootDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (keystoreFileExists) {
            create("release") {
                storeFile = keystoreProperties["storeFile"]?.let { file(it as String) }
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")?.takeIf { it.storeFile != null }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        getByName("debug") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debugConfig")
            resValue("string", "app_name", "HyperTube")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
            javaParameters = true
        }
    }

    packaging {
        jniLibs.excludes.add("lib/armeabi-v7a/*_neon.so")
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    namespace = "com.github.libretube"
}

dependencies {
    /* Android Core & Compose */
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    /* Lifecycle */
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.runtime.compose)

    /* Media3 ExoPlayer (DASH, HLS, Progressive) */
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.session)

    /* Networking & Serialization */
    implementation(libs.square.retrofit)
    implementation(libs.logging.interceptor)
    implementation(libs.kotlinx.serialization)
    implementation(libs.converter.kotlinx.serialization)

    /* NewPipe Extractor */
    implementation(libs.newpipeextractor)

    /* Coil Compose Image Loading */
    implementation(libs.coil.compose)

    /* Testing */
    testImplementation(libs.junit)
}
