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
    val isExpanded: Boolean = false,
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
    val priority: TodoPriority = TodoPriority.MEDIUM,
    val createdAt: LocalDateTime = LocalDateTime.now()
)

/** Todo 优先级 */
enum class TodoPriority {
    HIGH,
    MEDIUM,
    LOW
}

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

    /** 输入光标位置（字符索引） */
    var inputCursorPos: Int = 0

    /** 输入历史 */
    val inputHistory: MutableList<String> = mutableListOf()

    /** 输入历史导航索引 */
    var inputHistoryIndex: Int = -1

    /** 滚动偏移 */
    var scrollOffset: Int = 0

    /** 输入面板滚动偏移 */
    var inputScrollOffset: Int = 0
    /** 当前焦点面板 */
    var focusPanel: PanelType = PanelType.CENTER
        private set

    /** 侧边栏选中索引（独立于 currentSessionIndex，用于预览选择） */
    var sidebarSelectedIndex: Int = 0

    /** 切换焦点到下一个面板 */
    fun focusNext() {
        focusPanel = when (focusPanel) {
            PanelType.LEFT -> PanelType.CENTER
            PanelType.CENTER -> PanelType.RIGHT
            PanelType.RIGHT -> PanelType.INPUT
            PanelType.INPUT -> PanelType.LEFT
        }
        onFocusChanged()
    }

    /** 切换焦点到上一个面板 */
    fun focusPrevious() {
        focusPanel = when (focusPanel) {
            PanelType.LEFT -> PanelType.INPUT
            PanelType.CENTER -> PanelType.LEFT
            PanelType.RIGHT -> PanelType.CENTER
            PanelType.INPUT -> PanelType.RIGHT
        }
        onFocusChanged()
    }

    /** 焦点变化时同步状态 */
    private fun onFocusChanged() {
        when (focusPanel) {
            PanelType.LEFT -> sidebarSelectedIndex = currentSessionIndex
            else -> { }
        }
    }

    /** 侧边栏选择上移 */
    fun selectUp() {
        if (sidebarSelectedIndex > 0) {
            sidebarSelectedIndex--
        }
    }

    /** 侧边栏选择下移 */
    fun selectDown() {
        if (sidebarSelectedIndex < sessions.size - 1) {
            sidebarSelectedIndex++
        }
    }

    /** 确认切换会话 */
    fun confirmSelection() {
        if (focusPanel == PanelType.LEFT && sidebarSelectedIndex in sessions.indices) {
            currentSessionIndex = sidebarSelectedIndex
            scrollOffset = 0
        }
    }

    /** 切换指定消息的展开状态 */
    fun toggleMessageExpanded(messageId: String) {
        val idx = currentSession.messages.indexOfFirst { it.id == messageId }
        if (idx >= 0) {
            val msg = currentSession.messages[idx]
            currentSession.messages[idx] = msg.copy(isExpanded = !msg.isExpanded)
        }
    }

    /** 切换最后一条工具相关消息的展开状态 */
    fun toggleLastToolMessage() {
        val idx = currentSession.messages.indexOfLast {
            it.role == MessageRole.TOOL_CALL || it.role == MessageRole.TOOL_RESULT
        }
        if (idx >= 0) {
            val msg = currentSession.messages[idx]
            currentSession.messages[idx] = msg.copy(isExpanded = !msg.isExpanded)
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
        inputCursorPos = 0
        inputScrollOffset = 0
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

    /** 添加思考过程内容 */
    fun appendThoughtContent(content: String) {
        val lastMsg = currentSession.messages.lastOrNull()
        if (lastMsg?.role == MessageRole.ASSISTANT && lastMsg.isStreaming) {
            // 如果当前正在流式输出，先完成它，再加入 thought
            currentSession.messages[currentSession.messages.lastIndex] = lastMsg.copy(isStreaming = false)
        }
        // 检查最后一条是否已经是 thought
        val prevThought = currentSession.messages.lastOrNull()
        if (prevThought?.role == MessageRole.SYSTEM && prevThought.isStreaming) {
            currentSession.messages[currentSession.messages.lastIndex] = prevThought.copy(
                content = prevThought.content + content
            )
        } else {
            currentSession.messages.add(
                ChatMessage(role = MessageRole.SYSTEM, content = "💭 $content", isStreaming = true)
            )
        }
    }

    /** 添加工具调用消息 */
    fun addToolCall(toolName: String, args: String) {
        // 先关闭流式消息
        if (isStreaming) finishStreaming()
        currentSession.messages.add(
            ChatMessage(
                role = MessageRole.TOOL_CALL,
                content = "🔧 $toolName\n参数: $args",
                isStreaming = true
            )
        )
    }

    /** 追加工具调用更新（增量参数） */
    fun appendToolCallUpdate(content: String) {
        val lastMsg = currentSession.messages.lastOrNull()
        if (lastMsg?.role == MessageRole.TOOL_CALL && lastMsg.isStreaming) {
            currentSession.messages[currentSession.messages.lastIndex] = lastMsg.copy(
                content = lastMsg.content + content
            )
        }
    }

    /** 添加工具结果 */
    fun addToolResult(content: String) {
        // 关闭之前的流式消息
        val lastMsg = currentSession.messages.lastOrNull()
        if (lastMsg?.isStreaming == true) {
            currentSession.messages[currentSession.messages.lastIndex] = lastMsg.copy(isStreaming = false)
        }
        // 截断长结果
        val truncatedContent = if (content.length > 500) {
            content.take(500) + "\n\n... (结果过长，已截断)"
        } else {
            content
        }
        currentSession.messages.add(
            ChatMessage(
                role = MessageRole.TOOL_RESULT,
                content = "📎 $truncatedContent"
            )
        )
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
            inputCursorPos = inputBuffer.length
        }
    }

    fun inputHistoryNext() {
        if (inputHistoryIndex < inputHistory.size - 1) {
            inputHistoryIndex++
            inputBuffer = inputHistory[inputHistoryIndex]
            inputCursorPos = inputBuffer.length
        } else {
            inputHistoryIndex = inputHistory.size
            inputBuffer = ""
            inputCursorPos = 0
        }
    }
}