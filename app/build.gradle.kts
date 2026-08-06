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
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
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
        // 这些库自带 META-INF/native-image 元数据（或必须 build-time 初始化，如 kotlin stdlib）。
        // 若再强制 --initialize-at-run-time 整包，会与它们自身精确的 class-init 指令冲突，
        // 导致 GraalVM 报 "Classes that should be initialized at run time got initialized during image building"。
        val skipInitAtRunTime = setOf(
            "io.netty",              // 自带 native-image.properties，冲突源头
            "kotlin", "kotlinx", "org.jetbrains",   // kotlin stdlib 必须 build-time 初始化
            "ch.qos", "org.slf4j",  // logback / slf4j
            "org.springframework", "org.springframework.aop",
            "okhttp3", "okio",      // okhttp 自带 native-image 配置
            "io.projectreactor", "io.micrometer", "io.netty.resolver",
            "org.jline", "org.aesh", // TUI 后端
            "com.fasterxml",         // jackson
            "org.objectweb",         // ASM
            "org.apache"
        )

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
                            .forEach { rootPkg ->
                                // 前缀匹配：既要跳过黑名单根包本身，也要跳过其子包/包内直接类
                                // （如 kotlin.DeprecationLevel、kotlin.collections、io.netty.channel 等），
                                // 否则仍会生成 --initialize-at-run-time=kotlin.DeprecationLevel 触发 class-init 冲突。
                                if (skipInitAtRunTime.none { rootPkg == it || rootPkg.startsWith("$it.") }) {
                                    thirdPartyPackages.add(rootPkg)
                                }
                            }
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
    // 禁用 GraalVM reachability metadata repository（Windows 修复）：
    // 插件 0.10.2 为其生成的 --exclude-config 参数使用 Pattern.quote() 产生
    // 未转义的单反斜杠 Windows 路径（\QE:\...\xxx.jar\E）写入 .args 文件，
    // native-image 解析 .args 时把 \ 当作转义序列，路径被破坏后作为正则编译触发
    // PatternSyntaxException (Illegal/unsupported escape sequence) 导致构建失败。
    // 禁用后依赖内嵌的 META-INF/native-image 配置由 native-image 自动发现加载。
    metadataRepository {
        enabled.set(false)
    }

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
            // 关键：上面的 -Dlogback.configurationFile 只写入最终镜像，对 native-image 构建进程本身无效。
            // 构建进程是一个独立 JVM，编译期执行 netty 静态初始化（AbstractChannel 等 build-time 类）会触发
            // logback Logger 创建，此时 logback 读取的是 classpath 默认的 logback.xml（带 RollingFileAppender），
            // 于是实例化文件 appender → 打开文件 → 报 FileDescriptor in image heap。
            // 必须额外用 -J 前缀把系统属性传给 native-image 构建进程，使其加载纯控制台的配置。
            buildArgs.add("-J-Dlogback.configurationFile=" +
                layout.projectDirectory.file("src/jvmMain/resources/logback-native-simple.xml").asFile.absolutePath.replace('\\', '/'))
            // GraalVM for JDK 24 默认 run-time 初始化类；logback/slf4j 的静态 Logger 若在镜像构建期被初始化
            // 会把 Logger 对象写入镜像堆并报 UnsupportedFeatureException。按 GraalVM 官方建议固定为 build-time。
            buildArgs.add("--initialize-at-build-time=ch.qos.logback,org.slf4j,org.xml.sax.helpers.LocatorImpl")
            buildArgs.add("--trace-object-instantiation=java.io.FileDescriptor")
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
            // 自动化修复循环生成的参数（由 tools/native-fix-loop.py 写入，逐条追加，去重）
            val autoFixArgsProvider = provider {
                val f = layout.buildDirectory.file("auto-init-at-build-time.args").get().asFile
                if (f.exists()) {
                    f.readLines().filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
                } else {
                    emptyList()
                }
            }
            buildArgs.addAll(autoFixArgsProvider.get())

            println("Configuration file directory: ${project.projectDir}/src/jvmMain/resources/META-INF/native-image")
        }
    }
    // resources.autodetect() 已在 Windows 上禁用：
    // 插件 0.10.2 生成的 --exclude-config 使用未转义的单反斜杠 Windows 路径写入 .args 文件，
    // native-image 解析 .args 时将 \ 当作转义字符，导致路径损坏并触发
    // PatternSyntaxException (Illegal/unsupported escape sequence) 构建失败。
    // 资源包含规则已固化到 src/jvmMain/resources/META-INF/native-image/resource-config.json。
    // binaries.all { resources.autodetect() }
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
