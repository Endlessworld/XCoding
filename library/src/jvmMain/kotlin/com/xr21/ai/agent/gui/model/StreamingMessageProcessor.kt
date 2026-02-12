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

    // 累积的思考内容（向后兼容）
    private var accumulatedReasoningContent: String = ""

    // 当前正在流式输出的思考块
    private var currentReasoningBlock: ConversationMessage.ReasoningBlock? = null

    // 已完成的思考块列表
    private val completedReasoningBlocks: MutableList<ConversationMessage.ReasoningBlock> = mutableListOf()

    // 当前思考块的开始时间
    private var currentReasoningStartTime: Long = 0

    // 当前思考会话的消息ID（用于判断是否是同一个思考过程）
    private var currentReasoningMessageId: String? = null

    /**
     * 重置处理器状态（包括去重集合）
     */
    fun reset() {
        currentStreamingMessage = null
        pendingToolResponses.clear()
        processedMessageIds.clear()
        accumulatedReasoningContent = ""
        currentReasoningBlock = null
        completedReasoningBlocks.clear()
        currentReasoningStartTime = 0
        currentReasoningMessageId = null
    }

    /**
     * 处理 AgentOutput 流中的一帧
     * 返回更新后的 ConversationMessage 列表（增量更新）
     */
    fun processAgentOutput(
        output: AgentOutput<*>, currentMessages: MutableList<ConversationMessage>
    ): List<ConversationMessage> {
        val updates = mutableListOf<ConversationMessage>()
        val metadata: Map<String, Any> = output.metadata ?: emptyMap()
        val message = output.message
        val chunk = output.chunk ?: ""
        val isFinal = metadata["final"] == true
        val isThinking = metadata["reasoningContent"]?.toString()
            ?.isNotEmpty() == true && (message !is AssistantMessage || message.toolCalls.isEmpty())
        val hasError = metadata["error"] == true

        println("=== processAgentOutput ===")
        println("  message: $message")
        println("  message type: ${message?.javaClass?.simpleName}")
        println("  chunk: $chunk")
        println("  isFinal: $isFinal")
        println("  isThinking: $isThinking")

        // 处理工具调用
        if (message is AssistantMessage) {
            val toolCalls = message.toolCalls ?: emptyList()
            if (toolCalls.isNotEmpty()) {
                println("=== Processing tool calls from AssistantMessage ===")
                println("  tool calls count: ${toolCalls.size}")
                toolCalls.forEach { toolCall ->
                    println("  tool call: $toolCall")
                }

                // 创建一个包含工具调用的 AssistantMessage
                val toolCallMessage =
                    AssistantMessage.builder().toolCalls(toolCalls).content(chunk)  // 保留 chunk 内容，可能是工具调用相关的文本
                        .build()

                handleAssistantMessage(
                    message = toolCallMessage,
                    chunk = chunk,
                    isFinal = isFinal,
                    currentMessages = currentMessages,
                    updates = updates,
                    output = output
                )

                // 如果是最终输出，清理状态
                if (isFinal) {
                    currentStreamingMessage = null
                    pendingToolResponses.clear()
                }

                return updates
            }

        }

        // 生成唯一的消息ID用于去重
        // 直接使用原始消息内容，不进行编码修复
        val messageId = when {
            message?.metadata?.get("id") != null -> message.metadata["id"].toString()
            metadata["id"] != null -> metadata["id"].toString()
            output.timestamp > 0 -> "ts_${output.timestamp}"
            else -> "chunk_${chunk.hashCode()}_${System.currentTimeMillis()}"
        }

        // 对于非流式消息，检查是否已处理过，但对于流式响应（包含 chunk 或增量内容）或思考内容，允许更新
        // 注意：思考内容（isThinking）需要累积多个增量，所以不能被去重
        if (!isThinking && !isFinal && message != null && processedMessageIds.contains(messageId) && chunk.isBlank()) {
            println("=== Skipping duplicate message: $messageId ===")
            return emptyList()
        }
        processedMessageIds.add(messageId)

        // 处理 thinking 输出（正在思考...）
        // 创建独立的思考块，每个思考阶段作为一个独立的块
        if (isThinking) {
            val reasoningChunk = fixEncoding(metadata["reasoningContent"]?.toString() ?: "")
            // 获取消息ID用于判断是否是同一个思考会话
            val reasoningMessageId = message?.metadata?.get("id")?.toString()

            println("=== Thinking content analysis ===")
            println("  reasoningChunk: '$reasoningChunk'")
            println("  reasoningChunk length: ${reasoningChunk.length}")
            println("  accumulatedReasoningContent length: ${accumulatedReasoningContent.length}")
            println("  currentReasoningMessageId: $currentReasoningMessageId")
            println("  reasoningMessageId: $reasoningMessageId")

            if (reasoningChunk.isNotEmpty()) {
                // 判断是否是新的思考会话
                val isNewReasoningSession = reasoningMessageId != null &&
                    currentReasoningMessageId != null &&
                    reasoningMessageId != currentReasoningMessageId

                if (isNewReasoningSession) {
                    // 新的思考会话，完成当前思考块并开始新的
                    println("=== New reasoning session detected ===")
                    if (currentReasoningBlock != null) {
                        val completedBlock = currentReasoningBlock!!.copy(isStreaming = false)
                        completedReasoningBlocks.add(completedBlock)
                        println("  Completed previous block: ${completedBlock.id}")
                    }
                    // 清空累积内容，开始新的思考
                    accumulatedReasoningContent = reasoningChunk
                    currentReasoningBlock = null
                    currentReasoningStartTime = 0
                } else {
                    // 同一个思考会话，累积内容
                    // 判断是增量还是累积：
                    // - 如果 reasoningChunk 包含在累积内容的末尾，说明是重复，跳过
                    // - 如果 reasoningChunk 比累积内容长，说明是累积内容，替换
                    // - 否则是增量内容，追加

                    if (reasoningChunk == accumulatedReasoningContent) {
                        // 完全相同，跳过
                        println("=== Reasoning chunk same as accumulated, skipping ===")
                    } else if (reasoningChunk.length > accumulatedReasoningContent.length &&
                        reasoningChunk.startsWith(accumulatedReasoningContent)) {
                        // reasoningChunk 是累积内容（包含之前的内容），直接替换
                        accumulatedReasoningContent = reasoningChunk
                        println("=== Reasoning content updated (accumulated) ===")
                    } else if (accumulatedReasoningContent.endsWith(reasoningChunk)) {
                        // 新内容已经在累积内容末尾，跳过
                        println("=== Reasoning chunk already at end of accumulated, skipping ===")
                    } else if (!accumulatedReasoningContent.contains(reasoningChunk)) {
                        // 新内容不在累积内容中，追加
                        accumulatedReasoningContent += reasoningChunk
                        println("=== Reasoning content appended (incremental) ===")
                    } else {
                        // 内容包含在累积中，跳过
                        println("=== Reasoning chunk already in accumulated, skipping ===")
                    }
                }

                // 更新当前思考会话ID
                if (reasoningMessageId != null) {
                    currentReasoningMessageId = reasoningMessageId
                }

                println("  new accumulated length: ${accumulatedReasoningContent.length}")

                // 处理思考块
                val currentBlock = currentReasoningBlock
                if (currentBlock == null) {
                    // 创建新的思考块
                    currentReasoningStartTime = System.currentTimeMillis()
                    val newBlock = ConversationMessage.ReasoningBlock(
                        id = UUID.randomUUID().toString(),
                        content = accumulatedReasoningContent.trim(),
                        timestamp = currentReasoningStartTime,
                        isStreaming = true,
                        messageIndex = -1 // 设置为 -1 确保显示在最前面
                    )
                    currentReasoningBlock = newBlock
                    println("=== Created new reasoning block ===")
                    println("  block id: ${newBlock.id}")
                    println("  block content length: ${newBlock.content.length}")
                } else {
                    // 更新当前思考块的内容
                    val updatedBlock = currentBlock.copy(
                        content = accumulatedReasoningContent.trim()
                    )
                    currentReasoningBlock = updatedBlock
                    println("=== Updated current reasoning block ===")
                    println("  block id: ${updatedBlock.id}")
                    println("  content length: ${updatedBlock.content.length}")
                }
            }

            // 如果有当前流式消息，更新其思考块
            val streamingMsg = currentStreamingMessage
            if (streamingMsg != null) {
                // 思考块应该显示在消息的最前面，设置 messageIndex 为 0
                // 这样思考块会显示在所有文本和工具调用之前
                val updatedBlock = currentReasoningBlock?.copy(messageIndex = -1)
                if (updatedBlock != null) {
                    currentReasoningBlock = updatedBlock
                }

                val allBlocks = completedReasoningBlocks + listOfNotNull(currentReasoningBlock)
                val updatedMsg = streamingMsg.copy(
                    reasoningContent = accumulatedReasoningContent.trim(), reasoningBlocks = allBlocks
                )
                val index = currentMessages.indexOfFirst { it.id == streamingMsg.id }
                if (index >= 0) {
                    currentMessages[index] = updatedMsg
                    updates.add(updatedMsg)
                } else {
                    // 如果消息不在列表中，添加它
                    currentMessages.add(updatedMsg)
                    updates.add(updatedMsg)
                }
            } else {
                // 没有流式消息时，创建一个临时的思考消息用于显示
                val allBlocks = completedReasoningBlocks + listOfNotNull(currentReasoningBlock)
                if (allBlocks.isNotEmpty()) {
                    val tempMsg = ConversationMessage.Assistant(
                        id = "reasoning_temp_${System.currentTimeMillis()}",
                        timestamp = System.currentTimeMillis(),
                        rawMessages = emptyList(),
                        reasoningContent = accumulatedReasoningContent.trim(),
                        reasoningBlocks = allBlocks
                    )
                    currentMessages.add(tempMsg)
                    updates.add(tempMsg)

                    // 设置当前流式消息为临时消息，确保流式状态正确传递
                    currentStreamingMessage = tempMsg
                }
            }
        } else {
            // 非 thinking 输出，完成当前思考块（如果有）
            val currentBlock = currentReasoningBlock
            if (currentBlock != null) {
                // 计算思考持续时间
                val duration = (System.currentTimeMillis() - currentReasoningStartTime) / 1000
                val completedBlock = currentBlock.copy(
                    isStreaming = false
                )
                completedReasoningBlocks.add(completedBlock)
                println("=== Completed reasoning block ===")
                println("  block id: ${completedBlock.id}")
                println("  duration: ${duration}s")
                println("  total blocks: ${completedReasoningBlocks.size}")
                currentReasoningBlock = null
                currentReasoningStartTime = 0

                // 更新当前流式消息的思考块状态
                val streamingMsg = currentStreamingMessage
                if (streamingMsg != null) {
                    val allBlocks = completedReasoningBlocks + listOfNotNull(currentReasoningBlock)
                    val updatedMsg = streamingMsg.copy(
                        reasoningContent = accumulatedReasoningContent.trim(), reasoningBlocks = allBlocks
                    )
                    val index = currentMessages.indexOfFirst { it.id == streamingMsg.id }
                    if (index >= 0) {
                        currentMessages[index] = updatedMsg
                        updates.add(updatedMsg)
                    }
                }
            }

            println("=== Processing message after thinking check ===")
            println("  message type: ${message?.javaClass?.simpleName}")
            println("  is AssistantMessage: ${message is AssistantMessage}")
            println("  has tool calls: ${message?.let { it is AssistantMessage && it.toolCalls.isNotEmpty() } == true}")

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
                    println("=== Handling AssistantMessage ===")
                    println("  message.text: ${message.text}")
                    println("  message.toolCalls: ${message.toolCalls}")
                    println("  chunk: $chunk")
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
                    println("=== Handling Streaming Chunk ===")
                    println("  chunk: '$chunk'")
                    handleStreamingChunk(chunk, isFinal, currentMessages, updates, output)
                }

                // 情况4: 无内容的输出
                else -> {
                    println("=== Handling Empty Output ===")
                    println("  chunk: $chunk")
                    println("  message: $message")
                    println("  message type: ${message?.javaClass?.simpleName}")
                }
            }

            // 如果是最终输出，清理状态
            if (isFinal) {
                currentStreamingMessage = null
                pendingToolResponses.clear()
            }

            return updates
        }
        return updates
    }

    /**
     * 处理 ToolResponseMessage
     * 当收到工具响应时，更新对应的工具调用状态，而不是创建新消息
     */
    private fun handleToolResponse(
        toolResponseMessage: ToolResponseMessage,
        currentMessages: MutableList<ConversationMessage>,
        updates: MutableList<ConversationMessage>
    ) {
        // 尝试找到并更新当前流式消息
        val streamingMsg = currentStreamingMessage
        if (streamingMsg != null) {
            // 检查是否已经包含这个工具响应（避免重复添加）
            val existingResponse = streamingMsg.rawMessages.filterIsInstance<ToolResponseMessage>()
                .any { existing ->
                    existing.responses.any { resp ->
                        toolResponseMessage.responses.any { newResp -> newResp.id() == resp.id() }
                    }
                }

            if (!existingResponse) {
                val updatedMsg = streamingMsg.copy(
                    rawMessages = streamingMsg.rawMessages + toolResponseMessage,
                    reasoningContent = accumulatedReasoningContent.trim().ifBlank { streamingMsg.reasoningContent }
                )
                val index = currentMessages.indexOfFirst { it.id == streamingMsg.id }
                if (index >= 0) {
                    currentMessages[index] = updatedMsg
                    updates.add(updatedMsg)
                }
                // 更新当前流式消息引用
                currentStreamingMessage = updatedMsg
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

        println("=== Content Analysis ===")
        println("  content: '$content'")
        println("  content.trim().isNotEmpty(): ${content.trim().isNotEmpty()}")
        println("  message.toolCalls.isNotEmpty(): ${message.toolCalls.isNotEmpty()}")
        println("  hasVisibleContent: $hasVisibleContent")

        if (!hasVisibleContent) {
            println("=== Ignoring AssistantMessage with empty content ===")
            return
        }

        // 检查是否有当前正在流式输出的消息 - 优先复用
        val streamingMsg = currentStreamingMessage
        println("=== Streaming Message Check ===")
        println("  currentStreamingMessage: ${streamingMsg?.id}")
        println("  streamingMsg != null: ${streamingMsg != null}")

        if (streamingMsg != null) {
            println("=== Updating existing streaming message ===")
            // 更新现有流式消息
            val newRawMessages = streamingMsg.rawMessages.toMutableList()

            val lastAssistantIndex = newRawMessages.indexOfLast { it is AssistantMessage }
            if (lastAssistantIndex >= 0) {
                // 更新最后一个 AssistantMessage 的文本
                val lastAssistant = newRawMessages[lastAssistantIndex] as AssistantMessage
                val updatedAssistant = AssistantMessage.builder().toolCalls(lastAssistant.toolCalls + message.toolCalls)
                    .content((lastAssistant.text ?: "") + content).media(lastAssistant.media)
                    .properties(lastAssistant.metadata).build()
                newRawMessages[lastAssistantIndex] = updatedAssistant
            } else {
                // 如果没有 AssistantMessage，直接添加
                newRawMessages.add(message)
            }

            // 添加待处理的工具响应
            if (pendingToolResponses.isNotEmpty()) {
                newRawMessages.addAll(pendingToolResponses)
            }

            // 收集所有已存在的工具调用ID，避免重复添加
            val existingToolCallIds = newRawMessages.filterIsInstance<AssistantMessage>()
                .flatMap { it.toolCalls }
                .map { it.id }
                .toSet()

            // 添加工具调用（如果有且不重复）
            if (message.toolCalls.isNotEmpty()) {
                println("=== Adding tool calls to existing streaming message ===")
                println("  tool calls count: ${message.toolCalls.size}")
                println("  existing tool call IDs: $existingToolCallIds")

                // 只添加新的工具调用（不重复的）
                val newToolCalls = message.toolCalls.filter { it.id !in existingToolCallIds }
                println("  new tool calls count: ${newToolCalls.size}")

                // 为每个新的工具调用创建独立的AssistantMessage，确保UI能正确渲染
                newToolCalls.forEach { toolCall ->
                    val toolCallMessage =
                        AssistantMessage.builder().toolCalls(listOf(toolCall)).content("")  // 工具调用可能没有文本内容
                            .media(message.media).properties(message.metadata).build()
                    newRawMessages.add(toolCallMessage)
                }
            }

            // 获取所有思考块
            val allBlocks = completedReasoningBlocks + listOfNotNull(currentReasoningBlock)

            val updatedMsg = streamingMsg.copy(
                rawMessages = newRawMessages,
                reasoningBlocks = if (allBlocks.isNotEmpty()) allBlocks else streamingMsg.reasoningBlocks,
                reasoningContent = accumulatedReasoningContent.trim().ifBlank { streamingMsg.reasoningContent }
            )
            val index = currentMessages.indexOfFirst { it.id == streamingMsg.id }
            if (index >= 0) {
                currentMessages[index] = updatedMsg
                updates.add(updatedMsg)
            }

            if (isFinal) {
                pendingToolResponses.clear()
                currentStreamingMessage = null
                completedReasoningBlocks.clear()
            } else {
                currentStreamingMessage = updatedMsg
            }
        } else {
            println("=== No existing streaming message, creating new one ===")

            // 对于只有工具调用的消息，强制创建流式消息
            if (message.toolCalls.isNotEmpty()) {
                println("=== Creating new streaming message for tool calls ===")
                println("  tool calls count: ${message.toolCalls.size}")

                // 创建新的流式消息
                val rawList = mutableListOf<Message>()

                // 如果有文本内容，先添加文本消息
                if (content.trim().isNotEmpty()) {
                    rawList.add(message)
                } else {
                    // 即使没有文本内容，也要创建一个 AssistantMessage 来承载工具调用
                    val textMessage =
                        AssistantMessage.builder().toolCalls(message.toolCalls).content(chunk.ifBlank { content })
                            .media(message.media).properties(message.metadata).build()
                    rawList.add(textMessage)
                }

                // 注意：不再为每个工具调用创建独立的 AssistantMessage
                // 工具调用已经包含在上面的 message/textMessage 中
                // 这样避免了工具调用的重复渲染

                // 添加待处理的工具响应
                if (pendingToolResponses.isNotEmpty()) {
                    rawList.addAll(pendingToolResponses)
                }

                val assistantMsg = ConversationMessage.Assistant(
                    id = messageId,
                    timestamp = output.timestamp,
                    rawMessages = rawList,
                    reasoningContent = accumulatedReasoningContent.trim(),
                    reasoningBlocks = completedReasoningBlocks.toList()
                )
                currentMessages.add(assistantMsg)
                updates.add(assistantMsg)

                if (isFinal) {
                    pendingToolResponses.clear()
                    currentStreamingMessage = null
                    // 清空累积的思考内容
                    accumulatedReasoningContent = ""
                    completedReasoningBlocks.clear()
                } else {
                    currentStreamingMessage = assistantMsg
                }
                return
            }

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
                    rawMessages = rawList,
                    reasoningContent = accumulatedReasoningContent.trim(),
                    reasoningBlocks = completedReasoningBlocks.toList()
                )
                currentMessages.add(assistantMsg)
                updates.add(assistantMsg)

                if (isFinal) {
                    pendingToolResponses.clear()
                    // 清空累积的思考内容
                    accumulatedReasoningContent = ""
                    completedReasoningBlocks.clear()
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
                AssistantMessage.builder().toolCalls(lastAssistant.toolCalls).content(content)
                    .media(lastAssistant.media).properties(lastAssistant.metadata).build()
            } else {
                AssistantMessage.builder().toolCalls(lastAssistant.toolCalls)
                    .content((lastAssistant.text ?: "") + content).media(lastAssistant.media)
                    .properties(lastAssistant.metadata).build()
            }
            newRawMessages[lastAssistantIndex] = updatedAssistant
        }

        // 收集所有已存在的工具调用ID，避免重复添加
        val existingToolCallIds = newRawMessages.filterIsInstance<AssistantMessage>()
            .flatMap { it.toolCalls }
            .map { it.id }
            .toSet()

        // 添加工具调用（如果有且不重复）
        if (message.toolCalls.isNotEmpty()) {
            println("=== Adding tool calls to existing message ===")
            println("  tool calls count: ${message.toolCalls.size}")
            println("  existing tool call IDs: $existingToolCallIds")

            // 只添加新的工具调用（不重复的）
            val newToolCalls = message.toolCalls.filter { it.id !in existingToolCallIds }
            println("  new tool calls count: ${newToolCalls.size}")

            // 为每个新的工具调用创建独立的AssistantMessage，确保UI能正确渲染
            newToolCalls.forEach { toolCall ->
                val toolCallMessage =
                    AssistantMessage.builder().toolCalls(listOf(toolCall)).content("")  // 工具调用可能没有文本内容
                        .media(message.media).properties(message.metadata).build()
                newRawMessages.add(toolCallMessage)
            }
        }

        // 添加待处理的工具响应
        if (pendingToolResponses.isNotEmpty()) {
            newRawMessages.addAll(pendingToolResponses)
        }

        // 获取所有思考块
        val allBlocks = completedReasoningBlocks + listOfNotNull(currentReasoningBlock)

        val updatedMsg = existing.copy(
            rawMessages = newRawMessages,
            reasoningBlocks = if (allBlocks.isNotEmpty()) allBlocks else existing.reasoningBlocks,
            reasoningContent = accumulatedReasoningContent.trim().ifBlank { existing.reasoningContent }
        )
        val index = currentMessages.indexOfFirst { it.id == existing.id }
        if (index >= 0) {
            currentMessages[index] = updatedMsg
            updates.add(updatedMsg)
        }

        if (isFinal) {
            pendingToolResponses.clear()
            completedReasoningBlocks.clear()
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
        println("=== handleStreamingChunk ===")
        println("  chunk: '$chunk'")
        println("  isFinal: $isFinal")
        println("  currentStreamingMessage: ${currentStreamingMessage?.id}")

        // 检查 chunk 是否包含工具调用信息
        if (chunk.contains("[ToolCall[")) {
            println("=== Found ToolCall in chunk ===")
            println("  chunk: $chunk")

            // 尝试从 chunk 中提取工具调用信息
            // 这里需要根据实际的工具调用格式来解析
            // 暂时先打印出来，看看格式
        }

        val streamingMsg = currentStreamingMessage

        if (streamingMsg != null) {
            println("=== Updating existing streaming message with chunk ===")
            // 更新现有流式消息的文本
            val newRawMessages = streamingMsg.rawMessages.toMutableList()
            val lastAssistantIndex = newRawMessages.indexOfLast { it is AssistantMessage }

            if (lastAssistantIndex >= 0) {
                val lastAssistant = newRawMessages[lastAssistantIndex] as AssistantMessage
                val updatedAssistant = AssistantMessage.builder().toolCalls(lastAssistant.toolCalls)
                    .content((lastAssistant.text ?: "") + chunk).media(lastAssistant.media)
                    .properties(lastAssistant.metadata).build()

                newRawMessages[lastAssistantIndex] = updatedAssistant

                // 获取所有思考块
                val allBlocks = completedReasoningBlocks + listOfNotNull(currentReasoningBlock)

                val updatedMsg = streamingMsg.copy(
                    rawMessages = newRawMessages,
                    reasoningBlocks = if (allBlocks.isNotEmpty()) allBlocks else streamingMsg.reasoningBlocks,
                    reasoningContent = accumulatedReasoningContent.trim().ifBlank { streamingMsg.reasoningContent }
                )
                val index = currentMessages.indexOfFirst { it.id == streamingMsg.id }
                if (index >= 0) {
                    currentMessages[index] = updatedMsg
                    updates.add(updatedMsg)
                }

                if (!isFinal) {
                    currentStreamingMessage = updatedMsg
                }
            } else {
                // 如果没有 AssistantMessage，创建一个
                val newAssistant = AssistantMessage(chunk)
                newRawMessages.add(newAssistant)

                val updatedMsg = streamingMsg.copy(
                    rawMessages = newRawMessages,
                    reasoningContent = accumulatedReasoningContent.trim().ifBlank { streamingMsg.reasoningContent }
                )
                val index = currentMessages.indexOfFirst { it.id == streamingMsg.id }
                if (index >= 0) {
                    currentMessages[index] = updatedMsg
                    updates.add(updatedMsg)
                }

                if (!isFinal) {
                    currentStreamingMessage = updatedMsg
                }
            }

            if (isFinal) {
                currentStreamingMessage = null
            }
        } else {
            println("=== Creating new streaming message from chunk ===")
            // 创建新流式消息
            val allBlocks = completedReasoningBlocks + listOfNotNull(currentReasoningBlock)
            val newMsg = ConversationMessage.Assistant(
                id = UUID.randomUUID().toString(),
                timestamp = output.timestamp,
                rawMessages = listOf(AssistantMessage(chunk)),
                reasoningContent = accumulatedReasoningContent.trim(),
                reasoningBlocks = allBlocks
            )
            currentMessages.add(newMsg)
            updates.add(newMsg)

            if (!isFinal) {
                currentStreamingMessage = newMsg
            }
        }
    }

    /**
     * 获取当前流式消息
     */
    fun getCurrentStreamingMessage(): ConversationMessage.Assistant? {
        return currentStreamingMessage
    }


    private fun fixEncoding(text: String): String {
        // 不进行编码修复，直接返回原始文本
        // 编码问题应该在数据源头解决，而不是在这里修复
        return text
    }

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