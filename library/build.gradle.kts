plugins {
    kotlin("jvm")
    alias(libs.plugins.vanniktech.mavenPublish)
    id("org.graalvm.buildtools.native")
}

group = "com.xr21"
version = "1.0.0"

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
    // Spring Framework
//    implementation(libs.spring.core)
//    implementation(libs.spring.context)
//    implementation(libs.spring.web)

    // Spring AI
    implementation(libs.spring.ai.openai)
    implementation(libs.spring.ai.mcp.client)

    // Reactor
    implementation(libs.reactor.core)

    // Jackson
    implementation(libs.jackson.databind)

    // Alibaba AI
    implementation(libs.spring.ai.alibaba.graph)
    implementation(libs.spring.ai.alibaba.agent)

    // Utilities
    implementation(libs.hutool.all)
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
    description = "Runs the AcpLocalAgent with ACP protocol support"
    dependsOn("classes")
    mainClass.set("com.xr21.ai.agent.AcpLocalAgent")
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir = projectDir
}

tasks.register<JavaExec>("runAsyncAgentClient") {
    group = "application"
    description = "Runs the AsyncAgentClient"
    dependsOn("classes")
    mainClass.set("com.xr21.ai.agent.AsyncAgentClient")
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir = projectDir
}

mavenPublishing {
    publishToMavenCentral()

    signAllPublications()

    coordinates(group.toString(), "ai-agents", version.toString())

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
        attributes["Main-Class"] = "com.xr21.ai.agent.AcpLocalAgent"
    }

    from(sourceSets.main.get().output)

    from(configurations.runtimeClasspath.get().map {
        if (it.isDirectory) it else zipTree(it)
    })

    archiveClassifier.set("all")
}

// GraalVM Native Image Configuration
graalvmNative {
    binaries {
        named("main") {
            imageName.set("ai-agents")
            mainClass.set("com.xr21.ai.agent.AcpLocalAgent")
            javaLauncher.set(javaToolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(17))
                vendor.set(JvmVendorSpec.matching("GraalVM Community"))
            })
            
            // 构建参数优化
            buildArgs.addAll(
                "--no-fallback",
                "--allow-incomplete-classpath",
                "--report-unsupported-elements-at-runtime",
                "-H:+ReportExceptionStackTraces",
                "--enable-url-protocols=http,https",
                "--enable-all-security-services",
                "-O3",
                "--gc=serial"  // G1 在某些 GraalVM 版本中不可用，使用 serial
            )
        }
    }
    
    // Toolchain 检测
    toolchainDetection.set(true)
}

// Native compile tasks
tasks.named("nativeCompile") {
    dependsOn("classes", "fatJar")
    group = "build"
    description = "Compiles the application to a native executable"
}

tasks.named("nativeRun") {
    dependsOn("nativeCompile")
    group = "application"
    description = "Runs the native executable"
}
