import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.zip.ZipFile

plugins {
    kotlin("jvm")
    id("org.graalvm.buildtools.native")
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

// 反射参数名支持（与 :library 保持一致）
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
            srcDirs("src/jvmMain/java")
        }
        kotlin {
            srcDirs("src/jvmMain/kotlin")
        }
        resources {
            srcDirs("src/jvmMain/resources")
        }
    }
}

dependencies {
    implementation(project(":library"))
    implementation(project(":tui"))

    // ACP SDK（AgentApplication 直接调用 AcpAgentLauncher.launchWebSocketServer）
    implementation("com.agentclientprotocol:acp-jvm:0.23.0")
    implementation("com.agentclientprotocol:acp-model-jvm:0.23.0")
    implementation("com.agentclientprotocol:acp-ktor-jvm:0.23.0")
    implementation("com.agentclientprotocol:acp-ktor-server-jvm:0.23.0")
    implementation("com.agentclientprotocol:acp-ktor-server:0.23.0")
    // Ktor HTTP Server Engine (required by acp-ktor-server-jvm)
    implementation("io.ktor:ktor-server-netty:3.1.3")
    // Ktor HTTP Client Engine
    implementation("io.ktor:ktor-client-okhttp:3.1.3")
}

// ==================== 可执行入口 ====================
tasks.named<Jar>("jar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "com.xr21.ai.agent.AgentApplication"
        attributes["Multi-Release"] = "true"
    }
}

// 创建 fatJar 任务（打包所有依赖为可执行 fat JAR）
tasks.register<Jar>("fatJar") {
    dependsOn("classes")
    group = "build"
    description = "Builds a fat JAR with all dependencies"

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

    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")

    archiveBaseName.set("XAgent")
    archiveClassifier.set("all")
}

// 自动扫描所有依赖 JAR，提取第三方根包名，生成 --initialize-at-run-time 脚本
val initRunTimeScript = layout.buildDirectory.file("init-at-run-time.args")

tasks.register("generateInitAtRunTime") {
    group = "graalvm"
    description = "Scan dependency JARs and generate --initialize-at-run-time for all third-party packages"

    val outputFile = initRunTimeScript.get().asFile

    doLast {
        println("Scanning runtime classpath for third-party packages...")

        val thirdPartyPackages = mutableSetOf<String>()

        configurations.runtimeClasspath.get().forEach { file ->
            if (file.isFile && file.name.endsWith(".jar")) {
                try {
                    ZipFile(file).use { zip ->
                        zip.entries().asSequence()
                            .filter { !it.isDirectory && it.name.endsWith(".class") }
                            .map { it.name.removeSuffix(".class").replace('/', '.') }
                            .filter { className ->
                                !className.startsWith("java.") &&
                                !className.startsWith("javax.") &&
                                !className.startsWith("jdk.") &&
                                !className.startsWith("sun.") &&
                                !className.startsWith("com.sun.") &&
                                !className.startsWith("org.w3c.") &&
                                !className.startsWith("org.xml.") &&
                                !className.startsWith("org.ietf.") &&
                                !className.startsWith("org.omg.") &&
                                !className.startsWith("com.xr21.") &&
                                !className.contains("module-info") &&
                                !className.endsWith(".package-info")
                            }
                            .map { className ->
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

        outputFile.parentFile?.mkdirs()
        val initArgs = thirdPartyPackages.sorted().map { "--initialize-at-run-time=$it" }
        outputFile.writeText(initArgs.joinToString("\n"))
        println("Generated init-at-run-time.args with ${thirdPartyPackages.size} root packages")
    }
}

// ==================== GraalVM Native Build Configuration ====================
graalvmNative {
    binaries {
        named("main") {
            imageName.set("XAgent")
            mainClass.set("com.xr21.ai.agent.AgentApplication")

            buildArgs.add("--no-fallback")
            buildArgs.add("-H:+UnlockExperimentalVMOptions")
            buildArgs.add("-H:+ReportExceptionStackTraces")
            buildArgs.add("--enable-url-protocols=http,https")
            buildArgs.add("-O2")
            buildArgs.add("--verbose")
            buildArgs.add("--gc=serial")
            buildArgs.add("-H:EnableURLProtocols=all")
            buildArgs.add("-H:+AllowIncompleteClasspath")
            buildArgs.add("-Dlogback.statusListener=ch.qos.logback.core.status.NopStatusListener")
            buildArgs.add("-Dlogback.console=disabled")
            buildArgs.add("-Dlogback.configurationFile=classpath:logback-native-simple.xml")
            buildArgs.add("-H:ConfigurationFileDirectories=${project.projectDir}/src/jvmMain/resources/META-INF/native-image")
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

// 便捷的更新任务
tasks.register("updateNativeConfig") {
    group = "graalvm"
    description = "手动触发重新生成 init-at-run-time.args 脚本"
    dependsOn("generateInitAtRunTime")
}

// 便捷的运行任务
tasks.register<JavaExec>("runAcpAgent") {
    group = "application"
    description = "Runs AcpLocalAgent with ACP protocol support"
    dependsOn("classes")
    mainClass.set("com.xr21.ai.agent.AgentApplication")
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir = projectDir
}

tasks.register<JavaExec>("runTui") {
    group = "application"
    description = "Runs the TUI application"
    dependsOn("classes")
    mainClass.set("com.xr21.ai.agent.AgentApplication")
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir = projectDir
    args("--tui")
}
