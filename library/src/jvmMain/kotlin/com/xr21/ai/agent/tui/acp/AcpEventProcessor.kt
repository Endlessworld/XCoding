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
package com.xr21.ai.agent.tui.acp

import com.xr21.ai.agent.tui.state.AppState
import com.xr21.ai.agent.tui.state.TodoItem
import com.xr21.ai.agent.tui.state.TodoStatus
import com.xr21.ai.agent.tui.state.TokenUsage
import kotlinx.coroutines.flow.Flow

/**
 * ACP 事件处理器
 *
 * 将 ACP 协议事件转换为应用状态更新。
 *
 * TODO: 1.10 阶段实现完整的 ACP 事件处理
 */
class AcpEventProcessor(private val appState: AppState) {

    suspend fun processEventStream(events: Flow<String>) {
        events.collect { event ->
            processEvent(event)
        }
    }

    fun processEvent(event: String) {
        when {
            // 文本增量
            event.startsWith("text:") -> {
                val content = event.removePrefix("text:")
                appState.appendStreamingContent(content)
            }
            // 完成
            event.startsWith("done") -> {
                appState.finishStreaming()
            }
            // 错误
            event.startsWith("error:") -> {
                val error = event.removePrefix("error:")
                appState.errorMessage = error
                appState.finishStreaming()
            }
            // 思考过程（thought chunk）
            event.startsWith("thought:") -> {
                val content = event.removePrefix("thought:")
                appState.appendThoughtContent(content)
            }
            // 工具调用
            event.startsWith("tool_call:") -> {
                val parts = event.removePrefix("tool_call:").split("|", limit = 2)
                val toolName = parts.getOrElse(0) { "unknown" }
                val args = parts.getOrElse(1) { "" }
                appState.addToolCall(toolName, args)
            }
            // 工具调用更新（增量）
            event.startsWith("tool_call_update:") -> {
                val content = event.removePrefix("tool_call_update:")
                appState.appendToolCallUpdate(content)
            }
            // 工具结果
            event.startsWith("tool_result:") -> {
                val content = event.removePrefix("tool_result:")
                appState.addToolResult(content)
            }
            // Todo 项
            event.startsWith("todo:") -> {
                val todoContent = event.removePrefix("todo:")
                appState.todos.add(TodoItem(content = todoContent))
            }
            // Todo 状态更新
            event.startsWith("todo_status:") -> {
                val parts = event.removePrefix("todo_status:").split(":", limit = 2)
                if (parts.size == 2) {
                    val id = parts[0]
                    val status = when (parts[1]) {
                        "completed" -> TodoStatus.COMPLETED
                        "in_progress" -> TodoStatus.IN_PROGRESS
                        "failed" -> TodoStatus.FAILED
                        "skipped" -> TodoStatus.SKIPPED
                        else -> TodoStatus.PENDING
                    }
                    val idx = appState.todos.indexOfFirst { it.id == id }
                    if (idx >= 0) {
                        appState.todos[idx] = appState.todos[idx].copy(status = status)
                    }
                }
            }
            // Token 用量
            event.startsWith("token:") -> {
                val parts = event.removePrefix("token:").split(",")
                if (parts.size == 3) {
                    appState.tokenUsage = TokenUsage(
                        promptTokens = parts[0].toLongOrNull() ?: 0,
                        completionTokens = parts[1].toLongOrNull() ?: 0,
                        totalTokens = parts[2].toLongOrNull() ?: 0
                    )
                }
            }
            // Agent 信息
            event.startsWith("agent:") -> {
                val parts = event.removePrefix("agent:").split("/")
                if (parts.size >= 1) {
                    appState.agentName = parts[0]
                    if (parts.size >= 2) appState.agentVersion = parts[1]
                }
            }
            // 模型名称
            event.startsWith("model:") -> {
                appState.modelName = event.removePrefix("model:")
            }
        }
    }
}