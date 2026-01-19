import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)

    alias(libs.plugins.vanniktech.mavenPublish)
    id("org.jetbrains.compose") version "1.6.10"
    id("org.jetbrains.kotlin.kapt")
}

group = "io.github.kotlin"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    jvm("jvm") {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                }
            }
        }

        // 包含 Java 源文件
        withJava()

        // 配置 kapt 注解处理器
        kapt {
            correctErrorTypes = true
        }
    }

    sourceSets {
        commonMain.dependencies {
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(compose.material3)
            implementation("org.jetbrains.compose.material:material-icons-extended:1.6.10")

            // Spring Framework
            implementation(libs.spring.core)
            implementation(libs.spring.context)

            // Spring AI
            implementation(libs.spring.ai.openai)

            // Reactor
            implementation(libs.reactor.core)

            // Jackson
            implementation(libs.jackson.databind)

            // Alibaba AI
            implementation(libs.spring.ai.alibaba.graph)
            implementation(libs.spring.ai.alibaba.agent)

            // Utilities
            implementation(libs.hutool.all)
            implementation(libs.lombok)
            implementation(libs.jna)
            implementation(libs.lanterna)
        }
    }
}

kapt {
    correctErrorTypes = true
}

tasks.register<JavaExec>("run") {
    group = "application"
    mainClass.set("com.xr21.ai.agent.gui.ChatApplicationKt")
    classpath = configurations["runtimeClasspath"] +
        files("${layout.buildDirectory.get().asFile}/classes/kotlin/jvm/main") +
        files("${layout.buildDirectory.get().asFile}/classes/java/main")
    workingDir = projectDir
}


mavenPublishing {
    publishToMavenCentral()

    signAllPublications()

    coordinates(group.toString(), "library", version.toString())

    pom {
        name = "My library"
        description = "A library."
        inceptionYear = "2024"
        url = "https://github.com/kotlin/multiplatform-library-template/"
        licenses {
            license {
                name = "XXX"
                url = "YYY"
                distribution = "ZZZ"
            }
        }
        developers {
            developer {
                id = "XXX"
                name = "YYY"
                url = "ZZZ"
            }
        }
        scm {
            url = "XXX"
            connection = "YYY"
            developerConnection = "ZZZ"
        }
    }
}
