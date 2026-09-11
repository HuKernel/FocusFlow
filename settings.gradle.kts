pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "FocusFlow"
include(":androidApp", ":desktopApp", ":server", ":shared:core", ":shared:database", ":shared:designsystem")
include(":shared:sync")
include(":shared:network")
include(":shared:feature:tasks")
include(":shared:feature:focus")
