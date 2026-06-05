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

import com.github.ajalt.mordant.rendering.TextAlign
import com.github.ajalt.mordant.widgets.Panel
import com.github.ajalt.mordant.widgets.Text

/**
 * 中间对话面板
 *
 * TODO: 1.11 阶段实现完整的消息流渲染
 */
import com.xr21.ai.agent.tui.state.AppState
import com.xr21.ai.agent.tui.state.MessageRole

class ChatPanel(private val appState: AppState) {
    fun render(): Panel {
        val messages = appState.currentSession.messages
        if (messages.isEmpty()) {
            return Panel(
                Text("开始新的对话\n\n输入消息后按 Ctrl+Enter 发送"),
                title = "对话",
                titleAlign = TextAlign.CENTER
            )
        }

        val content = messages.joinToString("\n\n") { msg ->
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

        return Panel(
            Text(content),
            title = "对话",
            titleAlign = TextAlign.CENTER
        )
    }
}