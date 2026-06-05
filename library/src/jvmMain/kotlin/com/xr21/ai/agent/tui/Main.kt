/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.xr21.ai.agent.tui

import com.github.ajalt.mordant.terminal.Terminal
import com.xr21.ai.agent.tui.config.TuiConfig
import kotlinx.coroutines.runBlocking

/**
 * TUI 应用入口
 *
 * 解析命令行参数，初始化 Terminal，启动 TUI 应用。
 *
 * 使用方式:
 *   java -jar ai-agent-tui.jar [options]
 *
 * 选项:
 *   --command <cmd>    Agent 启动命令
 *   --help             显示帮助信息
 *
 * TODO: 1.2 阶段实现完整的 CLI 参数解析
 */
fun main(args: Array<String>) = runBlocking {
    // 检测 Windows Terminal 环境
    detectWindowsTerminal()

    val config = parseArgs(args)
    val terminal = Terminal()
    val app = TuiApp(terminal, config)
    app.start()
}

/**
 * 检测 Windows Terminal 环境
 *
 * 在 Windows 上，检测当前是否运行在 Windows Terminal 中。
 * 如果不是，提示用户 Windows Terminal 可提供更好的 TUI 体验。
 * 检测方式：检查环境变量 WT_SESSION（Windows Terminal 特有）
 */
private fun detectWindowsTerminal() {
    if (!System.getProperty("os.name").lowercase().contains("windows")) return

    val wtSession = System.getenv("WT_SESSION")
    if (wtSession.isNullOrBlank()) {
        // 不在 Windows Terminal 中运行
        System.err.println("[提示] 检测到 Windows 环境但未使用 Windows Terminal。")
        System.err.println("       建议使用 Windows Terminal 以获得最佳 TUI 体验。")
    }
}

private fun parseArgs(args: Array<String>): TuiConfig {
    var config = TuiConfig()
    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--command" -> {
                // 收集 --command 后面所有非 `-` 开头的参数作为命令列表
                val commandParts = mutableListOf<String>()
                var j = i + 1
                while (j < args.size && !args[j].startsWith("-")) {
                    commandParts.add(args[j])
                    j++
                }
                if (commandParts.isNotEmpty()) {
                    config = config.copy(agentCommand = commandParts)
                }
                i = j - 1 // 外层循环会 i++，回退到下一个 flag 前一个位置
            }
            "--ws-url" -> {
                if (i + 1 < args.size && !args[i + 1].startsWith("-")) {
                    config = config.copy(webSocketUrl = args[i + 1])
                    i++
                }
            }
            "--ws-server-port" -> {
                if (i + 1 < args.size) {
                    val port = args[i + 1].toIntOrNull()
                    if (port != null) {
                        config = config.copy(webSocketServerPort = port)
                    }
                    i++
                }
            }
            "--tui" -> {
                // 显式忽略 --tui，当前入口就是 TUI 模式
            }
            "--help" -> {
                println("Usage: java -jar XAgent.jar --tui [options]")
                println("Options:")
                println("  --command <cmd>          Agent startup command (stdio mode)")
                println("  --ws-url <url>           WebSocket URL to connect to (e.g. ws://localhost:9988/acp)")
                println("  --ws-server-port <port>  Port for internal WebSocket server (default: 9988)")
                println("  --tui                    Start in TUI mode (default)")
                println("  --help                   Show this help")
                kotlin.system.exitProcess(0)
            }
        }
        i++
    }
    return config
}