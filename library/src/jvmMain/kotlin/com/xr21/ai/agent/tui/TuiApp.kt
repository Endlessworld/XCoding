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
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.widgets.Text
import com.xr21.ai.agent.tui.event.Action
import com.xr21.ai.agent.tui.event.EventLoop
import com.xr21.ai.agent.tui.event.InputHandler
import com.xr21.ai.agent.tui.layout.MainLayout
import com.xr21.ai.agent.tui.state.AppState

/**
 * TUI 应用主入口
 *
 * 管理应用生命周期：初始化、运行、清理。
 *
 * TODO: 1.13 阶段完成集成联调
 */
class TuiApp(private val terminal: Terminal) {
    private val appState = AppState()
    private val inputHandler = InputHandler(terminal)
    private val mainLayout = MainLayout(appState)
    private lateinit var eventLoop: EventLoop

    suspend fun start() {
        // 1. 进入原始模式
        terminal.enterRawMode()

        // 2. 清理屏幕
//        terminal.clearScreen()

        // 3. 初始化事件循环
        eventLoop = EventLoop(appState, inputHandler, buildActionHandlers())

        // 4. 渲染初始界面
        render()

        // 5. 启动事件循环
        try {
            eventLoop.run()
        } finally {
            cleanup()
        }
    }

    private fun buildActionHandlers(): Map<Action, suspend () -> Unit> {
        return mapOf(
            Action.SEND_MESSAGE to {
                sendMessage()
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
            Action.INPUT_HISTORY_PREV to {
                appState.inputHistoryPrev()
                render()
            },
            Action.INPUT_HISTORY_NEXT to {
                appState.inputHistoryNext()
                render()
            },
        )
    }

    private fun render() {
        terminal.cursor.move(0, 0)
        val rendered = mainLayout.render()
        terminal.println(rendered)
    }

    private fun sendMessage() {
        val message = appState.inputBuffer.trim()
        if (message.isEmpty()) return
        appState.sendMessage(message)
        render()
    }

    private fun cleanup() {
//        terminal.exitRawMode()
//        terminal.showCursor()
        terminal.println(Text("Goodbye!", colors = TextColors.brightGreen, style = TextStyles.bold))
    }
}