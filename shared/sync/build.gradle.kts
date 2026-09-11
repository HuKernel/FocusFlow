plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}
kotlin {
    androidTarget()
    jvm("desktop")
    jvmToolchain(17)
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core"))
            api(project(":shared:database"))
        }
        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(project(":server"))
            }
        }
    }
}
android { namespace = "com.focusflow.sync"; compileSdk = 36; defaultConfig { minSdk = 26 } }
