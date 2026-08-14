package com.xr21.ai.agent.plugins;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xr21.ai.agent.utils.GroovyToolBindings;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import lombok.extern.slf4j.Slf4j;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.springframework.ai.tool.ToolCallback;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/**
 * 插件加载器：目录包 plugin.json 解析校验 + legacy 合成清单 + 入口路径围栏 + GroovyShell 加载。
 * <p>
 * 对齐 Agent Plugins v1.0.0（§4/§5/§8/§9）：闭式 schema、name 约束、路径围栏、
 * PLUGIN_ROOT/PLUGIN_DATA 注入与 ${} 单次展开、失败隔离（单个插件/入口失败仅告警跳过）。
 */
@Slf4j
public final class GroovyPluginLoader {

    public static final String EXTENSION_NAMESPACE = "com.xr21.agent";
    private static final String PLUGIN_SCHEMA_URL = "https://agent-plugins.org/schemas/1.0.0/plugin.schema.json";
    private static final String PLUGINS_DIR = "plugins";
    private static final String LEGACY_TOOLS_DIR = "tools";
    private static final String DATA_DIR_NAME = "plugin-data";
    private static final Set<String> ALLOWED_TOP_LEVEL_FIELDS = Set.of(
            "$schema", "name", "version", "description", "author", "homepage",
            "repository", "license", "keywords", "extensions");
    private static final Pattern NAME_PATTERN = Pattern.compile("[a-z0-9-.]{1,64}");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long LOAD_TIMEOUT_SECONDS = 30;

    private static volatile boolean loaded = false;
    private static final ExecutorService executor = Executors.newCachedThreadPool();

    private GroovyPluginLoader() {
    }

    /**
     * 幂等加载：首次调用扫描全局+项目两级目录包与 legacy 文件并登记进 {@link GroovyPluginRegistry}。
     *
     * @param hostTools 宿主工具快照（注入插件闭包 bindings，tools.xxx 编排用）
     * @param cwd       项目工作目录（用于定位项目级 .agents/plugins）
     * @return 本次已加载插件列表
     */
    public static synchronized List<GroovyPlugin> loadAll(List<ToolCallback> hostTools, String cwd) {
        return loadAll(hostTools, cwd, null);
    }

    /**
     * 幂等加载（阶段二重载）：额外接收 {@link PluginContext} 能力容器。
     *
     * @param hostTools 宿主工具快照（注入插件闭包 bindings，tools.xxx 编排用）
     * @param cwd       项目工作目录（用于定位项目级 .agents/plugins）
     * @param ctx       插件能力上下文（client/chatModel/workers/sharedCache 注入，可为 null）
     * @return 本次已加载插件列表
     */
    public static synchronized List<GroovyPlugin> loadAll(List<ToolCallback> hostTools, String cwd, PluginContext ctx) {
        if (loaded) {
            return new ArrayList<>(GroovyPluginRegistry.get().plugins());
        }
        GroovyPluginRegistry.get().setPluginContext(ctx != null ? ctx : PluginContext.builder().hostTools(hostTools).build());
        Path userHome = Path.of(System.getProperty("user.home"));
        Path base = Path.of(cwd == null || cwd.isBlank() ? System.getProperty("user.dir") : cwd);
        Path dataRoot = userHome.resolve(".agents").resolve(DATA_DIR_NAME);

        // 标准目录包：先全局后项目（项目同名覆盖）
        scanPluginRoot(userHome.resolve(".agents").resolve(PLUGINS_DIR), dataRoot);
        scanPluginRoot(base.resolve(".agents").resolve(PLUGINS_DIR), dataRoot);
        // legacy 松散 .groovy：先全局后项目
        scanLegacyRoot(userHome.resolve(".agents").resolve(LEGACY_TOOLS_DIR), dataRoot);
        scanLegacyRoot(base.resolve(".agents").resolve(LEGACY_TOOLS_DIR), dataRoot);

        loaded = true;
        List<GroovyPlugin> result = new ArrayList<>(GroovyPluginRegistry.get().plugins());
        log.info("GroovyPluginLoader: loaded {} plugins", result.size());
        return result;
    }

    /** 重载全部插件（先逐个 close() 释放状态，再重新扫描；阶段三热重载/测试用）。 */
    public static synchronized void reload(List<ToolCallback> hostTools, String cwd) {
        reload(hostTools, cwd, null);
    }

    /** 重载全部插件（阶段二：带 PluginContext）。 */
    public static synchronized void reload(List<ToolCallback> hostTools, String cwd, PluginContext ctx) {
        loaded = false;
        GroovyPluginRegistry.get().unregisterAll();
        GroovyPluginRegistry.get().reset();
        loadAll(hostTools, cwd, ctx);
    }

    /** 扫描一个目录包根，加载每个含 plugin.json 的子目录。 */
    private static void scanPluginRoot(Path root, Path dataRoot) {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (var stream = Files.list(root)) {
            stream.filter(Files::isDirectory).forEach(dir -> {
                if (Files.exists(dir.resolve("plugin.json"))) {
                    try {
                        GroovyPlugin plugin = loadPlugin(dir, dataRoot);
                        if (plugin != null) {
                            GroovyPluginRegistry.get().register(plugin);
                        }
                    } catch (Exception e) {
                        log.warn("GroovyPluginLoader: plugin at {} rejected: {}", dir, e.getMessage());
                    }
                }
            });
        } catch (IOException e) {
            log.warn("GroovyPluginLoader: failed to scan plugin root {}: {}", root, e.getMessage());
        }
    }

    /** 扫描 legacy 根目录，每个松散 .groovy 文件包装为合成清单插件。 */
    private static void scanLegacyRoot(Path root, Path dataRoot) {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (var stream = Files.list(root)) {
            stream.filter(p -> p.toString().endsWith(".groovy") && Files.isRegularFile(p)).forEach(file -> {
                String name = file.getFileName().toString().replaceFirst("\\.groovy$", "");
                try {
                    validatePluginName(name, file);
                    Path dataDir = ensureDataDir(dataRoot, name);
                    String script = Files.readString(file, StandardCharsets.UTF_8);
                    script = expandPlaceholders(script, file.getParent(), dataDir);
                    Map<String, Object> desc = evaluate(script, file.getParent(), dataDir, name);
                    GroovyPlugin plugin = GroovyPluginParser.parse(name, desc, EXTENSION_NAMESPACE,
                            file.getParent(), dataDir, true);
                    GroovyPluginRegistry.get().register(plugin);
                } catch (Exception e) {
                    log.warn("GroovyPluginLoader: legacy plugin {} rejected: {}", file, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.warn("GroovyPluginLoader: failed to scan legacy root {}: {}", root, e.getMessage());
        }
    }

    /** 加载一个目录包插件：校验 plugin.json → 提取 entrypoints → 逐入口加载并合并描述。 */
    private static GroovyPlugin loadPlugin(Path dir, Path dataRoot) throws Exception {
        Path manifest = dir.resolve("plugin.json");
        JsonNode root = MAPPER.readTree(Files.readString(manifest, StandardCharsets.UTF_8));
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("plugin.json 必须是 JSON 对象");
        }
        // 闭式 schema：未知顶层字段报告并忽略（非致命）
        List<String> unknown = new ArrayList<>();
        root.fieldNames().forEachRemaining(f -> {
            if (!ALLOWED_TOP_LEVEL_FIELDS.contains(f)) {
                unknown.add(f);
            }
        });
        if (!unknown.isEmpty()) {
            log.warn("GroovyPluginLoader: plugin {} has unknown top-level fields {}, ignored", dir, unknown);
        }
        // $schema 识别：不支持则拒绝（致命）
        String schema = root.path("$schema").asText("");
        if (!PLUGIN_SCHEMA_URL.equals(schema)) {
            throw new IllegalArgumentException("不支持的 $schema: '" + schema + "'（期望 " + PLUGIN_SCHEMA_URL + "）");
        }
        // name 校验：缺失/非法则拒绝（致命）
        String name = root.path("name").asText("");
        validatePluginName(name, manifest);
        // extensions：非对象致命；无本客户端命名空间则忽略该插件
        JsonNode extensions = root.get("extensions");
        if (extensions != null && !extensions.isObject()) {
            throw new IllegalArgumentException("extensions 必须是对象");
        }
        JsonNode ext = extensions == null ? null : extensions.get(EXTENSION_NAMESPACE);
        JsonNode groovy = ext == null ? null : ext.get("groovy");
        if (groovy == null) {
            log.info("GroovyPluginLoader: plugin '{}' has no {} extension, ignored", name, EXTENSION_NAMESPACE);
            return null;
        }
        JsonNode entrypoints = groovy.get("entrypoints");
        if (entrypoints == null || !entrypoints.isArray() || entrypoints.isEmpty()) {
            throw new IllegalArgumentException("插件 '" + name + "' 缺少 extensions." + EXTENSION_NAMESPACE + ".groovy.entrypoints");
        }
        Path dataDir = ensureDataDir(dataRoot, name);
        Map<String, Object> desc = new LinkedHashMap<>();
        desc.put("version", root.path("version").asText("0.0.0"));
        desc.put("description", root.path("description").asText(""));
        desc.put("tools", new ArrayList<>());
        desc.put("hooks", new ArrayList<>());
        desc.put("interceptors", new ArrayList<>());
        // 逐入口编译加载；单个入口失败仅跳过该入口（协议失败隔离）
        for (JsonNode ep : entrypoints) {
            String entry = ep.asText();
            Path resolved = resolveEntrypoint(dir, entry);
            if (resolved == null) {
                log.warn("GroovyPluginLoader: plugin '{}' entry '{}' invalid or outside plugin root, skipped", name, entry);
                continue;
            }
            try {
                String script = Files.readString(resolved, StandardCharsets.UTF_8);
                script = expandPlaceholders(script, dir, dataDir);
                Map<String, Object> scriptDesc = evaluate(script, dir, dataDir, name);
                mergeDesc(desc, scriptDesc);
            } catch (Exception e) {
                log.warn("GroovyPluginLoader: plugin '{}' entry '{}' failed, skipped: {}", name, entry, e.getMessage());
            }
        }
        return GroovyPluginParser.parse(name, desc, EXTENSION_NAMESPACE, dir, dataDir, false);
    }

    /** 校验插件名（协议 §5.5）：1-64 字符、a-z0-9-._、首尾字母数字、无 --/..。 */
    private static void validatePluginName(String name, Path source) {
        if (name == null || name.isEmpty() || !NAME_PATTERN.matcher(name).matches()
                || name.contains("--") || name.contains("..")
                || !Character.isLetterOrDigit(name.charAt(0))
                || !Character.isLetterOrDigit(name.charAt(name.length() - 1))) {
            throw new IllegalArgumentException("非法插件名 '" + name + "'（1-64 字符、a-z0-9-._、首尾字母数字、无 --/..）: " + source);
        }
    }

    /** 入口路径围栏（协议 §4.1）：必须 ./ 开头，解析后必须落在插件根内且为普通文件。 */
    private static Path resolveEntrypoint(Path root, String entry) {
        if (entry == null || !entry.startsWith("./")) {
            return null;
        }
        Path resolved = root.resolve(entry.substring(2)).normalize();
        if (!resolved.startsWith(root) || !Files.isRegularFile(resolved)) {
            return null;
        }
        return resolved;
    }

    /** ${PLUGIN_ROOT}/${PLUGIN_DATA} 单次非递归文本替换（协议 §9.2），反斜杠转义以适配 Groovy 字符串。 */
    private static String expandPlaceholders(String script, Path root, Path dataDir) {
        return script
                .replace("${PLUGIN_ROOT}", escapeForGroovy(root.toAbsolutePath().toString()))
                .replace("${PLUGIN_DATA}", escapeForGroovy(dataDir.toAbsolutePath().toString()));
    }

    private static String escapeForGroovy(String path) {
        return path.replace("\\", "\\\\");
    }

    /** 确保 PLUGIN_DATA 可写目录存在（协议 §9.1）。 */
    private static Path ensureDataDir(Path dataRoot, String pluginName) throws IOException {
        Path dir = dataRoot.resolve(pluginName).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        if (!Files.isWritable(dir)) {
            throw new IOException("PLUGIN_DATA 目录不可写: " + dir);
        }
        return dir;
    }

    /** 在独立线程执行入口脚本（超时保护），返回脚本最后表达式的 Map。 */
    private static Map<String, Object> evaluate(String script, Path root, Path dataDir, String pluginName) {
        PluginContext ctx = GroovyPluginRegistry.get().getPluginContext();
        Binding binding = new Binding();
        binding.setVariable("tools", new GroovyToolBindings(GroovyPluginRegistry.get().hostTools(), null, ctx));
        binding.setVariable("PLUGIN_ROOT", root.toAbsolutePath().toString());
        binding.setVariable("PLUGIN_DATA", dataDir.toAbsolutePath().toString());
        binding.setVariable("cwd", System.getProperty("user.dir"));
        // 阶段二：conversation 门面（无 ToolContext 时为空操作门面）+ 能力上下文
        binding.setVariable("conversation", new ConversationAccess(null));
        binding.setVariable("pluginContext", ctx);
        CompilerConfiguration cc = new CompilerConfiguration();
        cc.addCompilationCustomizers(new ImportCustomizer() {{
            addStarImports("groovy.json");
        }});
        GroovyShell shell = new GroovyShell(binding, cc);
        Future<Object> future = executor.submit(() -> shell.evaluate(script));
        Object value;
        try {
            value = future.get(LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new IllegalStateException("插件 '" + pluginName + "' 入口脚本加载超时（" + LOAD_TIMEOUT_SECONDS + "s）");
        } catch (Exception e) {
            throw new IllegalStateException("插件 '" + pluginName + "' 入口脚本执行失败: " + e.getMessage());
        }
        if (value instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, v) -> out.put(String.valueOf(k), v));
            return out;
        }
        return Map.of();
    }

    /** 把每个入口返回 Map 的 tools/hooks/interceptors/init/close 合并进描述（init/close 首条生效）。 */
    @SuppressWarnings("unchecked")
    private static void mergeDesc(Map<String, Object> target, Map<String, Object> source) {
        if (source.get("tools") instanceof List<?> t) {
            ((List<Object>) target.get("tools")).addAll((List<?>) t);
        }
        if (source.get("hooks") instanceof List<?> h) {
            ((List<Object>) target.get("hooks")).addAll((List<?>) h);
        }
        if (source.get("interceptors") instanceof List<?> i) {
            ((List<Object>) target.get("interceptors")).addAll((List<?>) i);
        }
        if (source.containsKey("init") && !target.containsKey("init")) {
            target.put("init", source.get("init"));
        }
        if (source.containsKey("close") && !target.containsKey("close")) {
            target.put("close", source.get("close"));
        }
    }
}
