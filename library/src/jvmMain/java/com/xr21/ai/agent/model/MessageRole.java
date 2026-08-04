/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 */
package com.xr21.ai.agent.model;

/**
 * 消息角色。
 *
 * <p>ACP 协议仅定义 {@code user} 和 {@code assistant} 两种角色。
 * TUI 保留了 ERROR 用于异常展示。
 * 工具调用等事件作为 {@code assistant} 消息内部的组成部分，
 * 不再作为独立的消息角色。
 */
public enum MessageRole {
    USER, ASSISTANT, ERROR
}
