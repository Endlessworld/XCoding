package com.xr21.ai.agent.gui.model

import com.xr21.ai.agent.entity.AgentOutput
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.ToolResponseMessage
import java.util.*

/**
 * 流式消息处理器
 * 在流式聊天时累积 chunks，更新或创建 ConversationMessage
 *
 * 状态管理：
 * - currentStreamingMessage: 当前正在流式输出的 Assistant 消息
 * - pendingToolResponses: 等待关联到 Assistant 消息的 ToolResponse 列表
 */
class StreamingMessageProcessor {

    private var currentStreamingMessage: ConversationMessage.Assistant? = null
    private var pendingToolResponses: MutableList<ToolResponseMessage> = mutableListOf()

    // 用于消息去重的集合
    private val processedMessageIds = mutableSetOf<String>()

    /**
     * 重置处理器状态（包括去重集合）
     */
    fun reset() {
        currentStreamingMessage = null
        pendingToolResponses.clear()
        processedMessageIds.clear()
    }

    /**
     * 处理 AgentOutput 流中的一帧
     * 返回更新后的 ConversationMessage 列表（增量更新）
     */
    fun processAgentOutput(
        output: AgentOutput<*>,
        currentMessages: MutableList<ConversationMessage>
    ): List<ConversationMessage> {
        val updates = mutableListOf<ConversationMessage>()
        val metadata: Map<String, Any> = output.metadata ?: emptyMap()
        val message = output.message
        val chunk = output.chunk ?: ""
        val isFinal = metadata["final"] == true
        val isThinking = metadata["reasoningContent"]?.toString()?.isNotEmpty() == true
        val hasError = metadata["error"] == true

        // 生成唯一的消息ID用于去重
        // 直接使用原始消息内容，不进行编码修复
        val messageId = when {
            message?.metadata?.get("id") != null -> message.metadata["id"].toString()
            metadata["id"] != null -> metadata["id"].toString()
            output.timestamp > 0 -> "ts_${output.timestamp}"
            else -> "chunk_${chunk.hashCode()}_${System.currentTimeMillis()}"
        }

        // 对于非流式消息，检查是否已处理过，但对于流式响应（包含 chunk 或增量内容），允许更新
        if (!isFinal && message != null && processedMessageIds.contains(messageId) && chunk.isBlank()) {
            return emptyList()
        }
        processedMessageIds.add(messageId)

        // 处理 thinking 输出（正在思考...）
        if (isThinking) {
            // thinking 输出不渲染到界面上，但需要记录已处理以避免重复
            return emptyList()
        }

        // 处理错误
        if (hasError) {
            val errorContent = chunk.ifBlank { message?.text ?: "发生错误" }
            val errorMsg = ConversationMessage.Assistant(
                id = UUID.randomUUID().toString(),
                timestamp = output.timestamp,
                rawMessages = listOf(AssistantMessage(errorContent))
            )
            currentMessages.add(errorMsg)
            updates.add(errorMsg)
            currentStreamingMessage = null
            pendingToolResponses.clear()
            return updates
        }

        when {
            // 情况1: ToolResponseMessage
            message is ToolResponseMessage -> {
                println("=== Handling ToolResponseMessage ===")
                handleToolResponse(message, currentMessages, updates)
            }

            // 情况2: AssistantMessage（可能包含 toolCalls）
            message is AssistantMessage -> {
//                println("=== Handling AssistantMessage ===")
//                println("  message.text: ${message.text}")
//                println("  message.toolCalls: ${message.toolCalls}")
//                println("  chunk: $chunk")
                handleAssistantMessage(
                    message = message,
                    chunk = chunk,
                    isFinal = isFinal,
                    currentMessages = currentMessages,
                    updates = updates,
                    output = output
                )
            }

            // 情况3: 只有 chunk，没有完整的 message（流式输出中）
            chunk.isNotBlank() && message == null -> {
//                println("=== Handling Streaming Chunk ===")
//                println("  chunk: $chunk")
                handleStreamingChunk(chunk, isFinal, currentMessages, updates, output)
            }

            // 情况4: 无内容的输出
            else -> {
//                println("=== Handling Empty Output ===")
//                println("  chunk: $chunk")
//                println("  message: $message")
            }
        }

        // 如果是最终输出，清理状态
        if (isFinal) {
            currentStreamingMessage = null
            pendingToolResponses.clear()
        }

        return updates
    }

    /**
     * 处理 ToolResponseMessage
     */
    private fun handleToolResponse(
        toolResponseMessage: ToolResponseMessage,
        currentMessages: MutableList<ConversationMessage>,
        updates: MutableList<ConversationMessage>
    ) {
        // 尝试找到并更新当前流式消息
        val streamingMsg = currentStreamingMessage
        if (streamingMsg != null) {
            val updatedMsg = streamingMsg.copy(
                rawMessages = streamingMsg.rawMessages + toolResponseMessage
            )
            val index = currentMessages.indexOfFirst { it.id == streamingMsg.id }
            if (index >= 0) {
                currentMessages[index] = updatedMsg
                updates.add(updatedMsg)
            }
        } else {
            // 如果没有流式消息，添加到待处理列表
            pendingToolResponses.add(toolResponseMessage)
        }
    }

    /**
     * 处理 AssistantMessage（可能包含 toolCalls）
     * 如果当前有流式消息，将新的 AssistantMessage 内容合并到同一个 ConversationMessage 中
     */
    private fun handleAssistantMessage(
        message: AssistantMessage,
        chunk: String,
        isFinal: Boolean,
        currentMessages: MutableList<ConversationMessage>,
        updates: MutableList<ConversationMessage>,
        output: AgentOutput<*>
    ) {
        val messageId = message.metadata["id"]?.toString() ?: UUID.randomUUID().toString()
        val content = message.text ?: chunk

        // 检查内容是否为空或只包含不可见字符
        val hasVisibleContent = content.trim().isNotEmpty() || message.toolCalls.isNotEmpty()

        if (!hasVisibleContent) {
            println("=== Ignoring AssistantMessage with empty content ===")
            return
        }

        // 检查是否有当前正在流式输出的消息 - 优先复用
        val streamingMsg = currentStreamingMessage
        if (streamingMsg != null) {
            // 更新现有流式消息
            val newRawMessages = streamingMsg.rawMessages.toMutableList()

            val lastAssistantIndex = newRawMessages.indexOfLast { it is AssistantMessage }
            if (lastAssistantIndex >= 0) {
                // 更新最后一个 AssistantMessage 的文本
                val lastAssistant = newRawMessages[lastAssistantIndex] as AssistantMessage
                val updatedAssistant = AssistantMessage.builder()
                    .toolCalls(lastAssistant.toolCalls + message.toolCalls)
                    .content((lastAssistant.text ?: "") + content)
                    .media(lastAssistant.media)
                    .properties(lastAssistant.metadata)
                    .build()
                newRawMessages[lastAssistantIndex] = updatedAssistant
            } else {
                // 如果没有 AssistantMessage，直接添加
                newRawMessages.add(message)
            }

            // 添加待处理的工具响应
            if (pendingToolResponses.isNotEmpty()) {
                newRawMessages.addAll(pendingToolResponses)
            }

            val updatedMsg = streamingMsg.copy(rawMessages = newRawMessages)
            val index = currentMessages.indexOfFirst { it.id == streamingMsg.id }
            if (index >= 0) {
                currentMessages[index] = updatedMsg
                updates.add(updatedMsg)
            }

            if (isFinal) {
                pendingToolResponses.clear()
                currentStreamingMessage = null
            } else {
                currentStreamingMessage = updatedMsg
            }
        } else {
            // 检查是否已有具有相同 ID 的消息
            val existingIndex = currentMessages.indexOfFirst {
                it is ConversationMessage.Assistant && it.id == messageId
            }

            if (existingIndex >= 0) {
                // 更新现有消息
                val existing = currentMessages[existingIndex] as ConversationMessage.Assistant
                updateExistingAssistantMessage(existing, message, content, isFinal, currentMessages, updates)
            } else {
                // 创建新消息
                val rawList = mutableListOf<Message>(message)
                if (pendingToolResponses.isNotEmpty()) {
                    rawList.addAll(pendingToolResponses)
                }

                val assistantMsg = ConversationMessage.Assistant(
                    id = messageId,
                    timestamp = output.timestamp,
                    rawMessages = rawList
                )
                currentMessages.add(assistantMsg)
                updates.add(assistantMsg)

                if (isFinal) {
                    pendingToolResponses.clear()
                } else {
                    currentStreamingMessage = assistantMsg
                }
            }
        }
    }

    /**
     * 更新现有的 AssistantMessage
     */
    private fun updateExistingAssistantMessage(
        existing: ConversationMessage.Assistant,
        message: AssistantMessage,
        content: String,
        isFinal: Boolean,
        currentMessages: MutableList<ConversationMessage>,
        updates: MutableList<ConversationMessage>
    ) {
        val newRawMessages = existing.rawMessages.toMutableList()
        val lastAssistantIndex = newRawMessages.indexOfLast { it is AssistantMessage }

        if (lastAssistantIndex >= 0) {
            // 更新最后一个 AssistantMessage 的文本
            val lastAssistant = newRawMessages[lastAssistantIndex] as AssistantMessage
            val updatedAssistant = if (isFinal) {
                AssistantMessage.builder()
                    .toolCalls(lastAssistant.toolCalls)
                    .content(content)
                    .media(lastAssistant.media)
                    .properties(lastAssistant.metadata)
                    .build()
            } else {
                AssistantMessage.builder()
                    .toolCalls(lastAssistant.toolCalls)
                    .content((lastAssistant.text ?: "") + content)
                    .media(lastAssistant.media)
                    .properties(lastAssistant.metadata)
                    .build()
            }
            newRawMessages[lastAssistantIndex] = updatedAssistant
        }

        // 添加工具调用（如果有）
        if (message.toolCalls.isNotEmpty()) {
            newRawMessages.add(message)
        }

        // 添加待处理的工具响应
        if (pendingToolResponses.isNotEmpty()) {
            newRawMessages.addAll(pendingToolResponses)
        }

        val updatedMsg = existing.copy(rawMessages = newRawMessages)
        val index = currentMessages.indexOfFirst { it.id == existing.id }
        if (index >= 0) {
            currentMessages[index] = updatedMsg
            updates.add(updatedMsg)
        }

        if (isFinal) {
            pendingToolResponses.clear()
        } else {
            currentStreamingMessage = updatedMsg
        }
    }

    /**
     * 处理流式 chunk（没有完整 message）
     */
    private fun handleStreamingChunk(
        chunk: String,
        isFinal: Boolean,
        currentMessages: MutableList<ConversationMessage>,
        updates: MutableList<ConversationMessage>,
        output: AgentOutput<*>
    ) {
        val streamingMsg = currentStreamingMessage

        if (streamingMsg != null) {
            // 更新现有流式消息的文本
            val newRawMessages = streamingMsg.rawMessages.toMutableList()
            val lastAssistantIndex = newRawMessages.indexOfLast { it is AssistantMessage }

            if (lastAssistantIndex >= 0) {
                val lastAssistant = newRawMessages[lastAssistantIndex] as AssistantMessage
                val updatedAssistant = AssistantMessage.builder()
                    .toolCalls(lastAssistant.toolCalls)
                    .content((lastAssistant.text ?: "") + chunk)
                    .media(lastAssistant.media)
                    .properties(lastAssistant.metadata)
                    .build()

                newRawMessages[lastAssistantIndex] = updatedAssistant

                val updatedMsg = streamingMsg.copy(rawMessages = newRawMessages)
                val index = currentMessages.indexOfFirst { it.id == streamingMsg.id }
                if (index >= 0) {
                    currentMessages[index] = updatedMsg
                    updates.add(updatedMsg)
                }
            } else {
                // 如果没有 AssistantMessage，创建一个
                val newAssistant = AssistantMessage(chunk)
                newRawMessages.add(newAssistant)
                val updatedMsg = streamingMsg.copy(rawMessages = newRawMessages)
                val index = currentMessages.indexOfFirst { it.id == streamingMsg.id }
                if (index >= 0) {
                    currentMessages[index] = updatedMsg
                    updates.add(updatedMsg)
                }
            }

            if (isFinal) {
                currentStreamingMessage = null
            }
        } else {
            // 创建新流式消息
            val newMsg = ConversationMessage.Assistant(
                id = UUID.randomUUID().toString(),
                timestamp = output.timestamp,
                rawMessages = listOf(AssistantMessage(chunk))
            )
            currentMessages.add(newMsg)
            updates.add(newMsg)

            if (!isFinal) {
                currentStreamingMessage = newMsg
            } else {
                pendingToolResponses.clear()
            }
        }
    }

    /**
     * 获取当前流式消息
     */
    /**
     * 修复字符编码问题
     */
    private fun fixEncoding(text: String): String {
        // 如果字符串包含乱码字符，直接返回原始字符串，避免进一步处理
        // 这个方法目前不做任何处理，只是为了保持代码结构
        return text
    }

    fun getCurrentStreamingMessage(): ConversationMessage.Assistant? = currentStreamingMessage

    companion object {
        @Volatile
        private var instance: StreamingMessageProcessor? = null

        fun getInstance(): StreamingMessageProcessor {
            return instance ?: synchronized(this) {
                instance ?: StreamingMessageProcessor().also { instance = it }
            }
        }
    }
}
