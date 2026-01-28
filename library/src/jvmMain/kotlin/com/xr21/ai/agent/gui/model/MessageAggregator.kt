package com.xr21.ai.agent.gui.model

import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.MessageType
import org.springframework.ai.chat.messages.ToolResponseMessage
import java.util.UUID

/**
 * 消息聚合器
 * 将连续的 AssistantMessage + ToolResponseMessage 合并为一条 ConversationMessage.Assistant
 *
 * 消息顺序规则：
 * - UserMessage 用户输入
 * - AssistantMessage 文本输出/工具调用
 * - ToolResponseMessage 工具执行结果
 * - AssistantMessage 文本输出/工具调用
 * - ToolResponseMessage 工具执行结果
 * - ... 直到一轮对话结束
 *
 * 聚合逻辑：
 * 1. 每个 UserMessage 后面紧跟的一组 (AssistantMessage + ToolResponseMessage*) 合并为一条 Assistant 消息
 * 2. ToolResponseMessage 必须紧跟在包含对应 toolCallId 的 AssistantMessage 后面
 */
class MessageAggregator {

    /**
     * 聚合原始消息列表为 UI 消息列表
     */
    fun aggregate(rawMessages: List<Message>): List<ConversationMessage> {
        if (rawMessages.isEmpty()) return emptyList()

        val result = mutableListOf<ConversationMessage>()
        var index = 0

        while (index < rawMessages.size) {
            val message = rawMessages[index]

            when (message.messageType) {
                MessageType.USER -> {
                    // 用户消息直接添加
                    result.add(ConversationMessage.fromMessage(message))
                    index++
                }

                MessageType.ASSISTANT -> {
                    // 尝试聚合 AssistantMessage + 后续的 ToolResponseMessage*
                    val (assistantMsg, consumedCount) = consumeAssistantWithResponses(rawMessages, index)
                    result.add(assistantMsg)
                    index += consumedCount
                }

                else -> {
                    // 其他类型消息直接添加
                    result.add(ConversationMessage.fromMessage(message))
                    index++
                }
            }
        }

        return result
    }

    /**
     * 消费 AssistantMessage 及其后续的所有消息，直到遇到用户消息为止
     * 聚合从 startIndex 开始到下一个用户消息之前的所有消息
     */
    private fun consumeAssistantWithResponses(
        rawMessages: List<Message>,
        startIndex: Int
    ): Pair<ConversationMessage.Assistant, Int> {
        val assistantMessage = rawMessages[startIndex] as AssistantMessage
        val rawList = mutableListOf<Message>(assistantMessage)

        var currentIndex = startIndex + 1

        // 收集后续的所有消息，直到遇到用户消息为止
        while (currentIndex < rawMessages.size) {
            val nextMessage = rawMessages[currentIndex]

            if (nextMessage.messageType == MessageType.USER) {
                // 遇到用户消息，停止收集
                break
            } else {
                // 其他类型消息（ASSISTANT, TOOL 等）都添加到列表中
                rawList.add(nextMessage)
                currentIndex++
            }
        }

        val assistantId = assistantMessage.metadata["message_id"]?.toString()
            ?: UUID.randomUUID().toString()
        val timestamp = assistantMessage.metadata["timestamp"]?.toString()?.toLongOrNull()
            ?: System.currentTimeMillis()

        val conversationAssistant = ConversationMessage.Assistant(
            id = assistantId,
            timestamp = timestamp,
            rawMessages = rawList
        )

        return conversationAssistant to (currentIndex - startIndex)
    }

    /**
     * 查找结果列表中最后一个 AssistantMessage 的索引
     */
    private fun findPreviousAssistantMessage(result: List<ConversationMessage>): Int {
        return result.indexOfLast { it is ConversationMessage.Assistant }
    }

    companion object {
        @Volatile
        private var instance: MessageAggregator? = null

        fun getInstance(): MessageAggregator {
            return instance ?: synchronized(this) {
                instance ?: MessageAggregator().also { instance = it }
            }
        }
    }
}
