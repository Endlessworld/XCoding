package com.xr21.ai.agent;

import com.agentclientprotocol.sdk.agent.AcpAgent;
import com.agentclientprotocol.sdk.agent.AcpSyncAgent;
import com.agentclientprotocol.sdk.agent.SyncPromptContext;
import com.agentclientprotocol.sdk.agent.transport.StdioAcpAgentTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema.*;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.xr21.ai.agent.config.AiModels;
import com.xr21.ai.agent.entity.AgentOutput;
import com.xr21.ai.agent.tools.ToolKindFind;
import com.xr21.ai.agent.utils.SinksUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.xr21.ai.agent.utils.ToolsUtil.describeMcpServer;

/**
 * ACP 协议包装的 LocalAgent
 * 支持通过 ACP (Agent Client Protocol) 与客户端通信
 */
@Slf4j
public class AcpLocalAgent {

    private final Map<String, AcpSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, Agent> agents = new ConcurrentHashMap<>();
    // Store MCP servers per session
    private static final Map<String, List<McpServer>> sessionMcpServers = new ConcurrentHashMap<>();

    private final SessionModeState sessionModeState = new SessionModeState("chat", List.of(new SessionMode("Agent", "Agent", "智能体模式"), new SessionMode("Plan", "Plan", "规划执行模式")));
    private final SessionModelState sessionModelState = new SessionModelState(AiModels.KIMI_K2_5.getModelName(), AiModels.availableModes());
    public static void main(String[] args) {
        AcpLocalAgent acpAgent = new AcpLocalAgent();
        acpAgent.start();
    }

    /**
     * 构建并启动 ACP Agent
     */
    public void start() {
        System.err.println("[AcpLocalAgent] Starting...");

        var transport = new StdioAcpAgentTransport();

        AcpSyncAgent acpAgent = AcpAgent.sync(transport)
                // 初始化处理器
                .initializeHandler(req -> {
                    System.err.println("[AcpLocalAgent] Client initialized, protocol version: " + req.protocolVersion());
                    System.err.println("[AcpLocalAgent] Client capabilities: " + req.clientCapabilities());
                    return InitializeResponse.ok();
                })
                // 新会话处理器
                .newSessionHandler(req -> {
                    String sessionId = UUID.randomUUID().toString();
                    String threadId = "acp-session-" + System.currentTimeMillis();
                    sessions.put(sessionId, new AcpSession(sessionId, threadId));
                    System.err.println("[AcpLocalAgent] New session created: " + sessionId);
                    System.err.println("[AcpLocalAgent] Working directory: " + req.cwd());

                    // Store MCP servers for this session
                    List<McpServer> mcpServers = req.mcpServers();
                    if (mcpServers != null && !mcpServers.isEmpty()) {
                        sessionMcpServers.put(sessionId, new ArrayList<>(mcpServers));
                        System.err.println("[McpAgent] Received " + mcpServers.size() + " MCP server(s)");
                        for (McpServer server : mcpServers) {
                            System.err.println("[McpAgent]   - " + describeMcpServer(server));
                        }
                    } else {
                        System.err.println("[McpAgent] No MCP servers provided");
                    }
                    agents.put(sessionId, LocalAgent.createAgent(req.cwd(),mcpServers));
                    return new NewSessionResponse(sessionId, sessionModeState, sessionModelState);
                })
                // 加载会话处理器
                .loadSessionHandler(req -> {
                    System.err.println("[AcpLocalAgent] Load session: " + req.sessionId());
                    if (sessions.containsKey(req.sessionId())) {
                        System.err.println("[AcpLocalAgent] Session found");
                        Optional<Checkpoint> checkpointOpt = LocalAgent.fileSystemSaver.get(RunnableConfig.builder()
                                .threadId(sessions.get(req.sessionId()).threadId)
                                .build());
                        String modelId = checkpointOpt.get()
                                .getState()
                                .getOrDefault("model", AiModels.KIMI_K2_5.getModelName())
                                .toString();
                        var modelState = checkpointOpt.map(Checkpoint::getState)
                                .map(e -> new SessionModelState(modelId, AiModels.availableModes()))
                                .orElse(sessionModelState);
                        return new LoadSessionResponse(sessionModeState, modelState);
                    }
                    System.err.println("[AcpLocalAgent] Session not found");
                    return new LoadSessionResponse(sessionModeState, sessionModelState);
                })
                // 提示处理器 - 核心逻辑
                .promptHandler(this::handlePrompt)
                // 取消处理器
                .cancelHandler(req -> {
//                    System.err.println("[AcpLocalAgent] Cancel request for session: " + req.sessionId());
                    // 这里可以添加取消正在处理的请求的逻辑
                })

                .build();

//        System.err.println("[AcpLocalAgent] Ready, waiting for messages...");
        acpAgent.run();
//        System.err.println("[AcpLocalAgent] Shutdown.");
    }

    /**
     * 处理用户提示
     */
    @SneakyThrows
    private PromptResponse handlePrompt(PromptRequest req, SyncPromptContext context) {
        String sessionId = req.sessionId();
        AcpSession session = sessions.get(sessionId);

        if (session == null) {
            context.sendMessage("Error: Session not found");
            return PromptResponse.endTurn();
        }
        var agent = agents.get(sessionId);
        if (agent == null) {
            context.sendMessage("Error: agent not created");
            return PromptResponse.endTurn();
        }
        // 提取用户输入文本
        String userInput = extractTextFromPrompt(req);
        System.err.println("[AcpLocalAgent] Received: " + userInput);

        // 记录到历史
        session.history.add("User: " + userInput);

        // 发送思考过程
        context.sendThought("正在处理您的请求...");

        // 使用 LocalAgent 处理输入
        StringBuilder responseBuilder = new StringBuilder();

        Flux<AgentOutput<Object>> flux = SinksUtil.toFlux(agent, userInput, session.threadId, null, new HashMap<>());

        flux.doOnNext(output -> {
            if (output.getChunk() != null) {
                String chunk = output.getChunk();
                responseBuilder.append(chunk);
                // 流式发送消息片段给客户端
                context.sendMessage(chunk);
            }
            if (output.getThink() != null) {
                String think = output.getThink();
                // 流式发送消息片段给客户端
                context.sendThought(think);
            }

            // 处理工具调用
            if (output.getMessage() instanceof org.springframework.ai.chat.messages.AssistantMessage message) {
                if (!CollectionUtils.isEmpty(message.getToolCalls())) {
                    for (var toolCall : message.getToolCalls()) {
                        // 发送工具调用更新
                        context.sendUpdate(sessionId, new ToolCall("tool_call", toolCall.id(), toolCall.name(), ToolKindFind.find(toolCall.name()), ToolCallStatus.IN_PROGRESS, List.of(new ToolCallContentBlock("", new TextContent(toolCall.arguments()))), List.of(new ToolCallLocation("D:\\local-github\\ai-agents", 1)), toolCall.arguments(), null, null));
                    }
                }
            }
            // 处理工具调用
            if (output.getMessage() instanceof org.springframework.ai.chat.messages.ToolResponseMessage message) {
                if (!CollectionUtils.isEmpty(message.getResponses())) {
                    for (var responses : message.getResponses()) {
                        // 发送工具调用更新
                        context.sendUpdate(sessionId, new ToolCall("tool_call", responses.id(), responses.name(), ToolKindFind.find(responses.name()), ToolCallStatus.IN_PROGRESS, List.of(), null, null, responses, message.getMetadata()));
                    }
                }
            }

            // 处理工具调用反馈
            if (!CollectionUtils.isEmpty(output.getToolFeedbacks())) {
                for (var toolFeedback : output.getToolFeedbacks()) {
                    String feedbackMsg = String.format("[工具] %s: %s", toolFeedback.getName(), toolFeedback.getDescription());
                    context.sendThought(feedbackMsg);
                    // 发送工具完成更新
                    context.sendUpdate(sessionId, new ToolCallUpdateNotification("tool_call_update", UUID.randomUUID()
                            .toString(), toolFeedback.getName(), ToolKind.THINK, ToolCallStatus.COMPLETED, List.of(), null, null, null, null));
                }
            }
        }).blockLast();

        String finalResponse = responseBuilder.toString();
        session.history.add("Assistant: " + finalResponse);

        System.err.println("[AcpLocalAgent] Response complete, length: " + finalResponse.length());
        return PromptResponse.endTurn();
    }

    /**
     * 从 PromptRequest 中提取文本
     */
    private String extractTextFromPrompt(PromptRequest req) {
        StringBuilder sb = new StringBuilder();
        for (ContentBlock content : req.prompt()) {
            if (content instanceof TextContent textContent) {
                sb.append(textContent.text());
            }
        }
        return sb.toString();
    }

    /**
     * ACP 会话状态
     */
    private static class AcpSession {
        final String sessionId;
        final String threadId;
        final List<String> history;

        AcpSession(String sessionId, String threadId) {
            this.sessionId = sessionId;
            this.threadId = threadId;
            this.history = new ArrayList<>();
        }
    }

}
