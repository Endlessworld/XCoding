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
package com.xr21.ai.agent.tui.state

import com.xr21.ai.agent.tui.acp.ConnectionState
import com.xr21.ai.agent.tui.layout.PanelType
import java.time.LocalDateTime
import java.util.*

/** 会话 */
data class Session(
    val id: String = UUID.randomUUID().toString().take(8),
    val name: String = "New Session",
    val messages: MutableList<ChatMessage> = mutableListOf(),
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

/** 聊天消息 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString().take(8),
    val role: MessageRole,
    val content: String,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val isStreaming: Boolean = false,
    val metadata: Map<String, String> = emptyMap()
)

/** 消息角色 */
enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL_CALL,
    TOOL_RESULT,
    ERROR
}

/** Todo 项 */
data class TodoItem(
    val id: String = UUID.randomUUID().toString().take(8),
    val content: String,
    val status: TodoStatus = TodoStatus.PENDING,
    val createdAt: LocalDateTime = LocalDateTime.now()
)

/** Todo 状态 */
enum class TodoStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    SKIPPED
}

/** Token 用量 */
data class TokenUsage(
    val promptTokens: Long = 0,
    val completionTokens: Long = 0,
    val totalTokens: Long = 0,
    val costUsd: Double = 0.0
)

/** 应用状态 */
class AppState {
    /** 会话列表 */
    val sessions: MutableList<Session> = mutableListOf(Session())

    /** 当前会话索引 */
    var currentSessionIndex: Int = 0
        private set

    /** 当前会话 */
    val currentSession: Session
        get() = sessions[currentSessionIndex]

    /** 输入缓冲区 */
    var inputBuffer: String = ""

    /** 输入历史 */
    val inputHistory: MutableList<String> = mutableListOf()

    /** 输入历史导航索引 */
    var inputHistoryIndex: Int = -1

    /** 滚动偏移 */
    var scrollOffset: Int = 0
    /** 当前焦点面板 */
    var focusPanel: PanelType = PanelType.CENTER
        private set

    /** 切换焦点到下一个面板 */
    fun focusNext() {
        focusPanel = when (focusPanel) {
            PanelType.LEFT -> PanelType.CENTER
            PanelType.CENTER -> PanelType.RIGHT
            PanelType.RIGHT -> PanelType.INPUT
            PanelType.INPUT -> PanelType.LEFT
        }
    }

    /** 切换焦点到上一个面板 */
    fun focusPrevious() {
        focusPanel = when (focusPanel) {
            PanelType.LEFT -> PanelType.INPUT
            PanelType.CENTER -> PanelType.LEFT
            PanelType.RIGHT -> PanelType.CENTER
            PanelType.INPUT -> PanelType.RIGHT
        }
    }


    /** 连接状态 */
    var connectionState: ConnectionState = ConnectionState.DISCONNECTED

    /** Agent 名称 */
    var agentName: String = "ai-agent"

    /** Agent 版本 */
    var agentVersion: String = ""

    /** 模型名称 */
    var modelName: String = ""

    /** 当前会话计数 */
    val sessionCount: Int get() = sessions.size

    /** 总会话数（含已关闭） */
    var totalSessions: Int = 1

    /** Token 用量 */
    var tokenUsage: TokenUsage = TokenUsage()

    /** Todo 列表 */
    val todos: MutableList<TodoItem> = mutableListOf()

    /** 是否正在流式响应 */
    var isStreaming: Boolean = false

    /** 错误消息 */
    var errorMessage: String? = null

    /** 消息发送 */
    fun sendMessage(content: String) {
        if (content.isBlank()) return
        // 添加用户消息
        currentSession.messages.add(
            ChatMessage(role = MessageRole.USER, content = content)
        )
        // 保存到输入历史
        inputHistory.add(content)
        inputHistoryIndex = inputHistory.size
        // 清空输入
        inputBuffer = ""
        // 更新会话时间
        currentSession.updatedAt
        // 标记流式响应开始
        isStreaming = true
    }

    /** 添加助手的流式消息 */
    fun appendStreamingContent(content: String) {
        val lastMsg = currentSession.messages.lastOrNull()
        if (lastMsg?.isStreaming == true) {
            currentSession.messages[currentSession.messages.lastIndex] = lastMsg.copy(
                content = lastMsg.content + content
            )
        } else {
            currentSession.messages.add(
                ChatMessage(role = MessageRole.ASSISTANT, content = content, isStreaming = true)
            )
        }
    }

    /** 完成流式响应 */
    fun finishStreaming() {
        val idx = currentSession.messages.lastIndex
        if (idx >= 0) {
            val msg = currentSession.messages[idx]
            if (msg.isStreaming) {
                currentSession.messages[idx] = msg.copy(isStreaming = false)
            }
        }
        isStreaming = false
    }

    /** 新建会话 */
    fun newSession() {
        sessions.add(Session())
        currentSessionIndex = sessions.size - 1
        totalSessions++
        scrollOffset = 0
    }

    /** 切换会话 */
    fun switchSession(index: Int) {
        if (index in sessions.indices) {
            currentSessionIndex = index
            scrollOffset = 0
        }
    }

    /** 关闭当前会话 */
    fun closeCurrentSession() {
        if (sessions.size <= 1) {
            // 至少保留一个会话
            sessions[0] = Session()
            currentSessionIndex = 0
        } else {
            sessions.removeAt(currentSessionIndex)
            if (currentSessionIndex >= sessions.size) {
                currentSessionIndex = sessions.size - 1
            }
        }
        scrollOffset = 0
    }

    /** 清空当前会话 */
    fun clearConversation() {
        currentSession.messages.clear()
        scrollOffset = 0
    }

    /** 滚动 */
    fun scrollUp() {
        scrollOffset = (scrollOffset - 1).coerceAtLeast(0)
    }

    fun scrollDown() {
        scrollOffset += 1
    }

    fun scrollPageUp() {
        scrollOffset = (scrollOffset - 20).coerceAtLeast(0)
    }

    fun scrollPageDown() {
        scrollOffset += 20
    }

    /** 输入历史导航 */
    fun inputHistoryPrev() {
        if (inputHistory.isEmpty()) return
        if (inputHistoryIndex > 0) {
            inputHistoryIndex--
            inputBuffer = inputHistory[inputHistoryIndex]
        }
    }

    fun inputHistoryNext() {
        if (inputHistoryIndex < inputHistory.size - 1) {
            inputHistoryIndex++
            inputBuffer = inputHistory[inputHistoryIndex]
        } else {
            inputHistoryIndex = inputHistory.size
            inputBuffer = ""
        }
    }
}