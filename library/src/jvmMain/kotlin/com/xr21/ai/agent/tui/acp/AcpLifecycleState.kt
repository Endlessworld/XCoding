/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 */
package com.xr21.ai.agent.tui.acp

/**
 * ACP 客户端生命周期状态
 *
 * 状态转换图：
 * CREATED → CONNECTING → INITIALIZED → SESSION_ACTIVE → DISCONNECTED → DESTROYED
 *              ↓              ↓              ↓
 *         DISCONNECTED   DISCONNECTED    DISCONNECTED
 *              ↓
 *           DESTROYED
 */
enum class AcpLifecycleState {
    /** 已创建，尚未连接 */
    CREATED,

    /** 正在连接中 */
    CONNECTING,

    /** 已初始化（Client.initialize 完成），尚未创建会话 */
    INITIALIZED,

    /** 会话已激活（ClientSession 已创建） */
    SESSION_ACTIVE,

    /** 已断开连接 */
    DISCONNECTED,

    /** 已销毁，不可再使用 */
    DESTROYED
}
