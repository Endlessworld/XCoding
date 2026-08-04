pluginManagement {
    repositories {
        mavenLocal()
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
        // Local Maven repository
        maven("file:///D:/Program Files/repository")
        // Maven Central Snapshots for ACP SDK
        maven("https://central.sonatype.com/repository/maven-snapshots/") {
            mavenContent {
                snapshotsOnly()
            }
        }
        maven("https://packages.jetbrains.team/maven/p/ki/simple/maven")
    }
}

rootProject.name = "ai-agents"
include(":library")
include(":tui")
include(":app")

//includeBuild("E:/local-github/tamboui")