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

import com.github.ajalt.mordant.input.enterRawMode
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.terminal.Terminal
import com.xr21.ai.agent.tui.acp.AcpClientManager
import com.xr21.ai.agent.tui.acp.AcpEventProcessor
import com.xr21.ai.agent.tui.config.TuiConfig
import com.xr21.ai.agent.tui.event.Action
import com.xr21.ai.agent.tui.event.EventLoop
import com.xr21.ai.agent.tui.event.InputHandler
import com.xr21.ai.agent.tui.layout.MainLayout
import com.xr21.ai.agent.tui.state.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * TUI 应用主入口
 *
 * 管理应用生命周期：初始化、运行、清理。
 * 集成 ACP 客户端实现完整的发送→接收→渲染链路。
 */
class TuiApp(
    private val terminal: Terminal,
    private val config: TuiConfig = TuiConfig()
) {
    private val appState = AppState()
    private val inputHandler = InputHandler(terminal)
    private val mainLayout = MainLayout(appState, terminal, config.theme)
    private val acpClient = AcpClientManager(appState)
    private val acpProcessor = AcpEventProcessor(appState)
    private lateinit var eventLoop: EventLoop
    private val scope = CoroutineScope(Dispatchers.Default)
    private var statusBarUpdateJob: kotlinx.coroutines.Job? = null

    suspend fun start() {
        // 1. 进入原始模式（返回 AutoCloseable，在 finally 中自动退出）
        val rawMode = terminal.enterRawMode()

        // 进入 alternate screen buffer，避免旧帧进入 scrollback
        terminal.rawPrint("\u001b[?1049h")

        // 2. 初始化事件循环
        eventLoop = EventLoop(appState, inputHandler, buildActionHandlers())

        // 3. 渲染初始界面
        render()

        // 4. 启动状态栏定时刷新（每秒更新系统时间）
        startStatusBarTimer()

        // 5. 连接 Agent（非阻塞启动）
        connectToAgent()

        // 5. 启动事件循环（阻塞，直到退出）
        try {
            eventLoop.run()
        } catch (e: Exception) {
            appState.errorMessage = "运行时异常: ${e.message}"
        } finally {
            try {
                rawMode.close()
            } catch (_: Exception) {}
            cleanup()
        }
    }

    /**
     * 连接 ACP Agent（WebSocket 模式或 Stdio 模式）
     */
    private suspend fun connectToAgent() {
        scope.launch {
            val result = acpClient.connect(config)
            if (result.isSuccess) {
                // 启动 ACP 事件收集
                acpClient.startEventCollection { event ->
                    acpProcessor.processEvent(event)
                    render()
                }
                render()
            } else {
                appState.errorMessage = "Agent 连接失败: ${result.exceptionOrNull()?.message}"
                render()
            }
        }
    }

    private fun buildActionHandlers(): Map<Action, suspend () -> Unit> {
        return mapOf(
            Action.SEND_MESSAGE to {
                sendMessage()
            },
            Action.CANCEL_OR_INTERRUPT to {
                cancelResponse()
            },
            Action.QUIT_APP to {
                eventLoop.stop()
            },
            Action.NOOP to {
                render()
            },
            Action.CLEAR_CONVERSATION to {
                appState.clearConversation()
                render()
            },
            Action.NEW_SESSION to {
                appState.newSession()
                render()
            },
            Action.CLOSE_SESSION to {
                appState.closeCurrentSession()
                render()
            },
            Action.SCROLL_UP to {
                appState.scrollUp()
                render()
            },
            Action.SCROLL_DOWN to {
                appState.scrollDown()
                render()
            },
            Action.SCROLL_PAGE_UP to {
                appState.scrollPageUp()
                render()
            },
            Action.SCROLL_PAGE_DOWN to {
                appState.scrollPageDown()
                render()
            },
            Action.SCROLL_TOP to {
                appState.scrollOffset = 0
                render()
            },
            Action.SCROLL_BOTTOM to {
                appState.scrollOffset = Int.MAX_VALUE
                render()
            },
            Action.INPUT_HISTORY_PREV to {
                appState.inputHistoryPrev()
                render()
            },
            Action.INPUT_HISTORY_NEXT to {
                appState.inputHistoryNext()
                render()
            },
            Action.FOCUS_NEXT to {
                appState.focusNext()
                render()
            },
            Action.FOCUS_PREVIOUS to {
                appState.focusPrevious()
                render()
            },
            Action.SELECT_UP to {
                appState.selectUp()
                render()
            },
            Action.SELECT_DOWN to {
                appState.selectDown()
                render()
            },
            Action.SELECT_CONFIRM to {
                appState.confirmSelection()
                render()
            },
            Action.TOGGLE_EXPAND to {
                appState.toggleLastToolMessage()
                render()
            },
        )
    }

    private fun render() {
        terminal.cursor.hide()
        // 重新检测终端尺寸（支持窗口 resize）
        terminal.updateSize()
        // 清屏 + 光标移到左上角
        terminal.cursor.move { clearScreen(); setPosition(0, 0) }
        val rendered = mainLayout.render()
        terminal.println(rendered)
        terminal.cursor.show()
    }

    /**
     * 发送消息：更新 AppState + 通过 ACP 发送到 Agent
     */
    private suspend fun sendMessage() {
        val message = appState.inputBuffer.trim()
        if (message.isEmpty()) return

        // 1. 更新本地状态（添加用户消息）
        appState.sendMessage(message)
        render()

        // 2. 通过 ACP 发送到 Agent
        if (acpClient.isActive) {
            scope.launch {
                val result = acpClient.sendPrompt(message)
                if (result.isFailure) {
                    appState.errorMessage = "发送失败: ${result.exceptionOrNull()?.message}"
                    render()
                }
            }
        } else {
            // 没有 Agent 连接时，提示用户
            appState.appendStreamingContent("Agent 未连接，无法处理消息。使用 --ws-url 连接或 --command 启动 Agent。")
            appState.finishStreaming()
            render()
        }
    }

    /**
     * 中断当前响应
     */
    private suspend fun cancelResponse() {
        if (appState.isStreaming) {
            if (acpClient.isActive) {
                scope.launch {
                    acpClient.sendCancel()
                }
            }
            appState.finishStreaming()
            render()
        }
    }

    private fun startStatusBarTimer() {
        statusBarUpdateJob = scope.launch {
            while (true) {
                delay(1000)
                render()
            }
        }
    }

    private fun cleanup() {
        statusBarUpdateJob?.cancel()
        statusBarUpdateJob = null
        acpClient.disconnect()
        // 恢复终端状态
        try {
            terminal.cursor.show()
            terminal.cursor.move { clearScreen(); setPosition(0, 0) }
            terminal.println((config.theme.success + TextStyles.bold)("Goodbye!"))
        } catch (_: Exception) {
            // 忽略清理时的异常
        } finally {
            // 清屏后退出 alternate screen buffer，防止内容残留在主屏幕
            try {
                terminal.cursor.move { clearScreen(); setPosition(0, 0) }
                terminal.rawPrint("\u001b[?1049l")
            } catch (_: Exception) {
                // 忽略清理时的异常
            }
        }
    }
}