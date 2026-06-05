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
 * 中间对话面板
 *
 * TODO: 1.11 阶段实现完整的消息流渲染
 */

import com.github.ajalt.mordant.markdown.Markdown
import com.github.ajalt.mordant.rendering.BorderType
import com.github.ajalt.mordant.rendering.TextAlign
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.widgets.Panel
import com.xr21.ai.agent.tui.state.AppState
import com.xr21.ai.agent.tui.state.MessageRole

class ChatPanel(
    private val appState: AppState,
    private val terminal: Terminal? = null
) {

    fun render(isFocused: Boolean = false, availableLines: Int = 30): Panel {
        val borderType = if (isFocused) BorderType.DOUBLE else BorderType.ROUNDED
        val messages = appState.currentSession.messages
        if (messages.isEmpty()) {
            return Panel(
                "开始新的对话\n\n输入消息后按 Ctrl+Enter 发送",
                title = "对话",
                titleAlign = TextAlign.CENTER
            )
        }

        // 构建完整消息列表（每行一条消息，消息内容按行拆分）
        val timeFormatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        val allLines = messages.flatMap { msg ->
            val role = when (msg.role) {
                MessageRole.USER -> "👤 你"
                MessageRole.ASSISTANT -> "🤖 AI"
                MessageRole.SYSTEM -> "⚙ 系统"
                MessageRole.TOOL_CALL -> "🔧 工具"
                MessageRole.TOOL_RESULT -> "📎 结果"
                MessageRole.ERROR -> "❌ 错误"
            }
            val timestamp = msg.timestamp.format(timeFormatter)
            val suffix = if (msg.isStreaming) " ▌" else ""
            // 工具消息折叠时只显示摘要
            val effectiveContent = when {
                (msg.role == MessageRole.TOOL_CALL || msg.role == MessageRole.TOOL_RESULT) && !msg.isExpanded -> {
                    val firstLine = msg.content.lineSequence().firstOrNull() ?: ""
                    val hint = if (isFocused) " [Space 展开]" else " [折叠]"
                    "$firstLine…$hint"
                }
                msg.role == MessageRole.ASSISTANT && terminal != null -> {
                    try {
                        terminal.render(Markdown(msg.content))
                    } catch (_: Exception) {
                        msg.content
                    }
                }
                else -> msg.content
            }
            val contentLines = effectiveContent.lines().ifEmpty { listOf("") }
            // 首行带角色和时间戳
            listOf("$role  [$timestamp]") + contentLines.map { it + suffix }
        }

        val maxOffset = (allLines.size - availableLines).coerceAtLeast(0)
        // 如果 scrollOffset 为 Int.MAX_VALUE 或超出范围，跳到最大偏移
        val offset = if (appState.scrollOffset == Int.MAX_VALUE || appState.scrollOffset > maxOffset) {
            maxOffset
        } else {
            appState.scrollOffset.coerceIn(0, maxOffset)
        }
        val visibleMessages = allLines.drop(offset).take(availableLines)
        val content = visibleMessages.joinToString("\n")

        // 显示滚动指示器
        val scrollHint = when {
            offset > 0 && maxOffset > 0 && offset < maxOffset -> "↑ 上翻中 ($offset/$maxOffset) ↓"
            offset > 0 -> "↑ 上翻中 ($offset/$maxOffset) 底部"
            maxOffset > 0 -> "↓ 更多消息 (PageDown)"
            else -> ""
        }
        val displayContent = if (scrollHint.isNotEmpty()) {
            "$scrollHint\n$content"
        } else {
            content
        }

        return Panel(
            displayContent.trimEnd(),
            title = "对话",
            titleAlign = TextAlign.CENTER,
            borderType = borderType
        )
    }
}