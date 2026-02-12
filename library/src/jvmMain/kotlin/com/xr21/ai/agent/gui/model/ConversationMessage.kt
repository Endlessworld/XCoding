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
     * 思考段落块
     * 每个思考段落作为独立的卡片显示
     */
    data class ReasoningBlock(
        val id: String,
        val content: String,
        val timestamp: Long,
        val isStreaming: Boolean = false,
        val messageIndex: Int = -1 // 关联到rawMessages中的索引，用于确定位置
    )

    /**
     * 助手消息
     * 通过 rawMessages 存储原始消息，支持混合渲染：
     * - AssistantMessage: 文本内容 + 工具调用
     * - ToolResponseMessage: 工具响应（通过 id 关联到对应的工具调用）
     * - ReasoningBlock: 思考过程段落
     */
    data class Assistant(
        override val id: String,
        override val timestamp: Long,
        override val rawMessages: List<Message>,
        /**
         * 思考过程段落列表
         * 每个段落作为独立的卡片显示
         */
        val reasoningBlocks: List<ReasoningBlock> = emptyList(),
        /**
         * 兼容字段：单个思考内容字符串
         * 用于向后兼容和流式处理
         */
        val reasoningContent: String? = null
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
         * 包含：思考段落、文本、工具调用
         * 思考块会嵌入在对应的消息位置
         */
        fun buildMixedContentItems(): List<MixedContentItem> {
            val items = mutableListOf<MixedContentItem>()
            val toolResponseById = mutableMapOf<String, ToolResponseMessage>()
            var textIndex = 0 // 用于生成稳定的 Text 索引

            // 先收集所有工具响应，按 id 索引
            rawMessages.filterIsInstance<ToolResponseMessage>().forEach { response ->
                response.responses.forEach { resp ->
                    toolResponseById[resp.id()] = response
                }
            }
            println("=== buildMixedContentItems ===")
            println("  reasoningContent length: ${reasoningContent?.length ?: 0}")
            println("  reasoningBlocks.size: ${reasoningBlocks.size}")
            reasoningBlocks.forEachIndexed { idx, block ->
                println("  reasoningBlocks[$idx]: id=${block.id}, messageIndex=${block.messageIndex}, content.length=${block.content.length}, isStreaming=${block.isStreaming}")
            }
            
            // 创建一个按messageIndex排序的思考块映射
            val reasoningBlockByIndex = reasoningBlocks
                .filter { it.messageIndex >= 0 }
                .groupBy { it.messageIndex }

            // 收集没有 messageIndex 或 messageIndex 为负数的思考块（应该显示在最前面）
            val frontReasoningBlocks = reasoningBlocks.filter { it.messageIndex < 0 }

            // 检查是否有有效的思考块内容
            val hasValidReasoningBlocks = reasoningBlocks.any { it.content.isNotBlank() }
            
            // 如果有向后兼容的 reasoningContent 且没有有效的思考块，添加它到最前面
            if (!hasValidReasoningBlocks && !reasoningContent.isNullOrBlank()) {
                println("Creating legacy reasoning block with content length: ${reasoningContent.length}")
                val legacyBlock = ReasoningBlock(
                    id = "legacy_reasoning",
                    content = reasoningContent,
                    timestamp = timestamp,
                    isStreaming = false
                )
                items.add(MixedContentItem.Reasoning(legacyBlock))
                println("Added legacy reasoning block to items, items.size = ${items.size}")
            }

            // 先添加应该显示在前面的思考块（没有 messageIndex 或 messageIndex < 0）
            frontReasoningBlocks.forEach { block ->
                println("Processing front reasoning block: id=${block.id}, content.length=${block.content.length}")
                if (block.content.isNotBlank()) {
                    items.add(MixedContentItem.Reasoning(block))
                }
            }

            // 遍历 rawMessages，按顺序构建混合内容
            rawMessages.forEachIndexed { messageIndex, message ->
                // 先添加该位置对应的思考块
                reasoningBlockByIndex[messageIndex]?.forEach { block ->
                    if (block.content.isNotBlank()) {
                        items.add(MixedContentItem.Reasoning(block))
                    }
                }

                when (message) {
                    is AssistantMessage -> {
                        // 如果有文本内容，添加文本项
                        val text = message.text?.trim()
                        if (!text.isNullOrBlank()) {
                            items.add(MixedContentItem.Text(text, textIndex++))
                        }

                        // 如果有工具调用，添加工具调用项
                        message.toolCalls.forEach { toolCall ->
                            items.add(MixedContentItem.ToolCall(toolCall))
                        }
                    }
                    is ToolResponseMessage -> {
                        // ToolResponseMessage 通过工具调用关联处理，这里不需要单独添加
                        // 因为响应内容会在对应的 ToolCall 中显示
                    }
                    else -> {
                        // 其他类型的消息，只取文本
                        val text = message.text?.trim()
                        if (!text.isNullOrBlank()) {
                            items.add(MixedContentItem.Text(text, textIndex++))
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

        /**
         * 思考段落
         */
        data class Reasoning(val block: ReasoningBlock) : MixedContentItem() {
            override val stableId: String = "reasoning_${block.id}_${block.content.length}_${block.isStreaming}"
        }

        data class Text(
            val content: String,
            val index: Int // 添加索引以确保 stableId 的稳定性
        ) : MixedContentItem() {
            override val stableId: String = "text_${index}"
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
            val id = message.metadata["id"]?.toString() ?: UUID.randomUUID().toString()
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
