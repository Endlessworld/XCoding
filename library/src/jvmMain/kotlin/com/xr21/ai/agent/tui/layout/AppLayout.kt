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

/** 面板类型 */
enum class PanelType { LEFT, CENTER, RIGHT, INPUT }

/**
 * 整体布局管理器
 *
 * TODO: 1.7 阶段实现完整的四分区布局
 */
class AppLayout(
    private val terminal: Terminal,
    private val appState: AppState
) {
    fun render(): String {
        // 使用 table 构建四分区布局
        val layout = table {
            // 三列：左侧 28%，中间 48%，右侧 24%
            column(0) { width = ColumnWidth.Fixed(30) }
            column(1) { width = ColumnWidth.Expand(1f) }
            column(2) { width = ColumnWidth.Fixed(25) }

            header {
                row {
                    cell(SidebarPanel(appState).render())
                    cell(ChatPanel(appState).render())
                    cell(InfoPanel(appState).render())
                }
            }
            footer {
                row {
                    cell("")
                    cell(InputPanel(appState).render())
                    cell("")
                }
            }
        }

        val statusBar = StatusBar(appState).render()
        return terminal.render(layout) + "\n" + statusBar
    }
}