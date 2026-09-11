plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}
dependencies {
    implementation(project(":shared:core"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.coroutines)
    implementation(libs.serialization)
    implementation(libs.logback)
    runtimeOnly(libs.h2)
    runtimeOnly(libs.postgresql)
    testImplementation(libs.ktor.server.test)
    testImplementation(kotlin("test"))
    testImplementation(libs.h2)
}
application { mainClass = "com.focusflow.server.ServerKt" }
kotlin { jvmToolchain(17) }
