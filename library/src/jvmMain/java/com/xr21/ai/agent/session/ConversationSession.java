package com.xr21.ai.agent.session;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.xr21.ai.agent.entity.ConversationMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 对话会话记录
 * 包含会话元数据和所有消息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSession {

    /**
     * 会话ID
     */
    @JsonProperty("session_id")
    private String sessionId;

    /**
     * 会话创建时间
     */
    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    /**
     * 最后更新时间
     */
    @JsonProperty("last_updated")
    private LocalDateTime lastUpdated;

    /**
     * 消息总数
     */
    @JsonProperty("message_count")
    private int messageCount;

    /**
     * 消息列表
     */
    @JsonProperty("messages")
    private List<ConversationMessage> messages;

    /**
     * 按类型统计的消息数量
     */
    @JsonProperty("type_statistics")
    private Map<ConversationMessage.MessageType, Long> typeStatistics;

    /**
     * 会话标签
     */
    @JsonProperty("tags")
    private List<String> tags;

    /**
     * 会话描述
     */
    @JsonProperty("description")
    private String description;

    /**
     * 会话版本
     */
    @JsonProperty("version")
    private String version;

    /**
     * 元数据
     */
    @JsonProperty("metadata")
    private Map<String, Object> metadata;

    public ConversationSession(String sessionId) {
        this.sessionId = sessionId;
        this.createdAt = LocalDateTime.now();
        this.lastUpdated = LocalDateTime.now();
        this.version = "1.0";
    }
}
