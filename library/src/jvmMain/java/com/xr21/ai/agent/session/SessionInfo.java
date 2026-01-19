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
}
