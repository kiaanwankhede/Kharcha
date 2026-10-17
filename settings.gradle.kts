pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Kharcha"

include(":core")
// Pass -PcoreOnly=true to build and test the pure-Kotlin logic without an Android SDK
// or access to Google's Maven repository.
if (providers.gradleProperty("coreOnly").orNull == "true") {
    rootProject.buildFileName = "core-only.gradle.kts"
} else {
    include(":app")
}
