plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}
android {
    namespace = "com.focusflow.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.focusflow.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 28
        versionName = "1.4.9"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":shared:feature:tasks"))
    implementation(project(":shared:designsystem"))
    implementation(project(":shared:network"))
    implementation(libs.glance.appwidget)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(project(":shared:database"))
    implementation(libs.activity.compose)
}
