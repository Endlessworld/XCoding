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

// Native compile tasks
tasks.named("nativeCompile") {
    dependsOn("classes", "fatJar")
    group = "build"
    description = "Compiles application to a native executable"
}

tasks.named("nativeRun") {
    dependsOn("nativeCompile")
    group = "application"
    description = "Runs native executable"
}

// 复制配置文件到构建目录
tasks.register<Copy>("copyNativeConfigs") {
    from(projectDir.resolve("native-reflect-config.json"))
    from(projectDir.resolve("native-resource-config.json"))
    into(layout.buildDirectory.dir("libs"))
    dependsOn("fatJar")
}

// 多平台 Docker 构建任务
tasks.register<Exec>("buildLinuxX64Docker") {
    group = "build"
    description = "Build native executable for Linux x64 using Docker"
    workingDir = projectDir
    commandLine("docker", "run", "--rm",
        "-v", "${projectDir}:/app",
        "-w", "/app",
        "ghcr.io/graalvm/native-image-community:24-linux",
        "sh", "-c",
        "./gradlew :library:fatJar :library:copyNativeConfigs && " +
        "native-image " +
        "-jar library/build/libs/XAgent-0.0.1-all.jar " +
        "-H:ReflectionConfigurationFiles=/app/library/build/libs/native-reflect-config.json " +
        "-H:ResourceConfigurationFiles=/app/library/build/libs/native-resource-config.json " +
        "-H:Name=XAgent-linux-x64 " +
        "--no-fallback -H:+UnlockExperimentalVMOptions -O2 --gc=serial " +
        "--initialize-at-build-time=org.slf4j,ch.qos.logback,org.slf4j.LoggerFactory")
    outputs.file(layout.buildDirectory.file("XAgent-linux-x64"))
}

tasks.register<Exec>("buildLinuxArm64Docker") {
    group = "build"
    description = "Build native executable for Linux ARM64 using Docker"
    workingDir = projectDir
    commandLine("docker", "run", "--rm",
        "-v", "${projectDir}:/app",
        "-w", "/app",
        "ghcr.io/graalvm/native-image-community:24-linux-arm64",
        "sh", "-c",
        "./gradlew :library:fatJar :library:copyNativeConfigs && " +
        "native-image " +
        "-jar library/build/libs/XAgent-0.0.1-all.jar " +
        "-H:ReflectionConfigurationFiles=/app/library/build/libs/native-reflect-config.json " +
        "-H:ResourceConfigurationFiles=/app/library/build/libs/native-resource-config.json " +
        "-H:Name=XAgent-linux-arm64 " +
        "--no-fallback -H:+UnlockExperimentalVMOptions -O2 --gc=serial " +
        "--initialize-at-build-time=org.slf4j,ch.qos.logback,org.slf4j.LoggerFactory")
    outputs.file(layout.buildDirectory.file("XAgent-linux-arm64"))
}

tasks.register<Exec>("buildDarwinX64Docker") {
    group = "build"
    description = "Build native executable for macOS x64 using Docker"
    workingDir = projectDir
    commandLine("docker", "run", "--rm",
        "-v", "${projectDir}:/app",
        "-w", "/app",
        "ghcr.io/graalvm/native-image-community:24-darwin",
        "sh", "-c",
        "./gradlew :library:fatJar :library:copyNativeConfigs && " +
        "native-image " +
        "-jar library/build/libs/XAgent-0.0.1-all.jar " +
        "-H:ReflectionConfigurationFiles=/app/library/build/libs/native-reflect-config.json " +
        "-H:ResourceConfigurationFiles=/app/library/build/libs/native-resource-config.json " +
        "-H:Name=XAgent-macos-x64 " +
        "--no-fallback -H:+UnlockExperimentalVMOptions -O2 --gc=serial --static " +
        "--initialize-at-build-time=org.slf4j,ch.qos.logback,org.slf4j.LoggerFactory")
    outputs.file(layout.buildDirectory.file("XAgent-macos-x64"))
}

tasks.register<Exec>("buildDarwinArm64Docker") {
    group = "build"
    description = "Build native executable for macOS ARM64 using Docker"
    workingDir = projectDir
    commandLine("docker", "run", "--rm",
        "-v", "${projectDir}:/app",
        "-w", "/app",
        "ghcr.io/graalvm/native-image-community:24-darwin-aarch64",
        "sh", "-c",
        "./gradlew :library:fatJar :library:copyNativeConfigs && " +
        "native-image " +
        "-jar library/build/libs/XAgent-0.0.1-all.jar " +
        "-H:ReflectionConfigurationFiles=/app/library/build/libs/native-reflect-config.json " +
        "-H:ResourceConfigurationFiles=/app/library/build/libs/native-resource-config.json " +
        "-H:Name=XAgent-macos-arm64 " +
        "--no-fallback -H:+UnlockExperimentalVMOptions -O2 --gc=serial --static " +
        "--initialize-at-build-time=org.slf4j,ch.qos.logback,org.slf4j.LoggerFactory")
    outputs.file(layout.buildDirectory.file("XAgent-macos-arm64"))
}

tasks.register<Exec>("buildWindowsX64Docker") {
    group = "build"
    description = "Build native executable for Windows x64 using Docker"
    workingDir = projectDir
    commandLine("docker", "run", "--rm",
        "-v", "${projectDir}:/app",
        "-w", "/app",
        "ghcr.io/graalvm/native-image-community:24-windows",
        "cmd", "/c",
        "gradlew.bat :library:fatJar :library:copyNativeConfigs && " +
        "native-image.exe " +
        "-jar library\\build\\libs\\XAgent-0.0.1-all.jar " +
        "-H:ReflectionConfigurationFiles=C:\\app\\library\\build\\libs\\native-reflect-config.json " +
        "-H:ResourceConfigurationFiles=C:\\app\\library\\build\\libs\\native-resource-config.json " +
        "-H:Name=XAgent-windows-x64 " +
        "--no-fallback -H:+UnlockExperimentalVMOptions -O2 --gc=serial")
    outputs.file(layout.buildDirectory.file("XAgent-windows-x64.exe"))
}

// 构建所有平台
tasks.register<Exec>("buildAllPlatformsDocker") {
    group = "build"
    description = "Build native executables for all platforms using Docker"
    dependsOn(
        "buildLinuxX64Docker",
        "buildLinuxArm64Docker",
        "buildDarwinX64Docker",
        "buildDarwinArm64Docker",
        "buildWindowsX64Docker"
    )
}