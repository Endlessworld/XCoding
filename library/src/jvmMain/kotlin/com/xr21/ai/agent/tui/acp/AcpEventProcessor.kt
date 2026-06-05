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

import com.agentclientprotocol.common.Event
import com.agentclientprotocol.model.*
import com.xr21.ai.agent.tui.state.*
import kotlinx.coroutines.flow.Flow

/**
 * ACP 事件处理器
 *
 * 将 ACP SDK 事件转换为应用状态更新。
 */
class AcpEventProcessor(private val appState: AppState) {

    suspend fun processEventStream(events: Flow<Event>) {
        events.collect { event ->
            processEvent(event)
        }
    }

    fun processEvent(event: Event) {
        when (event) {
            is Event.SessionUpdateEvent -> processSessionUpdate(event.update)
            is Event.PromptResponseEvent -> processPromptResponse(event.response)
        }
    }

    private fun processSessionUpdate(update: SessionUpdate) {
        when (update) {
            is SessionUpdate.AgentMessageChunk -> {
                val text = (update.content as? ContentBlock.Text)?.text ?: ""
                appState.appendStreamingContent(text)
            }
            is SessionUpdate.AgentThoughtChunk -> {
                val text = (update.content as? ContentBlock.Text)?.text ?: ""
                appState.appendThoughtContent(text)
            }
            is SessionUpdate.ToolCall -> {
                val args = extractTextFromToolContent(update.content)
                appState.addToolCall(update.title, args)
            }
            is SessionUpdate.ToolCallUpdate -> {
                when (update.status) {
                    ToolCallStatus.COMPLETED -> {
                        val result = extractTextFromToolContent(update.content ?: emptyList())
                        appState.addToolResult(result)
                    }
                    else -> {
                        val content = extractTextFromToolContent(update.content ?: emptyList())
                        if (content.isNotEmpty()) appState.appendToolCallUpdate(content)
                    }
                }
            }
            is SessionUpdate.PlanUpdate -> {
                appState.todos.clear()
                update.entries.forEach { entry ->
                    val priority = when (entry.priority) {
                        PlanEntryPriority.HIGH -> TodoPriority.HIGH
                        PlanEntryPriority.MEDIUM -> TodoPriority.MEDIUM
                        PlanEntryPriority.LOW -> TodoPriority.LOW
                    }
                    val status = when (entry.status) {
                        PlanEntryStatus.PENDING -> TodoStatus.PENDING
                        PlanEntryStatus.IN_PROGRESS -> TodoStatus.IN_PROGRESS
                        PlanEntryStatus.COMPLETED -> TodoStatus.COMPLETED
                    }
                    appState.todos.add(TodoItem(content = entry.content, priority = priority, status = status))
                }
            }
            is SessionUpdate.UsageUpdate -> {
                appState.tokenUsage = TokenUsage(totalTokens = update.used)
            }
            else -> {}
        }
    }

    private fun processPromptResponse(response: PromptResponse) {
        when (response.stopReason) {
            StopReason.END_TURN, StopReason.CANCELLED -> appState.finishStreaming()
            StopReason.MAX_TOKENS -> {
                appState.appendStreamingContent("\n[响应因 token 限制被截断]")
                appState.finishStreaming()
            }
            StopReason.MAX_TURN_REQUESTS -> {
                appState.appendStreamingContent("\n[达到最大请求次数限制]")
                appState.finishStreaming()
            }
            StopReason.REFUSAL -> {
                appState.appendStreamingContent("\n[Agent 拒绝响应]")
                appState.finishStreaming()
            }
        }
    }

    private fun extractTextFromToolContent(content: List<ToolCallContent>): String {
        return content.firstOrNull()?.let { c ->
            (c as? ToolCallContent.Content)?.let { (it.content as? ContentBlock.Text)?.text }
        } ?: ""
    }
}