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
            api(project(":shared:feature:focus"))
            api(project(":shared:sync"))
            implementation(project(":shared:designsystem"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.animation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(libs.lifecycle.viewmodel)
            implementation(libs.lifecycle.runtime)
        }
        commonTest.dependencies { implementation(kotlin("test")) }
        val desktopMain by getting { dependencies { implementation(libs.coroutines.swing) } }
        val desktopTest by getting {
            dependencies {
                implementation(compose.desktop.uiTestJUnit4)
                implementation(compose.desktop.currentOs)
            }
        }
    }
}
android { namespace = "com.focusflow.tasks"; compileSdk = 36; defaultConfig { minSdk = 26 } }
