pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "FocusFlow"
include(":androidApp", ":desktopApp", ":shared:core", ":shared:database", ":shared:designsystem")
include(":shared:feature:tasks")
