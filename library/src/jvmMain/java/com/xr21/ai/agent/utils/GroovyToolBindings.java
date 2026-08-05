package com.xr21.ai.agent.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 */
public class GroovyToolBindings implements GroovyObject {

    private final Map<String, ToolCallback> toolsByName = new LinkedHashMap<>();
    private final ToolContext toolContext;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, List<String>> schemaPropsCache = new ConcurrentHashMap<>();

    public GroovyToolBindings(List<ToolCallback> tools, ToolContext toolContext) {
        this.toolContext = toolContext;
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
        throw new MissingPropertyException(propertyName, getClass());
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
}
