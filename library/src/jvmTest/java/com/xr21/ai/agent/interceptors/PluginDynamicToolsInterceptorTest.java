package com.xr21.ai.agent.interceptors;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import com.xr21.ai.agent.plugins.GroovyPlugin;
import com.xr21.ai.agent.plugins.GroovyPluginRegistry;
import com.xr21.ai.agent.plugins.GroovyToolSpec;
import groovy.lang.Closure;
import groovy.lang.GroovyShell;
import org.junit.After;
import org.junit.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.tool.ToolCallback;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 路线 B 动态工具注入单元测试：验证 PluginDynamicToolsInterceptor 将 registry 中
 * 运行期新增的插件工具注入 ModelRequest.dynamicToolCallbacks，且已并入节点工具集的
 * 插件工具不重复注入。
 */
public class PluginDynamicToolsInterceptorTest {

    private final PluginDynamicToolsInterceptor interceptor = new PluginDynamicToolsInterceptor();

    @After
    public void tearDown() {
        GroovyPluginRegistry.get().reset();
    }

    private void registerPluginTool(String toolName) {
        Closure<?> run = (Closure<?>) new GroovyShell().evaluate("return { Map args -> [ok: true] }");
        GroovyToolSpec spec = GroovyToolSpec.builder()
                .name(toolName)
                .description("动态注入测试工具")
                .inputSchema("{\"type\":\"object\",\"properties\":{}}")
                .run(run)
                .build();
        GroovyPlugin plugin = GroovyPlugin.builder()
                .name("hot-plugin-" + toolName)
                .version("1.0.0")
                .namespace("com.xr21.agent")
                .root(Path.of("."))
                .dataDir(Path.of("."))
                .legacy(true)
                .tools(List.of(spec))
                .build();
        GroovyPluginRegistry.get().register(plugin);
    }

    /** 无插件工具时：请求原样透传，不注入。 */
    @Test
    public void passthroughWhenNoPluginTools() {
        ModelRequest request = ModelRequest.builder()
                .tools(List.of("read_file"))
                .build();
        ModelResponse response = interceptor.interceptModel(request, req -> {
            assertTrue("无插件工具时 dynamicToolCallbacks 应为空",
                    req.getDynamicToolCallbacks() == null || req.getDynamicToolCallbacks().isEmpty());
            return ModelResponse.of(new AssistantMessage("ok"));
        });
        assertEquals("ok", ((org.springframework.ai.chat.messages.AssistantMessage) response.getMessage()).getText());
    }

    /** 运行期注册插件工具后：应注入 dynamicToolCallbacks。 */
    @Test
    public void injectsRuntimeRegisteredPluginTools() {
        registerPluginTool("hot_weather");
        ModelRequest request = ModelRequest.builder()
                .tools(List.of("read_file", "write_file"))
                .build();
        interceptor.interceptModel(request, req -> {
            List<ToolCallback> dynamic = req.getDynamicToolCallbacks();
            assertTrue("应注入动态工具", dynamic != null && !dynamic.isEmpty());
            assertTrue("动态工具应包含 hot_weather",
                    dynamic.stream().anyMatch(tc -> tc.getToolDefinition().name().equals("hot_weather")));
            return ModelResponse.of(new AssistantMessage("ok"));
        });
    }

    /** 已并入节点工具集的插件工具：不重复注入。 */
    @Test
    public void skipsToolsAlreadyInNodeToolset() {
        registerPluginTool("already_merged");
        ModelRequest request = ModelRequest.builder()
                .tools(List.of("already_merged", "read_file"))
                .build();
        interceptor.interceptModel(request, req -> {
            List<ToolCallback> dynamic = req.getDynamicToolCallbacks();
            assertTrue("已在节点工具集的工具不应重复注入",
                    dynamic == null || dynamic.stream().noneMatch(tc -> tc.getToolDefinition().name().equals("already_merged")));
            return ModelResponse.of(new AssistantMessage("ok"));
        });
    }

    /** 部分已并入、部分新增：只注入新增的。 */
    @Test
    public void injectsOnlyNewTools() {
        registerPluginTool("hot_a");
        registerPluginTool("hot_b");
        ModelRequest request = ModelRequest.builder()
                .tools(List.of("hot_a", "read_file"))
                .build();
        interceptor.interceptModel(request, req -> {
            List<ToolCallback> dynamic = req.getDynamicToolCallbacks();
            assertTrue("应注入 hot_b", dynamic.stream().anyMatch(tc -> tc.getToolDefinition().name().equals("hot_b")));
            assertTrue("hot_a 已在节点工具集，不应重复注入",
                    dynamic.stream().noneMatch(tc -> tc.getToolDefinition().name().equals("hot_a")));
            return ModelResponse.of(new AssistantMessage("ok"));
        });
    }
}
