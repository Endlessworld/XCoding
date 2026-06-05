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
import com.github.ajalt.mordant.widgets.Panel
import com.xr21.ai.agent.tui.state.AppState
import com.xr21.ai.agent.tui.state.TodoPriority
import com.xr21.ai.agent.tui.state.TodoStatus

class InfoPanel(private val appState: AppState) {
    fun render(isFocused: Boolean = false): Panel {
        val borderType = if (isFocused) BorderType.DOUBLE else BorderType.ROUNDED
        val content = buildString {
            // Token 用量
            appendLine("📊 Token 用量")
            appendLine("  Prompt:  ${appState.tokenUsage.promptTokens}")
            appendLine("  生成:    ${appState.tokenUsage.completionTokens}")
            appendLine("  总计:    ${appState.tokenUsage.totalTokens}")
            if (appState.tokenUsage.costUsd > 0) {
                appendLine("  费用:    $${String.format("%.4f", appState.tokenUsage.costUsd)}")
            }
            appendLine()

            // Todo 列表
            if (appState.todos.isNotEmpty()) {
                val completed = appState.todos.count { it.status == TodoStatus.COMPLETED }
                val total = appState.todos.size
                appendLine("📋 Todo ($completed/$total)")
                appState.todos.forEach { todo ->
                    val statusIcon = when (todo.status) {
                        TodoStatus.PENDING -> "○"
                        TodoStatus.IN_PROGRESS -> "◌"
                        TodoStatus.COMPLETED -> "✓"
                        TodoStatus.FAILED -> "✗"
                        TodoStatus.SKIPPED -> "—"
                    }
                    val priorityIcon = when (todo.priority) {
                        TodoPriority.HIGH -> "🔴"
                        TodoPriority.MEDIUM -> "🟡"
                        TodoPriority.LOW -> "🔵"
                    }
                    appendLine("  $priorityIcon $statusIcon ${todo.content}")
                }
                appendLine()
            }

            // 模型信息
            appendLine("ℹ 信息")
            appendLine("  模型: ${appState.modelName.ifEmpty { "—" }}")
            appendLine("  Agent: ${appState.agentName} ${appState.agentVersion}")
        }

        return Panel(
            content.trimEnd(),
            title = "信息",
            titleAlign = TextAlign.CENTER,
            borderType = borderType
        )
    }
}