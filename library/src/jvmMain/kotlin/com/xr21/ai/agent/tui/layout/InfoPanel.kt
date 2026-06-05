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
 * 右侧信息面板
 *
 * TODO: 2.7 阶段实现完整的信息显示
 */
import com.github.ajalt.mordant.rendering.BorderType
import com.github.ajalt.mordant.rendering.TextAlign
import com.github.ajalt.mordant.rendering.Widget
import com.github.ajalt.mordant.widgets.Text
import com.xr21.ai.agent.tui.acp.ConnectionState
import com.xr21.ai.agent.tui.state.AppState
import com.xr21.ai.agent.tui.state.TodoPriority
import com.xr21.ai.agent.tui.state.TodoStatus
import com.xr21.ai.agent.tui.theme.GradientPanelWidget
import com.xr21.ai.agent.tui.theme.TuiTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class InfoPanel(
    private val appState: AppState,
    private val theme: TuiTheme
) {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun render(isFocused: Boolean = false): Widget {
        val borderType = if (isFocused) BorderType.DOUBLE else BorderType.ROUNDED
        val borderStyle = if (isFocused) theme.borderFocused else theme.borderNormal
        val titleStyle = if (isFocused) theme.panelTitleFocused else theme.panelTitle
        val content = buildString {
            // Token 用量
            appendLine(theme.accent("📊 Token 用量"))
            appendLine(theme.textSecondary("  Prompt: ") + theme.info("${appState.tokenUsage.promptTokens}"))
            appendLine(theme.textSecondary("  生成:    ") + theme.info("${appState.tokenUsage.completionTokens}"))
            appendLine(theme.textSecondary("  总计:    ") + theme.accent("${appState.tokenUsage.totalTokens}"))
            if (appState.tokenUsage.costUsd > 0) {
                appendLine(theme.textSecondary("  费用:    ") + theme.warning("$${String.format("%.4f", appState.tokenUsage.costUsd)}"))
            }
            appendLine()

            // Todo 列表
            if (appState.todos.isNotEmpty()) {
                val completed = appState.todos.count { it.status == TodoStatus.COMPLETED }
                val total = appState.todos.size
                appendLine(theme.accent("📋 Todo") + theme.textSecondary(" ($completed/$total)"))
                appState.todos.forEach { todo ->
                    val statusIcon = when (todo.status) {
                        TodoStatus.PENDING -> theme.textMuted("○")
                        TodoStatus.IN_PROGRESS -> theme.warning("◌")
                        TodoStatus.COMPLETED -> theme.success("✓")
                        TodoStatus.FAILED -> theme.error("✗")
                        TodoStatus.SKIPPED -> theme.textMuted("—")
                    }
                    val priorityStyle = when (todo.priority) {
                        TodoPriority.HIGH -> theme.error
                        TodoPriority.MEDIUM -> theme.warning
                        TodoPriority.LOW -> theme.info
                    }
                    appendLine("  " + priorityStyle("●") + " $statusIcon " + theme.textPrimary(todo.content))
                }
                appendLine()
            }

            // 模型信息
            appendLine(theme.accent("ℹ 信息"))
            appendLine(theme.textSecondary("  模型: ") + theme.textPrimary(appState.modelName.ifEmpty { "—" }))
            appendLine(theme.textSecondary("  Agent: ") + theme.textPrimary("${appState.agentName} ${appState.agentVersion}"))
            appendLine()

            // 连接状态
            val (connSymbol, connStyle) = when (appState.connectionState) {
                ConnectionState.CONNECTED -> "● 已连接" to theme.statusConnected
                ConnectionState.CONNECTING -> "◌ 连接中" to theme.statusConnecting
                ConnectionState.DISCONNECTED -> "○ 断开" to theme.statusDisconnected
                ConnectionState.RECONNECTING -> "◌ 重连中" to theme.statusConnecting
                ConnectionState.DISCONNECTED_ERROR -> "✕ 错误" to theme.statusError
            }
            appendLine(theme.accent("🔗 连接状态"))
            appendLine(theme.textSecondary("  状态: ") + connStyle(connSymbol))
            appendLine(theme.textSecondary("  会话: ") + theme.info("${appState.sessionCount}/${appState.totalSessions}"))

            // 时间
            val time = LocalTime.now().format(timeFormatter)
            appendLine(theme.textSecondary("  时间: ") + theme.accent(time))
        }

        return GradientPanelWidget(
            content = Text(content.trimEnd()),
            title = Text(titleStyle("信息")),
            titleAlign = TextAlign.CENTER,
            borderType = borderType,
            colors = theme.gradientInfo,
            mode = theme.gradientMode,
            fallbackStyle = borderStyle
        )
    }
}