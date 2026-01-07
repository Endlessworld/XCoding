package com.xr21.ai.agent.session;

import lombok.Data;

/**
 * 会话简要信息
 */
@Data
public class SessionInfo {
    /**
     * 会话ID
     */
    private String sessionId;
    
    /**
     * 文件路径
     */
    private String filePath;
    
    /**
     * 消息数量
     */
    private int messageCount;
    
    /**
     * 创建时间
     */
    private String createdAt;
    
    /**
     * 最后更新时间
     */
    private String lastUpdated;
    
    /**
     * 简要描述（第一条用户消息）
     */
    private String briefDescription;
}
