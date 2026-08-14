package com.xr21.ai.agent.plugins;

import com.xr21.ai.agent.utils.Json;
import groovy.lang.Closure;
import groovy.lang.GString;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把脚本返回的"插件描述 Map"解析为 {@link GroovyPlugin}。
 * <p>
 * 契约（设计文档 §4）：{@code [name, description, version, tools[], hooks[], interceptors[]]}；
 * 脚本返回的 name 仅作参考，插件名以 plugin.json（或 legacy 合成）为准。
 */
@Slf4j
public final class GroovyPluginParser {

    private GroovyPluginParser() {
    }

    @SuppressWarnings("unchecked")
    public static GroovyPlugin parse(String name, Map<String, Object> desc, String namespace,
                                     Path root, Path dataDir, boolean legacy) {
        String version = desc.get("version") == null ? "0.0.0" : String.valueOf(desc.get("version"));
        String description = desc.get("description") == null ? "" : String.valueOf(desc.get("description"));

        List<GroovyToolSpec> tools = new ArrayList<>();
        if (desc.get("tools") instanceof List<?> toolList) {
            for (Object o : toolList) {
                if (o instanceof Map<?, ?> m) {
                    tools.add(parseTool((Map<String, Object>) m));
                }
            }
        }
        List<GroovyHookSpec> hooks = new ArrayList<>();
        if (desc.get("hooks") instanceof List<?> hookList) {
            for (Object o : hookList) {
                if (o instanceof Map<?, ?> m) {
                    hooks.add(parseHook((Map<String, Object>) m));
                }
            }
        }
        List<GroovyInterceptorSpec> interceptors = new ArrayList<>();
        if (desc.get("interceptors") instanceof List<?> interceptorList) {
            for (Object o : interceptorList) {
                if (o instanceof Map<?, ?> m) {
                    interceptors.add(parseInterceptor((Map<String, Object>) m));
                }
            }
        }
        return GroovyPlugin.builder()
                .name(name)
                .version(version)
                .description(description)
                .namespace(namespace)
                .root(root)
                .dataDir(dataDir)
                .legacy(legacy)
                .tools(tools)
                .hooks(hooks)
                .interceptors(interceptors)
                .build();
    }

    private static GroovyToolSpec parseTool(Map<String, Object> m) {
        String toolName = String.valueOf(m.get("name"));
        String description = m.get("description") == null ? "" : String.valueOf(m.get("description"));
        Object schema = m.get("inputSchema");
        String inputSchema = schema == null ? null
                : (schema instanceof String s ? s : Json.toJson(toPlain(schema)));
        Object run = m.get("run");
        if (!(run instanceof Closure<?> closure)) {
            throw new IllegalArgumentException("工具 [" + toolName + "] 缺少 run 闭包");
        }
        return GroovyToolSpec.builder().name(toolName).description(description)
                .inputSchema(inputSchema).run(closure).build();
    }

    private static GroovyHookSpec parseHook(Map<String, Object> m) {
        String hookName = String.valueOf(m.get("name"));
        String position = m.get("position") == null ? "BEFORE_AGENT" : String.valueOf(m.get("position"));
        Object run = m.get("run");
        if (!(run instanceof Closure<?> closure)) {
            throw new IllegalArgumentException("钩子 [" + hookName + "] 缺少 run 闭包");
        }
        return GroovyHookSpec.builder().name(hookName).position(position).run(closure).build();
    }

    private static GroovyInterceptorSpec parseInterceptor(Map<String, Object> m) {
        String interceptorName = String.valueOf(m.get("name"));
        String type = m.get("type") == null ? "model" : String.valueOf(m.get("type"));
        Object apply = m.get("apply");
        if (!(apply instanceof Closure<?> closure)) {
            throw new IllegalArgumentException("拦截器 [" + interceptorName + "] 缺少 apply 闭包");
        }
        return GroovyInterceptorSpec.builder().name(interceptorName).type(type).apply(closure).build();
    }

    /** 深度转换为纯 Java 类型（GString → String），保证 Jackson 序列化安全。 */
    static Object toPlain(Object o) {
        if (o instanceof GString g) {
            return g.toString();
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
        return o;
    }
}
