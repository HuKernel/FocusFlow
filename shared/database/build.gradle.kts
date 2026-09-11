plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}
kotlin {
    androidTarget()
    jvm("desktop")
    jvmToolchain(17)
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core"))
            api(libs.room.runtime)
            implementation(libs.sqlite)
            api(libs.coroutines)
        }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}
android { namespace = "com.focusflow.database"; compileSdk = 36; defaultConfig { minSdk = 26 } }
dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspDesktop", libs.room.compiler)
}
room { schemaDirectory("$projectDir/schemas") }
