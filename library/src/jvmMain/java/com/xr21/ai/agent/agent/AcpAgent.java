package com.xr21.ai.agent.agent;

import com.agentclientprotocol.sdk.agent.SyncPromptContext;
import com.agentclientprotocol.sdk.annotation.*;
import com.agentclientprotocol.sdk.spec.AcpSchema;
import com.agentclientprotocol.sdk.spec.AcpSchema.*;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.xr21.ai.agent.config.AiModels;
import com.xr21.ai.agent.entity.AcpSession;
import com.xr21.ai.agent.entity.AgentOutput;
import com.xr21.ai.agent.entity.CancellableRequest;
import com.xr21.ai.agent.tools.ShellTools;
import com.xr21.ai.agent.tools.ToolKindFind;
import com.xr21.ai.agent.utils.SinksUtil;
import com.xr21.ai.agent.utils.ToolsUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeType;
import org.springframework.util.StringUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static com.xr21.ai.agent.utils.ToolsUtil.describeMcpServer;

@Slf4j
@com.agentclientprotocol.sdk.annotation.AcpAgent
public class AcpAgent {

    private static final Map<String, List<McpServer>> sessionMcpServers = new ConcurrentHashMap<>();
    private final Map<String, AcpSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, RunnableConfig> sessionsRunnableConfig = new ConcurrentHashMap<>();
    private final Map<String, CancellableRequest> activeRequests = new ConcurrentHashMap<>();
    private final SessionModeState sessionModeState = new SessionModeState("chat", List.of(new SessionMode("Agent", "Agent", "智能体模式"), new SessionMode("Plan", "Plan", "规划执行模式")));
    private final Supplier<SessionModelState> sessionModelStateSupplier = () -> new SessionModelState(AiModels.KIMI_K2_5.getModelName(), AiModels.availableModes());

    @Initialize
    InitializeResponse init(AcpSchema.InitializeRequest request) {
        log.info("[AcpAgent] Client initialized, protocol version:  {}", request.protocolVersion());
        log.info("[AcpAgent] Client capabilities:  {}", request.clientCapabilities());
        var mcpCapabilities = new McpCapabilities(true, true);
        var promptCapabilities = new PromptCapabilities(true, true, true);
        var authMethods = new AuthMethod("login", "login", "登录鉴权");
        var agentCapabilities = new AgentCapabilities(true, mcpCapabilities, promptCapabilities);
        return new InitializeResponse(1, agentCapabilities, List.of(authMethods), null);

    }

    @NewSession
    NewSessionResponse newSession(AcpSchema.NewSessionRequest request) {
        String sessionId = UUID.randomUUID().toString();
        String threadId = "acp-session-" + System.currentTimeMillis();
        String cwd = request.cwd() != null ? request.cwd() : System.getProperty("user.dir");
        sessions.put(sessionId, new AcpSession(sessionId, threadId, cwd));
        log.info("[AcpAgent] New session created:  {}", sessionId);
        log.info("[AcpAgent] Working directory:  {}", cwd);

        // Store MCP servers for this session
        List<McpServer> mcpServers = request.mcpServers();
        if (mcpServers != null && !mcpServers.isEmpty()) {
            sessionMcpServers.put(sessionId, new ArrayList<>(mcpServers));
            log.info("[McpAgent] Received  {}", mcpServers.size() + " MCP server(s)");
            for (McpServer server : mcpServers) {
                log.info("[McpAgent]   -  {}", describeMcpServer(server));
            }
        } else {
            log.info("[McpAgent] No MCP servers provided");
        }
        try {
            var builder = RunnableConfig.builder().threadId(sessionId);
//                        builder.addMetadata(RunnableConfig.HUMAN_FEEDBACK_METADATA_KEY, null);
            builder.addStateUpdate(new HashMap<>());
            RunnableConfig runnableConfig = builder.build();
            sessionsRunnableConfig.put(sessionId, runnableConfig);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return new NewSessionResponse(sessionId, sessionModeState, sessionModelStateSupplier.get());
    }

    @LoadSession
    LoadSessionResponse loadSession(AcpSchema.LoadSessionRequest request) {
        log.info("[AcpAgent] Load session:  {}", request.sessionId());
        if (sessions.containsKey(request.sessionId())) {
            log.info("[AcpAgent] Session found");
            Optional<Checkpoint> checkpointOpt = LocalAgent.fileSystemSaver.get(RunnableConfig.builder().threadId(sessions.get(request.sessionId()).threadId).build());
            String modelId = checkpointOpt.get().getState().getOrDefault("model", AiModels.KIMI_K2_5.getModelName()).toString();
            var modelState = checkpointOpt.map(Checkpoint::getState).map(e -> new SessionModelState(modelId, AiModels.availableModes())).orElse(sessionModelStateSupplier.get());
            return new LoadSessionResponse(sessionModeState, modelState);
        }
        log.info("[AcpAgent] Session not found");
        return new LoadSessionResponse(sessionModeState, sessionModelStateSupplier.get());
    }

    @Cancel
    void cancelSession(@SessionId String sessionId) {
        log.info("[AcpAgent] Cancel request for session:  {}", sessionId);
        // 取消该会话的所有活跃请求
        List<CancellableRequest> requestsToCancel = new ArrayList<>();
        for (Map.Entry<String, CancellableRequest> entry : activeRequests.entrySet()) {
            if (entry.getValue().sessionId.equals(sessionId)) {
                requestsToCancel.add(entry.getValue());
            }
        }
        // 执行取消操作
        for (CancellableRequest request : requestsToCancel) {
            log.info("[AcpAgent] Cancelling request: {} for session: {}", request.requestId, sessionId);
            request.cancel();
            activeRequests.remove(request.requestId);
        }
        // 清理会话资源
        cleanupSessionResources(sessionId);
        log.info("[AcpAgent] Cancelled {} active requests for session: {}", requestsToCancel.size(), sessionId);
    }

    @SetSessionMode
    SetSessionModeResponse setSessionMode(AcpSchema.SetSessionModeRequest request) {
        RunnableConfig runnableConfig = sessionsRunnableConfig.get(request.sessionId());
        runnableConfig.context().put("SetSessionModeRequest", request);
        log.info("[AcpAgent] setSessionMode {}", request);
        return new SetSessionModeResponse();
    }

    @SetSessionModel
    SetSessionModelResponse setSessionModel(AcpSchema.SetSessionModelRequest request) {
        RunnableConfig runnableConfig = sessionsRunnableConfig.get(request.sessionId());
        runnableConfig.context().put("SetSessionModelRequest", request);
        log.info("[AcpAgent] setSessionModel {}", request);
        return new SetSessionModelResponse();
    }

    @Prompt
    PromptResponse prompt(PromptRequest promptRequest, SyncPromptContext context) {
        String sessionId = promptRequest.sessionId();
        AcpSession session = sessions.get(sessionId);
        List<McpServer> mcpServers = sessionMcpServers.get(sessionId);

        if (session == null) {
            context.sendMessage("Error: Session not found");
            return PromptResponse.endTurn();
        }


        // 构建多模态用户输入
        UserMessage userMessage = buildUserMessage(promptRequest.prompt());
        log.info("[AcpAgent] Received:  {}", userMessage);

        // 生成请求ID
        String requestId = "req_" + System.currentTimeMillis() + "_" + sessionId;

        // 记录到历史对话
        session.history.add("User: " + userMessage);
        // 发送思考过程
        context.sendThought("✨✨✨XAgent正在处理中...✨✨✨");

        var availableCommand = List.of(new AvailableCommand("/init", "初始化AGENT.md", new AvailableCommandInput("/init")));
        context.sendUpdate(sessionId, new AvailableCommandsUpdate("available_commands_update", availableCommand));

        var runnableConfig = sessionsRunnableConfig.get(sessionId);
        runnableConfig.context().put("PromptRequest", promptRequest);
        runnableConfig.context().put("SyncPromptContext", context);
        var agent = LocalAgent.createAgent(session.getCwd(), mcpServers, runnableConfig);

        Flux<AgentOutput<Object>> flux = SinksUtil.toFlux(agent, userMessage, runnableConfig);
        // 注册可取消的请求
        Thread currentThread = Thread.currentThread();
        CancellableRequest cancellableRequest = registerCancellableRequest(requestId, sessionId, currentThread, flux);
        StringBuilder responseBuilder = new StringBuilder();
        AtomicBoolean isFirst = new AtomicBoolean(true);
        // 用于追踪是否是首次发送消息（用于添加换行符）
        AtomicBoolean isFirstMessage = new AtomicBoolean(true);
        // 使用可取消的 Flux 订阅
        Disposable disposable = flux.takeUntil(cancelSignal -> isRequestCancelled(requestId)).doOnCancel(() -> {
            log.info("[AcpAgent] Request {} Flux subscription was cancelled", requestId);
            if (isRequestCancelled(requestId)) {
                context.sendThought("\n请求已被用户取消");
            }
        }).doOnNext(output -> {
            // 检查请求是否已被取消
            if (isRequestCancelled(requestId)) {
                log.info("[AcpAgent] Request {} was cancelled, discarding output", requestId);
                return; // 直接返回，不处理输出
            }

            // 处理文本输出
            if (output.getChunk() != null) {
                String chunk = output.getChunk();
                responseBuilder.append(chunk);
                // 流式发送消息片段给客户端，首次发送前加换行
                if (isFirstMessage.getAndSet(false)) {
                    context.sendMessage("\n" + chunk);
                } else {
                    context.sendMessage(chunk);
                }
            }

            // 处理思考过程
            if (output.getThink() != null) {
                String think = output.getThink();
                if (isFirst.getAndSet(false)) {
                    context.sendMessage("> " + think);
                } else {
                    context.sendMessage(think.replaceAll("\n", "\n > "));
                }
            }

            // 处理工具调用请求 (AssistantMessage with ToolCalls)
            if (output.getMessage() instanceof AssistantMessage message) {
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

                        log.info("[AcpAgent] Tool call started:  {} arguments：{} id: {},", toolName, arguments, toolCallId);
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
                                null, responseData,  // rawOutput
                                null));

                        // 从请求跟踪中移除工具调用
                        removeToolCallFromRequest(requestId, toolCallId);
                        log.info("[AcpAgent] Tool call completed:  {} id: {} status: {} responseData: {}", toolName, toolCallId, status, responseData);
                    }
                }
            }

            // 处理工具调用反馈 (来自 HumanInTheLoop 等机制)
            if (!CollectionUtils.isEmpty(output.getToolFeedbacks())) {
                for (var toolFeedback : output.getToolFeedbacks()) {
                    String toolName = toolFeedback.getName();
                    String description = toolFeedback.getDescription();
                    String feedbackMsg = String.format("[工具] %s: %s", toolName, description);

                    // 发送思考过程
                    context.sendThought(feedbackMsg);

                    // 如果toolCallId，发送更新通知
                    String toolCallId = toolFeedback.getId();
                    if (toolCallId != null) {
                        context.sendUpdate(sessionId, new ToolCallUpdateNotification("tool_call_update", toolCallId, toolName, ToolKind.THINK, ToolCallStatus.COMPLETED, List.of(new ToolCallContentBlock("content", new TextContent(description))), null, null, null, null));
                    }
                }
            }

            // 处理Plan更新 (来自AcpWriteTodosTool等工具)
            if (output.getMetadata() != null && output.getMetadata().containsKey("acp_plan")) {
                Object planObj = output.getMetadata().get("acp_plan");
                if (planObj instanceof Plan plan) {
                    // 发送Plan更新到客户端
                    context.sendUpdate(sessionId, plan);
                    log.info("[AcpAgent] Sent Plan update with {} entries", plan.entries().size());
                }
            }
        }).doOnError(error -> {
            if (isRequestCancelled(requestId)) {
                log.info("[AcpAgent] Request {} cancelled with error: {}", requestId, error.getMessage());
                // 取消时不需要发送错误消息，已经发送了取消通知
            } else {
                log.warn("[AcpAgent] Request {} failed with error: {}", requestId, error.getMessage());
                context.sendThought("处理请求时发生错误: " + error.getMessage());
            }
        }).doFinally(signal -> {
            // 无论成功还是失败，都注销请求
            unregisterCancellableRequest(requestId);
            log.info("[AcpAgent] Request {} completed with signal: {}", requestId, signal);
        }).subscribe();

        // 设置 Disposable 以便取消
        cancellableRequest.setFluxDisposable(disposable);

        try {
            // 等待 Flux 完成或取消
            Thread.sleep(100); // 给 Flux 一点时间开始
            while (!disposable.isDisposed() && !isRequestCancelled(requestId)) {
                Thread.sleep(50);
            }

            if (isRequestCancelled(requestId)) {
                log.info("[AcpAgent] Request {} was cancelled during execution", requestId);
                // 确保 Disposable 被清理
                if (!disposable.isDisposed()) {
                    disposable.dispose();
                }
            }
        } catch (InterruptedException e) {
            log.info("[AcpAgent] Request {} thread interrupted", requestId);
            Thread.currentThread().interrupt(); // 恢复中断状态
            if (!disposable.isDisposed()) {
                disposable.dispose();
            }
        }

        String finalResponse = responseBuilder.toString();
        session.history.add("Assistant: " + finalResponse);

        log.info("[AcpAgent] Response complete, length:  {}", finalResponse.length());
        return PromptResponse.endTurn();
    }


    /**
     * 构建多模态用户消息
     *
     * @param contentBlocks 内容块列表
     * @return UserMessage 用户消息对象
     */
    private UserMessage buildUserMessage(List<ContentBlock> contentBlocks) {
        UserMessage.Builder builder = UserMessage.builder();
        List<Media> mediaList = new ArrayList<>();
        StringBuilder textBuilder = new StringBuilder();

        for (ContentBlock contentBlock : contentBlocks) {
            if (contentBlock instanceof TextContent textContent) {
                // 处理文本内容
                if (StringUtils.hasText(textContent.text())) {
                    textBuilder.append(textContent.text());
                }
            } else if (contentBlock instanceof ImageContent imageContent) {
                // 处理图片内容
                Media media = buildMediaFromContent(imageContent.mimeType(), imageContent.data(), imageContent.uri());
                if (media != null) {
                    mediaList.add(media);
                }
            } else if (contentBlock instanceof AudioContent audioContent) {
                // 处理音频内容
                Media media = buildMediaFromContent(audioContent.mimeType(), audioContent.data(), null);
                if (media != null) {
                    mediaList.add(media);
                }
            } else if (contentBlock instanceof ResourceLink resourceLink) {
                // 处理资源链接
                Media media = buildMediaFromContent(resourceLink.mimeType(), null, resourceLink.uri());
                if (media != null) {
                    mediaList.add(media);
                }
            } else if (contentBlock instanceof Resource resource) {
                // 处理嵌入资源
                EmbeddedResourceResource embeddedResource = resource.resource();
                if (embeddedResource instanceof TextResourceContents textResource) {
                    if (StringUtils.hasText(textResource.text())) {
                        textBuilder.append(textResource.text());
                    }
                } else if (embeddedResource instanceof BlobResourceContents blobResource) {
                    Media media = buildMediaFromContent(blobResource.mimeType(), blobResource.blob(), blobResource.uri());
                    if (media != null) {
                        mediaList.add(media);
                    }
                }
            }
        }

        // 设置文本内容
        if (StringUtils.hasText(textBuilder.toString())) {
            builder.text(textBuilder.toString());
        }

        // 设置媒体内容
        if (!mediaList.isEmpty()) {
            builder.media(mediaList);
        }

        return builder.build();
    }

    /**
     * 构建媒体对象
     *
     * @param mimeTypeStr MIME 类型字符串
     * @param data        Base64 编码的数据
     * @param uri         资源 URI
     * @return Media 对象，如果无法构建则返回 null
     */
    private Media buildMediaFromContent(String mimeTypeStr, String data, String uri) {
        try {
            // 解析 MIME 类型
            MimeType mimeType = null;
            if (StringUtils.hasText(mimeTypeStr)) {
                mimeType = MimeType.valueOf(mimeTypeStr);
            }

            if (mimeType == null) {
                log.warn("[AcpAgent] Cannot build media: missing or invalid MIME type");
                return null;
            }

            Media.Builder mediaBuilder = Media.builder().mimeType(mimeType);

            // 优先使用 Base64 数据
            if (StringUtils.hasText(data)) {
                byte[] decodedData = java.util.Base64.getDecoder().decode(data);
                mediaBuilder.data(decodedData);
            } else if (StringUtils.hasText(uri)) {
                // 使用 URI
                mediaBuilder.data(java.net.URI.create(uri));
            } else {
                log.warn("[AcpAgent] Cannot build media: both data and uri are empty");
                return null;
            }

            return mediaBuilder.build();
        } catch (IllegalArgumentException e) {
            log.warn("[AcpAgent] Failed to build media: {}", e.getMessage());
            return null;
        }
    }


    /**
     * 清理会话资源
     */
    private void cleanupSessionResources(String sessionId) {
        // 清理 MCP 服务器
        sessionMcpServers.remove(sessionId);

        // 清理会话
        sessions.remove(sessionId);

        // 清理后台进程（从 ShellTools）
        cleanupBackgroundProcesses(sessionId);

        log.info("[AcpAgent] Cleaned up resources for session: {}", sessionId);
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
                    log.info("[AcpAgent] Killed background shell: {} for session: {}", shellId, sessionId);
                } catch (Exception e) {
                    log.warn("[AcpAgent] Failed to kill shell {}: {}", shellId, e.getMessage());
                }
            }

            // 从所有活跃请求中移除该会话的 shell 进程
            for (CancellableRequest request : activeRequests.values()) {
                if (request.sessionId.equals(sessionId)) {
                    // 请求的 cancel() 方法会清理自己的 shell 进程
                    request.cancel();
                }
            }
        } catch (Exception e) {
            log.warn("[AcpAgent] Failed to cleanup background processes: {}", e.getMessage());
        }
    }

    /**
     * 注册可取消的请求
     */
    private CancellableRequest registerCancellableRequest(String requestId, String sessionId, Thread executionThread, Flux<?> flux) {
        CancellableRequest request = new CancellableRequest(requestId, sessionId, executionThread, flux);
        activeRequests.put(requestId, request);
        return request;
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
     * 为请求添加 shell 进程
     */
    private void addShellProcessToRequest(String requestId, String shellId, ShellTools.BackgroundProcess process) {
        CancellableRequest request = activeRequests.get(requestId);
        if (request != null) {
            request.addShellProcess(shellId, process);
        }
    }

    /**
     * 从请求中移除 shell 进程
     */
    private void removeShellProcessFromRequest(String requestId, String shellId) {
        CancellableRequest request = activeRequests.get(requestId);
        if (request != null) {
            request.removeShellProcess(shellId);
        }
    }

    /**
     * 检查请求是否已被取消
     */
    private boolean isRequestCancelled(String requestId) {
        CancellableRequest request = activeRequests.get(requestId);
        return request != null && request.cancelled;
    }

}
