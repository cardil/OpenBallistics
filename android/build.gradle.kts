plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose.compiler)
}

android {
    namespace = "org.openballistics.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.openballistics"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(project(":core"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.runtime.compose)

    debugImplementation(libs.compose.ui.tooling)
}

val sdkHome: String = System.getenv("ANDROID_HOME")
    ?: rootProject.file("local.properties")
        .takeIf { it.exists() }
        ?.readLines()
        ?.firstOrNull { it.startsWith("sdk.dir=") }
        ?.substringAfter("sdk.dir=")
    ?: error("Set ANDROID_HOME or sdk.dir in local.properties")

tasks.register<Exec>("run") {
    group = "application"
    description = "Install debug APK and launch the app on connected device"
    dependsOn("installDebug")
    commandLine(
        "$sdkHome/platform-tools/adb",
        "shell", "am", "start", "-n", "org.openballistics/.android.MainActivity",
    )
}
