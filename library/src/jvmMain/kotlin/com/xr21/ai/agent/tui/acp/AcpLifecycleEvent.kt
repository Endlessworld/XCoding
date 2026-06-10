/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 */
package com.xr21.ai.agent.tui.acp

/**
 * ACP 生命周期事件
 *
 * 通过 [AcpClientManager.lifecycleEvents] Flow 或 [AcpLifecycleListener] 监听。
 */
sealed class AcpLifecycleEvent {

    /** 状态变更 */
    data class StateChanged(
        val oldState: AcpLifecycleState,
        val newState: AcpLifecycleState
    ) : AcpLifecycleEvent()

    /** 连接成功 */
    data class Connected(
        val agentName: String,
        val agentVersion: String
    ) : AcpLifecycleEvent()

    /** 会话已创建 */
    data class SessionCreated(
        val sessionId: String
    ) : AcpLifecycleEvent()

    /** 会话已关闭 */
    object SessionClosed : AcpLifecycleEvent()

    /** 已断开连接 */
    data class Disconnected(
        val reason: String
    ) : AcpLifecycleEvent()

    /** 正在重连 */
    data class Reconnecting(
        val attempt: Int,
        val delayMs: Long
    ) : AcpLifecycleEvent()

    /** 发生错误 */
    data class ErrorOccurred(
        val error: Throwable
    ) : AcpLifecycleEvent()

    /** 已销毁 */
    object Destroyed : AcpLifecycleEvent()

    /** Prompt 完成 */
    data class PromptCompleted(
        val stopReason: String,
        val usage: String
    ) : AcpLifecycleEvent()
}
