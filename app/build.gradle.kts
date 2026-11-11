import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Automatically purges any leftover legacy LibreTube XML resources, MotionLayout scenes,
 * translations, unused vector drawables, fastlane metadata, or redundant extraction engines.
 */
fun purgeLegacyLibreTubeFiles(projectDir: File) {
    val rootFastlane = File(projectDir.parentFile, "fastlane")
    if (rootFastlane.exists()) {
        rootFastlane.deleteRecursively()
    }

    val assetsDir = File(projectDir, "src/main/assets")
    if (assetsDir.exists()) {
        assetsDir.deleteRecursively()
    }

    val schemasDir = File(projectDir, "schemas")
    if (schemasDir.exists()) {
        schemasDir.deleteRecursively()
    }

    val resDir = File(projectDir, "src/main/res")
    if (resDir.exists()) {
        resDir.listFiles()?.forEach { dir ->
            val name = dir.name
            if (dir.isDirectory && (
                    name.startsWith("values-") ||
                    name.startsWith("layout") ||
                    name.startsWith("menu") ||
                    name.startsWith("navigation") ||
                    name.startsWith("anim") ||
                    name.startsWith("raw") ||
                    name.startsWith("color")
                )
            ) {
                dir.deleteRecursively()
            }
        }

        val allowedValues = setOf("strings.xml", "colors.xml", "themes.xml")
        File(resDir, "values").listFiles()?.forEach { file ->
            if (file.name !in allowedValues) file.deleteRecursively()
        }

        val allowedXml = setOf("fileprovider_paths.xml", "network_security_config.xml")
        File(resDir, "xml").listFiles()?.forEach { file ->
            if (file.name !in allowedXml) file.deleteRecursively()
        }

        val allowedDrawables = setOf("ic_launcher_monochrome.xml")
        File(resDir, "drawable").listFiles()?.forEach { file ->
            if (file.name !in allowedDrawables) file.deleteRecursively()
        }

        val allowedAnyDpi = setOf("ic_launcher.xml", "ic_launcher_round.xml")
        File(resDir, "mipmap-anydpi-v26").listFiles()?.forEach { file ->
            if (file.name !in allowedAnyDpi) file.deleteRecursively()
        }

        val allowedMipmapPngs = setOf(
            "ic_launcher.png",
            "ic_launcher_foreground.png",
            "ic_launcher_round.png",
            "ic_launcher.webp",
            "ic_launcher_foreground.webp",
            "ic_launcher_round.webp"
        )
        resDir.listFiles()?.filter { it.isDirectory && it.name.startsWith("mipmap-") && it.name != "mipmap-anydpi-v26" }
            ?.forEach { mipmapDir ->
                mipmapDir.listFiles()?.forEach { file ->
                    if (file.name !in allowedMipmapPngs) file.deleteRecursively()
                }
            }
    }

    val basePkgDir = File(projectDir, "src/main/java/com/github/libretube")
    if (basePkgDir.exists()) {
        val allowedRootEntries = setOf("LibreTubeApp.kt", "api", "ui")
        basePkgDir.listFiles()?.forEach { entry ->
            if (entry.name !in allowedRootEntries) entry.deleteRecursively()
        }

        val allowedApiEntries = setOf(
            "ExternalApi.kt",
            "HyperStreamModels.kt",
            "HyperTubeRepository.kt",
            "JsonHelper.kt",
            "RetrofitInstance.kt"
        )
        File(basePkgDir, "api").listFiles()?.forEach { entry ->
            if (entry.name !in allowedApiEntries) entry.deleteRecursively()
        }

        val allowedUiEntries = setOf("activities", "components", "models", "screens", "theme")
        File(basePkgDir, "ui").listFiles()?.forEach { entry ->
            if (entry.name !in allowedUiEntries) entry.deleteRecursively()
        }
    }
}

purgeLegacyLibreTubeFiles(projectDir)

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
    }

    androidResources {
        localeFilters += listOf("en")
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

    /* Networking & Serialization (Piped REST API via Retrofit) */
    implementation(libs.square.retrofit)
    implementation(libs.logging.interceptor)
    implementation(libs.kotlinx.serialization)
    implementation(libs.converter.kotlinx.serialization)

    // Unused legacy extractor commented out per Phase 1 Architecture Purge
    // implementation(libs.newpipeextractor)

    /* Coil Compose Image Loading */
    implementation(libs.coil.compose)

    /* Testing */
    testImplementation(libs.junit)
}
