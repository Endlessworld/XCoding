//package com.xr21.ai.agent.entity;
//
//import com.fasterxml.jackson.annotation.JsonProperty;
//
//import java.time.LocalDateTime;
//import java.util.Map;
//import java.util.UUID;
//
///**
// * 对话消息实体类
// * 使用JSON格式记录不同类型的对话消息
// */
//public class ConversationMessage {
//
//    @JsonProperty("id")
//    public String id;
//
//    @JsonProperty("type")
//    public MessageType type;
//
//    @JsonProperty("content")
//    public String content;
//
//    @JsonProperty("timestamp")
//    public LocalDateTime timestamp;
//
//    @JsonProperty("session_id")
//    public String sessionId;
//
//    @JsonProperty("round")
//    public int round;
//
//    @JsonProperty("tool_call")
//    public ToolCallInfo toolCall;
//
//    @JsonProperty("tool_response")
//    public ToolResponseInfo toolResponse;
//
//    @JsonProperty("metadata")
//    public Map<String, Object> metadata;
//
//    // Builder pattern implementation
//    public static Builder builder() {
//        return new Builder();
//    }
//
//    public String getId() {
//        return id;
//    }
//
//    public void setId(String id) {
//        this.id = id;
//    }
//
//    public MessageType getType() {
//        return type;
//    }
//
//    public void setType(MessageType type) {
//        this.type = type;
//    }
//
//    public String getContent() {
//        return content;
//    }
//
//    public void setContent(String content) {
//        this.content = content;
//    }
//
//    public LocalDateTime getTimestamp() {
//        return timestamp;
//    }
//
//    public void setTimestamp(LocalDateTime timestamp) {
//        this.timestamp = timestamp;
//    }
//
//    public String getSessionId() {
//        return sessionId;
//    }
//
//    public void setSessionId(String sessionId) {
//        this.sessionId = sessionId;
//    }
//
//    public int getRound() {
//        return round;
//    }
//
//    public void setRound(int round) {
//        this.round = round;
//    }
//
//    public ToolCallInfo getToolCall() {
//        return toolCall;
//    }
//
//    public void setToolCall(ToolCallInfo toolCall) {
//        this.toolCall = toolCall;
//    }
//
//    public ToolResponseInfo getToolResponse() {
//        return toolResponse;
//    }
//
//    public void setToolResponse(ToolResponseInfo toolResponse) {
//        this.toolResponse = toolResponse;
//    }
//
//    public Map<String, Object> getMetadata() {
//        return metadata;
//    }
//
//    public void setMetadata(Map<String, Object> metadata) {
//        this.metadata = metadata;
//    }
//
//    public static class Builder {
//        private final ConversationMessage message = new ConversationMessage();
//
//        public Builder id(String id) {
//            message.id = id;
//            return this;
//        }
//
//        public Builder type(MessageType type) {
//            message.type = type;
//            return this;
//        }
//
//        public Builder content(String content) {
//            message.content = content;
//            return this;
//        }
//
//        public Builder timestamp(LocalDateTime timestamp) {
//            message.timestamp = timestamp;
//            return this;
//        }
//
//        public Builder sessionId(String sessionId) {
//            message.sessionId = sessionId;
//            return this;
//        }
//
//        public Builder round(int round) {
//            message.round = round;
//            return this;
//        }
//
//        public Builder toolCall(ToolCallInfo toolCall) {
//            message.toolCall = toolCall;
//            return this;
//        }
//
//        public Builder toolResponse(ToolResponseInfo toolResponse) {
//            message.toolResponse = toolResponse;
//            return this;
//        }
//
//        public Builder metadata(Map<String, Object> metadata) {
//            message.metadata = metadata;
//            return this;
//        }
//
//        public ConversationMessage build() {
//            if (message.id == null) {
//                message.id = UUID.randomUUID().toString();
//            }
//            if (message.timestamp == null) {
//                message.timestamp = LocalDateTime.now();
//            }
//            return message;
//        }
//    }
//
//    /**
//     * 创建用户消息
//     */
//    public static ConversationMessage createUserMessage(String sessionId, String content, int round) {
//        return builder()
//                .id(UUID.randomUUID().toString())
//                .type(MessageType.USER)
//                .content(content)
//                .timestamp(LocalDateTime.now())
//                .sessionId(sessionId)
//                .round(round)
//                .build();
//    }
//
//    /**
//     * 创建助手消息
//     */
//    public static ConversationMessage createAssistantMessage(String sessionId, String content, int round) {
//        return builder()
//                .id(UUID.randomUUID().toString())
//                .type(MessageType.ASSISTANT)
//                .content(content)
//                .timestamp(LocalDateTime.now())
//                .sessionId(sessionId)
//                .round(round)
//                .build();
//    }
//
//    /**
//     * 创建系统消息
//     */
//    public static ConversationMessage createSystemMessage(String sessionId, String content, int round) {
//        return builder()
//                .id(UUID.randomUUID().toString())
//                .type(MessageType.SYSTEM)
//                .content(content)
//                .timestamp(LocalDateTime.now())
//                .sessionId(sessionId)
//                .round(round)
//                .build();
//    }
//
//    /**
//     * 创建错误消息
//     */
//    public static ConversationMessage createErrorMessage(String sessionId, String errorMessage, int round) {
//        return builder()
//                .id(UUID.randomUUID().toString())
//                .type(MessageType.ERROR)
//                .content(errorMessage)
//                .timestamp(LocalDateTime.now())
//                .sessionId(sessionId)
//                .round(round)
//                .build();
//    }
//
//    /**
//     * 创建工具调用消息
//     */
//    public static ConversationMessage createToolCallMessage(String sessionId, String toolName,
//                                                            Map<String, Object> arguments, String callId, int round) {
//        ToolCallInfo toolCall = ToolCallInfo.builder()
//                .toolName(toolName)
//                .arguments(arguments)
//                .callId(callId)
//                .build();
//        return builder()
//                .id(UUID.randomUUID().toString())
//                .type(MessageType.TOOL_CALL)
//                .timestamp(LocalDateTime.now())
//                .sessionId(sessionId)
//                .round(round)
//                .toolCall(toolCall)
//                .build();
//    }
//
//    /**
//     * 创建工具响应消息
//     */
//    public static ConversationMessage createToolResponseMessage(String sessionId, String toolName,
//                                                                String response, boolean success, int round) {
//        ToolResponseInfo toolResponse = ToolResponseInfo.builder()
//                .toolName(toolName)
//                .response(response)
//                .success(success)
//                .build();
//        return builder()
//                .id(UUID.randomUUID().toString())
//                .type(MessageType.TOOL_RESPONSE)
//                .timestamp(LocalDateTime.now())
//                .sessionId(sessionId)
//                .round(round)
//                .toolResponse(toolResponse)
//                .build();
//    }
//
//    public enum MessageType {
//        USER,
//        ASSISTANT,
//        TOOL_CALL,
//        TOOL_RESPONSE,
//        SYSTEM,
//        ERROR,
//        FEEDBACK
//    }
//
//    public static class ToolCallInfo {
//        @JsonProperty("tool_name")
//        public String toolName;
//
//        @JsonProperty("arguments")
//        public Map<String, Object> arguments;
//
//        @JsonProperty("call_id")
//        public String callId;
//
//        public static ToolCallInfoBuilder builder() {
//            return new ToolCallInfoBuilder();
//        }
//
//        public static class ToolCallInfoBuilder {
//            private final ToolCallInfo info = new ToolCallInfo();
//
//            public ToolCallInfoBuilder toolName(String toolName) {
//                info.toolName = toolName;
//                return this;
//            }
//
//            public ToolCallInfoBuilder arguments(Map<String, Object> arguments) {
//                info.arguments = arguments;
//                return this;
//            }
//
//            public ToolCallInfoBuilder callId(String callId) {
//                info.callId = callId;
//                return this;
//            }
//
//            public ToolCallInfo build() {
//                return info;
//            }
//        }
//    }
//
//    public static class ToolResponseInfo {
//        @JsonProperty("tool_name")
//        public String toolName;
//
//        @JsonProperty("response")
//        public String response;
//
//        @JsonProperty("success")
//        public boolean success;
//
//        @JsonProperty("error_message")
//        public String errorMessage;
//
//        @JsonProperty("duration_ms")
//        public Long durationMs;
//
//        public static ToolResponseInfoBuilder builder() {
//            return new ToolResponseInfoBuilder();
//        }
//
//        public static class ToolResponseInfoBuilder {
//            private final ToolResponseInfo info = new ToolResponseInfo();
//
//            public ToolResponseInfoBuilder toolName(String toolName) {
//                info.toolName = toolName;
//                return this;
//            }
//
//            public ToolResponseInfoBuilder response(String response) {
//                info.response = response;
//                return this;
//            }
//
//            public ToolResponseInfoBuilder success(boolean success) {
//                info.success = success;
//                return this;
//            }
//
//            public ToolResponseInfoBuilder errorMessage(String errorMessage) {
//                info.errorMessage = errorMessage;
//                return this;
//            }
//
//            public ToolResponseInfoBuilder durationMs(Long durationMs) {
//                info.durationMs = durationMs;
//                return this;
//            }
//
//            public ToolResponseInfo build() {
//                return info;
//            }
//        }
//    }
//}
