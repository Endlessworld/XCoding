plugins {
    kotlin("jvm")
    alias(libs.plugins.vanniktech.mavenPublish)
    id("org.graalvm.buildtools.native")
    id("com.github.ben-manes.versions") version "0.51.0"
//    id("org.jlleitschuh.gradle.ktlint") version "12.1.0"
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

sourceSets {
    main {
        java {
            srcDirs("src/jvmMain/java")
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

dependencies {
    // Spring Framework (minimal)
    implementation(libs.spring.core)
    implementation(libs.spring.context)

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
    implementation(libs.acp.core)
    implementation(libs.acp.agent.support)
    implementation(libs.acp.websocket.jetty)

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.24")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.9.24")

    // Test dependencies
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.24")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.9.24")
}

tasks.register<JavaExec>("runAcpAgent") {
    group = "application"
    description = "Runs AcpLocalAgent with ACP protocol support"
    dependsOn("classes")
    mainClass.set("com.xr21.ai.agent.AcpLocalAgent")
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir = projectDir
}

tasks.register<JavaExec>("runAsyncAgentClient") {
    group = "application"
    description = "Runs AsyncAgentClient"
    dependsOn("classes")
    mainClass.set("com.xr21.ai.agent.AsyncAgentClient")
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir = projectDir
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

// Create a dedicated fatJar task
tasks.register<Jar>("fatJar") {
    dependsOn("classes")
    group = "build"
    description = "Builds a fat JAR with all dependencies"

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes["Main-Class"] = "com.xr21.ai.agent.AgentApplication"
    }

    from(sourceSets.main.get().output)

    from(
        configurations.runtimeClasspath.get().map {
            if (it.isDirectory) it else zipTree(it)
        }
    )

    archiveBaseName.set("XAgent")
    archiveClassifier.set("all")
}

// GraalVM Native Image Configuration
graalvmNative {
    binaries {
        named("main") {
            imageName.set("XAgent")
            mainClass.set("com.xr21.ai.agent.AgentApplication")

            // 构建参数优化
            buildArgs.addAll(
                "--no-fallback",
                "-H:+UnlockExperimentalVMOptions",
                "-H:+ReportExceptionStackTraces",
                "--enable-url-protocols=http,https",
                "-O2",
                "--gc=serial",
                "--initialize-at-build-time=org.slf4j",
                "--initialize-at-build-time=ch.qos.logback",
                "--initialize-at-build-time=org.slf4j.LoggerFactory",
                "--initialize-at-build-time=ch.qos.logback.classic.Logger",
                "--initialize-at-build-time=ch.qos.logback.core.status.NopStatusListener",
                "--initialize-at-build-time=ch.qos.logback.core.rolling.TimeBasedRollingPolicy",
                "--initialize-at-build-time=ch.qos.logback.classic.filter.LevelFilter",
                "--initialize-at-build-time=ch.qos.logback.classic.filter.ThresholdFilter",
                "--initialize-at-build-time=ch.qos.logback.core.ConsoleAppender",
                "--initialize-at-build-time=ch.qos.logback.core.rolling.RollingFileAppender",
                "--initialize-at-build-time=ch.qos.logback.classic.encoder.PatternLayoutEncoder",
                "-H:ReflectionConfigurationFiles=${projectDir}/native-reflect-config.json",
                "-H:ResourceConfigurationFiles=${projectDir}/native-resource-config.json",
                "-H:EnableURLProtocols=all",
                "--initialize-at-build-time=org.springframework.aot.hint.ClasspathHint",
                "--initialize-at-build-time=org.springframework.aot.hint.TypeHint",
                // SLF4J Service Provider
                "--initialize-at-build-time=org.slf4j.simple.SimpleLogger",
                "--initialize-at-build-time=org.slf4j.ext.XLogger",
                // MCP Jackson Service Provider
                "--initialize-at-build-time=io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapperSupplier",
                "--initialize-at-build-time=ch.qos.logback.classic.spi.LogbackServiceProvider",
                "-H:+AllowIncompleteClasspath",
            )
        }
    }

    // 禁用工具链检测，使用当前环境
    toolchainDetection.set(false)
}
