/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 */
package com.xr21.ai.agent.tui.acp

/**
 * 重连策略
 *
 * 定义 [AcpClientManager] 在连接断开后的自动重连行为。
 */
sealed class ReconnectStrategy {

    /** 固定间隔重连 */
    data class FixedInterval(
        val intervalMs: Long = 3000
    ) : ReconnectStrategy()

    /** 指数退避重连 */
    data class ExponentialBackoff(
        val initialIntervalMs: Long = 1000,
        val maxIntervalMs: Long = 30000,
        val multiplier: Double = 2.0
    ) : ReconnectStrategy()

    /** 自定义重连策略 */
    data class Custom(
        val strategy: suspend (attempt: Int) -> Long
    ) : ReconnectStrategy()

    /** 不重连 */
    object NoReconnect : ReconnectStrategy()
}
