// Standalone build that holds the shared Gradle configuration for every WebTools module as
// precompiled convention plugins. Included by the root settings through
// pluginManagement { includeBuild("build-logic") }.

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "build-logic"
