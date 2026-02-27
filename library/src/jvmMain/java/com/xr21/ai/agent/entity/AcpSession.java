package com.xr21.ai.agent.entity;

import java.util.ArrayList;
import java.util.List;

/**
 * ACP 会话状态
 */
public class AcpSession {
    public final List<String> history;
    public final String threadId;
    final String sessionId;
    final String cwd;

    public AcpSession(String sessionId, String threadId, String cwd) {
        this.sessionId = sessionId;
        this.threadId = threadId;
        this.cwd = cwd;
        this.history = new ArrayList<>();
    }


}