package com.xr21.ai.agent.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xr21.ai.agent.plugins.ConversationAccess;
import com.xr21.ai.agent.plugins.GroovyPluginParser;
import com.xr21.ai.agent.plugins.GroovyPluginRegistry;
import com.xr21.ai.agent.plugins.PluginContext;
import groovy.lang.GroovyObject;
import groovy.lang.MetaClass;
import groovy.lang.MissingMethodException;
import groovy.lang.MissingPropertyException;
import groovy.lang.GString;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 绑定到 Groovy 脚本的 tools 对象。
 * 脚本内通过 tools.xxx(...) 动态调用当前已注册的所有 MCP 工具，实现工具编排。
 * 阶段二新增：tools.inject(key) 白名单能力注入、tools.plugin(name, desc) 运行时注册通道。
 */
public class GroovyToolBindings implements GroovyObject {

    private final Map<String, ToolCallback> toolsByName = new LinkedHashMap<>();
    private final ToolContext toolContext;
    private final PluginContext pluginContext;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, List<String>> schemaPropsCache = new ConcurrentHashMap<>();

    public GroovyToolBindings(List<ToolCallback> tools, ToolContext toolContext) {
        this(tools, toolContext, null);
    }

    public GroovyToolBindings(List<ToolCallback> tools, ToolContext toolContext, PluginContext pluginContext) {
        this.toolContext = toolContext;
        this.pluginContext = pluginContext;
        this.metaClass = InvokerHelper.getMetaClass(this.getClass());
        for (ToolCallback cb : tools) {
            toolsByName.putIfAbsent(cb.getToolDefinition().name(), cb);
        }
    }

    /**
     * 脚本中调用 tools.工具名(...) 时触发，将参数绑定到对应 ToolCallback 并执行。
     */
    @Override
    public Object invokeMethod(String name, Object args) {
        // 内置方法：判断指定类名在当前脚本环境（类加载器）下是否可加载/调用。
        if ("canLoad".equals(name)) {
            return canLoad(resolveClassName(args));
        }
        // 内置方法：返回指定工具的工具信息（名称/描述/输入schema）
        if ("inspect".equals(name)) {
            return inspect(resolveToolName(args));
        }
        // 阶段二：白名单能力注入（client/chatModel/conversation/workers/sharedCache/hostTools）
        if ("inject".equals(name)) {
            return inject(resolveInjectKey(args));
        }
        // 阶段二：运行时注册插件能力（tools.plugin(name, desc)）
        if ("plugin".equals(name)) {
            return plugin(args);
        }

        ToolCallback callback = toolsByName.get(name);

        if (callback == null) {
            throw new MissingMethodException(name, getClass(), toArgsArray(args));
        }
        try {
            String input = buildInput(callback, args);
            String raw = callback.call(input, toolContext);
            return parseResult(raw);
        } catch (Exception e) {
            return Map.of("success", false, "error", "调用 MCP 工具 [" + name + "] 失败: " + e.getMessage());
        }
    }

    /**
     * 脚本中通过 tools.names 获取当前所有可用工具名。
     */
    @Override
    public Object getProperty(String propertyName) {
        if ("names".equals(propertyName)) {
            return new ArrayList<>(toolsByName.keySet());
        }
        // 阶段二：tools.conversation 直接访问工作流上下文门面（无 ToolContext 时返回空门面）
        if ("conversation".equals(propertyName)) {
            return pluginContext != null
                    ? pluginContext.inject("conversation")
                    : new ConversationAccess(toolContext);
        }
        // 阶段二：tools.injectNames 列出可注入能力
        if ("injectNames".equals(propertyName)) {
            return pluginContext != null ? pluginContext.injectNames() : List.of("conversation");
        }
        throw new MissingPropertyException(propertyName, getClass());
    }

    /** 阶段二：白名单能力注入。 */
    private Object inject(String key) {
        if (pluginContext == null) {
            if ("conversation".equals(key)) {
                return new ConversationAccess(toolContext);
            }
            return Map.of("success", false, "error", "pluginContext 未注入，仅支持 conversation");
        }
        return pluginContext.inject(key);
    }

    /** 阶段二：运行时注册插件（tools.plugin(name, desc)）。 */
    @SuppressWarnings("unchecked")
    private Object plugin(Object args) {
        Object[] arr = toArgsArray(args);
        if (arr.length < 2 || !(arr[1] instanceof Map<?, ?> desc)) {
            return Map.of("success", false, "error", "plugin 需要 (name, desc) 两个参数，如 tools.plugin('my-tools', [tools: [...]])");
        }
        String name = String.valueOf(arr[0]);
        try {
            var parsed = GroovyPluginParser.parse(name, (Map<String, Object>) desc,
                    com.xr21.ai.agent.plugins.GroovyPluginLoader.EXTENSION_NAMESPACE,
                    java.nio.file.Path.of(System.getProperty("user.dir")),
                    java.nio.file.Path.of(System.getProperty("user.dir")), true);
            GroovyPluginRegistry.get().register(parsed);
            return Map.of("success", true, "pluginId", name);
        } catch (Exception e) {
            return Map.of("success", false, "error", "插件注册失败: " + e.getMessage());
        }
    }

    /** 从调用参数中解析注入能力 key。 */
    private String resolveInjectKey(Object args) {
        Object[] arr = toArgsArray(args);
        if (arr.length == 0 || arr[0] == null) {
            throw new IllegalArgumentException("inject 需要一个能力 key，如 tools.inject('client')");
        }
        return String.valueOf(arr[0]);
    }

    @Override
    public void setProperty(String propertyName, Object newValue) {
        throw new UnsupportedOperationException("tools 绑定对象为只读，不允许设置属性: " + propertyName);
    }

    @Override
    public MetaClass getMetaClass() {
        return metaClass;
    }

    @Override
    public void setMetaClass(MetaClass metaClass) {
        this.metaClass = metaClass;
    }
    private MetaClass metaClass;

    /**
     * 将 Groovy 调用参数转换为工具输入 JSON。支持：
     * 1) 单个 Map —— 直接作为入参对象（推荐）；
     * 2) 单个值 —— 若工具只有一个参数，自动包裹；
     * 3) 多个位置参数 —— 依据工具输入 schema 的属性顺序按名绑定。
     */
    private String buildInput(ToolCallback callback, Object args) throws Exception {
        if (args == null) {
            return "{}";
        }
        // Groovy 对"单个 Map 参数"的调用会包装成 Object[]{map}，此处展开，
        // 使 tools.xxx([key: value]) 遵循文档语义：把 Map 直接作为工具入参对象。
        if (args instanceof Object[] arr0 && arr0.length == 1 && arr0[0] instanceof Map<?, ?>) {
            return objectMapper.writeValueAsString(toPlain(arr0[0]));
        }
        if (args instanceof Object[] arr) {
            List<String> props = propertyNames(callback);
            if (props.isEmpty()) {
                return objectMapper.writeValueAsString(toPlain(arr));
            }
            if (arr.length > props.size()) {
                throw new IllegalArgumentException("工具 [" + callback.getToolDefinition().name()
                        + "] 期望最多 " + props.size() + " 个位置参数，实际传入 " + arr.length);
            }
            Map<String, Object> obj = new LinkedHashMap<>();
            for (int i = 0; i < arr.length; i++) {
                obj.put(props.get(i), toPlain(arr[i]));
            }
            return objectMapper.writeValueAsString(obj);
        }
        if (args instanceof Map<?, ?> map) {
            return objectMapper.writeValueAsString(toPlain(map));
        }
        List<String> props = propertyNames(callback);
        if (props.size() == 1) {
            return objectMapper.writeValueAsString(Map.of(props.get(0), toPlain(args)));
        }
        return objectMapper.writeValueAsString(toPlain(args));
    }

    /**
     * 解析工具输入 schema，提取参数属性名（保持 JSON 中的顺序）。
     */
    private List<String> propertyNames(ToolCallback callback) {
        return schemaPropsCache.computeIfAbsent(callback.getToolDefinition().name(), n -> {
            try {
                JsonNode root = objectMapper.readTree(callback.getToolDefinition().inputSchema());
                List<String> names = new ArrayList<>();
                root.path("properties").fieldNames().forEachRemaining(names::add);
                return names;
            } catch (Exception e) {
                return List.of();
            }
        });
    }

    /**
     * 解析工具返回的字符串：能解析为 JSON 则返回结构化对象，否则原样返回。
     */
    private Object parseResult(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            return objectMapper.convertValue(node, Object.class);
        } catch (Exception e) {
            return raw;
        }
    }

    /**
     * 将 Groovy 值深度转换为纯 Java 类型（GString 转 String），以便 Jackson 序列化。
     */
    private Object toPlain(Object o) {
        if (o instanceof GString) {
            return o.toString();
        }
        if (o instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, v) -> out.put(String.valueOf(k), toPlain(v)));
            return out;
        }
        if (o instanceof List<?> l) {
            List<Object> out = new ArrayList<>();
            l.forEach(v -> out.add(toPlain(v)));
            return out;
        }
        if (o instanceof Object[] arr) {
            List<Object> out = new ArrayList<>();
            for (Object v : arr) {
                out.add(toPlain(v));
            }
            return out;
        }
        if (o instanceof groovy.lang.Closure) {
            throw new IllegalArgumentException("不支持将 Closure 作为工具参数，请传入普通值");
        }
        return o;
    }

    private Object[] toArgsArray(Object args) {
        if (args instanceof Object[] arr) {
            return arr;
        }
        return args == null ? new Object[0] : new Object[]{args};
    }

    /**
     * 从调用参数中解析出类名字符串（兼容直接传 String 或 Map 包裹）。
     */
    private String resolveClassName(Object args) {
        Object[] arr = toArgsArray(args);
        if (arr.length == 0 || arr[0] == null) {
            throw new IllegalArgumentException("canLoad 需要一个类名参数，如 tools.canLoad('java.lang.String')");
        }
        Object first = arr[0];
        if (first instanceof Map<?, ?> m && m.containsKey("className")) {
            return String.valueOf(m.get("className"));
        }
        if (first instanceof Map<?, ?> m && m.containsKey("class_name")) {
            return String.valueOf(m.get("class_name"));
        }
        return String.valueOf(first);
    }

    /**
     * 内置方法：判断指定类名在当前脚本环境（类加载器链）下是否可加载，
     * 并在可加载时进一步探测是否可实例化调用。
     */
    private Object canLoad(String className) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("className", className);
        try {
            Class<?> clazz = Class.forName(className, false, resolveClassLoader());
            result.put("success", true);
            result.put("classLoader", clazz.getClassLoader() != null ? clazz.getClassLoader().toString() : "bootstrap");
            result.put("isInterface", clazz.isInterface());
            result.put("isAbstract", java.lang.reflect.Modifier.isAbstract(clazz.getModifiers()));
            // 探测是否可实例化（非接口/抽象/枚举，且存在可访问的无参构造器）。
            boolean instantiable = !clazz.isInterface() && !clazz.isEnum()
                    && !java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())
                    && hasAccessibleNoArgConstructor(clazz);
            result.put("instantiable", instantiable);
            // 探测是否可直接调用静态方法（存在任何非 private 静态方法）。
            boolean hasStaticCallable = java.util.Arrays.stream(clazz.getMethods())
                    .anyMatch(m -> java.lang.reflect.Modifier.isStatic(m.getModifiers())
                            && !java.lang.reflect.Modifier.isPrivate(m.getModifiers()));
            result.put("hasStaticCallable", hasStaticCallable);
            return result;
        } catch (Throwable e) {
            result.put("success", false);
            result.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
            return result;
        }
    }

    /**
     * 从调用参数中解析出工具名字符串（兼容直接传 String 或 Map 包裹）。
     */
    private String resolveToolName(Object args) {
        Object[] arr = toArgsArray(args);
        if (arr.length == 0 || arr[0] == null) {
            throw new IllegalArgumentException("inspect 需要一个工具名参数，如 tools.inspect('read_file')");
        }
        Object first = arr[0];
        if (first instanceof Map<?, ?> m && m.containsKey("toolName")) {
            return String.valueOf(m.get("toolName"));
        }
        if (first instanceof Map<?, ?> m && m.containsKey("tool_name")) {
            return String.valueOf(m.get("tool_name"));
        }
        return String.valueOf(first);
    }

    /**
     * 内置方法：返回指定工具的工具信息，包括名称、描述与输入 schema（JSON）。
     */
    private Object inspect(String toolName) {
        ToolCallback callback = toolsByName.get(toolName);
        if (callback == null) {
            return Map.of("success", false, "error", "未找到工具: " + toolName
                    + "（可用 tools.names 查看全部工具）");
        }
        var def = callback.getToolDefinition();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("success", true);
        info.put("name", def.name());
        info.put("description", def.description());
        try {
            JsonNode schema = objectMapper.readTree(def.inputSchema());
            info.put("inputSchema", objectMapper.convertValue(schema, Object.class));
        } catch (Exception e) {
            info.put("inputSchema", def.inputSchema());
            info.put("inputSchemaParseError", e.getMessage());
        }
        return info;
    }

    /**
     * 依次尝试上下文类加载器与当前类所在类加载器，尽可能反映脚本运行环境的可见类。
     */
    private ClassLoader resolveClassLoader() {
        ClassLoader contextCl = Thread.currentThread().getContextClassLoader();
        if (contextCl != null) {
            return contextCl;
        }
        ClassLoader ownCl = getClass().getClassLoader();
        return ownCl != null ? ownCl : ClassLoader.getSystemClassLoader();
    }

    private boolean hasAccessibleNoArgConstructor(Class<?> clazz) {
        try {
            java.lang.reflect.Constructor<?> ctor = clazz.getDeclaredConstructor();
            return !java.lang.reflect.Modifier.isPrivate(ctor.getModifiers());
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}
