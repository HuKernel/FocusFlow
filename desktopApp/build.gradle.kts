plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":shared:designsystem"))
    implementation(project(":shared:database"))
    implementation(compose.desktop.currentOs)
}
compose.desktop { application { mainClass = "com.focusflow.desktop.MainKt" } }
