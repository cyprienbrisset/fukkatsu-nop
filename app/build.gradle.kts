import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // AGP 9.x has built-in Kotlin support, so org.jetbrains.kotlin.android is intentionally not applied.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.cyprienbrisset.fukkatsunop"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.cyprienbrisset.fukkatsunop"
        minSdk = 28
        targetSdk = 37
        val major = 1
        val minor = 0
        val build = 18
        versionCode = build
        versionName = "$major.$minor.$build"

        val localProps = Properties()
        val localPropsFile = rootProject.file("local.properties")
        if (localPropsFile.exists()) localProps.load(localPropsFile.inputStream())
        buildConfigField("String", "GOOGLE_CLIENT_ID",
            "\"${localProps["GOOGLE_CLIENT_ID"] ?: ""}\"")
        buildConfigField("String", "GOOGLE_CLIENT_SECRET",
            "\"${localProps["GOOGLE_CLIENT_SECRET"] ?: ""}\"")
        buildConfigField("String", "GOOGLE_ANDROID_CLIENT_ID",
            "\"${localProps["GOOGLE_ANDROID_CLIENT_ID"] ?: ""}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val localProps = Properties()
            val localPropsFile = rootProject.file("local.properties")
            if (localPropsFile.exists()) localProps.load(localPropsFile.inputStream())
            storeFile = file(localProps["KEYSTORE_PATH"] as String)
            storePassword = localProps["KEYSTORE_PASSWORD"] as String
            keyAlias = localProps["KEY_ALIAS"] as String
            keyPassword = localProps["KEY_PASSWORD"] as String
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            optimization {
                enable = false
            }
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.timber)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.mlkit.face.detection)
    implementation(libs.jmdns)
    implementation(libs.bcprov)
    implementation(libs.gplayapi)
    implementation(libs.zxing.core)
    implementation("com.alphacephei:vosk-android:0.3.47")
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}

// Deploy + launch in one command:  ./gradlew deployDebug
tasks.register("deployDebug") {
    dependsOn("assembleDebug")
    val apk = layout.buildDirectory.file("outputs/apk/debug/app-debug.apk")
    doLast {
        val adb = "${System.getenv("HOME")}/Library/Android/sdk/platform-tools/adb"
        fun run(vararg cmd: String) = ProcessBuilder(*cmd).inheritIO().start().waitFor()
        check(run(adb, "install", "-r", apk.get().asFile.absolutePath) == 0) { "adb install failed" }
        run(adb, "shell", "am", "start",
            "-n", "com.cyprienbrisset.fukkatsunop/.MainActivity",
            "-a", "android.intent.action.MAIN",
            "-c", "android.intent.category.LAUNCHER")
    }
}
