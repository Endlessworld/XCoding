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
 * 使用 mordant 原生 Markdown Widget 渲染助手消息，
 * 参考 mordant-markdown 模块实现（GFM Flavour + 主题样式）。
 */

import com.github.ajalt.mordant.markdown.Markdown
import com.github.ajalt.mordant.rendering.*
import com.github.ajalt.mordant.table.ColumnWidth
import com.github.ajalt.mordant.table.verticalLayout
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.widgets.Text
import com.xr21.ai.agent.tui.state.AppState
import com.xr21.ai.agent.tui.state.ChatMessage
import com.xr21.ai.agent.tui.state.MessageRole
import com.xr21.ai.agent.tui.theme.GradientPanelWidget
import com.xr21.ai.agent.tui.theme.TuiTheme
import java.time.format.DateTimeFormatter

class ChatPanel(
    private val appState: AppState,
    private val theme: TuiTheme,
    private val terminal: Terminal? = null
) {

    fun render(isFocused: Boolean = false, availableLines: Int = 30): Widget {
        val borderType = if (isFocused) BorderType.DOUBLE else BorderType.ROUNDED
        val borderStyle = if (isFocused) theme.borderFocused else theme.borderNormal
        val titleStyle = if (isFocused) theme.panelTitleFocused else theme.panelTitle
        val messages = appState.currentSession.messages
        if (messages.isEmpty()) {
            return GradientPanelWidget(
                content = Text(theme.textMuted("开始新的对话\n\n输入消息后按 Ctrl+Enter 发送")),
                title = Text(titleStyle("对话")),
                titleAlign = TextAlign.CENTER,
                borderType = borderType,
                colors = theme.gradientChat,
                mode = theme.gradientMode,
                fallbackStyle = borderStyle
            )
        }

        return GradientPanelWidget(
            content = ChatMessagesWidget(
                messages = messages,
                theme = theme,
                availableLines = availableLines,
                scrollOffset = appState.scrollOffset,
                isFocused = isFocused
            ),
            title = Text(titleStyle("对话")),
            titleAlign = TextAlign.CENTER,
            borderType = borderType,
            colors = theme.gradientChat,
            mode = theme.gradientMode,
            fallbackStyle = borderStyle
        )
    }
}

/**
 * 消息列表 Widget
 *
 * 在 [render] 阶段利用传入的 width 完成自动换行与滚动裁剪，
 * 避免提前调用 [Terminal.render] 把 Markdown 压成平面字符串。
 */
private class ChatMessagesWidget(
    private val messages: List<ChatMessage>,
    private val theme: TuiTheme,
    private val availableLines: Int,
    private val scrollOffset: Int,
    private val isFocused: Boolean
) : Widget {

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    override fun measure(t: Terminal, width: Int): WidthRange {
        return WidthRange(0, width)
    }

    override fun render(t: Terminal, width: Int): Lines {
        val cells = mutableListOf<Widget>()

        for (msg in messages) {
            val (roleLabel, roleStyle) = when (msg.role) {
                MessageRole.USER -> "👤 你" to theme.userMessage
                MessageRole.ASSISTANT -> "🤖 AI" to theme.assistantMessage
                MessageRole.SYSTEM -> "⚙ 系统" to theme.systemMessage
                MessageRole.TOOL_CALL -> "🔧 工具" to theme.toolMessage
                MessageRole.TOOL_RESULT -> "📎 结果" to theme.toolMessage
                MessageRole.ERROR -> "❌ 错误" to theme.errorMessage
            }
            val timestamp = msg.timestamp.format(timeFormatter)
            val streamingSuffix = if (msg.isStreaming) " ▌" else ""
            val header = roleStyle("$roleLabel  ") + theme.textMuted("[$timestamp]")

            val contentWidget: Widget = when {
                (msg.role == MessageRole.TOOL_CALL || msg.role == MessageRole.TOOL_RESULT) && !msg.isExpanded -> {
                    val firstLine = msg.content.lineSequence().firstOrNull() ?: ""
                    val hint = if (isFocused) theme.scrollHint(" [Space 展开]") else theme.textMuted(" [折叠]")
                    Text("$firstLine…$hint")
                }

                msg.role == MessageRole.ASSISTANT -> {
                    // 参考 mordant-markdown 源码：Markdown 是原生 Widget，支持 GFM、代码块、表格等
                    val md = Markdown(msg.content)
                    if (streamingSuffix.isNotEmpty()) {
                        verticalLayout {
                            spacing = 0
                            this.width = ColumnWidth.Expand(1f)
                            cell(md)
                            cell(Text(theme.scrollHint(streamingSuffix)))
                        }
                    } else {
                        md
                    }
                }

                else -> Text(msg.content + streamingSuffix)
            }

            cells.add(Text(header))
            cells.add(contentWidget)
            if (msg !== messages.last()) {
                cells.add(Text("")) // 消息间空行
            }
        }

        val layout = verticalLayout {
            spacing = 0
            this.width = ColumnWidth.Expand(1f)
            for (cell in cells) {
                cell(cell)
            }
        }

        val allLines = layout.render(t, width)
        val maxOffset = (allLines.height - availableLines).coerceAtLeast(0)
        val offset = if (scrollOffset == Int.MAX_VALUE || scrollOffset > maxOffset) {
            maxOffset
        } else {
            scrollOffset.coerceIn(0, maxOffset)
        }

        val scrollHint = when {
            offset > 0 && maxOffset > 0 && offset < maxOffset -> "↑ 上翻中 ($offset/$maxOffset) ↓"
            offset > 0 -> "↑ 上翻中 ($offset/$maxOffset) 底部"
            maxOffset > 0 -> "↓ 更多消息 (PageDown)"
            else -> ""
        }

        val hintLines = if (scrollHint.isNotEmpty()) {
            Text(theme.scrollHint(scrollHint)).render(t, width)
        } else {
            Lines(emptyList())
        }

        val contentAvailableLines = (availableLines - hintLines.height).coerceAtLeast(0)
        val visibleContent = allLines.lines.drop(offset).take(contentAvailableLines)

        return Lines(hintLines.lines + visibleContent)
    }
}