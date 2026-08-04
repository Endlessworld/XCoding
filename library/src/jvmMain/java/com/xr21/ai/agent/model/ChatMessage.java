/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 */
package com.xr21.ai.agent.model;

import com.agentclientprotocol.model.SessionUpdate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 聊天消息 —— ACP 事件容器。
 *
 * <p>ASSISTANT 消息保留原始的 ACP 流式事件列表，渲染时通过
 * {@link com.xr21.ai.agent.bridge.BridgeKt} 提供的桥接方法按需聚合。
 *
 * <p>USER 消息直接使用 {@code content} 存储文本（简化处理）。
 */
public class ChatMessage {
    public final String id;
    public final MessageRole role;
    public final LocalDateTime timestamp;
    public String content;                    // USER/ERROR 消息文本（回退）
    public final List<SessionUpdate> events;  // ASSISTANT 消息原始 ACP 事件
    public boolean isStreaming;
    public boolean isExpanded;                // 控制思考/工具详情折叠

    public ChatMessage(MessageRole role) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.role = role;
        this.timestamp = LocalDateTime.now();
        this.content = "";
        this.events = new ArrayList<>();
        this.isStreaming = false;
        this.isExpanded = false;
    }

    public ChatMessage(MessageRole role, String content) {
        this(role);
        this.content = content;
    }

    public ChatMessage(MessageRole role, boolean isStreaming) {
        this(role);
        this.isStreaming = isStreaming;
    }
}
