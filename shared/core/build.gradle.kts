plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
}
kotlin {
    androidTarget()
    jvm("desktop")
    jvmToolchain(17)
    sourceSets {
        commonMain.dependencies { implementation(libs.serialization) }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}
android { namespace = "com.focusflow.core"; compileSdk = 36; defaultConfig { minSdk = 26 } }
