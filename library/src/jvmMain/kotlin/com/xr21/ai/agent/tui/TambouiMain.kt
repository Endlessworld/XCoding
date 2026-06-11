/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 */
package com.xr21.ai.agent.tui

/**
 * Tamboui 版 TUI 应用入口
 *
 * 解析命令行参数，初始化 Tamboui TUI，启动 ACP 桥接。
 */
fun main(args: Array<String>) {
    try {
        val app = TuiApp()
        app.start()
    } catch (e: Exception) {
        System.err.println("TUI 运行时异常: ${e.message}")
        e.printStackTrace()
    }
}

