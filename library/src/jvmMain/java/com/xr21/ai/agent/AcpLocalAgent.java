package com.xr21.ai.agent;

import com.agentclientprotocol.sdk.agent.AcpAgent;
import com.agentclientprotocol.sdk.agent.AcpSyncAgent;
import com.agentclientprotocol.sdk.agent.SyncPromptContext;
import com.agentclientprotocol.sdk.agent.transport.StdioAcpAgentTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema;
import com.agentclientprotocol.sdk.spec.AcpSchema.*;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.xr21.ai.agent.config.AiModels;
import com.xr21.ai.agent.entity.AgentOutput;
import com.xr21.ai.agent.tools.ShellTools;
import com.xr21.ai.agent.tools.ToolKindFind;
import com.xr21.ai.agent.utils.SinksUtil;
import com.xr21.ai.agent.utils.ToolsUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.xr21.ai.agent.utils.ToolsUtil.describeMcpServer;

/**
 * ACP 协议包装LocalAgent
 * 支持通过 ACP (Agent Client Protocol) 与客户端通信
 */
@Slf4j
public class AcpLocalAgent {

    // Store MCP servers per session
    private static final Map<String, List<McpServer>> sessionMcpServers = new ConcurrentHashMap<>();
    private final Map<String, AcpSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, Agent> agents = new ConcurrentHashMap<>();
    private final Map<String, CancellableRequest> activeRequests = new ConcurrentHashMap<>();
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
        log.info("[AcpLocalAgent] Starting...");

        var transport = new StdioAcpAgentTransport();

        AcpSyncAgent acpAgent = AcpAgent.sync(transport)
                // 初始化处理器
                .initializeHandler(req -> {
                    log.info("[AcpLocalAgent] Client initialized, protocol version:  {}", req.protocolVersion());
                    log.info("[AcpLocalAgent] Client capabilities:  {}", req.clientCapabilities());
                    return InitializeResponse.ok();
                })
                // 新会话处理器
                .newSessionHandler(req -> {
                    String sessionId = UUID.randomUUID().toString();
                    String threadId = "acp-session-" + System.currentTimeMillis();
                    String cwd = req.cwd() != null ? req.cwd() : System.getProperty("user.dir");
                    sessions.put(sessionId, new AcpSession(sessionId, threadId, cwd));
                    log.info("[AcpLocalAgent] New session created:  {}", sessionId);
                    log.info("[AcpLocalAgent] Working directory:  {}", cwd);

                    // Store MCP servers for this session
                    List<McpServer> mcpServers = req.mcpServers();
                    if (mcpServers != null && !mcpServers.isEmpty()) {
                        sessionMcpServers.put(sessionId, new ArrayList<>(mcpServers));
                        log.info("[McpAgent] Received  {}", mcpServers.size() + " MCP server(s)");
                        for (McpServer server : mcpServers) {
                            log.info("[McpAgent]   -  {}", describeMcpServer(server));
                        }
                    } else {
                        log.info("[McpAgent] No MCP servers provided");
                    }
                    agents.put(sessionId, LocalAgent.createAgent(req.cwd(), mcpServers));
                    return new NewSessionResponse(sessionId, sessionModeState, sessionModelState);
                })
                // 加载会话处理�?
                .loadSessionHandler(req -> {
                    log.info("[AcpLocalAgent] Load session:  {}", req.sessionId());
                    if (sessions.containsKey(req.sessionId())) {
                        log.info("[AcpLocalAgent] Session found");
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
                    log.info("[AcpLocalAgent] Session not found");
                    return new LoadSessionResponse(sessionModeState, sessionModelState);
                })
                // 提示处理�?- 核心逻辑
                .promptHandler(this::handlePrompt)
                // 取消处理�?
                .cancelHandler(this::handleCancel)

                .build();

        log.info("[AcpLocalAgent] Ready, waiting for messages...");
        acpAgent.run();
        log.info("[AcpLocalAgent] Shutdown.");
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
        log.info("[AcpLocalAgent] Received:  {}", userInput);

        // 生成请求ID
        String requestId = "req_" + System.currentTimeMillis() + "_" + sessionId;

        // 记录到历�?
        session.history.add("User: " + userInput);

        // 发送思考过�?
        context.sendThought("正在处理您的请求...");

        // 使用 LocalAgent 处理输入
        StringBuilder responseBuilder = new StringBuilder();

        Flux<AgentOutput<Object>> flux = SinksUtil.toFlux(agent, userInput, session.threadId, null, new HashMap<>());

        // 注册可取消的请求
        Thread currentThread = Thread.currentThread();
        registerCancellableRequest(requestId, sessionId, currentThread, flux);

        flux.doOnNext(output -> {
            // 检查请求是否已被取消
            if (isRequestCancelled(requestId)) {
                log.info("[AcpLocalAgent] Request {} was cancelled, stopping processing", requestId);
                throw new RuntimeException("Request cancelled");
            }
            // 处理文本输出
            if (output.getChunk() != null) {
                String chunk = output.getChunk();
                responseBuilder.append(chunk);
                // 流式发送消息片段给客户�?
                context.sendMessage(chunk);
            }

            // 处理思考过�?
            if (output.getThink() != null) {
                String think = output.getThink();
                // 流式发送思考过程给客户�?
                context.sendThought(think);
            }

            // 处理工具调用请求 (AssistantMessage with ToolCalls)
            if (output.getMessage() instanceof org.springframework.ai.chat.messages.AssistantMessage message) {
                if (!CollectionUtils.isEmpty(message.getToolCalls())) {
                    for (var toolCall : message.getToolCalls()) {
                        String toolCallId = toolCall.id();
                        String toolName = toolCall.name();
                        String arguments = toolCall.arguments();
                        // 获取工具类型
                        ToolKind toolKind = ToolKindFind.find(toolName);
                        // 构建工具调用内容
                        List<ToolCallContent> contentList = new ArrayList<>();
                        if (StringUtils.hasText(arguments)) {
                            contentList.add(new ToolCallContentBlock("content", new TextContent(arguments)));
                        }
                        // 发送工具调用开始通知 (status: PENDING)
                        // ToolCall 是完整的工具调用记录，包含输入参
                        context.sendUpdate(sessionId, new ToolCall("tool_call", toolCallId, toolName, toolKind, ToolCallStatus.IN_PROGRESS, contentList, null,  // locations
                                arguments,  // rawInput
                                null,  // rawOutput - 执行后才有
                                message.getMetadata()));

                        // 将工具调用添加到请求跟踪
                        addToolCallToRequest(requestId, toolCallId);

                        log.info("[AcpLocalAgent] Tool call started:  {}", toolName + " (id: " + toolCallId + ")");
                    }
                }
            }

            // 处理工具调用响应 (ToolResponseMessage - 工具执行完成)
            if (output.getMessage() instanceof org.springframework.ai.chat.messages.ToolResponseMessage message) {
                if (!CollectionUtils.isEmpty(message.getResponses())) {
                    for (var response : message.getResponses()) {
                        String toolCallId = response.id();
                        String toolName = response.name();
                        String responseData = response.responseData();

                        // 解析 ToolResult 格式的响应数据
                        ToolsUtil.ToolResultData resultData = ToolsUtil.parseToolResult(responseData);

                        // 构建位置信息
                        List<ToolCallLocation> locations = resultData.locations;
                        ToolCallStatus status = resultData.success ? ToolCallStatus.COMPLETED : (resultData.error != null ? ToolCallStatus.FAILED : ToolCallStatus.COMPLETED);

                        // 发送工具调用更新通知 (status: COMPLETED/FAILED)
                        context.sendUpdate(sessionId, new ToolCallUpdateNotification("tool_call_update", toolCallId, toolName, ToolKindFind.find(toolName), status, null, locations,  // 位置信息
                                null,
                                responseData,  // rawOutput
                                null));

                        // 从请求跟踪中移除工具调用
                        removeToolCallFromRequest(requestId, toolCallId);

                        log.info("[AcpLocalAgent] Tool call completed:  {}", toolName + " (id: " + toolCallId + ", status: " + status + ")");
                    }
                }
            }

            // 处理工具调用反馈 (来自 HumanInTheLoop 等机�?
            if (!CollectionUtils.isEmpty(output.getToolFeedbacks())) {
                for (var toolFeedback : output.getToolFeedbacks()) {
                    String toolName = toolFeedback.getName();
                    String description = toolFeedback.getDescription();
                    String feedbackMsg = String.format("[工具] %s: %s", toolName, description);

                    // 发送思考过
                    context.sendThought(feedbackMsg);

                    // 如果toolCallId，发送更新通知
                    String toolCallId = toolFeedback.getId();
                    if (toolCallId != null) {
                        context.sendUpdate(sessionId, new ToolCallUpdateNotification("tool_call_update", toolCallId, toolName, ToolKind.THINK, ToolCallStatus.COMPLETED, List.of(new ToolCallContentBlock("content", new TextContent(description))), null, null, null, null));
                    }
                }
            }
        }).doOnError(error -> {
            log.warn("[AcpLocalAgent] Request {} failed with error: {}", requestId, error.getMessage());
            if (error.getMessage() != null && error.getMessage().contains("cancelled")) {
                context.sendThought("请求已被用户取消");
            } else {
                context.sendThought("处理请求时发生错误: " + error.getMessage());
            }
        }).doFinally(signal -> {
            // 无论成功还是失败，都注销请求
            unregisterCancellableRequest(requestId);
            log.info("[AcpLocalAgent] Request {} completed with signal: {}", requestId, signal);
        }).blockLast();

        String finalResponse = responseBuilder.toString();
        session.history.add("Assistant: " + finalResponse);

        log.info("[AcpLocalAgent] Response complete, length:  {}", finalResponse.length());
        return PromptResponse.endTurn();
    }

    /**
     * �?PromptRequest 中提取文�?
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
     * 处理取消请求
     */
    private void handleCancel(AcpSchema.CancelNotification req) {
        String sessionId = req.sessionId();
        log.info("[AcpLocalAgent] Cancel request for session:  {}", sessionId);

        // 取消该会话的所有活跃请求
        List<CancellableRequest> requestsToCancel = new ArrayList<>();
        for (Map.Entry<String, CancellableRequest> entry : activeRequests.entrySet()) {
            if (entry.getValue().sessionId.equals(sessionId)) {
                requestsToCancel.add(entry.getValue());
            }
        }

        // 执行取消操作
        for (CancellableRequest request : requestsToCancel) {
            log.info("[AcpLocalAgent] Cancelling request: {} for session: {}", request.requestId, sessionId);
            request.cancel();
            activeRequests.remove(request.requestId);
        }

        // 清理会话资源
        cleanupSessionResources(sessionId);

        log.info("[AcpLocalAgent] Cancelled {} active requests for session: {}", requestsToCancel.size(), sessionId);
    }

    /**
     * Tool call tracking info
     */
    private static class ToolCallInfo {
        final String toolCallId;
        final String toolName;
        final String arguments;
        final long startTime;

        ToolCallInfo(String toolCallId, String toolName, String arguments) {
            this.toolCallId = toolCallId;
            this.toolName = toolName;
            this.arguments = arguments;
            this.startTime = System.currentTimeMillis();
        }
    }

    /**
     * 清理会话资源
     */
    private void cleanupSessionResources(String sessionId) {
        // 清理 MCP 服务器
        sessionMcpServers.remove(sessionId);

        // 清理代理
        agents.remove(sessionId);

        // 清理会话
        sessions.remove(sessionId);

        // 清理后台进程（从 ShellTools）
        cleanupBackgroundProcesses(sessionId);

        log.info("[AcpLocalAgent] Cleaned up resources for session: {}", sessionId);
    }

    /**
     * 清理后台进程
     */
    private void cleanupBackgroundProcesses(String sessionId) {
        try {
            Map<String, ShellTools.BackgroundProcess> backgroundProcesses = ShellTools.backgroundProcesses;
            // 查找并清理与该会话相关的进程
            List<String> processesToRemove = new ArrayList<>();
            for (Map.Entry<String, ShellTools.BackgroundProcess> entry : backgroundProcesses.entrySet()) {
                String shellId = entry.getKey();
                // 假设 shellId 包含会话信息或我们可以通过其他方式关联
                // 这里简单清理所有进程，实际应用中可能需要更精确的关联
                processesToRemove.add(shellId);
                try {
                    // 调用 KillShell 工具
                    ShellTools.builder().build().killShell(shellId);
                    log.info("[AcpLocalAgent] Killed background shell: {} for session: {}", shellId, sessionId);
                } catch (Exception e) {
                    log.warn("[AcpLocalAgent] Failed to kill shell {}: {}", shellId, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("[AcpLocalAgent] Failed to cleanup background processes: {}", e.getMessage());
        }
    }

    /**
     * 注册可取消的请求
     */
    private void registerCancellableRequest(String requestId, String sessionId, Thread executionThread, Flux<?> flux) {
        CancellableRequest request = new CancellableRequest(requestId, sessionId, executionThread, flux);
        activeRequests.put(requestId, request);
    }

    /**
     * 注销可取消的请求
     */
    private void unregisterCancellableRequest(String requestId) {
        activeRequests.remove(requestId);
    }

    /**
     * 为请求添加工具调用
     */
    private void addToolCallToRequest(String requestId, String toolCallId) {
        CancellableRequest request = activeRequests.get(requestId);
        if (request != null) {
            request.addToolCall(toolCallId);
        }
    }

    /**
     * 从请求中移除工具调用
     */
    private void removeToolCallFromRequest(String requestId, String toolCallId) {
        CancellableRequest request = activeRequests.get(requestId);
        if (request != null) {
            request.removeToolCall(toolCallId);
        }
    }

    /**
     * 检查请求是否已被取消
     */
    private boolean isRequestCancelled(String requestId) {
        CancellableRequest request = activeRequests.get(requestId);
        return request != null && request.cancelled;
    }

    /**
     * ACP 会话状态
     */
    private static class AcpSession {
        final String sessionId;
        final String threadId;
        final String cwd;
        final List<String> history;

        AcpSession(String sessionId, String threadId, String cwd) {
            this.sessionId = sessionId;
            this.threadId = threadId;
            this.cwd = cwd;
            this.history = new ArrayList<>();
        }


    }

    /**
     * 可取消的请求信息
     */
    private static class CancellableRequest {
        final String requestId;
        final String sessionId;
        final Thread executionThread;
        final Flux<?> flux;
        final List<String> activeToolCallIds;
        final long startTime;
        volatile boolean cancelled;

        CancellableRequest(String requestId, String sessionId, Thread executionThread, Flux<?> flux) {
            this.requestId = requestId;
            this.sessionId = sessionId;
            this.executionThread = executionThread;
            this.flux = flux;
            this.activeToolCallIds = new ArrayList<>();
            this.startTime = System.currentTimeMillis();
            this.cancelled = false;
        }

        void cancel() {
            this.cancelled = true;

            // 中断执行线程
            if (executionThread != null && executionThread.isAlive()) {
                executionThread.interrupt();
            }

            // 取消 Flux
            if (flux != null) {
                try {
                    flux.blockFirst();
                } catch (Exception e) {
                    // 忽略取消异常
                }
            }
        }

        void addToolCall(String toolCallId) {
            synchronized (activeToolCallIds) {
                activeToolCallIds.add(toolCallId);
            }
        }

        void removeToolCall(String toolCallId) {
            synchronized (activeToolCallIds) {
                activeToolCallIds.remove(toolCallId);
            }
        }

        List<String> getActiveToolCalls() {
            synchronized (activeToolCallIds) {
                return new ArrayList<>(activeToolCallIds);
            }
        }
    }

}


