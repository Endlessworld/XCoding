import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
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

tasks.named<Jar>("jar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
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
    test {
        java {
            srcDirs("src/jvmTest/java")
        }
        kotlin {
            srcDirs("src/jvmTest/kotlin")
        }
        resources {
            srcDirs("src/jvmTest/resources")
        }
    }
}

dependencies {
    // 依赖核心库（agent/acp/bridge/model 等）
    implementation(project(":library"))

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
    implementation("dev.tamboui:tamboui-benchmarks:0.4.0-SNAPSHOT")
    implementation("net.java.dev.jna:jna:5.14.0")

    // ACP SDK（TUI 侧直接使用 ACP 客户端类型）
    implementation("com.agentclientprotocol:acp-jvm:0.23.0")
    implementation("com.agentclientprotocol:acp-model-jvm:0.23.0")
    implementation("com.agentclientprotocol:acp-ktor-jvm:0.23.0")
    implementation("com.agentclientprotocol:acp-ktor-client-jvm:0.23.0")
    // Ktor HTTP Client Engine (required by acp-ktor-jvm at runtime)
    implementation("io.ktor:ktor-client-okhttp:3.1.3")

    // Kotlin 协程与序列化
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")

    // Jakarta Annotations（TuiApp 使用 @Nullable）
    implementation("jakarta.annotation:jakarta.annotation-api:2.1.1")

    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
    testImplementation("junit:junit:4.13.2")
}
