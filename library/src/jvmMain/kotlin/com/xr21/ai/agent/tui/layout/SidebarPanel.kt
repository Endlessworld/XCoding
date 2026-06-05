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

/**
 * 左侧会话列表面板
 *
 * TODO: 2.4 阶段实现完整的会话列表交互
 */
import com.github.ajalt.mordant.rendering.BorderType
import com.github.ajalt.mordant.rendering.TextAlign
import com.github.ajalt.mordant.widgets.Panel
import com.xr21.ai.agent.tui.state.AppState

class SidebarPanel(private val appState: AppState) {
    fun render(isFocused: Boolean = false): Panel {
        val borderType = if (isFocused) BorderType.DOUBLE else BorderType.ROUNDED
        val sessionList = appState.sessions.mapIndexed { index, session ->
            val prefix = if (index == appState.currentSessionIndex) "▸ " else "  "
            val name = session.name
            "$prefix$name"
        }.joinToString("\n")

        val content = buildString {
            appendLine("会话 (${appState.sessionCount})")
            appendLine()
            appendLine(sessionList)
            appendLine()
            appendLine("[+] 新会话  Ctrl+N")
            appendLine("[×] 关闭    Ctrl+W")
        }

        return Panel(
            content.trimEnd(),
            title = "会话",
            titleAlign = TextAlign.CENTER,
            borderType = borderType
        )
    }
}

