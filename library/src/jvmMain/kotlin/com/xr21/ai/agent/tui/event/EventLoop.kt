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

import com.xr21.ai.agent.tui.state.AppState

/**
 * 主事件循环
 *
 * 负责：
 * 1. 监听键盘输入
 * 2. 从 ACP 客户端接收异步事件
 * 3. 调度 Action 到对应的 Handler
 * 4. 触发 UI 重绘
 *
 * TODO: 1.8 阶段实现完整的事件循环
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
            val action = DEFAULT_KEY_BINDINGS[keyEvent] ?: handleInputChar(keyEvent)
            val handler = actionHandlers[action]
            if (handler != null) {
                handler()
            }
        }
    }

    fun stop() {
        running = false
    }

    private fun handleInputChar(keyEvent: KeyEvent): Action? {
        if (keyEvent is KeyEvent.CharInput) {
            appState.inputBuffer += keyEvent.char
            return Action.NOOP
        }
        if (keyEvent == KeyEvent.Backspace && appState.inputBuffer.isNotEmpty()) {
            appState.inputBuffer = appState.inputBuffer.dropLast(1)
            return Action.NOOP
        }
        return null
    }
}