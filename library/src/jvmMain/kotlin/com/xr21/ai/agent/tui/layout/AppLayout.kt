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
 * 管理四分区 TUI 布局，支持面板焦点切换和边框高亮。
 */
class AppLayout(
    private val terminal: Terminal,
    private val appState: AppState
) {
    companion object {
        private var cachedWidth: Int? = null

        /**
         * 获取终端宽度（缓存，仅首次检测）
         *
         * 策略：
         * 1. 尝试执行 Windows 的 `mode con` 命令获取列数
         * 2. 尝试读取环境变量 COLUMNS
         * 3. 回退默认值 120
         */
        private fun detectTerminalWidth(): Int {
            try {
                // Windows: 使用 mode con 命令
                if (System.getProperty("os.name").lowercase().contains("windows")) {
                    val process = ProcessBuilder("cmd", "/c", "mode", "con")
                        .redirectErrorStream(true)
                        .start()
                    val output = process.inputStream.bufferedReader().readText()
                    process.waitFor()
                    // 解析 "Columns: xxx"
                    val regex = Regex("Columns:\\s+(\\d+)")
                    val match = regex.find(output)
                    if (match != null) {
                        return match.groupValues[1].toInt().coerceIn(40, 400)
                    }
                }
            } catch (_: Exception) {}

            // 尝试环境变量
            val envWidth = System.getenv("COLUMNS")?.toIntOrNull()
            if (envWidth != null) return envWidth.coerceIn(40, 400)

            return 120
        }
    }

    fun render(): String {
        val focus = appState.focusPanel
        // 获取终端宽度（检测一次后缓存）
        val tw = cachedWidth ?: detectTerminalWidth().also { cachedWidth = it }
        val terminalWidth = tw.coerceIn(40, 400)
        val sidebarWidth = (terminalWidth * 0.22f).toInt().coerceIn(15, 40)
        val infoWidth = (terminalWidth * 0.20f).toInt().coerceIn(15, 35)

        // 使用 table 构建四分区布局
        val layout = table {
            // 三列：左侧 22%，中间 58%，右侧 20%
            column(0) { width = ColumnWidth.Fixed(sidebarWidth) }
            column(1) { width = ColumnWidth.Expand(1f) }
            column(2) { width = ColumnWidth.Fixed(infoWidth) }

            header {
                row {
                    cell(SidebarPanel(appState).render(focus == PanelType.LEFT))
                    cell(ChatPanel(appState).render(focus == PanelType.CENTER))
                    cell(InfoPanel(appState).render(focus == PanelType.RIGHT))
                }
            }
            footer {
                row {
                    cell("")
                    cell(InputPanel(appState).render(focus == PanelType.INPUT))
                    cell("")
                }
            }
        }

        val statusBar = StatusBar(appState).render()
        return terminal.render(layout) + "\n" + statusBar
    }
}