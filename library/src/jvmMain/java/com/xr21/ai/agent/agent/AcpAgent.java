package com.xr21.ai.agent.agent;

import com.agentclientprotocol.sdk.agent.SyncPromptContext;
import com.agentclientprotocol.sdk.annotation.*;
import com.agentclientprotocol.sdk.spec.AcpSchema;
import com.agentclientprotocol.sdk.spec.AcpSchema.*;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.xr21.ai.agent.config.AiModels;
import com.xr21.ai.agent.entity.AcpSession;
import com.xr21.ai.agent.entity.AgentOutput;
import com.xr21.ai.agent.entity.CancellableRequest;
import com.xr21.ai.agent.tools.ShellTools;
import com.xr21.ai.agent.tools.ToolKindFind;
import com.xr21.ai.agent.utils.PermissionSettings;
import com.xr21.ai.agent.utils.SinksUtil;
import com.xr21.ai.agent.utils.ToolsUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.content.Media;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeType;
import org.springframework.util.StringUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    private final SessionModeState sessionModeState = new SessionModeState("Agent", List.of(new SessionMode("Agent", "Agent", "单智能体模式"),
            new SessionMode("ForkAgent", "Fork Agent", "可动态fork出多个并行子代理处理任务")));
    private final Supplier<SessionModelState> sessionModelStateSupplier = () -> new SessionModelState(AiModels.defaultModel(), AiModels.availableModels());

    @Initialize
    InitializeResponse init(AcpSchema.InitializeRequest request) {
        log.info("[AcpAgent] Client initialized, protocol version:  {}", request.protocolVersion());
        log.info("[AcpAgent] Client capabilities:  {}", request.clientCapabilities());
        var mcpCapabilities = new McpCapabilities(true, true);
        var promptCapabilities = new PromptCapabilities(true, true, true);
        var authMethods = new AuthMethod("login", "login", "登录鉴权");
        var agentCapabilities = new AgentCapabilities(true, mcpCapabilities, promptCapabilities);
        return new InitializeResponse(request.protocolVersion(), agentCapabilities, List.of(authMethods), null);
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
            Optional<Checkpoint> checkpointOpt = LocalAgent.FILE_SYSTEM_SAVER.get(RunnableConfig.builder()
                    .threadId(sessions.get(request.sessionId()).threadId)
                    .build());
            String modelId = checkpointOpt.get().getState().getOrDefault("model", AiModels.defaultModel()).toString();
            var modelState = checkpointOpt.map(Checkpoint::getState)
                    .map(e -> new SessionModelState(modelId, AiModels.availableModels()))
                    .orElse(sessionModelStateSupplier.get());
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
        log.info("[AcpAgent] Received:  {}", promptRequest);

        // 生成请求ID
        String requestId = "req_" + System.currentTimeMillis() + "_" + sessionId;

        // 记录到历史对话
        session.history.add("User: " + userMessage);
        // 发送思考过程
        context.sendThought("✨✨✨XAgent正在处理中...✨✨✨\n");

        var availableCommand = List.of(new AvailableCommand("/init", "初始化AGENT.md", new AvailableCommandInput("/init")));
        context.sendUpdate(sessionId, new AvailableCommandsUpdate("available_commands_update", availableCommand));

        var runnableConfig = sessionsRunnableConfig.get(sessionId);
        runnableConfig.context().put("PromptRequest", promptRequest);
        runnableConfig.context().put("SyncPromptContext", context);
        Agent agent = LocalAgent.createAgent(session.getCwd(), mcpServers, runnableConfig);

        // 注册可取消的请求
        Thread currentThread = Thread.currentThread();

        // 用于收集响应内容
        StringBuilder responseBuilder = new StringBuilder();

        // 创建共享的状态对象，用于递归调用时传递上下文
        AgentFlowState flowState = new AgentFlowState(requestId, sessionId, 0, context, responseBuilder);

        // 使用 expand 实现递归的 Flux 流处理
        Flux<AgentOutput<Object>> recursiveFlux = createRecursiveAgentFlux(agent, userMessage, runnableConfig, flowState);

        CancellableRequest cancellableRequest = registerCancellableRequest(requestId, sessionId, currentThread, recursiveFlux);

        // 使用可取消的 Flux 订阅
        Disposable disposable = recursiveFlux.takeUntil(cancelSignal -> isRequestCancelled(requestId))
                .doOnCancel(() -> {
                    log.info("[AcpAgent] Request {} Flux subscription was cancelled", requestId);
                    if (isRequestCancelled(requestId)) {
                        context.sendThought("\n请求已被用户取消");
                    }
                })
                .doOnError(error -> {
                    if (isRequestCancelled(requestId)) {
                        log.info("[AcpAgent] Request {} cancelled with error: {}", requestId, error.getMessage());
                    } else {
                        log.warn("[AcpAgent] Request {} failed with error: {}", requestId, error.getMessage());
                        context.sendThought("处理请求时发生错误: " + error.getMessage());
                    }
                })
                .doFinally(signal -> {
                    unregisterCancellableRequest(requestId);
                    log.info("[AcpAgent] Request {} completed with signal: {}", requestId, signal);
                })
                .subscribe();

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
                if (!disposable.isDisposed()) {
                    disposable.dispose();
                }
            }
        } catch (InterruptedException e) {
            log.info("[AcpAgent] Request {} thread interrupted", requestId);
            Thread.currentThread().interrupt();
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
     * 创建递归的 Agent Flux 流处理
     * 使用 expand 操作符实现递归的会话恢复，每次遇到人介入审核时，会请求权限后继续执行
     * 递归的每一次会话流都使用相同的 flux 流处理逻辑
     *
     * @param agent          Agent 实例
     * @param userMessage    用户消息
     * @param runnableConfig 运行配置
     * @param flowState      流处理状态
     * @return 递归处理的 Flux 流
     */
    private Flux<AgentOutput<Object>> createRecursiveAgentFlux(Agent agent, UserMessage userMessage, RunnableConfig runnableConfig, AgentFlowState flowState) {
        // 创建初始的 Flux
        Flux<AgentOutput<Object>> initialFlux = SinksUtil.toFlux(agent, userMessage, runnableConfig);

        // 使用 expand 实现递归处理
        // 每次递归都会执行相同的流处理逻辑 processAgentOutput
        return initialFlux.expand(output -> {
            // 处理输出 - 使用统一的流处理逻辑
            processAgentOutput(output, flowState);
            // 检查是否是中断元数据（人介入审核）
            if (output.getInterruptionMetadata() != null) {
                log.info("[AcpAgent] Detected human intervention, requesting permission...");

                // 处理人介入审核，获取批准决策
                InterruptionMetadata approvalMetadata = processHumanIntervention(flowState, runnableConfig, output.getInterruptionMetadata());

                // 检查是否被用户拒绝所有操作
                boolean allRejected = approvalMetadata.toolFeedbacks()
                        .stream()
                        .allMatch(fb -> fb.getResult() == InterruptionMetadata.ToolFeedback.FeedbackResult.REJECTED);

                if (allRejected) {
                    log.info("[AcpAgent] All tool calls were rejected, ending flow");
                    return Flux.empty();
                }

                // 使用批准决策创建新的 Flux 继续执行（会话恢复）
                log.info("[AcpAgent] Resuming agent flow with approval metadata");
                return SinksUtil.toFlux(agent, userMessage, runnableConfig);
            }

            // 正常输出，不是中断点，返回空 Flux 结束此分支
            return Flux.empty();
        });
    }

    /**
     * 处理人介入审核流程
     *
     * @param flowState             流处理状态
     * @param runnableConfig        运行配置
     * @param interruptionMetadata  中断元数据
     * @return 包含批准决策的 InterruptionMetadata
     */
    private InterruptionMetadata processHumanIntervention(AgentFlowState flowState, RunnableConfig runnableConfig, InterruptionMetadata interruptionMetadata) {
        // 获取工作目录
        AcpSession session = sessions.get(flowState.sessionId);
        String cwd = session != null ? session.getCwd() : System.getProperty("user.dir");

        // 加载权限设置
        PermissionSettings.Settings permissionSettings = PermissionSettings.load(cwd);

        // 构建批准反馈
        InterruptionMetadata.Builder feedbackBuilder = InterruptionMetadata.builder()
                .nodeId(interruptionMetadata.node())
                .state(interruptionMetadata.state());

        // 对每个工具调用设置批准决策
        for (InterruptionMetadata.ToolFeedback toolFeedback : interruptionMetadata.toolFeedbacks()) {
            List<ToolCallContent> contentBlocks = List.of(new ToolCallContentBlock("content", new TextContent(toolFeedback.getDescription())));

            var toolCallUpdate = new ToolCallUpdate(toolFeedback.getId(), toolFeedback.getName(), ToolKindFind.find(toolFeedback.getName()), ToolCallStatus.PENDING, contentBlocks, null, toolFeedback.getArguments(), null);

            // 构建工具标识符，用于持久化 (格式: "ToolName(arguments)" 或 "ToolName")
            String toolPattern = buildToolPattern(toolFeedback.getName(), toolFeedback.getArguments());

            // 首先检查持久化的权限
            PermissionSettings.PermissionAction persistedAction = PermissionSettings.checkPermission(toolFeedback.getName(), toolFeedback.getArguments());

            InterruptionMetadata.ToolFeedback.Builder approvedFeedbackBuilder = InterruptionMetadata.ToolFeedback.builder(toolFeedback);
            InterruptionMetadata.ToolFeedback approvedFeedback;

            if (persistedAction != null) {
                // 使用持久化的权限决策
                if (persistedAction == PermissionSettings.PermissionAction.ALLOW) {
                    approvedFeedback = approvedFeedbackBuilder.result(InterruptionMetadata.ToolFeedback.FeedbackResult.APPROVED).build();
                    log.info("[AcpAgent] Auto-approved (persisted) for tool: {}", toolFeedback.getName());
                } else {
                    approvedFeedback = approvedFeedbackBuilder.result(InterruptionMetadata.ToolFeedback.FeedbackResult.REJECTED).build();
                    log.info("[AcpAgent] Auto-rejected (persisted) for tool: {}", toolFeedback.getName());
                }
                feedbackBuilder.addToolFeedback(approvedFeedback);
                continue;
            }

            // 没有持久化权限，请求用户选择
            var permissionOptions = Arrays.stream(PermissionOptionKind.values())
                    .map(kind -> new PermissionOption(kind.name(), kind.name(), kind))
                    .toList();

            var requestPermissionRequest = new RequestPermissionRequest(flowState.sessionId, toolCallUpdate, permissionOptions);
            log.info("[AcpAgent] requestPermission : {}",requestPermissionRequest);
            AcpSchema.RequestPermissionResponse permissionResponse = flowState.context.requestPermission(requestPermissionRequest);
            log.info("[AcpAgent] permissionResponse : {}",permissionResponse);
            if (permissionResponse.outcome() instanceof PermissionCancelled) {
                approvedFeedback = approvedFeedbackBuilder.result(InterruptionMetadata.ToolFeedback.FeedbackResult.REJECTED).build();
                feedbackBuilder.addToolFeedback(approvedFeedback);
            } else if (permissionResponse.outcome() instanceof PermissionSelected selected) {
                PermissionOptionKind permissionOptionKind = PermissionOptionKind.valueOf(selected.optionId());
                InterruptionMetadata.ToolFeedback.FeedbackResult feedbackResult = InterruptionMetadata.ToolFeedback.FeedbackResult.REJECTED;

                switch (permissionOptionKind) {
                    case ALLOW_ONCE -> {
                        feedbackResult = InterruptionMetadata.ToolFeedback.FeedbackResult.APPROVED;
                        log.info("[AcpAgent] Permission granted (once) for tool: {}", toolFeedback.getName());
                    }
                    case ALLOW_ALWAYS -> {
                        feedbackResult = InterruptionMetadata.ToolFeedback.FeedbackResult.APPROVED;
                        // 持久化 ALLOW 权限
                        PermissionSettings.addAllowPermission(cwd, toolPattern);
                        log.info("[AcpAgent] Permission granted (always) for tool: {}", toolFeedback.getName());
                    }
                    case REJECT_ONCE -> {
                        feedbackResult = InterruptionMetadata.ToolFeedback.FeedbackResult.REJECTED;
                        log.info("[AcpAgent] Permission rejected (once) for tool: {}", toolFeedback.getName());
                    }
                    case REJECT_ALWAYS -> {
                        feedbackResult = InterruptionMetadata.ToolFeedback.FeedbackResult.REJECTED;
                        // 持久化 REJECT 权限
                        PermissionSettings.addRejectPermission(cwd, toolPattern);
                        log.info("[AcpAgent] Permission rejected (always) for tool: {}", toolFeedback.getName());
                    }
                }

                approvedFeedback = approvedFeedbackBuilder.result(feedbackResult).build();
                feedbackBuilder.addToolFeedback(approvedFeedback);
            }
        }

        InterruptionMetadata approvalMetadata = feedbackBuilder.build();
        runnableConfig.metadata().ifPresent(metadata -> {
            metadata.put(RunnableConfig.HUMAN_FEEDBACK_METADATA_KEY, approvalMetadata);
        });
        log.info("[AcpAgent] interruptionMetadata : {}",approvalMetadata);
        return approvalMetadata;
    }

    /**
     * 构建工具标识符模式，用于持久化存储
     * 格式: "ToolName(arguments)" 或 "ToolName"
     */
    private String buildToolPattern(String toolName, String arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return toolName;
        }
        // 如果参数太长，只保留前100个字符
        String truncatedArgs = arguments.length() > 100 ? arguments.substring(0, 100) + "..." : arguments;
        return toolName + "(" + truncatedArgs + ")";
    }

    /**
     * 处理 Agent 输出流的核心逻辑
     * 提取为独立方法，确保在递归的每一次会话流中都能使用相同的处理逻辑
     *
     * @param output           Agent 输出
     * @param flowState        流处理状态
     */
    private void processAgentOutput(AgentOutput<Object> output, AgentFlowState flowState) {
        // 检查请求是否已被取消
        if (isRequestCancelled(flowState.requestId)) {
            log.info("[AcpAgent] Request {} was cancelled, discarding output", flowState.requestId);
            return;
        }
        // 使用 flowState 中持久化的状态，确保递归后状态连续
        AtomicBoolean isFirst = flowState.isFirst;
        AtomicBoolean isFirstMessage = flowState.isFirstMessage;
        SyncPromptContext context = flowState.context;
        StringBuilder responseBuilder = flowState.responseBuilder;
        // 处理文本输出
        if (output.getTokenUsage() instanceof DefaultUsage usage) {
            if (usage.getTotalTokens() != null) {
                flowState.totalTokens += usage.getTotalTokens();
            }
            context.sendThought("\ntokens usage: promptTokens %s completionTokens %s request totalTokens %s session totalTokens %s".formatted(usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens(), flowState.totalTokens));
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
                context.sendMessage(think);
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
                    List<ToolCallLocation> locations = new ArrayList<>();

                    if (StringUtils.hasText(arguments)) {
                        if ("edit_file".equals(toolName)) {
                            JSONObject json = JSON.parseObject(arguments);
                            locations.add(new ToolCallLocation(json.getString("filePath"), 1));
                            contentList.add(new ToolCallDiff("diff", json.getString("filePath"), json.getString("oldString"), json.getString("newString")));
                        } else {
                            contentList.add(new ToolCallContentBlock("content", new TextContent(arguments)));
                        }
                    }

                    // 发送工具调用开始通知 (status: PENDING)
                    ToolCall toolCallNotification = new ToolCall("tool_call", toolCallId, toolName, toolKind, ToolCallStatus.IN_PROGRESS, contentList, locations, arguments, null, message.getMetadata());

                    if ("write_todos".equals(toolName)) {
                        context.sendThought("✨✨✨让我更新任务进度...");
                    } else {
                        context.sendUpdate(flowState.sessionId, toolCallNotification);
                    }

                    // 将工具调用添加到请求跟踪
                    addToolCallToRequest(flowState.requestId, toolCallId);
                    log.info("[AcpAgent] Tool call started: {} arguments：{} id: {}", toolName, arguments, toolCallId);
                }
            }
        }

        // 处理工具调用响应 (ToolResponseMessage - 工具执行完成)
        if (output.getMessage() instanceof ToolResponseMessage message) {
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
                    if (!"write_todos".equals(toolName)) {
                        // 发送工具调用更新通知 (status: COMPLETED/FAILED)
                        context.sendUpdate(flowState.sessionId, new ToolCallUpdateNotification("tool_call_update", toolCallId, toolName, ToolKindFind.find(toolName), status, null, locations, null, responseData, null));
                    }
                    // 从请求跟踪中移除工具调用
                    removeToolCallFromRequest(flowState.requestId, toolCallId);
                    log.info("[AcpAgent] Tool call completed: {} id: {} status: {} responseData: {}", toolName, toolCallId, status, responseData);
                }
            }
        }
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
                MimeType mimeType = MimeType.valueOf(URLConnection.guessContentTypeFromName(resourceLink.uri()));
                if (mimeType.getType().contains("image")) {
                    // 处理资源链接
                    Media media = buildMediaFromContent(mimeType.getType(), null, resourceLink.uri());
                    if (media != null) {
                        mediaList.add(media);
                    } else {
                        textBuilder.append(resourceLink.uri());
                    }
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
                    textBuilder.append(blobResource.uri());
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
            log.warn("[AcpAgent] buildMediaFromContent {} {} {}", mimeTypeStr, data, uri);
            // 解析 MIME 类型
            MimeType mimeType = null;
            if (StringUtils.hasText(mimeTypeStr)) {
                mimeType = MimeType.valueOf(mimeTypeStr);
            } else {
                mimeType = MimeType.valueOf(URLConnection.guessContentTypeFromName(uri));
            }

            Media.Builder mediaBuilder = Media.builder().mimeType(mimeType);
            // 优先使用 Base64 数据
            if (StringUtils.hasText(data)) {
                byte[] decodedData = Base64.getDecoder().decode(data);
                mediaBuilder.data(decodedData);
            } else if (StringUtils.hasText(uri)) {
                if (mimeType.getType().contains("image")) {
                    log.info("[AcpAgent] mimeType is image: {}", mimeType);
                    Path imagePath = Paths.get(URI.create(uri));
                    byte[] imageBytes = Files.readAllBytes(imagePath);
                    String base64Image = Base64.getEncoder().encodeToString(imageBytes);
                    byte[] decodedData = Base64.getDecoder().decode(base64Image);
                    mediaBuilder.data(decodedData);
                }
            } else {
                log.warn("[AcpAgent] Cannot build media: both data and uri are empty");
                return null;
            }
            return mediaBuilder.build();
        } catch (Throwable e) {
            log.error("[AcpAgent] Failed to build media: {}", e);
            return null;
        }
    }

    /**
     * 清理会话资源
     */
    private void cleanupSessionResources(String sessionId) {
        // 清理 MCP 服务器
//        sessionMcpServers.remove(sessionId);
//
//        // 清理会话
//        sessions.remove(sessionId);

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

    /**
     * Agent 流处理状态类
     * 用于在递归调用中共享上下文状态
     */
    private static class AgentFlowState {
        final String requestId;
        final String sessionId;
        Integer totalTokens;
        final SyncPromptContext context;
        final StringBuilder responseBuilder;
        // 持久化 isFirst 状态，用于追踪思考过程的首次输出
        final AtomicBoolean isFirst;
        // 持久化 isFirstMessage 状态，用于追踪消息的首次输出（添加换行符）
        final AtomicBoolean isFirstMessage;

        AgentFlowState(String requestId, String sessionId, Integer totalTokens, SyncPromptContext context, StringBuilder responseBuilder) {
            this.requestId = requestId;
            this.sessionId = sessionId;
            this.totalTokens = totalTokens;
            this.context = context;
            this.responseBuilder = responseBuilder;
            this.isFirst = new AtomicBoolean(true);
            this.isFirstMessage = new AtomicBoolean(true);
        }

        /**
         * 创建新的 AgentFlowState，继承 isFirst 和 isFirstMessage 状态
         * 用于递归调用时保持状态的连续性
         */
        AgentFlowState copyWithNewRequestId(String newRequestId) {
            AgentFlowState newState = new AgentFlowState(newRequestId, this.sessionId, this.totalTokens, this.context, this.responseBuilder);
            // 继承之前的状态，确保递归后 isFirstMessage 不会重置
            newState.isFirst.set(this.isFirst.get());
            newState.isFirstMessage.set(this.isFirstMessage.get());
            return newState;
        }
    }

}
