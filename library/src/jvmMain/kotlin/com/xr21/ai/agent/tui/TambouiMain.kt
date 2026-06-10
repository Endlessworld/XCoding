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
    detectWindowsTerminal()

    val app = TambouiTuiApp()

    // 自动感知 OS 主题模式（夜间/白天）
    val isDark = OsThemeDetector.isDarkMode()
    if (isDark != null) {
        app.setThemeMode(isDark)
        app.appState.isDarkMode = isDark
        System.err.println(if (isDark) "[TUI] 检测到暗色模式" else "[TUI] 检测到亮色模式")
    } else {
        app.setThemeMode(false)
        app.appState.isDarkMode = false
        System.err.println("[TUI] 无法检测 OS 主题，使用默认暗色模式")
    }
    val bridge = TambouiAcpBridge(app.appState)
    app.setAcpBridge(bridge)

    try {
        app.start()
    } catch (e: Exception) {
        System.err.println("TUI 运行时异常: ${e.message}")
        e.printStackTrace()
    }
}

private fun detectWindowsTerminal() {
    // Windows Terminal detection removed to avoid console noise during ACP connection
}
