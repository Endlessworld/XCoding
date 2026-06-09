/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 */
package com.xr21.ai.agent.tui;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Todo 项
 */
public class TodoItem {
    public final String id;
    public final String content;
    public final TodoStatus status;
    public final TodoPriority priority;
    public final LocalDateTime createdAt;

    public TodoItem(String content, TodoStatus status, TodoPriority priority) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.content = content;
        this.status = status;
        this.priority = priority;
        this.createdAt = LocalDateTime.now();
    }
}
