plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}
kotlin {
    androidTarget()
    jvm("desktop")
    jvmToolchain(17)
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:database"))
            implementation(project(":shared:designsystem"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.animation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            api(libs.lifecycle.viewmodel)
            implementation(libs.lifecycle.runtime)
        }
        val desktopMain by getting { dependencies { implementation(libs.coroutines.swing) } }
    }
}
android { namespace = "com.focusflow.focus"; compileSdk = 36; defaultConfig { minSdk = 26 } }
