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
import com.github.ajalt.mordant.rendering.BorderType
import com.github.ajalt.mordant.rendering.TextAlign
import com.github.ajalt.mordant.widgets.Panel
import com.xr21.ai.agent.tui.state.AppState
import com.xr21.ai.agent.tui.state.MessageRole

class ChatPanel(private val appState: AppState) {
    /** 面板内可见行数（估算值，后续可通过终端尺寸精确计算） */
    private val visibleLines = 30

    fun render(isFocused: Boolean = false): Panel {
        val borderType = if (isFocused) BorderType.DOUBLE else BorderType.ROUNDED
        val messages = appState.currentSession.messages
        if (messages.isEmpty()) {
            return Panel(
                "开始新的对话\n\n输入消息后按 Ctrl+Enter 发送",
                title = "对话",
                titleAlign = TextAlign.CENTER
            )
        }

        // 构建完整消息列表（每行一条消息）
        val allLines = messages.map { msg ->
            val role = when (msg.role) {
                MessageRole.USER -> "👤 你"
                MessageRole.ASSISTANT -> "🤖 AI"
                MessageRole.SYSTEM -> "⚙ 系统"
                MessageRole.TOOL_CALL -> "🔧 工具"
                MessageRole.TOOL_RESULT -> "📎 结果"
                MessageRole.ERROR -> "❌ 错误"
            }
            val suffix = if (msg.isStreaming) " ▌" else ""
            "$role\n${msg.content}$suffix"
        }

        // 根据 scrollOffset 裁剪可见消息
        val offset = appState.scrollOffset.coerceIn(0, (allLines.size - 1).coerceAtLeast(0))
        val visibleMessages = allLines.drop(offset).take(visibleLines)
        val content = visibleMessages.joinToString("\n\n")

        // 显示滚动指示器
        val scrollHint = when {
            offset > 0 && visibleMessages.size >= visibleLines -> "↑ 上翻 $offset 条"
            offset > 0 -> "↑ 上翻 $offset 条 (底部)"
            allLines.size > visibleLines -> ""
            else -> ""
        }
        val displayContent = if (scrollHint.isNotEmpty()) {
            "$scrollHint\n\n$content"
        } else {
            content
        }

        return Panel(
            displayContent,
            title = "对话",
            titleAlign = TextAlign.CENTER,
            borderType = borderType
        )
    }
}