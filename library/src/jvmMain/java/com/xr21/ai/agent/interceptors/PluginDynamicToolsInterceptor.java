package com.xr21.ai.agent.interceptors;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import com.xr21.ai.agent.plugins.GroovyPluginRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 路线 B：运行时热挂载 —— 每轮模型调用前把当前已注册插件工具注入 ModelRequest.dynamicToolCallbacks。
 * <p>
 * 框架 2.0.0-M1.1 原生支持动态工具：{@code AgentLlmNode} 会把 dynamicToolCallbacks 合并进本轮
 * 模型调用，并通过 {@code RunnableConfig.DYNAMIC_TOOL_CALLBACKS_METADATA_KEY} 传给
 * {@code AgentToolNode} 解析执行。本拦截器使"脚本执行中途用 tools.plugin(...) 注册的新工具，
 * 下一轮模型调用即对模型可见、可被调用执行"（真·常驻热挂载）。
 * <p>
 * 去重策略：跳过已在 ModelRequest.tools（节点工具名列表）中的工具——构建期并入的插件工具
 * （路线 A）已在节点工具集，无需重复注入；只注入运行期新增的插件工具。
 */
@Slf4j
public class PluginDynamicToolsInterceptor extends ModelInterceptor {

    @Override
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
        List<ToolCallback> pluginTools = GroovyPluginRegistry.get().toolCallbacks();
        if (pluginTools == null || pluginTools.isEmpty()) {
            return handler.call(request);
        }
        Set<String> nodeToolNames = new HashSet<>(request.getTools() == null ? List.of() : request.getTools());
        List<ToolCallback> toInject = new ArrayList<>();
        for (ToolCallback cb : pluginTools) {
            if (cb != null && !nodeToolNames.contains(cb.getToolDefinition().name())) {
                toInject.add(cb);
            }
        }
        if (toInject.isEmpty()) {
            return handler.call(request);
        }
        log.debug("PluginDynamicTools: injecting {} plugin tools into model request", toInject.size());
        ModelRequest enhancedRequest = ModelRequest.builder(request).dynamicToolCallbacks(toInject).build();
        return handler.call(enhancedRequest);
    }

    @Override
    public String getName() {
        return "plugin_dynamic_tools";
    }
}
