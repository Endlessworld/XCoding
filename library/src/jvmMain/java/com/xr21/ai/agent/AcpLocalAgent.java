package com.xr21.ai.agent;

import com.agentclientprotocol.sdk.agent.AcpAgent;
import com.agentclientprotocol.sdk.agent.AcpSyncAgent;
import com.agentclientprotocol.sdk.agent.SyncPromptContext;
import com.agentclientprotocol.sdk.agent.transport.StdioAcpAgentTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema.*;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xr21.ai.agent.config.AiModels;
import com.xr21.ai.agent.entity.AgentOutput;
import com.xr21.ai.agent.tools.ToolKindFind;
import com.xr21.ai.agent.tools.ToolResult;
import com.xr21.ai.agent.utils.SinksUtil;
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
                .cancelHandler(req -> {
                    log.info("[AcpLocalAgent] Cancel request for session:  {}", req.sessionId());
                    // 这里可以添加取消正在处理的请求的逻辑
                })

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

        // 记录到历�?
        session.history.add("User: " + userInput);

        // 发送思考过�?
        context.sendThought("正在处理您的请求...");

        // 使用 LocalAgent 处理输入
        StringBuilder responseBuilder = new StringBuilder();

        Flux<AgentOutput<Object>> flux = SinksUtil.toFlux(agent, userInput, session.threadId, null, new HashMap<>());

        flux.doOnNext(output -> {
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
                        // ToolCall 是完整的工具调用记录，包含输入参�?
                        context.sendUpdate(sessionId, new ToolCall("tool_call", toolCallId, toolName, toolKind, ToolCallStatus.IN_PROGRESS, contentList, null,  // locations
                                arguments,  // rawInput
                                null,  // rawOutput - 执行后才�?
                                message.getMetadata()));

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

                        // 解析 ToolResult 格式的响应数�?
                        ToolResultData resultData = parseToolResult(responseData);

                        // 构建位置信息
                        List<ToolCallLocation> locations = resultData.locations;

                        // 确定状�?
                        ToolCallStatus status = resultData.success ? ToolCallStatus.COMPLETED : (resultData.error != null ? ToolCallStatus.FAILED : ToolCallStatus.COMPLETED);

                        // 发送工具调用更新通知 (status: COMPLETED/FAILED)
                        context.sendUpdate(sessionId, new ToolCallUpdateNotification("tool_call_update", toolCallId, toolName, ToolKindFind.find(toolName), status, null, locations,  // 位置信息
                                null,  // rawInput - 已在之前�?ToolCall 中发�?
                                responseData,  // rawOutput
                                null));

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

                    // 发送思考过�?
                    context.sendThought(feedbackMsg);

                    // 如果�?toolCallId，发送更新通知
                    String toolCallId = toolFeedback.getId();
                    if (toolCallId != null) {
                        context.sendUpdate(sessionId, new ToolCallUpdateNotification("tool_call_update", toolCallId, toolName, ToolKind.THINK, ToolCallStatus.COMPLETED, List.of(new ToolCallContentBlock("content", new TextContent(description))), null, null, null, null));
                    }
                }
            }
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
     * 解析 ToolResult 格式的响应数�?
     */
    private ToolResultData parseToolResult(String responseData) {
        ToolResultData result = new ToolResultData();

        if (!StringUtils.hasText(responseData)) {
            result.success = true;
            return result;
        }

        try {
            // 尝试解析�?JSON Map
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> jsonMap = objectMapper.readValue(responseData, new TypeReference<Map<String, Object>>() {
            });

            // 提取 success
            if (jsonMap.containsKey(ToolResult.KEY_SUCCESS)) {
                result.success = Boolean.TRUE.equals(jsonMap.get(ToolResult.KEY_SUCCESS));
            } else {
                result.success = !jsonMap.containsKey(ToolResult.KEY_ERROR);
            }

            // 提取 content
            if (jsonMap.containsKey(ToolResult.KEY_CONTENT)) {
                result.content = String.valueOf(jsonMap.get(ToolResult.KEY_CONTENT));
            }

            // 提取 error
            if (jsonMap.containsKey(ToolResult.KEY_ERROR)) {
                result.error = String.valueOf(jsonMap.get(ToolResult.KEY_ERROR));
            }

            // 提取 locations
            if (jsonMap.containsKey(ToolResult.KEY_LOCATIONS)) {
                Object locs = jsonMap.get(ToolResult.KEY_LOCATIONS);
                if (locs instanceof List) {
                    List<Map<String, Object>> locList = (List<Map<String, Object>>) locs;
                    result.locations = new ArrayList<>();
                    for (Map<String, Object> loc : locList) {
                        String path = loc.get("path") != null ? String.valueOf(loc.get("path")) : null;
                        Integer line = loc.get("line") != null ? ((Number) loc.get("line")).intValue() : null;
                        result.locations.add(new ToolCallLocation(path, line));
                    }
                }
            }

            // 提取 toolCallContents (简化处理，只提取原始数�?
            if (jsonMap.containsKey(ToolResult.KEY_TOOL_CALL_CONTENTS)) {
                // 这里可以添加更复杂的解析逻辑
                // 目前简单地将原始数据保�?
            }

        } catch (Exception e) {
            // 解析失败，视为纯文本内容
            result.success = true;
            result.content = responseData;
        }

        return result;
    }

    /**
     * 工具结果数据结构
     */
    private static class ToolResultData {
        boolean success = true;
        String content;
        String error;
        List<ToolCallLocation> locations;
        List<ToolCallContent> toolCallContents;
    }

    /**
     * ACP 会话状�?
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

}


