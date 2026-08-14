package com.xr21.ai.agent.plugins;

import com.agentclientprotocol.common.ClientSessionOperations;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import lombok.Builder;
import lombok.Getter;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 插件能力上下文容器（设计文档 §5.6）：加载时把宿主可用能力注册进去，
 * 插件脚本通过 {@link #inject(String)} 受控获取，不直接拿容器引用。
 * <p>
 * 注入白名单：只放行预定义 key（client/chatModel/conversation/workers/sharedCache），
 * 未白名单的 key 返回错误，防止脚本窃取任意宿主对象破坏隔离。
 */
@Getter
@Builder
public class PluginContext {

    /** 当前工具调用的 ToolContext（可为 null，插件入口加载期无 ToolContext）。 */
    private final ToolContext toolContext;
    /** ACP 回传（MsgTool/通知）。 */
    private final ClientSessionOperations client;
    /** ChatModel（LLM 钩子/SummarizationHook 等需要）。 */
    private final ChatModel chatModel;
    /** WorkerTool 注入的 worker map。 */
    private final Map<String, ReactAgent> workers;
    /** 共享缓存/跨插件工具（ContextCacheTool 等有状态单例）。 */
    private final Map<String, ToolCallback> sharedTools;
    /** 宿主工具快照（插件闭包 tools.xxx 编排）。 */
    private final List<ToolCallback> hostTools;

    /** 白名单注入：key → 能力。未白名单返回错误 Map。 */
    public Object inject(String key) {
        return switch (key == null ? "" : key) {
            case "client" -> client;
            case "chatModel" -> chatModel;
            case "conversation" -> new ConversationAccess(toolContext);
            case "workers" -> workers == null ? Map.of() : workers;
            case "sharedCache" -> sharedTools == null ? Map.of() : sharedTools;
            case "hostTools" -> hostTools == null ? List.of() : hostTools;
            default -> Map.of("success", false, "error", "unknown capability: " + key);
        };
    }

    /** 可用注入能力清单（供脚本 tools.inject.names 探索）。 */
    public List<String> injectNames() {
        return List.of("client", "chatModel", "conversation", "workers", "sharedCache", "hostTools");
    }

    /** 便捷：以当前 toolContext 创建 conversation 访问（供工具执行期闭包使用）。 */
    public ConversationAccess conversation(ToolContext ctx) {
        return new ConversationAccess(ctx);
    }

    /** 便捷：把当前 ToolContext 换掉，返回新容器（工具执行期每次构建）。 */
    public PluginContext withToolContext(ToolContext ctx) {
        return PluginContext.builder()
                .toolContext(ctx)
                .client(client)
                .chatModel(chatModel)
                .workers(workers)
                .sharedTools(sharedTools)
                .hostTools(hostTools)
                .build();
    }

    /** 兼容 Map 视图（供 Groovy 脚本 bindings 直接访问，保留白名单语义）。 */
    public Map<String, Object> asMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("client", client);
        out.put("chatModel", chatModel);
        out.put("workers", workers == null ? Map.of() : workers);
        out.put("sharedCache", sharedTools == null ? Map.of() : sharedTools);
        out.put("hostTools", hostTools == null ? List.of() : hostTools);
        return out;
    }
}
