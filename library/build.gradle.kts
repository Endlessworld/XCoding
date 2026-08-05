import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    alias(libs.plugins.vanniktech.mavenPublish)
}

group = "com.xr21"
version = "0.0.1"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    jvmToolchain(17)
}

// ==================== 反射参数名支持 ====================
// 确保编译时写入方法参数名，使反射能获取真实参数名而非 arg0/arg1
// Java 使用 -parameters，Kotlin 使用 -java-parameters（等价）
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters")
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-java-parameters")
    }
}

sourceSets {
    main {
        java {
            srcDirs("src/jvmMain/java", "src/jvmMain/kotlin")
        }
        kotlin {
            srcDirs("src/jvmMain/kotlin")
        }
        resources {
            srcDirs("src/jvmMain/resources")
        }
    }
    test {
        java {
            srcDirs("src/jvmTest/java", "src/jvmTest/kotlin")
        }
        resources {
            srcDirs("src/jvmTest/resources")
        }
    }
}

tasks.named<Jar>("jar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

dependencies {
    // Spring AI
    implementation(libs.spring.ai.openai)
    implementation(libs.spring.ai.mcp.client)

    // Reactor (required by Spring AI)
    implementation(libs.reactor.core)

    // Jackson
    implementation(libs.jackson.databind)

    // Alibaba AI
    implementation(libs.spring.ai.alibaba.graph)
    implementation(libs.spring.ai.alibaba.agent)

    // Utilities
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // ACP SDK - Agent Client Protocol
    implementation(libs.acp.jvm)
    implementation(libs.acp.model.jvm)
    implementation(libs.acp.ktor.jvm)
    implementation(libs.acp.ktor.client.jvm)
    implementation(libs.acp.ktor.server.jvm)
// Source: https://mvnrepository.com/artifact/com.agentclientprotocol/acp-ktor-server
    implementation("com.agentclientprotocol:acp-ktor-server:0.23.0")
    // Ktor HTTP Client Engine (required by acp-ktor-jvm at runtime)
    implementation(libs.ktor.client.okhttp)

    // Ktor HTTP Server Engine (required by acp-ktor-server-jvm for WebSocket server mode)
    implementation(libs.ktor.server.netty)

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.7")

    // Jsoup - HTML parsing (used by WebSearchTool for DuckDuckGo search)
    implementation(libs.jsoup)

    // Groovy - Groovy script execution (GroovyScriptTool binds a `tools` object for MCP tool orchestration)
    implementation("org.codehaus.groovy:groovy:3.0.25")
    // Test dependencies
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}

mavenPublishing {
    publishToMavenCentral()

    signAllPublications()

    coordinates(group.toString(), "XAgent", version.toString())

    pom {
        name = "AI Agents"
        description = "A multiplatform AI agents library with Spring AI and Alibaba AI integration"
        inceptionYear = "2024"
        url = "https://github.com/your-username/ai-agents"
        licenses {
            license {
                name = "Apache-2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0"
                distribution = "repo"
            }
        }
        developers {
            developer {
                id = "xr21"
                name = "XR21 Team"
                url = "https://github.com/your-username"
            }
        }
        scm {
            url = "https://github.com/your-username/ai-agents"
            connection = "scm:git:git://github.com/your-username/ai-agents.git"
            developerConnection = "scm:git:ssh://github.com/your-username/ai-agents.git"
        }
    }
}


