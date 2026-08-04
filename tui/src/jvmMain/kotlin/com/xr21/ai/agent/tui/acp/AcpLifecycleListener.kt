/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 */
package com.xr21.ai.agent.tui.acp

/**
 * ACP 生命周期监听器
 *
 * 用于监听 [AcpClientManager] 的生命周期事件。
 * 与 [AcpClientManager.lifecycleEvents] Flow 相比，此接口更适合 Java 调用方。
 */
fun interface AcpLifecycleListener {
    /** 生命周期事件回调 */
    fun onLifecycleEvent(event: AcpLifecycleEvent)
}
