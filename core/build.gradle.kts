plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    jvmToolchain(17)

    jvm()

    @Suppress("UnstableApiUsage")
    android {
        namespace = "org.openballistics.core"
        compileSdk = 35
        minSdk = 26
    }

    sourceSets {
        commonMain.dependencies {
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
