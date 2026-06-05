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

import com.github.ajalt.mordant.rendering.BorderType
import com.github.ajalt.mordant.rendering.TextAlign
import com.github.ajalt.mordant.widgets.Panel
import com.xr21.ai.agent.tui.state.AppState
import com.xr21.ai.agent.tui.theme.TuiTheme

/**
 * 会话列表弹框
 *
 * 按 Ctrl+P 时弹出，显示所有会话列表。
 * 使用 Up/Down 选择，Enter 确认切换，Esc 关闭。
 */
class SessionListPopup(
    private val appState: AppState,
    private val theme: TuiTheme
) {
    fun render(): Panel {
        val sessionList = appState.sessions.mapIndexed { index, session ->
            val isSelected = index == appState.sidebarSelectedIndex
            val isCurrent = index == appState.currentSessionIndex
            val prefix = when {
                isSelected && isCurrent -> theme.selectedText("▸ ")
                isSelected -> theme.selectedText("▸ ")
                isCurrent -> theme.currentIndicator("● ")
                else -> theme.textMuted("  ")
            }
            val name = when {
                isSelected -> theme.selectedText(session.name)
                isCurrent -> theme.textSecondary(session.name)
                else -> theme.textMuted(session.name)
            }
            "  $prefix$name"
        }.joinToString("\n")

        val content = buildString {
            appendLine(sessionList)
            appendLine()
            appendLine(theme.keyHint("↑↓ 选择  Enter 切换  Esc 关闭"))
        }

        return Panel(
            content.trimEnd(),
            title = theme.accent("会话列表 (${appState.sessionCount})"),
            titleAlign = TextAlign.CENTER,
            borderType = BorderType.DOUBLE,
            borderStyle = theme.borderFocused
        )
    }
}