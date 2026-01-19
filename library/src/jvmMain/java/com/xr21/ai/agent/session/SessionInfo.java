package com.xr21.ai.agent.session;

/**
 * 会话简要信息
 */
public class SessionInfo {
    /**
     * 会话ID
     */
    public String sessionId;

    /**
     * 文件路径
     */
    public String filePath;

    /**
     * 消息数量
     */
    public int messageCount;

    /**
     * 创建时间
     */
    public String createdAt;

    /**
     * 最后更新时间
     */
    public String lastUpdated;

    /**
     * 简要描述（第一条用户消息）
     */
    public String briefDescription;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public int getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(int messageCount) {
        this.messageCount = messageCount;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(String lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public String getBriefDescription() {
        return briefDescription;
    }

    public void setBriefDescription(String briefDescription) {
        this.briefDescription = briefDescription;
    }
}
