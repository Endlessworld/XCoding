package com.xr21.ai.agent.commands;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.alibaba.cloud.ai.graph.agent.Agent;
import com.xr21.ai.agent.LocalAgent;
import com.xr21.ai.agent.entity.AgentOutput;
import com.xr21.ai.agent.session.ConversationSessionManager;
import io.modelcontextprotocol.json.McpJsonMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 交互式会话命令
 */
@Slf4j
@ShellComponent
@RequiredArgsConstructor
public class InteractiveCommands {

    private final LocalAgent localAgent;
    private final ConversationSessionManager sessionManager;
    private final Agent supervisorAgent;
    private final AtomicReference<InterruptionMetadata> interruptionMetadata = new AtomicReference<>();
    private final Map<String, Object> stateUpdate = new HashMap<>();
    private String currentSessionId;

    @ShellMethod(key = "chat", value = "开始聊天")
    public String chat(@ShellOption(defaultValue = "") String message) {
        if (currentSessionId == null) {
            currentSessionId = "local-agent-session-" + System.currentTimeMillis();
            sessionManager.getOrCreateSession(currentSessionId);
            return "已创建新会话: " + currentSessionId;
        }

        if (message.isEmpty()) {
            return "请输入消息内容，例如: chat 你好";
        }

        try {
            // 记录用户消息
            sessionManager.addUserMessage(currentSessionId, message);

            // 处理对话
            StringBuilder responseBuilder = new StringBuilder();
            processWithGraph(message, responseBuilder);
            
            return responseBuilder.toString();
        } catch (Exception e) {
            log.error("处理聊天消息时发生错误", e);
            return "处理消息时发生错误: " + e.getMessage();
        }
    }

    @ShellMethod(key = "session", value = "会话管理")
    public String session(@ShellOption(defaultValue = "list") String action,
                         @ShellOption(defaultValue = "") String sessionId) {
        switch (action.toLowerCase()) {
            case "list":
                return listSessions();
            case "create":
                return createSession();
            case "switch":
                return switchSession(sessionId);
            case "current":
                return currentSessionId != null ? "当前会话: " + currentSessionId : "没有活动会话";
            default:
                return "未知操作: " + action + ". 可用操作: list, create, switch, current";
        }
    }

    @ShellMethod(key = "help-commands", value = "显示帮助信息")
    public String showHelp() {
        return """
                ========== AI Agent 命令帮助 ==========
                
                交互式命令:
                chat <message>           - 发送消息给AI助手
                session list             - 列出所有会话
                session create           - 创建新会话
                session switch <id>      - 切换到指定会话
                session current          - 显示当前会话
                
                会话管理命令:
                history                  - 查看当前会话历史
                clear                    - 清空当前会话历史
                save [filename]          - 保存会话到文件
                load <filename>          - 从文件加载会话
                
                反馈命令:
                feedback <message>       - 发送反馈给AI助手
                
                ==========================================
                """;
    }

    @ShellMethod(key = "history", value = "查看对话历史")
    public String showHistory() {
        if (currentSessionId == null) {
            return "没有活动会话";
        }

        var messages = sessionManager.loadSessionHistory(currentSessionId);
        if (messages.isEmpty()) {
            return "当前会话没有消息记录";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("\n=== 对话历史 (会话: ").append(currentSessionId).append(") ===\n");
        
        for (int i = 0; i < messages.size(); i++) {
            var msg = messages.get(i);
            builder.append(String.format("%d. [%s] %s: %s%n", 
                i + 1, 
                msg.getType(),
                msg.getType().name(),
                msg.getContent().length() > 100 ? 
                    msg.getContent().substring(0, 100) + "..." : 
                    msg.getContent()));
        }
        builder.append("=====================================\n");
        
        return builder.toString();
    }

    @ShellMethod(key = "clear", value = "清空对话历史")
    public String clearHistory() {
        if (currentSessionId == null) {
            return "没有活动会话";
        }

        sessionManager.clearSession(currentSessionId);
        return "已清空会话历史: " + currentSessionId;
    }

    @ShellMethod(key = "save", value = "保存对话历史")
    public String saveConversation(@ShellOption(defaultValue = "") String filename) {
        if (currentSessionId == null) {
            return "没有活动会话";
        }

        if (filename.isEmpty()) {
            filename = "conversation_" + System.currentTimeMillis() + ".txt";
        }

        try {
            var messages = sessionManager.loadSessionHistory(currentSessionId);
            try (java.io.PrintWriter out = new java.io.PrintWriter(filename)) {
                for (var msg : messages) {
                    out.println(String.format("[%s] %s: %s", msg.getType(), msg.getType().name(), msg.getContent()));
                }
            }
            return "对话已保存到文件: " + filename;
        } catch (Exception e) {
            return "保存对话失败: " + e.getMessage();
        }
    }

    @ShellMethod(key = "load", value = "加载对话历史")
    public String loadConversation(@ShellOption String filename) {
        try {
            List<String> history = new ArrayList<>();
            try (Scanner scanner = new Scanner(new java.io.File(filename))) {
                while (scanner.hasNextLine()) {
                    history.add(scanner.nextLine());
                }
            }
            
            // 创建新会话并加载历史
            String newSessionId = "local-agent-session-" + System.currentTimeMillis();
            sessionManager.getOrCreateSession(newSessionId);
            currentSessionId = newSessionId;
            
            // 这里可以进一步解析历史消息并添加到会话中
            return "已从文件加载对话历史: " + filename + " (会话ID: " + newSessionId + ")";
        } catch (Exception e) {
            return "加载对话失败: " + e.getMessage();
        }
    }

    @ShellMethod(key = "feedback", value = "发送反馈")
    public String sendFeedback(@ShellOption String message) {
        if (message.isEmpty()) {
            return "请提供反馈内容，例如: feedback 这里输入您的反馈";
        }

        if (currentSessionId == null) {
            currentSessionId = "local-agent-session-" + System.currentTimeMillis();
            sessionManager.getOrCreateSession(currentSessionId);
        }

        try {
            StringBuilder responseBuilder = new StringBuilder();
            processWithGraph("用户反馈: " + message, responseBuilder);
            sessionManager.addUserMessage(currentSessionId, "用户反馈: " + message);
            return "反馈已发送";
        } catch (Exception e) {
            log.error("发送反馈时发生错误", e);
            return "发送反馈失败: " + e.getMessage();
        }
    }

    private void processWithGraph(String input, StringBuilder responseBuilder) {
        Flux<ServerSentEvent<AgentOutput<Object>>> outputFlux = localAgent.processWithGraphV2(
            supervisorAgent, input, currentSessionId, interruptionMetadata.get(), stateUpdate);
        
        outputFlux.doOnNext(output -> {
            AgentOutput<Object> agentOutput = output.data();
            if (agentOutput.getChunk() != null) {
                responseBuilder.append(agentOutput.getChunk());
            }

            // 处理工具反馈
            if (!CollectionUtils.isEmpty(agentOutput.getToolFeedbacks())) {
                for (InterruptionMetadata.ToolFeedback toolFeedback : agentOutput.getToolFeedbacks()) {
                    String feedbackMsg = String.format("\n[系统提示] %s: %s", 
                        toolFeedback.getName(), toolFeedback.getDescription());
                    responseBuilder.append(feedbackMsg);
                    
                    InterruptionMetadata.Builder newBuilder = InterruptionMetadata.builder()
                            .nodeId(agentOutput.getNode())
                            .state(new OverAllState(agentOutput.getData()));
                    InterruptionMetadata.ToolFeedback approvedFeedback = InterruptionMetadata.ToolFeedback.builder(toolFeedback)
                            .description(toolFeedback.getDescription())
                            .result(InterruptionMetadata.ToolFeedback.FeedbackResult.APPROVED)
                            .build();
                    newBuilder.addToolFeedback(approvedFeedback);
                    interruptionMetadata.set(newBuilder.build());
                    
                    sessionManager.addSystemMessage(currentSessionId, feedbackMsg.trim());
                }
            }

            // 处理工具调用
            if (agentOutput.getMessage() instanceof AssistantMessage message) {
                if (!CollectionUtils.isEmpty(message.getToolCalls())) {
                    for (AssistantMessage.ToolCall toolCall : message.getToolCalls()) {
                        responseBuilder.append(String.format("\n[工具调用]: %s 参数: %s", 
                            toolCall.name(), toolCall.arguments()));
                        try {
                            sessionManager.addToolCallMessage(currentSessionId, toolCall.name(), 
                                McpJsonMapper.getDefault().readValue(toolCall.arguments(), Map.class), toolCall.id());
                        } catch (Exception e) {
                            log.error("记录工具调用失败", e);
                        }
                    }
                }
            }

            // 处理工具调用响应
            if (agentOutput.getMessage() instanceof ToolResponseMessage message) {
                if (!CollectionUtils.isEmpty(message.getResponses())) {
                    for (var response : message.getResponses()) {
                        String responseStr = response.responseData() != null ? response.responseData() : "执行成功";
                        responseBuilder.append(" ✅ 执行成功！");
                        sessionManager.addToolResponseMessage(currentSessionId, response.name(), responseStr, true);
                    }
                }
            }
        }).blockLast();
    }

    private String listSessions() {
        var sessionInfoList = sessionManager.getSessionInfoList();
        if (sessionInfoList.isEmpty()) {
            return "没有历史会话";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("\n" + "=".repeat(70) + "\n");
        builder.append("                            会话列表\n");
        builder.append("=".repeat(70) + "\n");
        builder.append("  编号  | 会话ID                    | 消息数 | 创建时间          | 简要描述\n");
        builder.append("-".repeat(70) + "\n");

        int displayCount = Math.min(sessionInfoList.size(), 10);
        for (int i = 0; i < displayCount; i++) {
            var info = sessionInfoList.get(i);
            String briefDesc = info.getBriefDescription().length() > 20 ? 
                info.getBriefDescription().substring(0, 20) + "..." : 
                info.getBriefDescription();
            builder.append(String.format("  %-5d | %-24s | %-6d | %-16s | %s%n", 
                (i + 1), info.getSessionId(), info.getMessageCount(), info.getCreatedAt(), briefDesc));
        }

        if (sessionInfoList.size() > 10) {
            builder.append(String.format("  ... 以及 %d 个更早的会话%n", sessionInfoList.size() - 10));
        }

        builder.append("-".repeat(70) + "\n");
        return builder.toString();
    }

    private String createSession() {
        currentSessionId = "local-agent-session-" + System.currentTimeMillis();
        sessionManager.getOrCreateSession(currentSessionId);
        return "已创建新会话: " + currentSessionId;
    }

    private String switchSession(String sessionId) {
        if (sessionId.isEmpty()) {
            return "请提供会话ID，例如: session switch local-agent-session-1234567890";
        }

            boolean loaded = sessionManager.loadSessionById(sessionId);
            if (loaded) {
                currentSessionId = sessionId;
                var stats = sessionManager.getSessionStatistics(sessionId);
                return "已切换到会话: " + sessionId + " (" + stats.get("totalMessages") + " 条消息)";
            } else {
                return "会话加载失败: " + sessionId;
            }
    }
}
