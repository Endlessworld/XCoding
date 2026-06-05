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
package com.xr21.ai.agent.tui.layout

import com.github.ajalt.mordant.table.ColumnWidth
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.terminal.Terminal
import com.xr21.ai.agent.tui.state.AppState
import com.xr21.ai.agent.tui.theme.TuiTheme

/** 面板类型（两栏 + 输入框，LEFT 仅用于弹框焦点，不参与主布局） */
enum class PanelType { LEFT, CENTER, INPUT }

/**
 * 整体布局管理器（两栏布局 + 会话列表弹框）
 *
 * ┌──────────────────────┬──────────────┐
 * │   对话消息流          │   信息面板    │
 * │   (ChatPanel)        │  (InfoPanel) │
 * │                      │   Token/Todo │
 * │                      │   模型/Agent │
 * │                      │   连接/时间   │
 * ├──────────────────────┴──────────────┤
 * │        输入框 (合并两列)              │
 * │        [Enter 发送, Alt+Enter 换行]   │
 * └─────────────────────────────────────┘
 *
 * 按 Ctrl+P 弹出会话列表弹框覆盖在主布局之上。
 */
class AppLayout(
    private val terminal: Terminal,
    private val appState: AppState,
    private val theme: TuiTheme
) {
    fun render(): String {
        val focus = appState.focusPanel
        val terminalWidth = terminal.size.width.coerceIn(40, 400)
        val chatWidth = (terminalWidth * 0.65f).toInt().coerceIn(25, 280)
        val infoWidth = (terminalWidth - chatWidth).coerceIn(15, 50)

        val terminalHeight = terminal.size.height.coerceIn(10, 200)
        val inputAvailableLines = 4
        val chatAvailableLines = (terminalHeight - inputAvailableLines - 2).coerceAtLeast(5)

        // 两栏主布局（左侧 ChatPanel + 右侧 InfoPanel）
        // InfoPanel 使用右浮动效果，紧贴终端窗口右边框
        val mainTable = table {
            column(0) { width = ColumnWidth.Fixed(chatWidth) }
            column(1) {
                width = ColumnWidth.Fixed(infoWidth)
            }

            header {
                row {
                    cell(ChatPanel(appState, theme, terminal).render(focus == PanelType.CENTER, chatAvailableLines))
                    cell(InfoPanel(appState, theme).render(isFocused = false))
                }
            }
        }

        // 底部输入框（合并两列宽度）
        val inputPanel = InputPanel(appState, theme).render(focus == PanelType.INPUT, inputAvailableLines)

        val mainContent = terminal.render(mainTable) + "\n" + terminal.render(inputPanel)

        // 如果弹框可见，覆盖渲染在最上方
        return if (appState.isSessionListPopupVisible) {
            val popup = SessionListPopup(appState, theme).render()
            mainContent + "\n\n" + terminal.render(popup)
        } else {
            mainContent
        }
    }
}