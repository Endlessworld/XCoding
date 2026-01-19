pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://packages.jetbrains.team/maven/p/ki/simple/maven")
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://packages.jetbrains.team/maven/p/ki/simple/maven")
    }
}

rootProject.name = "ai-agents-multiplatform"
include(":library")