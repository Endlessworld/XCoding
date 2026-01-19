package com.xr21.ai.agent.session;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.xr21.ai.agent.entity.ConversationMessage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 对话会话记录
 * 包含会话元数据和所有消息
 */
public class ConversationSession {

    @JsonProperty("session_id")
    public String sessionId;

    @JsonProperty("created_at")
    public LocalDateTime createdAt;

    @JsonProperty("last_updated")
    public LocalDateTime lastUpdated;

    @JsonProperty("message_count")
    public int messageCount;

    @JsonProperty("messages")
    public List<ConversationMessage> messages;

    @JsonProperty("type_statistics")
    public Map<ConversationMessage.MessageType, Long> typeStatistics;

    @JsonProperty("tags")
    public List<String> tags;

    @JsonProperty("description")
    public String description;

    @JsonProperty("version")
    public String version;

    @JsonProperty("metadata")
    public Map<String, Object> metadata;

    public ConversationSession() {
    }

    public ConversationSession(String sessionId) {
        this.sessionId = sessionId;
        this.createdAt = LocalDateTime.now();
        this.lastUpdated = LocalDateTime.now();
        this.version = "1.0";
    }


    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public int getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(int messageCount) {
        this.messageCount = messageCount;
    }

    public List<ConversationMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<ConversationMessage> messages) {
        this.messages = messages;
    }

    public Map<ConversationMessage.MessageType, Long> getTypeStatistics() {
        return typeStatistics;
    }

    public void setTypeStatistics(Map<ConversationMessage.MessageType, Long> typeStatistics) {
        this.typeStatistics = typeStatistics;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final ConversationSession session = new ConversationSession();

        public Builder sessionId(String sessionId) {
            session.sessionId = sessionId;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            session.createdAt = createdAt;
            return this;
        }

        public Builder lastUpdated(LocalDateTime lastUpdated) {
            session.lastUpdated = lastUpdated;
            return this;
        }

        public Builder messageCount(int messageCount) {
            session.messageCount = messageCount;
            return this;
        }

        public Builder messages(List<ConversationMessage> messages) {
            session.messages = messages;
            return this;
        }

        public Builder typeStatistics(Map<ConversationMessage.MessageType, Long> typeStatistics) {
            session.typeStatistics = typeStatistics;
            return this;
        }

        public Builder tags(List<String> tags) {
            session.tags = tags;
            return this;
        }

        public Builder description(String description) {
            session.description = description;
            return this;
        }

        public Builder version(String version) {
            session.version = version;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            session.metadata = metadata;
            return this;
        }

        public ConversationSession build() {
            return session;
        }
    }
}
