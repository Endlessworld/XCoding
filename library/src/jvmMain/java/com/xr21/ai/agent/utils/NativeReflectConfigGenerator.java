package com.xr21.ai.agent.utils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Native Reflection Config Generator
 * <p>
 * 自动扫描项目中的类，生成 GraalVM native-reflect-config.json 配置文件。
 * 类似 Spring AOT 的自动检测功能，但专注于 Jackson 序列化所需的反射配置。
 * <p>
 * 功能特性：
 * 1. 自动扫描项目源码中的类
 * 2. 自动扫描依赖 JAR 中的类（新增）
 * 3. 自动检测 Jackson 注解
 * 4. 自动检测多态类型
 * 5. 自动检测 sealed 接口
 * 6. 保留现有配置，只添加新类
 * 7. 自动备份
 *
 * @author XR21
 */
public class NativeReflectConfigGenerator {

    private static final String CONFIG_FILE = "native-reflect-config.json";
    private static final String BACKUP_FILE = "native-reflect-config.json.backup";

    // 要扫描的包
    private static final String[] SCAN_PACKAGES = {
            "com"
    };

    // 已知需要反射的 JAR 包前缀（空数组表示扫描所有 JAR）
    // 设置为 null 时扫描所有 JAR
    private static final String[] REQUIRED_JAR_PREFIXES = null; // 扫描所有依赖 JAR

    // Jackson 注解模式
    private static final Pattern[] JACKSON_ANNOTATION_PATTERNS = {
            Pattern.compile("@JsonProperty\\s*\\(?"),
            Pattern.compile("@JsonIgnoreProperties?"),
            Pattern.compile("@JsonInclude"),
            Pattern.compile("@JsonTypeInfo"),
            Pattern.compile("@JsonSubTypes"),
            Pattern.compile("@JsonTypeName"),
            Pattern.compile("@JsonCreator"),
            Pattern.compile("@JsonDeserialize"),
            Pattern.compile("@JsonSerialize"),
            Pattern.compile("@JsonAnyGetter"),
            Pattern.compile("@JsonAnySetter"),
            Pattern.compile("@JsonGetter"),
            Pattern.compile("@JsonSetter"),
            Pattern.compile("@JsonIgnore"),
            Pattern.compile("@JsonManagedReference"),
            Pattern.compile("@JsonBackReference"),
            Pattern.compile("@JsonIdentityInfo"),
            Pattern.compile("@JsonFormat")
    };

    // 内部类模式
    private static final Pattern INNER_CLASS_PATTERN = Pattern.compile(
            "(public|private|protected)?\\s*(static)?\\s*(class|interface|enum|record)\\s+(\\w+)"
    );

    // sealed 接口模式
    private static final Pattern SEALED_PATTERN = Pattern.compile(
            "sealed\\s+(interface|class)\\s+(\\w+)"
    );

    // permits 子句模式
    private static final Pattern PERMITS_PATTERN = Pattern.compile(
            "permits\\s+([\\w,\\s]+)"
    );

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║       Native Reflection Config Generator v2.0                 ║");
        System.out.println("║       支持源码 + 依赖 JAR 扫描                                  ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        NativeReflectConfigGenerator generator = new NativeReflectConfigGenerator();
        try {
            generator.generateConfig();
            System.out.println("\n✅ 完成! 配置文件: " + CONFIG_FILE);
        } catch (Exception e) {
            System.err.println("\n❌ 生成失败: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    public void generateConfig() throws Exception {
        System.out.println("📦 扫描包: " + String.join(", ", SCAN_PACKAGES));
        System.out.println();

        List<Map<String, Object>> reflectConfig = new ArrayList<>();

        // 1. 加载现有的反射配置（保留用户自定义的配置）
        List<Map<String, Object>> existingConfig = loadExistingConfig();
        System.out.println("📂 加载现有配置: " + existingConfig.size() + " 条目");

        // 用于跟踪已处理的类
        Set<String> processedClasses = new LinkedHashSet<>();

        // 2. 加载现有类名
        for (Map<String, Object> entry : existingConfig) {
            String className = (String) entry.get("name");
            if (className != null) {
                processedClasses.add(className);
            }
        }

        System.out.println("📊 已处理类: " + processedClasses.size());
        System.out.println();

        // 3. 扫描项目源码类
        System.out.println("🔍 扫描项目源码类...");
        for (String packageName : SCAN_PACKAGES) {
            scanPackage(packageName, reflectConfig, processedClasses);
        }

        // 4. 扫描依赖 JAR 中的类（新增功能）
        System.out.println("\n🔍 扫描依赖 JAR...");
        scanDependencyJars(reflectConfig, processedClasses);

        // 5. 添加必需的第三方类
        System.out.println("\n📚 添加必需的第三方类...");
        addRequiredThirdPartyClasses(reflectConfig, processedClasses);

        // 6. 写入配置文件
        System.out.println("\n💾 写入配置文件...");
        writeConfig(reflectConfig);

        // 7. 打印统计信息
        printSummary(reflectConfig, existingConfig);
    }

    /**
     * 扫描依赖 JAR 文件
     */
    private void scanDependencyJars(List<Map<String, Object>> config, Set<String> processedClasses) throws Exception {
        // 获取 classpath 中的所有 JAR 文件
        String classpath = System.getProperty("java.class.path");
        String[] paths = classpath.split(File.pathSeparator);

        Set<String> scannedJars = new HashSet<>();
        int totalClassesAdded = 0;

        for (String path : paths) {
            File file = new File(path);

            // 只处理 JAR 文件
            if (!file.getName().endsWith(".jar")) {
                continue;
            }

            // 检查是否是需要扫描的 JAR
            boolean shouldScan = true;
            if (REQUIRED_JAR_PREFIXES != null && REQUIRED_JAR_PREFIXES.length > 0) {
                shouldScan = false;
                String lowerName = file.getName().toLowerCase();
                for (String prefix : REQUIRED_JAR_PREFIXES) {
                    if (lowerName.contains(prefix)) {
                        shouldScan = true;
                        break;
                    }
                }
            }

            if (!shouldScan) {
                continue;
            }

            if (scannedJars.contains(file.getName())) {
                continue;
            }
            scannedJars.add(file.getName());

            System.out.println("  📦 扫描 JAR: " + file.getName());

            try (JarFile jarFile = new JarFile(file)) {
                int jarClassesAdded = scanJar(jarFile, config, processedClasses);
                totalClassesAdded += jarClassesAdded;
            } catch (Exception e) {
                System.err.println("  ⚠️  无法扫描 JAR: " + file.getName() + " - " + e.getMessage());
            }
        }

        System.out.println("  ✅ 共扫描 " + scannedJars.size() + " 个 JAR, 添加 " + totalClassesAdded + " 个类");
    }

    /**
     * 扫描单个 JAR 文件
     * @return 添加的类数量
     */
    private int scanJar(JarFile jarFile, List<Map<String, Object>> config, Set<String> processedClasses) throws Exception {
        Enumeration<JarEntry> entries = jarFile.entries();
        int addedCount = 0;

        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();

            // 只处理类文件
            if (!name.endsWith(".class")) {
                continue;
            }

            // 转换为类名
            String className = name.substring(0, name.length() - 6).replace('/', '.');

            // 检查是否是需要反射的类
            if (shouldRegisterClass(className)) {
                if (!processedClasses.contains(className)) {
                    // 尝试从 JAR 中读取类的字节码来分析注解
                    try {
                        InputStream is = jarFile.getInputStream(entry);
                        byte[] bytecode = is.readAllBytes();
                        is.close();

                        if (needsReflection(bytecode)) {
                            addReflectionConfig(config, className, processedClasses);
                            System.out.println("    ➕ 添加: " + className);
                            addedCount++;
                        }
                    } catch (Exception e) {
                        // 如果无法读取，至少添加类名
                        addReflectionConfig(config, className, processedClasses);
                        System.out.println("    ➕ 添加: " + className);
                        addedCount++;
                    }
                }
            }
        }
        
        return addedCount;
    }

    /**
     * 检查类是否应该被注册
     */
    private boolean shouldRegisterClass(String className) {
        // 排除常见的 JDK 内部类和不需要反射的类
        String[] excludePrefixes = {
                "java.",
                "jdk.",
                "javax.",
                "com.sun.",
                "sun.",
                "oracle.",
                "kotlin.",
                "kotlinx.",
                "io.netty.",
                "reactor.",
                "org.reactivestreams.",
                "org.springframework.boot.loader.",
                "org.springframework.boot.loader.jar.",
                "META-INF.",
                "META-INF-INF"
        };

        for (String prefix : excludePrefixes) {
            if (className.startsWith(prefix)) {
                return false;
            }
        }

        // 只处理特定包下的类
        for (String pkg : SCAN_PACKAGES) {
            if (className.startsWith(pkg)) {
                return true;
            }
        }

        // 额外添加一些已知的需要反射的包（如果扫描所有 JAR）
        if (REQUIRED_JAR_PREFIXES == null) {
            String[] additionalPackages = {
                    "com.fasterxml.jackson",
                    "io.modelcontextprotocol",
                    "com.agentclientprotocol",
                    "org.slf4j",
                    "ch.qos.logback",
                    "org.slf4j",
                    "com.google.gson"
            };

            for (String pkg : additionalPackages) {
                if (className.startsWith(pkg)) {
                    return true;
                }
            }
            
            // 当扫描所有 JAR 时，也添加其他常见反射需要的包
            String[] commonReflectPackages = {
                    "org.apache.",
                    "com.fasterxml.",
                    "io.swagger.",
                    "org.hibernate.",
                    "org.mybatis."
            };
            
            for (String pkg : commonReflectPackages) {
                if (className.startsWith(pkg)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 从字节码检测是否需要反射配置
     */
    private boolean needsReflection(byte[] bytecode) {
        // 简单的字节码分析：检查是否包含 Jackson 注解
        // 注意：这是简化版本，完整的实现需要使用 ASM 或 Javassist

        String bytecodeStr = new String(bytecode, 0, Math.min(bytecode.length, 50000));

        // 检查常见的 Jackson 注解
        String[] jacksonAnnotations = {
                "Lcom/fasterxml/jackson/annotation/JsonProperty",
                "Lcom/fasterxml/jackson/annotation/JsonTypeInfo",
                "Lcom/fasterxml/jackson/annotation/JsonSubTypes",
                "Lcom/fasterxml/jackson/annotation/JsonIgnoreProperties",
                "Lcom/fasterxml/jackson/annotation/JsonInclude"
        };

        for (String annotation : jacksonAnnotations) {
            if (bytecodeStr.contains(annotation)) {
                return true;
            }
        }

        return false;
    }

    private List<Map<String, Object>> loadExistingConfig() {
        try {
            File file = new File(CONFIG_FILE);
            if (!file.exists()) {
                return new ArrayList<>();
            }

            String content = Files.readString(file.toPath());
            return parseJson(content);
        } catch (Exception e) {
            System.out.println("⚠️  无法加载现有配置: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseJson(String content) {
        List<Map<String, Object>> result = new ArrayList<>();

        content = content.trim();
        if (!content.startsWith("[")) {
            return result;
        }

        int depth = 0;
        int objectStart = -1;
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (c == '\\') {
                escaped = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (inString) continue;

            switch (c) {
                case '{':
                    if (depth == 0) {
                        objectStart = i;
                    }
                    depth++;
                    break;
                case '}':
                    depth--;
                    if (depth == 0 && objectStart >= 0) {
                        String objectStr = content.substring(objectStart, i + 1);
                        Map<String, Object> obj = parseJsonObject(objectStr);
                        if (obj != null) {
                            result.add(obj);
                        }
                        objectStart = -1;
                    }
                    break;
            }
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonObject(String str) {
        Map<String, Object> result = new LinkedHashMap<>();

        str = str.trim();
        if (str.startsWith("{")) str = str.substring(1);
        if (str.endsWith("}")) str = str.substring(0, str.length() - 1);

        Pattern keyValuePattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*([^,}]+)");
        Matcher matcher = keyValuePattern.matcher(str);

        while (matcher.find()) {
            String key = matcher.group(1);
            String value = matcher.group(2).trim();

            if (value.equals("true")) {
                result.put(key, true);
            } else if (value.equals("false")) {
                result.put(key, false);
            } else if (value.startsWith("\"") && value.endsWith("\"")) {
                result.put(key, value.substring(1, value.length() - 1));
            }
        }

        return result;
    }

    private void scanPackage(String packageName, List<Map<String, Object>> config, Set<String> processedClasses) {
        String basePath = "src/jvmMain/java/" + packageName.replace('.', '/');
        Path path = Paths.get(basePath);

        if (!Files.exists(path)) {
            scanFromClasspath(packageName, config, processedClasses);
            return;
        }

        try {
            Files.walk(path)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(javaFile -> {
                        try {
                            String content = Files.readString(javaFile);
                            processJavaFile(packageName, javaFile.toFile(), content, config, processedClasses);
                        } catch (IOException e) {
                            System.err.println("Error reading file: " + javaFile);
                        }
                    });
        } catch (IOException e) {
            System.err.println("Error scanning package: " + packageName);
        }
    }

    private void scanFromClasspath(String packageName, List<Map<String, Object>> config, Set<String> processedClasses) {
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            String path = packageName.replace('.', '/');
            java.util.Enumeration<java.net.URL> resources = classLoader.getResources(path);

            while (resources.hasMoreElements()) {
                java.net.URL resource = resources.nextElement();
                File dir = new File(resource.getFile());

                if (dir.exists() && dir.isDirectory()) {
                    scanDirectory(dir, packageName, config, processedClasses);
                }
            }
        } catch (IOException e) {
            // Silently ignore
        }
    }

    private void scanDirectory(File dir, String packageName, List<Map<String, Object>> config, Set<String> processedClasses) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(file, packageName + "." + file.getName(), config, processedClasses);
            } else if (file.getName().endsWith(".java")) {
                try {
                    String content = Files.readString(file.toPath());
                    processJavaFile(packageName, file, content, config, processedClasses);
                } catch (IOException e) {
                    System.err.println("Error reading: " + file);
                }
            }
        }
    }

    private void processJavaFile(String packageName, File file, String content,
                                 List<Map<String, Object>> config, Set<String> processedClasses) {
        String className = file.getName().replace(".java", "");
        String fullClassName = packageName + "." + className;

        boolean needsReflection = containsJacksonAnnotations(content) ||
                containsPolymorphicTypes(content) ||
                isSealedInterface(content, packageName, className, config, processedClasses);

        if (needsReflection && !processedClasses.contains(fullClassName)) {
            addReflectionConfig(config, fullClassName, processedClasses);
            System.out.println("  ➕ 添加: " + fullClassName);
        }

        detectInnerClasses(content, packageName, className, config, processedClasses);
        detectPermittedClasses(content, packageName, className, config, processedClasses);
    }

    private boolean containsJacksonAnnotations(String content) {
        for (Pattern pattern : JACKSON_ANNOTATION_PATTERNS) {
            if (pattern.matcher(content).find()) {
                return true;
            }
        }

        if (content.contains("record ") || content.contains("interface ")) {
            return content.contains("JsonTypeInfo") || content.contains("JsonSubTypes");
        }

        return false;
    }

    private boolean containsPolymorphicTypes(String content) {
        return content.contains("@JsonTypeInfo") || content.contains("@JsonSubTypes");
    }

    private boolean isSealedInterface(String content, String packageName, String className,
                                      List<Map<String, Object>> config, Set<String> processedClasses) {
        Matcher sealedMatcher = SEALED_PATTERN.matcher(content);
        if (sealedMatcher.find()) {
            Matcher permitsMatcher = PERMITS_PATTERN.matcher(content);
            if (permitsMatcher.find()) {
                String permits = permitsMatcher.group(1);
                String[] classes = permits.split(",");

                for (String cls : classes) {
                    cls = cls.trim();
                    if (!cls.isEmpty()) {
                        String fullPermitName = packageName + "." + className + "$" + cls;
                        if (!processedClasses.contains(fullPermitName)) {
                            addReflectionConfig(config, fullPermitName, processedClasses);
                            System.out.println("  ➕ 添加 (sealed permit): " + fullPermitName);
                        }
                    }
                }
                return true;
            }
        }
        return false;
    }

    private void detectPermittedClasses(String content, String packageName, String outerClassName,
                                        List<Map<String, Object>> config, Set<String> processedClasses) {
        Matcher permitsMatcher = PERMITS_PATTERN.matcher(content);
        if (permitsMatcher.find()) {
            String permits = permitsMatcher.group(1);
            String[] classes = permits.split(",");

            for (String cls : classes) {
                cls = cls.trim();
                if (!cls.isEmpty() && !cls.contains(".")) {
                    String fullName = packageName + "." + outerClassName + "$" + cls;
                    if (!processedClasses.contains(fullName)) {
                        addReflectionConfig(config, fullName, processedClasses);
                        System.out.println("  ➕ 添加 (permit): " + fullName);
                    }
                }
            }
        }
    }

    private void detectInnerClasses(String content, String packageName, String outerClassName,
                                    List<Map<String, Object>> config, Set<String> processedClasses) {
        Matcher matcher = INNER_CLASS_PATTERN.matcher(content);

        while (matcher.find()) {
            String innerClassName = matcher.group(4);

            if (innerClassName != null && !innerClassName.equals(outerClassName)) {
                String fullInnerName = packageName + "." + outerClassName + "$" + innerClassName;

                if (!processedClasses.contains(fullInnerName)) {
                    String innerContent = extractInnerClassContent(content, innerClassName);
                    if (innerContent != null && containsJacksonAnnotations(innerContent)) {
                        addReflectionConfig(config, fullInnerName, processedClasses);
                        System.out.println("  ➕ 添加 (内部类): " + fullInnerName);
                    }
                }
            }
        }
    }

    private String extractInnerClassContent(String content, String innerClassName) {
        Pattern pattern = Pattern.compile(
                "(public|private|protected)?\\s*(static)?\\s*\\w+\\s+" + innerClassName + "\\s*[{]"
        );
        Matcher matcher = pattern.matcher(content);

        if (matcher.find()) {
            int start = matcher.start();
            int braceCount = 0;
            boolean found = false;
            for (int i = start; i < content.length(); i++) {
                if (content.charAt(i) == '{') {
                    braceCount++;
                    found = true;
                } else if (content.charAt(i) == '}') {
                    braceCount--;
                    if (found && braceCount == 0) {
                        return content.substring(start, i + 1);
                    }
                }
            }
        }
        return null;
    }

    private void addReflectionConfig(List<Map<String, Object>> config, String className, Set<String> processedClasses) {
        if (processedClasses.contains(className)) {
            return;
        }

        processedClasses.add(className);

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", className);
        entry.put("allDeclaredConstructors", true);
        entry.put("allPublicConstructors", true);
        entry.put("allDeclaredMethods", true);
        entry.put("allPublicMethods", true);
        entry.put("allDeclaredFields", true);
        entry.put("allPublicFields", true);

        config.add(entry);
    }

    /**
     * 添加必需的第三方类（确保关键类都被注册）
     */
    private void addRequiredThirdPartyClasses(List<Map<String, Object>> config, Set<String> processedClasses) {
        // 关键类映射表：类名 -> 是否包含字段
        String[][] requiredClasses = {
                // Jackson 核心
                {"com.fasterxml.jackson.databind.ObjectMapper", "true"},
                {"com.fasterxml.jackson.databind.JsonNode", "true"},
                {"com.fasterxml.jackson.databind.node.ObjectNode", "true"},
                {"com.fasterxml.jackson.databind.node.ArrayNode", "true"},
                {"com.fasterxml.jackson.databind.DeserializationContext", "true"},
                {"com.fasterxml.jackson.databind.SerializationContext", "true"},
                {"com.fasterxml.jackson.databind.JavaType", "true"},
                {"com.fasterxml.jackson.databind.type.TypeFactory", "true"},

                // Jackson annotation
                {"com.fasterxml.jackson.annotation.JsonProperty", "false"},
                {"com.fasterxml.jackson.annotation.JsonIgnoreProperties", "false"},
                {"com.fasterxml.jackson.annotation.JsonInclude", "false"},
                {"com.fasterxml.jackson.annotation.JsonTypeInfo", "false"},
                {"com.fasterxml.jackson.annotation.JsonSubTypes", "false"},
                {"com.fasterxml.jackson.annotation.JsonCreator", "false"},
                {"com.fasterxml.jackson.annotation.JsonGetter", "false"},
                {"com.fasterxml.jackson.annotation.JsonSetter", "false"},

                // MCP
                {"io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapperSupplier", "true"},
                {"io.modelcontextprotocol.json.McpJsonMapper", "false"},
                {"io.modelcontextprotocol.json.TypeRef", "false"},

                // SLF4J
                {"org.slf4j.Logger", "false"},
                {"org.slf4j.LoggerFactory", "false"},
                {"org.slf4j.simple.SimpleLogger", "true"},
                {"org.slf4j.spi.SLF4JServiceProvider", "true"},

                // Logback
                {"ch.qos.logback.classic.Logger", "true"},
                {"ch.qos.logback.classic.LoggerContext", "true"},
                {"ch.qos.logback.core.status.NopStatusListener", "true"},
                {"ch.qos.logback.core.rolling.TimeBasedRollingPolicy", "true"},
                {"ch.qos.logback.classic.filter.LevelFilter", "true"},
                {"ch.qos.logback.classic.filter.ThresholdFilter", "true"},
                {"ch.qos.logback.core.ConsoleAppender", "true"},
                {"ch.qos.logback.core.rolling.RollingFileAppender", "true"},
                {"ch.qos.logback.classic.encoder.PatternLayoutEncoder", "true"},
                {"ch.qos.logback.classic.spi.LogbackServiceProvider", "true"},

                // ACP SDK 核心类
                {"com.agentclientprotocol.sdk.spec.AcpSchema", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$JSONRPCMessage", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$JSONRPCRequest", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$JSONRPCResponse", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$JSONRPCError", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$JSONRPCNotification", "true"},

                // ACP Initialize
                {"com.agentclientprotocol.sdk.spec.AcpSchema$InitializeRequest", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$InitializeResponse", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$ClientCapabilities", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$AgentCapabilities", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$FileSystemCapability", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$McpCapabilities", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$PromptCapabilities", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$AuthMethod", "true"},

                // ACP Session
                {"com.agentclientprotocol.sdk.spec.AcpSchema$NewSessionRequest", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$NewSessionResponse", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$LoadSessionRequest", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$LoadSessionResponse", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$PromptRequest", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$PromptResponse", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$SessionModeState", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$SessionMode", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$SessionModelState", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$ModelInfo", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$SetSessionModeRequest", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$SetSessionModeResponse", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$SetSessionModelRequest", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$SetSessionModelResponse", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$CancelNotification", "true"},

                // ACP Content
                {"com.agentclientprotocol.sdk.spec.AcpSchema$ContentBlock", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$TextContent", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$ImageContent", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$AudioContent", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$ResourceLink", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$Resource", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$EmbeddedResourceResource", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$TextResourceContents", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$BlobResourceContents", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$Annotations", "true"},

                // ACP Session Update
                {"com.agentclientprotocol.sdk.spec.AcpSchema$SessionUpdate", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$UserMessageChunk", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$AgentMessageChunk", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$AgentThoughtChunk", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$ToolCall", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$ToolCallUpdate", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$ToolCallUpdateNotification", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$ToolCallContent", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$ToolCallContentBlock", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$ToolCallDiff", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$ToolCallTerminal", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$ToolCallLocation", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$Plan", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$PlanEntry", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$AvailableCommandsUpdate", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$AvailableCommand", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$AvailableCommandInput", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$CurrentModeUpdate", "true"},

                // ACP Terminal
                {"com.agentclientprotocol.sdk.spec.AcpSchema$CreateTerminalRequest", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$CreateTerminalResponse", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$TerminalOutputRequest", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$TerminalOutputResponse", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$ReleaseTerminalRequest", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$ReleaseTerminalResponse", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$WaitForTerminalExitRequest", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$WaitForTerminalExitResponse", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$KillTerminalCommandRequest", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$KillTerminalCommandResponse", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$TerminalExitStatus", "true"},

                // ACP File
                {"com.agentclientprotocol.sdk.spec.AcpSchema$ReadTextFileRequest", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$ReadTextFileResponse", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$WriteTextFileRequest", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$WriteTextFileResponse", "true"},

                // ACP Permission
                {"com.agentclientprotocol.sdk.spec.AcpSchema$RequestPermissionRequest", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$RequestPermissionResponse", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$SessionNotification", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$PermissionOption", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$RequestPermissionOutcome", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$PermissionCancelled", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$PermissionSelected", "true"},

                // ACP MCP Server
                {"com.agentclientprotocol.sdk.spec.AcpSchema$McpServer", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$McpServerStdio", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$McpServerHttp", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$McpServerSse", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$EnvVariable", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$HttpHeader", "true"},

                // ACP Enums
                {"com.agentclientprotocol.sdk.spec.AcpSchema$StopReason", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$ToolCallStatus", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$ToolKind", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$Role", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$PermissionOptionKind", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$PlanEntryStatus", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$PlanEntryPriority", "true"},

                // ACP Auth
                {"com.agentclientprotocol.sdk.spec.AcpSchema$AuthenticateRequest", "true"},
                {"com.agentclientprotocol.sdk.spec.AcpSchema$AuthenticateResponse", "true"},

                // ACP Agent
                {"com.agentclientprotocol.sdk.agent.DefaultAcpAsyncAgent", "true"},
                {"com.agentclientprotocol.sdk.agent.transport.StdioAcpAgentTransport", "true"},
                {"com.agentclientprotocol.sdk.agent.AcpAsyncAgent", "true"},

                // Spring ACP
                {"com.agentclientprotocol.spring.agent.support.AcpAgentSupport", "true"},
        };

        int addedCount = 0;
        for (String[] cls : requiredClasses) {
            if (!processedClasses.contains(cls[0])) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("name", cls[0]);
                entry.put("allDeclaredConstructors", true);
                entry.put("allPublicConstructors", true);
                entry.put("allDeclaredMethods", true);
                entry.put("allPublicMethods", true);

                if ("true".equals(cls[1])) {
                    entry.put("allDeclaredFields", true);
                    entry.put("allPublicFields", true);
                }

                config.add(entry);
                processedClasses.add(cls[0]);
                System.out.println("  ➕ 添加 (必需): " + cls[0]);
                addedCount++;
            }
        }

        System.out.println("  ✅ 共添加 " + addedCount + " 个必需类");
    }

    private void writeConfig(List<Map<String, Object>> config) throws IOException {
        // 获取系统当前工作目录
        String workingDir = System.getProperty("user.dir");
        System.out.println("  📂 当前工作目录: " + workingDir);
        
        File configFile = new File(CONFIG_FILE);
        
        // 确保父目录存在
        File parentDir = configFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (parentDir.mkdirs()) {
                System.out.println("  📁 创建目录: " + parentDir.getAbsolutePath());
            }
        }
        
        if (configFile.exists()) {
            Files.copy(configFile.toPath(), Paths.get(BACKUP_FILE), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("  📁 备份: " + BACKUP_FILE);
        }

        try (Writer writer = new FileWriter(configFile)) {
            store(writer, config);
        }
    }

    private void store(Writer writer, List<Map<String, Object>> config) throws IOException {
        writer.write("[\n");

        for (int i = 0; i < config.size(); i++) {
            Map<String, Object> entry = config.get(i);
            writer.write("  {\n");

            int j = 0;
            int entrySize = entry.size();
            for (Map.Entry<String, Object> field : entry.entrySet()) {
                writer.write("    \"" + field.getKey() + "\": ");

                Object value = field.getValue();
                if (value instanceof Boolean) {
                    writer.write(value.toString());
                } else if (value instanceof String) {
                    writer.write("\"" + escapeJson((String) value) + "\"");
                }

                if (++j < entrySize) {
                    writer.write(",");
                }
                writer.write("\n");
            }

            writer.write("  }");
            if (i < config.size() - 1) {
                writer.write(",");
            }
            writer.write("\n");
        }

        writer.write("]\n");
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private void printSummary(List<Map<String, Object>> config, List<Map<String, Object>> existingConfig) {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║                     📊 统计信息                              ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        System.out.printf("║  总条目数:     %-45d║%n", config.size());
        System.out.printf("║  现有条目数:   %-45d║%n", existingConfig.size());
        System.out.printf("║  新增条目数:   %-45d║%n", config.size() - existingConfig.size());
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
    }
}