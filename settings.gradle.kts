pluginManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://packages.jetbrains.team/maven/p/ki/simple/maven")
    }
    plugins {
        kotlin("plugin.lombok") version "2.2.20"
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // vendored: 私有仓库才发布的 Alibaba 两个构件（jar + pom 随项目提交）
        maven {
            name = "vendor-alibaba"
            url = uri("${rootDir}/vendor-repo")
        }
        // Local Maven repository
//        maven("file:///E:/Program Files/repository")
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