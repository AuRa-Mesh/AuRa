import com.android.build.api.variant.ApplicationVariant
import com.android.build.api.variant.impl.VariantOutputImpl
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp") version "2.3.2"
}

// Подпись release: скопируйте keystore.properties.example → keystore.properties в корне проекта
// (файл в .gitignore). Пути storeFile — относительно корня репозитория.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}
val localPropertiesFile = rootProject.file("local.properties")
val localProperties = Properties()
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}
val profileQrHmac = localProperties
    .getProperty("AURA_PROFILE_QR_HMAC")
    ?: "dev-aura-profile-qr-hmac-change-with-deploy"

android {
    namespace = "com.example.aura"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.aura"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "PROFILE_QR_HMAC_SECRET", "\"${profileQrHmac.replace("\\", "\\\\").replace("\"", "\\\"")}\"")

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    androidResources {
        // Только английский и русский (без прочих локалей из зависимостей).
        localeFilters += listOf("en", "ru")
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    signingConfigs {
        create("release") {
            if (!keystorePropertiesFile.exists()) return@create
            val storePath = keystoreProperties.getProperty("storeFile")?.trim().orEmpty()
            val storePwd = keystoreProperties.getProperty("storePassword")
            val alias = keystoreProperties.getProperty("keyAlias")?.trim().orEmpty()
            val keyPwd = keystoreProperties.getProperty("keyPassword")
            if (storePath.isEmpty() || storePwd == null || alias.isEmpty() || keyPwd == null) return@create
            val ks = rootProject.file(storePath)
            if (!ks.isFile) return@create
            storeFile = ks
            storePassword = storePwd
            keyAlias = alias
            keyPassword = keyPwd
        }
    }

    buildTypes {
        release {
            // R8: обфускация имён + удаление неиспользуемого кода; правила в proguard-rules.pro
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            val releaseCfg = signingConfigs.findByName("release")
            if (releaseCfg?.storeFile != null) {
                signingConfig = releaseCfg
            }
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

    lint {
        // Prefer stringResource / LocalActivity over time; thousands of call sites.
        disable += "LocalContextGetResourceValueCall"
        disable += "ContextCastToActivity"
    }
}

androidComponents {
    onVariants(selector().all()) { variant ->
        val appVariant = variant as? ApplicationVariant ?: return@onVariants
        val buildTypeName = appVariant.buildType ?: "unknown"
        appVariant.outputs.forEach { output ->
            val outImpl = output as? VariantOutputImpl ?: return@forEach
            outImpl.outputFileName.set(
                output.versionName.map { versionName ->
                    "Aura-Mesh-${versionName}-${buildTypeName}.apk"
                },
            )
        }
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("org.maplibre.gl:android-sdk:10.3.7")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.lifecycle:lifecycle-process:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")

    implementation("androidx.security:security-crypto:1.0.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    val room = "2.7.1"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")

    val coil = "2.7.0"
    implementation("io.coil-kt:coil-compose:$coil")
    implementation("io.coil-kt:coil-gif:$coil")

    val cameraX = "1.4.2"
    implementation("androidx.camera:camera-core:$cameraX")
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
    implementation("androidx.camera:camera-view:$cameraX")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.google.zxing:core:3.5.3")

    implementation("com.github.mik3y:usb-serial-for-android:3.10.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}