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

    // Jsoup - HTML parsing (used by WebSearchTool for DuckDuckGo search)
    implementation(libs.jsoup)
    // Mordant TUI
    implementation(libs.mordant)
    implementation(libs.mordant.coroutines)
    implementation(libs.mordant.markdown)
    implementation(libs.mordant.jvm.jna)
    // Test dependencies
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}

tasks.register<JavaExec>("runAcpAgent") {
    group = "application"
    description = "Runs AcpLocalAgent with ACP protocol support"
    dependsOn("classes")
    mainClass.set("com.xr21.ai.agent.AcpLocalAgent")
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir = projectDir
}


tasks.register<JavaExec>("runTui") {
    group = "application"
    description = "Runs XAgent TUI interface"
    dependsOn("classes")

    mainClass.set("com.xr21.ai.agent.tui.MainKt")
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir = projectDir
    // 默认以当前目录为工作目录启动 Agent
    args = listOf(projectDir.absolutePath)
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

// ==================== GraalVM Native Build Automation ====================

// 1. 确保基本的 jar 任务存在（打包项目类，不含依赖）
tasks.named<Jar>("jar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "com.xr21.ai.agent.AgentApplication"
    }
}

// 2. 创建 fatJar 任务（打包所有依赖）
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

// 3. 抽取 fatJar 中所有类到临时目录
val extractClassesDir = layout.buildDirectory.dir("extracted-classes")

tasks.register<Copy>("extractAllClasses") {
    dependsOn("fatJar")
    group = "graalvm"
    description = "Extract all classes from fatJar for analysis"

    val fatJar = tasks.named<Jar>("fatJar").get().archiveFile.get().asFile
    from(zipTree(fatJar)) {
        include("**/*.class")
        // 排除模块-info.class 和包-info.class
        exclude("module-info.class")
        exclude("**/package-info.class")
    }
    into(extractClassesDir)
}

// 4. 生成 --initialize-at-build-time 脚本并注入到 native-image 参数
val initBuildTimeScript = layout.buildDirectory.file("init-at-build-time.args")

tasks.register<DefaultTask>("generateInitAtBuildTime") {
    dependsOn("extractAllClasses")
    group = "graalvm"
    description = "Generate --initialize-at-build-time script from extracted classes"

    val extractedDir = extractClassesDir.get().asFile
    val outputFile = initBuildTimeScript.get().asFile

    doLast {
        println("Extracted directory: ${extractedDir.absolutePath}")
        println("Output file: ${outputFile.absolutePath}")

        // 检查提取目录是否存在
        if (!extractedDir.exists()) {
            println("ERROR: Extracted directory does not exist!")
            return@doLast
        }

        // 扫描所有类文件，提取包名.类名
        val classFiles = fileTree(extractedDir) {
            include("**/*.class")
            exclude("module-info.class")
            exclude("**/package-info.class")
        }

        println("Found ${classFiles.count()} class files")

        // 需要排除的 JVM 内部类前缀
        val jvmExcludes = setOf(
            "java.lang.invoke.", "java.lang.reflect.", "java.util.concurrent.",
            "jdk.internal.", "sun.nio.", "sun.net.",
            "com.sun.proxy.", "com.sun.tools.", "com.sun.image.", "com.sun.xml.internal."
        )

        // 需要排除的系统类加载器相关类
        val systemExcludes = setOf(
            "java.lang.ClassLoader", "java.security.SecureClassLoader", "java.net.URLClassLoader",
            "java.lang.Thread", "java.lang.System", "java.lang.Runtime", "java.lang.Class",
            "java.lang.Object", "java.lang.String", "java.lang.Integer", "java.lang.Long",
            "java.lang.Boolean", "java.lang.Double", "java.lang.Float", "java.lang.Short",
            "java.lang.Byte", "java.lang.Character", "java.lang.Number"
        )

        // 需要排除的 Hazelcast 相关类（将在运行时初始化）
        val hazelcastExcludes = setOf(
            "io.micrometer.core.instrument.binder.cache.HazelcastIMapAdapter",
            "io.micrometer.core.instrument.binder.cache.HazelcastCacheMeterBinder",
            "io.micrometer.core.instrument.binder.cache.HazelcastCacheMetrics",
        )

        val initClasses = mutableSetOf<String>()

        classFiles.forEach { file ->
            val relativePath = file.relativeTo(extractedDir).path
            val className = relativePath.replace("/", ".").replace("\\", ".").removeSuffix(".class")

            val isJvmInternal = jvmExcludes.any { className.startsWith(it) }
            val isSystemClass = systemExcludes.contains(className)
            val isHazelcastClass = hazelcastExcludes.contains(className)
            val shouldSkip = listOf("module-info", "package-info", "META-INF.versions.9").any { className.contains(it) }

            if (!isJvmInternal && !isSystemClass && !isHazelcastClass && !shouldSkip) {
                initClasses.add(className)
            }
        }

        // 写入脚本文件到 build 目录
        outputFile.parentFile?.mkdirs()
        val initArgs = initClasses.sorted().map { "--initialize-at-build-time=$it" }
        outputFile.writeText(initArgs.joinToString("\n"))
        println("Generated init-at-build-time.args with ${initClasses.size} classes")
    }
}

// ==================== GraalVM Native Build Configuration ====================

graalvmNative {
    binaries {
        named("main") {
            imageName.set("XAgent")
            mainClass.set("com.xr21.ai.agent.AgentApplication")

            // 基础构建参数
            buildArgs.add("--no-fallback")
            buildArgs.add("-H:+UnlockExperimentalVMOptions")
            buildArgs.add("-H:+ReportExceptionStackTraces")
            buildArgs.add("--enable-url-protocols=http,https")
            buildArgs.add("-O2")
            buildArgs.add("--verbose")
            buildArgs.add("--gc=serial")
            // 基础必需项
            buildArgs.add("--initialize-at-build-time=org.slf4j")
            buildArgs.add("--initialize-at-build-time=ch.qos.logback")
            buildArgs.add("--initialize-at-build-time=ch.qos.logback.classic.filter.LevelFilter")
            buildArgs.add("--initialize-at-build-time=ch.qos.logback.classic.filter.ThresholdFilter")
            buildArgs.add("--initialize-at-build-time=ch.qos.logback.classic")
            buildArgs.add("--initialize-at-build-time=ch.qos.logback.core")
            // Micrometer 相关类 - 运行时初始化
            buildArgs.add("--initialize-at-run-time=io.micrometer.core.instrument.binder.cache.HazelcastIMapAdapter")
            buildArgs.add("--initialize-at-run-time=io.micrometer.core.instrument.binder.cache.HazelcastCacheMeterBinder")
            buildArgs.add("--initialize-at-run-time=io.micrometer.core.instrument.binder.cache.HazelcastCacheMetrics")
            buildArgs.add("-H:EnableURLProtocols=all")
            buildArgs.add("-H:+AllowIncompleteClasspath")
            // logback 配置
            buildArgs.add("-Dlogback.statusListener=ch.qos.logback.core.status.NopStatusListener")
            buildArgs.add("-Dlogback.console=disabled")
            buildArgs.add("-Dlogback.configurationFile=classpath:logback-native-simple.xml")
            // 指定 reachability metadata 目录
            buildArgs.add("-H:ConfigurationFileDirectories=${project.projectDir}/src/jvmMain/resources/META-INF/native-image")
            // 使用 provider 延迟读取自动生成的初始化参数
            val initArgsProvider = provider {
                val scriptFile = layout.buildDirectory.file("init-at-build-time.args").get().asFile
                if (scriptFile.exists()) {
                    scriptFile.readLines().filter { it.startsWith("--initialize-at-build-time=") }
                } else {
                    emptyList()
                }
            }
            buildArgs.addAll(initArgsProvider.get())

            println("Configuration file directory: ${project.projectDir}/src/jvmMain/resources/META-INF/native-image")
        }
    }
    binaries.all {
        resources.autodetect()
    }
    toolchainDetection.set(false)
}

// 在 nativeCompile 任务前确保 generateInitAtBuildTime 已执行
tasks.named("nativeCompile") {
    dependsOn("generateInitAtBuildTime")
    doFirst {
        println("Starting native compilation...")
    }
}

// 创建一个便捷的更新任务
tasks.register("updateNativeConfig") {
    group = "graalvm"
    description = "手动触发重新生成 init-at-build-time.args 脚本"
    dependsOn("generateInitAtBuildTime")
}
