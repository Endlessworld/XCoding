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
import com.xr21.ai.agent.tui.theme.TuiTheme

class ChatPanel(
    private val appState: AppState,
    private val theme: TuiTheme,
    private val terminal: Terminal? = null
) {

    fun render(isFocused: Boolean = false, availableLines: Int = 30): Panel {
        val borderType = if (isFocused) BorderType.DOUBLE else BorderType.ROUNDED
        val borderStyle = if (isFocused) theme.borderFocused else theme.borderNormal
        val titleStyle = if (isFocused) theme.panelTitleFocused else theme.panelTitle
        val messages = appState.currentSession.messages
        if (messages.isEmpty()) {
            return Panel(
                theme.textMuted("开始新的对话\n\n输入消息后按 Ctrl+Enter 发送"),
                title = titleStyle("对话"),
                titleAlign = TextAlign.CENTER,
                borderType = borderType,
                borderStyle = borderStyle
            )
        }

        val timeFormatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        val allLines = messages.flatMap { msg ->
            val (roleLabel, roleStyle) = when (msg.role) {
                MessageRole.USER -> "👤 你" to theme.userMessage
                MessageRole.ASSISTANT -> "🤖 AI" to theme.assistantMessage
                MessageRole.SYSTEM -> "⚙ 系统" to theme.systemMessage
                MessageRole.TOOL_CALL -> "🔧 工具" to theme.toolMessage
                MessageRole.TOOL_RESULT -> "📎 结果" to theme.toolMessage
                MessageRole.ERROR -> "❌ 错误" to theme.errorMessage
            }
            val timestamp = msg.timestamp.format(timeFormatter)
            val suffix = if (msg.isStreaming) theme.scrollHint(" ▌") else ""
            val effectiveContent = when {
                (msg.role == MessageRole.TOOL_CALL || msg.role == MessageRole.TOOL_RESULT) && !msg.isExpanded -> {
                    val firstLine = msg.content.lineSequence().firstOrNull() ?: ""
                    val hint = if (isFocused) theme.scrollHint(" [Space 展开]") else theme.textMuted(" [折叠]")
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
            val header = roleStyle("$roleLabel  ") + theme.textMuted("[$timestamp]")
            listOf(header) + contentLines.map { theme.textPrimary(it) + suffix }
        }

        val maxOffset = (allLines.size - availableLines).coerceAtLeast(0)
        val offset = if (appState.scrollOffset == Int.MAX_VALUE || appState.scrollOffset > maxOffset) {
            maxOffset
        } else {
            appState.scrollOffset.coerceIn(0, maxOffset)
        }
        val visibleMessages = allLines.drop(offset).take(availableLines)
        val content = visibleMessages.joinToString("\n")

        val scrollHint = when {
            offset > 0 && maxOffset > 0 && offset < maxOffset -> theme.scrollHint("↑ 上翻中 ($offset/$maxOffset) ↓")
            offset > 0 -> theme.scrollHint("↑ 上翻中 ($offset/$maxOffset) 底部")
            maxOffset > 0 -> theme.scrollHint("↓ 更多消息 (PageDown)")
            else -> ""
        }
        val displayContent = if (scrollHint.isNotEmpty()) {
            "$scrollHint\n$content"
        } else {
            content
        }

        return Panel(
            displayContent.trimEnd(),
            title = titleStyle("对话"),
            titleAlign = TextAlign.CENTER,
            borderType = borderType,
            borderStyle = borderStyle
        )
    }
}