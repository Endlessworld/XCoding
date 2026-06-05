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
package com.xr21.ai.agent.tui.event

import com.xr21.ai.agent.tui.layout.PanelType
import com.xr21.ai.agent.tui.state.AppState

/**
 * 主事件循环
 *
 * 负责：
 * 1. 监听键盘输入
 * 2. 调度 Action 到对应的 Handler
 * 3. 触发 UI 重绘
 */
class EventLoop(
    private val appState: AppState,
    private val inputHandler: InputHandler,
    private val actionHandlers: Map<Action, suspend () -> Unit>
) {
    private var running = true

    suspend fun run() {
        while (running) {
            val keyEvent = inputHandler.readKey()
            val action = resolveAction(keyEvent)
            val handler = actionHandlers[action]
            if (handler != null) {
                handler()
            }
        }
    }

    fun stop() {
        running = false
    }

    /**
     * 将 KeyEvent 解析为 Action
     * 优先匹配快捷键映射，再处理普通字符输入
     * Up/Down/Enter 根据当前焦点面板做上下文分发
     */
    private fun resolveAction(keyEvent: KeyEvent): Action? {
        // 上下文敏感的按键：根据焦点面板分发不同 Action
        when (keyEvent) {
            KeyEvent.Up -> {
                return when (appState.focusPanel) {
                    PanelType.LEFT -> Action.SELECT_UP
                    PanelType.CENTER -> Action.SCROLL_UP
                    PanelType.INPUT -> Action.INPUT_HISTORY_PREV
                    else -> Action.NOOP
                }
            }
            KeyEvent.Down -> {
                return when (appState.focusPanel) {
                    PanelType.LEFT -> Action.SELECT_DOWN
                    PanelType.CENTER -> Action.SCROLL_DOWN
                    PanelType.INPUT -> Action.INPUT_HISTORY_NEXT
                    else -> Action.NOOP
                }
            }
            else -> { }
        }

        // 先查快捷键映射表
        val boundAction = DEFAULT_KEY_BINDINGS[keyEvent]
        if (boundAction != null) return boundAction

        // 处理普通字符输入和退格
        return handleInputChar(keyEvent)
    }

    private fun handleInputChar(keyEvent: KeyEvent): Action? {
        return when (keyEvent) {
            is KeyEvent.CharInput -> {
                val pos = appState.inputCursorPos.coerceIn(0, appState.inputBuffer.length)
                appState.inputBuffer = appState.inputBuffer.substring(0, pos) + keyEvent.char + appState.inputBuffer.substring(pos)
                appState.inputCursorPos = pos + 1
                Action.NOOP
            }
            KeyEvent.Backspace -> {
                if (appState.inputBuffer.isNotEmpty() && appState.inputCursorPos > 0) {
                    val pos = appState.inputCursorPos - 1
                    appState.inputBuffer = appState.inputBuffer.substring(0, pos) + appState.inputBuffer.substring(pos + 1)
                    appState.inputCursorPos = pos
                }
                Action.NOOP
            }
            KeyEvent.Enter -> {
                // Enter 根据焦点面板分发：Sidebar 确认选择，Input 发送消息
                when (appState.focusPanel) {
                    PanelType.LEFT -> Action.SELECT_CONFIRM
                    else -> {
                        if (appState.inputBuffer.isNotBlank()) {
                            Action.SEND_MESSAGE
                        } else {
                            null
                        }
                    }
                }
            }
            KeyEvent.AltEnter -> {
                // Alt+Enter 插入换行
                val pos = appState.inputCursorPos.coerceIn(0, appState.inputBuffer.length)
                appState.inputBuffer = appState.inputBuffer.substring(0, pos) + "\n" + appState.inputBuffer.substring(pos)
                appState.inputCursorPos = pos + 1
                Action.NOOP
            }
            KeyEvent.Space -> {
                // Space 在 ChatPanel 焦点时切换工具消息展开，否则输入空格
                when (appState.focusPanel) {
                    PanelType.CENTER -> Action.TOGGLE_EXPAND
                    PanelType.INPUT -> {
                        val pos = appState.inputCursorPos.coerceIn(0, appState.inputBuffer.length)
                        appState.inputBuffer = appState.inputBuffer.substring(0, pos) + " " + appState.inputBuffer.substring(pos)
                        appState.inputCursorPos = pos + 1
                        Action.NOOP
                    }
                    else -> Action.NOOP
                }
            }
            KeyEvent.Left -> {
                if (appState.inputCursorPos > 0) {
                    appState.inputCursorPos--
                }
                Action.NOOP
            }
            KeyEvent.Right -> {
                if (appState.inputCursorPos < appState.inputBuffer.length) {
                    appState.inputCursorPos++
                }
                Action.NOOP
            }
            KeyEvent.Home -> {
                appState.inputCursorPos = 0
                Action.NOOP
            }
            KeyEvent.End -> {
                appState.inputCursorPos = appState.inputBuffer.length
                Action.NOOP
            }
            else -> null
        }
    }
}