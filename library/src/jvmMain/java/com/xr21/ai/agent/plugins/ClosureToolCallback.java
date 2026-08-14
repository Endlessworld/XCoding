package com.xr21.ai.agent.plugins;

import com.xr21.ai.agent.utils.GroovyToolBindings;
import groovy.lang.Closure;
import groovy.lang.Script;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把脚本返回/注册的 run 闭包适配为 Spring AI {@link ToolCallback}（委托 FunctionToolCallback）。
 * <p>
 * 关键：闭包执行时将其 delegate 绑定到宿主 {@link GroovyToolBindings}（含当前 ToolContext），
 * resolveStrategy 设为 DELEGATE_FIRST，并同步更新脚本 binding 的 tools 变量，使闭包跨脚本
 * 存活时仍能访问宿主工具与工作流上下文。
 */
@Slf4j
public class ClosureToolCallback implements ToolCallback {

    private static final String DEFAULT_SCHEMA = """
            {"type":"object","properties":{}}""";

    private final GroovyToolSpec spec;
    private final ToolCallback delegate;

    public ClosureToolCallback(GroovyToolSpec spec) {
        this.spec = spec;
        this.delegate = FunctionToolCallback.builder(spec.getName(), (Map<String, Object> args, ToolContext ctx) -> invoke(args, ctx))
                .description(spec.getDescription())
                .inputSchema(spec.getInputSchema() == null || spec.getInputSchema().isBlank() ? DEFAULT_SCHEMA : spec.getInputSchema())
                .inputType(Map.class)
                .build();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public String call(String toolInput) {
        return delegate.call(toolInput);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return delegate.call(toolInput, toolContext);
    }

    private Object invoke(Map<String, Object> args, ToolContext toolContext) {
        Closure<?> run = spec.getRun();
        if (run == null) {
            return Map.of("success", false, "error", "插件工具 [" + spec.getName() + "] 未提供 run 闭包");
        }
        // 闭包 delegate 绑定宿主上下文：可编排宿主工具、访问 ToolContext；DELEGATE_FIRST 使
        // 直接方法调用/属性先查 bindings，变量名（如 tools）仍回退到脚本 binding。
        PluginContext ctx = GroovyPluginRegistry.get().getPluginContext();
        PluginContext ctxWithTool = ctx != null ? ctx.withToolContext(toolContext) : null;
        GroovyToolBindings bindings = new GroovyToolBindings(GroovyPluginRegistry.get().hostTools(), toolContext, ctxWithTool);
        run.setDelegate(bindings);
        run.setResolveStrategy(Closure.DELEGATE_FIRST);
        // 同步更新脚本 binding 的 tools/conversation 变量，使闭包内 tools.xxx / conversation.xxx
        // 使用带当前 ToolContext 的 bindings 与门面。
        if (run.getOwner() instanceof Script script) {
            script.getBinding().setVariable("tools", bindings);
            script.getBinding().setVariable("conversation", new com.xr21.ai.agent.plugins.ConversationAccess(toolContext));
        }
        try {
            Object result = (args == null || args.isEmpty()) ? run.call() : run.call(args);
            return toPlain(result);
        } catch (Exception e) {
            log.warn("Groovy plugin tool '{}' execution failed", spec.getName(), e);
            return Map.of("success", false, "error", "插件工具 [" + spec.getName() + "] 执行失败: " + e.getMessage());
        }
    }

    /** 深度转换为纯 Java 类型（GString → String），保证 Jackson 序列化安全。 */
    private Object toPlain(Object o) {
        if (o instanceof groovy.lang.GString g) {
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
