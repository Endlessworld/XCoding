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

/** 面板类型 */
enum class PanelType { LEFT, CENTER, RIGHT, INPUT }

/**
 * 整体布局管理器
 *
 * 管理四分区 TUI 布局，支持面板焦点切换和边框高亮。
 */
class AppLayout(
    private val terminal: Terminal,
    private val appState: AppState,
    private val theme: TuiTheme
) {
    fun render(): String {
        val focus = appState.focusPanel
        val terminalWidth = terminal.size.width.coerceIn(40, 400)
        val sidebarWidth = (terminalWidth * 0.22f).toInt().coerceIn(15, 40)
        val infoWidth = (terminalWidth * 0.20f).toInt().coerceIn(15, 35)

        val terminalHeight = terminal.size.height.coerceIn(10, 200)
        val inputAvailableLines = 3
        val chatAvailableLines = (terminalHeight - inputAvailableLines - 5).coerceAtLeast(5)

        val layout = table {
            column(0) { width = ColumnWidth.Fixed(sidebarWidth) }
            column(1) { width = ColumnWidth.Expand(1f) }
            column(2) { width = ColumnWidth.Fixed(infoWidth) }

            header {
                row {
                    cell(SidebarPanel(appState, theme).render(focus == PanelType.LEFT))
                    cell(ChatPanel(appState, theme, terminal).render(focus == PanelType.CENTER, chatAvailableLines))
                    cell(InfoPanel(appState, theme).render(focus == PanelType.RIGHT))
                }
            }
            footer {
                row {
                    cell("")
                    cell(InputPanel(appState, theme).render(focus == PanelType.INPUT, inputAvailableLines))
                    cell("")
                }
            }
        }

        val statusBar = StatusBar(appState, theme).render()
        return terminal.render(layout) + "\n" + statusBar
    }
}