/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 */
package com.xr21.ai.agent.tui;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 会话
 */
public class Session {
    public final String id;
    public final List<ChatMessage> messages;
    public final LocalDateTime createdAt;
    public String name;
    public LocalDateTime updatedAt;

    public Session() {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.name = "New Session";
        this.messages = new ArrayList<>();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}
