import java.util.zip.ZipFile

plugins {
    kotlin("jvm")
    alias(libs.plugins.vanniktech.mavenPublish)
    id("org.graalvm.buildtools.native")
    id("com.github.ben-manes.versions") version "0.51.0"
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
    implementation("io.agentscope:agentscope-core:2.0.0")
    implementation("io.agentscope:agentscope-harness:2.0.0")
    implementation("io.agentscope:agentscope-extensions-model-openai:2.0.0")
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
    // Mordant TUI
//    implementation(libs.mordant)
//    implementation(libs.mordant.coroutines)
//    implementation(libs.mordant.markdown)
//    implementation(libs.mordant.jvm.jna)

    // Tamboui TUI (all modules)
    implementation("dev.tamboui:tamboui-tui:0.4.0-SNAPSHOT")
    implementation("dev.tamboui:tamboui-widgets:0.4.0-SNAPSHOT")
    implementation("dev.tamboui:tamboui-core:0.4.0-SNAPSHOT")
    implementation("dev.tamboui:tamboui-markdown:0.4.0-SNAPSHOT")
    implementation("dev.tamboui:tamboui-jline3-backend:0.4.0-SNAPSHOT")
    implementation("dev.tamboui:tamboui-css:0.4.0-SNAPSHOT")
    implementation("dev.tamboui:tamboui-image:0.4.0-SNAPSHOT")
    implementation("dev.tamboui:tamboui-panama-backend:0.4.0-SNAPSHOT")
    implementation("dev.tamboui:tamboui-aesh-backend:0.4.0-SNAPSHOT")
    implementation("dev.tamboui:tamboui-picocli:0.4.0-SNAPSHOT")
    implementation("dev.tamboui:tamboui-toolkit:0.4.0-SNAPSHOT")
    implementation("dev.tamboui:tamboui-toolkit-markdown:0.4.0-SNAPSHOT")
    implementation("dev.tamboui:tamboui-annotations:0.4.0-SNAPSHOT")
    implementation("dev.tamboui:tamboui-processor:0.4.0-SNAPSHOT")
    implementation("dev.tamboui:tamboui-tfx:0.4.0-SNAPSHOT")
    implementation("dev.tamboui:tamboui-tfx-tui:0.4.0-SNAPSHOT")
    implementation("dev.tamboui:tamboui-tfx-toolkit:0.4.0-SNAPSHOT")
//    implementation("dev.tamboui:tamboui-demos:0.4.0-SNAPSHOT")
//    implementation("dev.tamboui:tamboui-benchmarks:0.4.0-SNAPSHOT")
    implementation("net.java.dev.jna:jna:5.14.0")
    // Test dependencies
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}

tasks.withType<Test> {
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
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

tasks.register<JavaExec>("runHarnessDemo") {
    group = "application"
    description = "Runs AgiHarnessAgentConsoleDemo - ACP client debug console"
    dependsOn("compileTestKotlin")
    mainClass.set("com.xr21.ai.agent.acp.AgiHarnessAgentConsoleDemoKt")
    classpath = sourceSets.test.get().runtimeClasspath
    workingDir = projectDir
    standardInput = System.`in`
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
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
        attributes["Multi-Release"] = "true"
    }
}

// 2. 创建 fatJar 任务（打包所有依赖为可执行 fat JAR）
tasks.register<Jar>("fatJar") {
    dependsOn("classes")
    group = "build"
    description = "Builds a fat JAR with all dependencies"

    // 排除重复文件（META-INF 中的服务描述文件等）
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes["Main-Class"] = "com.xr21.ai.agent.AgentApplication"
        attributes["Multi-Release"] = "true"
    }

    from(sourceSets.main.get().output)

    from(
        configurations.runtimeClasspath.get().map {
            if (it.isDirectory) it else zipTree(it)
        }
    )

    // 排除 META-INF 中的签名文件（避免 jar 签名冲突）
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")

    archiveBaseName.set("XAgent")
    // 不设置 classifier，这样 jar 名称就是 XAgent-all.jar（因为启用了 jar 任务的 all 分类器）
    // 实际上 archiveClassifier 就是后缀，这里设为 "all" 会生成 XAgent-all.jar
    archiveClassifier.set("all")
}

// 3. 自动扫描所有依赖 JAR，提取第三方根包名，生成 --initialize-at-run-time 脚本
//    策略：只让自己的代码 (com.xr21) 在 build-time 初始化，所有第三方依赖在 run-time 初始化
//    这样任何新依赖都自动安全，无需手动维护排除列表
val initRunTimeScript = layout.buildDirectory.file("init-at-run-time.args")

tasks.register("generateInitAtRunTime") {
    group = "graalvm"
    description = "Scan dependency JARs and generate --initialize-at-run-time for all third-party packages"

    val outputFile = initRunTimeScript.get().asFile

    doLast {
        println("Scanning runtime classpath for third-party packages...")

        // 收集所有依赖 JAR 中的根包名（前2级，如 com.google、org.apache）
        val thirdPartyPackages = mutableSetOf<String>()

        configurations.runtimeClasspath.get().forEach { file ->
            if (file.isFile && file.name.endsWith(".jar")) {
                try {
                    ZipFile(file).use { zip ->
                        zip.entries().asSequence()
                            .filter { !it.isDirectory && it.name.endsWith(".class") }
                            .map { it.name.removeSuffix(".class").replace('/', '.') }
                            .filter { className ->
                                // 排除 JDK 内部类
                                !className.startsWith("java.") &&
                                !className.startsWith("javax.") &&
                                !className.startsWith("jdk.") &&
                                !className.startsWith("sun.") &&
                                !className.startsWith("com.sun.") &&
                                !className.startsWith("org.w3c.") &&
                                !className.startsWith("org.xml.") &&
                                !className.startsWith("org.ietf.") &&
                                !className.startsWith("org.omg.") &&
                                // 排除自己的代码
                                !className.startsWith("com.xr21.") &&
                                // 排除 module-info / package-info
                                !className.contains("module-info") &&
                                !className.endsWith(".package-info")
                            }
                            .map { className ->
                                // 提取根包名（前2级）：com.google.xxx.yyy → com.google
                                val parts = className.split('.')
                                if (parts.size >= 2) "${parts[0]}.${parts[1]}" else parts[0]
                            }
                            .forEach { rootPkg -> thirdPartyPackages.add(rootPkg) }
                    }
                } catch (e: Exception) {
                    println("  WARN: Cannot read ${file.name}: ${e.message}")
                }
            }
        }

        println("Found ${thirdPartyPackages.size} third-party root packages")

        // 生成 --initialize-at-run-time 参数
        outputFile.parentFile?.mkdirs()
        val initArgs = thirdPartyPackages.sorted().map { "--initialize-at-run-time=$it" }
        outputFile.writeText(initArgs.joinToString("\n"))
        println("Generated init-at-run-time.args with ${thirdPartyPackages.size} root packages")
        thirdPartyPackages.sorted().take(20).forEach { println("  $it") }
        if (thirdPartyPackages.size > 20) println("  ... and ${thirdPartyPackages.size - 20} more")
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
            buildArgs.add("-H:EnableURLProtocols=all")
            buildArgs.add("-H:+AllowIncompleteClasspath")
            // logback 配置
            buildArgs.add("-Dlogback.statusListener=ch.qos.logback.core.status.NopStatusListener")
            buildArgs.add("-Dlogback.console=disabled")
            buildArgs.add("-Dlogback.configurationFile=classpath:logback-native-simple.xml")
            // 指定 reachability metadata 目录
            buildArgs.add("-H:ConfigurationFileDirectories=${project.projectDir}/src/jvmMain/resources/META-INF/native-image")
            // 自动扫描所有第三方依赖根包名，统一标记为 --initialize-at-run-time
            // 只让自己的代码 (com.xr21) 在 build-time 初始化，任何新依赖都自动安全
            val initArgsProvider = provider {
                val scriptFile = layout.buildDirectory.file("init-at-run-time.args").get().asFile
                if (scriptFile.exists()) {
                    scriptFile.readLines().filter { it.startsWith("--initialize-at-run-time=") }
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

// 在 nativeCompile 任务前确保 generateInitAtRunTime 已执行
tasks.named("nativeCompile") {
    dependsOn("generateInitAtRunTime")
    doFirst {
        println("Starting native compilation...")
    }
}

// 创建一个便捷的更新任务
tasks.register("updateNativeConfig") {
    group = "graalvm"
    description = "手动触发重新生成 init-at-run-time.args 脚本"
    dependsOn("generateInitAtRunTime")
}
