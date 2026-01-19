package com.xr21.ai.agent.entity;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 对话消息实体类
 * 使用JSON格式记录不同类型的对话消息
 */
public class ConversationMessage {

    /**
     * 消息ID
     */
    @JsonProperty("id")
    public String id;

    /**
     * 消息类型
     */
    @JsonProperty("type")
    public MessageType type;

    /**
     * 消息内容
     */
    @JsonProperty("content")
    public String content;

    /**
     * 消息时间戳
     */
    @JsonProperty("timestamp")
    public LocalDateTime timestamp;

    /**
     * 消息所属会话ID
     */
    @JsonProperty("session_id")
    public String sessionId;

    /**
     * 消息轮次
     */
    @JsonProperty("round")
    public int round;

    /**
     * 工具调用信息（当type为TOOL_CALL时）
     */
    @JsonProperty("tool_call")
    public ToolCallInfo toolCall;

    /**
     * 工具响应信息（当type为TOOL_RESPONSE时）
     */
    @JsonProperty("tool_response")
    public ToolResponseInfo toolResponse;

    /**
     * 元数据
     */
    @JsonProperty("metadata")
    public Map<String, Object> metadata;

    /**
     * 创建用户消息
     */
    public static ConversationMessage createUserMessage(String sessionId, String content, int round) {
        return ConversationMessage.builder()
                .id(java.util.UUID.randomUUID().toString())
                .type(MessageType.USER)
                .content(content)
                .timestamp(LocalDateTime.now())
                .sessionId(sessionId)
                .round(round)
                .build();
    }

    /**
     * 创建助手消息
     */
    public static ConversationMessage createAssistantMessage(String sessionId, String content, int round) {
        return ConversationMessage.builder()
                .id(java.util.UUID.randomUUID().toString())
                .type(MessageType.ASSISTANT)
                .content(content)
                .timestamp(LocalDateTime.now())
                .sessionId(sessionId)
                .round(round)
                .build();
    }

    /**
     * 创建系统消息
     */
    public static ConversationMessage createSystemMessage(String sessionId, String content, int round) {
        return ConversationMessage.builder()
                .id(java.util.UUID.randomUUID().toString())
                .type(MessageType.SYSTEM)
                .content(content)
                .timestamp(LocalDateTime.now())
                .sessionId(sessionId)
                .round(round)
                .build();
    }

    /**
     * 创建错误消息
     */
    public static ConversationMessage createErrorMessage(String sessionId, String errorMessage, int round) {
        return ConversationMessage.builder()
                .id(java.util.UUID.randomUUID().toString())
                .type(MessageType.ERROR)
                .content(errorMessage)
                .timestamp(LocalDateTime.now())
                .sessionId(sessionId)
                .round(round)
                .build();
    }

    /**
     * 创建工具调用消息
     */
    public static ConversationMessage createToolCallMessage(String sessionId, String toolName,
                                                            Map<String, Object> arguments, String callId, int round) {
        return ConversationMessage.builder()
                .id(java.util.UUID.randomUUID().toString())
                .type(MessageType.TOOL_CALL)
                .timestamp(LocalDateTime.now())
                .sessionId(sessionId)
                .round(round)
                .toolCall(ToolCallInfo.builder()
                        .toolName(toolName)
                        .arguments(arguments)
                        .callId(callId)
                        .build())
                .build();
    }

    /**
     * 创建工具响应消息
     */
    public static ConversationMessage createToolResponseMessage(String sessionId, String toolName,
                                                                String response, boolean success, int round) {
        return ConversationMessage.builder()
                .id(java.util.UUID.randomUUID().toString())
                .type(MessageType.TOOL_RESPONSE)
                .timestamp(LocalDateTime.now())
                .sessionId(sessionId)
                .round(round)
                .toolResponse(ToolResponseInfo.builder()
                        .toolName(toolName)
                        .response(response)
                        .success(success)
                        .build())
                .build();
    }

    /**
     * 消息类型枚举
     */
    public enum MessageType {
        USER,           // 用户消息
        ASSISTANT,      // 助手消息
        TOOL_CALL,      // 工具调用
        TOOL_RESPONSE,  // 工具响应
        SYSTEM,         // 系统消息
        ERROR,          // 错误消息
        FEEDBACK        // 用户反馈
    }

    /**
     * 工具调用信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCallInfo {
        @JsonProperty("tool_name")
        private String toolName;

        @JsonProperty("arguments")
        private Map<String, Object> arguments;

        @JsonProperty("call_id")
        private String callId;
    }

    /**
     * 工具响应信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolResponseInfo {
        @JsonProperty("tool_name")
        private String toolName;

        @JsonProperty("response")
        private String response;

        @JsonProperty("success")
        private boolean success;

        @JsonProperty("error_message")
        private String errorMessage;

        @JsonProperty("duration_ms")
        private Long durationMs;
    }
}
