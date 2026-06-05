/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 */
package com.xr21.ai.agent.tui.java;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 聊天消息
 */
public class ChatMessage {
    public final String id;
    public final MessageRole role;
    public final LocalDateTime timestamp;
    public String content;
    public boolean isStreaming;
    public boolean isExpanded;

    public ChatMessage(MessageRole role, String content) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.role = role;
        this.content = content;
        this.timestamp = LocalDateTime.now();
        this.isStreaming = false;
        this.isExpanded = false;
    }

    public ChatMessage(MessageRole role, String content, boolean isStreaming) {
        this(role, content);
        this.isStreaming = isStreaming;
    }
}
