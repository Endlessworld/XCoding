package com.xr21.ai.agent.gui.model

import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.MessageType
import org.springframework.ai.chat.messages.ToolResponseMessage
import java.util.UUID

/**
 * UI层统一消息模型
 * 将连续的 AssistantMessage + ToolResponseMessage 合并为一条消息展示
 * 通过 rawMessages 存储原始消息列表，支持混合排版
 */
sealed class ConversationMessage {
    abstract val id: String
    abstract val timestamp: Long
    abstract val rawMessages: List<Message>

    /**
     * 用户消息
     */
    data class User(
        override val id: String,
        override val timestamp: Long,
        val content: String,
        override val rawMessages: List<Message>
    ) : ConversationMessage()

    /**
     * 助手消息
     * 通过 rawMessages 存储原始消息，支持混合渲染：
     * - AssistantMessage: 文本内容 + 工具调用
     * - ToolResponseMessage: 工具响应（通过 id 关联到对应的工具调用）
     */
    data class Assistant(
        override val id: String,
        override val timestamp: Long,
        override val rawMessages: List<Message>
    ) : ConversationMessage() {
        /**
         * 获取所有文本内容
         */
        val text: String
            get() = rawMessages.filterIsInstance<AssistantMessage>()
                .joinToString("\n") { it.text ?: "" }
                .trim()

        /**
         * 获取所有工具调用
         */
        val toolCalls: List<AssistantMessage.ToolCall>
            get() = rawMessages.filterIsInstance<AssistantMessage>()
                .flatMap { it.toolCalls }

        /**
         * 获取所有工具响应
         */
        val toolResponses: List<ToolResponseMessage>
            get() = rawMessages.filterIsInstance<ToolResponseMessage>()

        /**
         * 检查指定工具调用ID是否有响应
         */
        fun hasToolResponseFor(toolCallId: String): Boolean {
            return rawMessages.filterIsInstance<ToolResponseMessage>()
                .any { response -> response.responses.any { it.id() == toolCallId } }
        }

        /**
         * 获取指定工具调用的响应
         */
        fun getToolResponseFor(toolCallId: String): ToolResponseMessage? {
            return rawMessages.filterIsInstance<ToolResponseMessage>()
                .find { response -> response.responses.any { it.id() == toolCallId } }
        }

        /**
         * 构建混合内容项列表，用于渲染
         */
        fun buildMixedContentItems(): List<MixedContentItem> {
            val items = mutableListOf<MixedContentItem>()
            val toolResponseById = mutableMapOf<String, ToolResponseMessage>()

            // 先收集所有工具响应，按 id 索引
            rawMessages.filterIsInstance<ToolResponseMessage>().forEach { response ->
                response.responses.forEach { resp ->
                    toolResponseById[resp.id()] = response
                }
            }

            // 遍历 rawMessages，构建混合内容
            rawMessages.forEach { message ->
                when (message) {
                    is AssistantMessage -> {
                        // 如果有文本内容，添加文本项
                        val text = message.text?.trim()
                        if (!text.isNullOrBlank()) {
                            items.add(MixedContentItem.Text(text))
                        }

                        // 如果有工具调用，添加工具调用项
                        message.toolCalls.forEach { toolCall ->
                            items.add(MixedContentItem.ToolCall(toolCall))

                            // 查找对应的工具响应
                            val response = toolResponseById[toolCall.id]
                            if (response != null) {
                                items.add(MixedContentItem.ToolResponse(response, toolCall.id))
                            }
                        }
                    }
                    is ToolResponseMessage -> {
                        // ToolResponseMessage 已经通过上面的逻辑处理过了
                        // 这里不需要额外处理
                    }
                    else -> {
                        // 其他类型的消息，只取文本
                        val text = message.text?.trim()
                        if (!text.isNullOrBlank()) {
                            items.add(MixedContentItem.Text(text))
                        }
                    }
                }
            }

            return items
        }
    }

    /**
     * 混合内容项类型
     */
    sealed class MixedContentItem {
        /**
         * 获取稳定的唯一标识符，用于重组时保持状态一致性
         */
        abstract val stableId: String

        data class Text(val content: String) : MixedContentItem() {
            override val stableId: String = "text_${content.hashCode()}"
        }

        data class ToolCall(val toolCall: AssistantMessage.ToolCall) : MixedContentItem() {
            override val stableId: String = "tool_call_${toolCall.id}"
        }

        data class ToolResponse(
            val responseMessage: ToolResponseMessage,
            val toolCallId: String
        ) : MixedContentItem() {
            override val stableId: String = "tool_response_${toolCallId}"
        }
    }

    companion object {
        /**
         * 从 Message 创建 ConversationMessage
         */
        fun fromMessage(message: Message): ConversationMessage {
            val id = message.metadata["message_id"]?.toString() ?: UUID.randomUUID().toString()
            val timestamp = message.metadata["timestamp"]?.toString()?.toLongOrNull() ?: System.currentTimeMillis()

            return when (message.messageType) {
                MessageType.USER -> User(
                    id = id,
                    timestamp = timestamp,
                    content = message.text ?: "",
                    rawMessages = listOf(message)
                )
                MessageType.ASSISTANT -> Assistant(
                    id = id,
                    timestamp = timestamp,
                    rawMessages = listOf(message)
                )
                MessageType.TOOL -> Assistant(
                    id = id,
                    timestamp = timestamp,
                    rawMessages = listOf(message)
                )
                else -> User(
                    id = id,
                    timestamp = timestamp,
                    content = message.text ?: "",
                    rawMessages = listOf(message)
                )
            }
        }
    }
}

/**
 * 检查消息是否为用户消息
 */
fun ConversationMessage.isUser(): Boolean = this is ConversationMessage.User

/**
 * 检查消息是否为助手消息
 */
fun ConversationMessage.isAssistant(): Boolean = this is ConversationMessage.Assistant

/**
 * 检查消息是否有工具调用
 */
fun ConversationMessage.hasToolCalls(): Boolean {
    return when (this) {
        is ConversationMessage.Assistant -> toolCalls.isNotEmpty()
        else -> false
    }
}

/**
 * 检查消息是否有工具响应
 */
fun ConversationMessage.hasToolResponses(): Boolean {
    return when (this) {
        is ConversationMessage.Assistant -> toolResponses.isNotEmpty()
        else -> false
    }
}

/**
 * 获取消息文本内容
 */
fun ConversationMessage.getTextContent(): String {
    return when (this) {
        is ConversationMessage.User -> content
        is ConversationMessage.Assistant -> text
    }
}
