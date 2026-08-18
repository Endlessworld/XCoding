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
package com.xr21.ai.agent.bridge

import com.agentclientprotocol.annotations.UnstableApi
import com.agentclientprotocol.model.*
import com.agentclientprotocol.model.SessionUpdate.ToolCallUpdate
import com.fasterxml.jackson.core.type.TypeReference
import com.xr21.ai.agent.model.ChatMessage
import com.xr21.ai.agent.model.MessageRole
import com.xr21.ai.agent.tools.ToolKindFind
import com.xr21.ai.agent.utils.Json
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.springframework.ai.chat.messages.AssistantMessage

/**
 * ACP 事件聚合/查询桥接。
 *
 * Java 侧通过此类调用 Kotlin 侧的 ACP 类型操作，包括：
 * - 流式事件聚合（连续同类型 chunk 合并）
 * - 事件查询/分组
 * - 文本提取
 */
object BridgeKt {

    private val logger = KotlinLogging.logger {}
    // ========== 事件聚合（核心）==========

    /**
     * 将 ACP 事件聚合到消息列表中。
     *
     * 规则：
     * - UserMessageChunk → 追加到最后一个 USER 消息；不存在则新建
     * - AgentMessageChunk / AgentThoughtChunk / ToolCall / ToolCallUpdate → 追加到最后一个 ASSISTANT 消息
     * - 连续同类型 chunk（message 或 thought）→ 合并文本到同一事件
     */
    @JvmStatic
    @UnstableApi
    fun addEventToMessages(messages: MutableList<ChatMessage>, event: SessionUpdate) {
        when (event) {
            is SessionUpdate.UserMessageChunk -> {
                val text = (event.content as? ContentBlock.Text)?.text ?: ""
                if (text.isEmpty()) return
                val last = messages.lastOrNull()
                if (last != null && last.role == MessageRole.USER) {
                    mergeOrAppendChunk(last.events, event)
                } else {
                    messages.add(ChatMessage(MessageRole.USER).apply {
                        events.add(SessionUpdate.UserMessageChunk(ContentBlock.Text(text), event.messageId))
                    })
                }
            }

            is SessionUpdate.AgentMessageChunk -> {
                val text = (event.content as? ContentBlock.Text)?.text ?: ""
                val last = messages.lastOrNull()
                if (last != null && last.role == MessageRole.ASSISTANT) {
                    mergeOrAppendChunk(last.events, event)
                } else {
                    messages.add(ChatMessage(MessageRole.ASSISTANT, true).apply {
                        events.add(SessionUpdate.AgentMessageChunk(ContentBlock.Text(text), event.messageId))
                    })
                }
            }

            is SessionUpdate.AgentThoughtChunk -> {
                val text = (event.content as? ContentBlock.Text)?.text ?: ""
                val last = messages.lastOrNull()
                if (last != null && last.role == MessageRole.ASSISTANT) {
                    mergeOrAppendChunk(last.events, event)
                } else {
                    messages.add(ChatMessage(MessageRole.ASSISTANT, true).apply {
                        events.add(SessionUpdate.AgentThoughtChunk(ContentBlock.Text(text), event.messageId))
                    })
                }
            }

            is SessionUpdate.ToolCall -> {
                val last = messages.lastOrNull()
                if (last != null && last.role == MessageRole.ASSISTANT) {
                    last.events.add(event)
                } else {
                    messages.add(ChatMessage(MessageRole.ASSISTANT, true).apply {
                        events.add(event)
                    })
                }
            }

            is SessionUpdate.ToolCallUpdate -> {
                val last = messages.lastOrNull()
                if (last != null && last.role == MessageRole.ASSISTANT) {
                    last.events.add(event)
                } else {
                    messages.add(ChatMessage(MessageRole.ASSISTANT, true).apply {
                        events.add(event)
                    })
                }
            }

            else -> {
                // Unknown / other types: append as standalone ERROR message
                messages.add(ChatMessage(MessageRole.ERROR).apply {
                    // Store as a text representation since we can't store arbitrary types
                })
            }
        }
    }

    /** 合并连续同类型 chunk；不同类型则追加新事件 */
    @UnstableApi
    private fun mergeOrAppendChunk(events: MutableList<SessionUpdate>, newEvent: SessionUpdate) {
        if (events.isEmpty()) {
            events.add(newEvent)
            return
        }
        when (val last = events.last()) {
            is SessionUpdate.UserMessageChunk if newEvent is SessionUpdate.UserMessageChunk -> {
                val lastText = (last.content as? ContentBlock.Text)?.text ?: ""
                val newText = (newEvent.content as? ContentBlock.Text)?.text ?: ""
                events[events.size - 1] = SessionUpdate.UserMessageChunk(
                    ContentBlock.Text(lastText + newText), newEvent.messageId
                )
            }

            is SessionUpdate.AgentMessageChunk if newEvent is SessionUpdate.AgentMessageChunk -> {
                val lastText = (last.content as? ContentBlock.Text)?.text ?: ""
                val newText = (newEvent.content as? ContentBlock.Text)?.text ?: ""
                events[events.size - 1] = SessionUpdate.AgentMessageChunk(
                    ContentBlock.Text(lastText + newText), newEvent.messageId
                )
            }

            is SessionUpdate.AgentThoughtChunk if newEvent is SessionUpdate.AgentThoughtChunk -> {
                val lastText = (last.content as? ContentBlock.Text)?.text ?: ""
                val newText = (newEvent.content as? ContentBlock.Text)?.text ?: ""
                events[events.size - 1] = SessionUpdate.AgentThoughtChunk(
                    ContentBlock.Text(lastText + newText), newEvent.messageId
                )
            }
            else -> events.add(newEvent)
        }
    }

    // ========== 文本聚合（渲染时用）==========

    /** 聚合所有 UserMessageChunk 的文本 */
    @JvmStatic
    fun getUserMessageText(events: List<SessionUpdate>): String = buildString {
        for (e in events) {
            if (e is SessionUpdate.UserMessageChunk) {
                append((e.content as? ContentBlock.Text)?.text ?: "")
            }
        }
    }

    /** 聚合所有 AgentMessageChunk 的文本 */
    @JvmStatic
    fun getAgentMessageText(events: List<SessionUpdate>): String = buildString {
        for (e in events) {
            if (e is SessionUpdate.AgentMessageChunk) {
                append((e.content as? ContentBlock.Text)?.text ?: "")
            }
        }
    }

    /** 聚合所有 AgentThoughtChunk 的文本 */
    @JvmStatic
    fun getAgentThoughtText(events: List<SessionUpdate>): String = buildString {
        for (e in events) {
            if (e is SessionUpdate.AgentThoughtChunk) {
                append((e.content as? ContentBlock.Text)?.text ?: "")
            }
        }
    }

    // ========== 事件查询 ==========

    /** 获取消息中最后一个事件的简单类名 */
    @JvmStatic
    fun getLastEventType(events: List<SessionUpdate>): String {
        return events.lastOrNull()?.javaClass?.simpleName ?: ""
    }

    /** 判断最后一个事件是否是可合并的 chunk 类型 */
    @JvmStatic
    fun isLastEventChunk(events: List<SessionUpdate>): Boolean {
        val last = events.lastOrNull() ?: return false
        return last is SessionUpdate.UserMessageChunk
                || last is SessionUpdate.AgentMessageChunk
                || last is SessionUpdate.AgentThoughtChunk
    }

    /** 查找指定 toolCallId 的 ToolCall 事件 */
    @JvmStatic
    fun findToolCall(events: List<SessionUpdate>, toolCallId: String): SessionUpdate.ToolCall? {
        for (e in events) {
            if (e is SessionUpdate.ToolCall && e.toolCallId.value == toolCallId) {
                return e
            }
        }
        return null
    }

    /** 获取指定 toolCallId 的所有 ToolCallUpdate 事件 */
    @JvmStatic
    fun getToolCallUpdates(events: List<SessionUpdate>, toolCallId: String): List<SessionUpdate.ToolCallUpdate> {
        return events.filterIsInstance<SessionUpdate.ToolCallUpdate>()
            .filter { it.toolCallId.value == toolCallId }
    }

    /** 判断消息中是否包含任何 AgentThoughtChunk */
    @JvmStatic
    fun hasThoughtEvents(events: List<SessionUpdate>): Boolean {
        return events.any { it is SessionUpdate.AgentThoughtChunk }
    }

    /** 判断消息中是否包含任何 ToolCall 事件 */
    @JvmStatic
    fun hasToolCallEvents(events: List<SessionUpdate>): Boolean {
        return events.any { it is SessionUpdate.ToolCall }
    }

    /** 获取消息中所有 ToolCall 事件 */
    @JvmStatic
    fun getToolCalls(events: List<SessionUpdate>): List<SessionUpdate.ToolCall> {
        return events.filterIsInstance<SessionUpdate.ToolCall>()
    }

    // ========== 工具调用内容提取 ==========

    /** 从 ToolCall 的 content 列表提取文本 */
    @JvmStatic
    fun extractToolCallText(toolCall: SessionUpdate.ToolCall): String {
        return toolCall.content.joinToString("") {
            when (it) {
                is ToolCallContent.Content -> (it.content as? ContentBlock.Text)?.text ?: ""
                is ToolCallContent.Diff -> it.newText
                is ToolCallContent.Terminal -> ""
            }
        }
    }

    /** 从 ToolCallUpdate 的 content 列表提取文本 */
    @JvmStatic
    fun extractToolCallUpdateText(update: ToolCallUpdate): String {
        return (update.content ?: emptyList()).joinToString("") {
            when (it) {
                is ToolCallContent.Content -> (it.content as? ContentBlock.Text)?.text ?: ""
                is ToolCallContent.Diff -> it.newText
                is ToolCallContent.Terminal -> ""
            }
        }
    }

    /** 从单个 ToolCallUpdate 提取状态字符串 */
    @JvmStatic
    fun getToolCallStatusString(status: ToolCallStatus?): String {
        return when (status) {
            ToolCallStatus.COMPLETED -> "COMPLETED"
            ToolCallStatus.FAILED -> "FAILED"
            ToolCallStatus.IN_PROGRESS, ToolCallStatus.PENDING -> "IN_PROGRESS"
            null -> "IN_PROGRESS"
        }
    }

    // ========== 已有桥接方法（保留）==========

    @OptIn(UnstableApi::class)
    @JvmStatic
    @JvmOverloads
    fun buildAgentThoughtChunk(
        content: ContentBlock,
        messageId: MessageId? = null
    ): SessionUpdate.AgentThoughtChunk {
        return SessionUpdate.AgentThoughtChunk(content, messageId)
    }

    @JvmStatic
    @JvmOverloads
    fun createToolCallLocation(path: String, line: Int = 0, _meta: JsonElement? = null): ToolCallLocation {
        return ToolCallLocation(
            path = path,
            line = if (line >= 0) line.toUInt() else null,
            _meta = _meta
        )
    }

    @JvmStatic
    fun buildToolCallUpdate(toolCall: AssistantMessage.ToolCall, arguments: String?): ToolCallUpdate {
        return ToolCallUpdate(
            toolCallId = ToolCallId(toolCall.id()),
            kind = ToolKindFind.find(toolCall.name()),
            status = ToolCallStatus.PENDING,
//            content = build(toolCall.name(), arguments),
            rawInput = runCatching {
                kotlinx.serialization.json.Json.parseToJsonElement(toolCall.arguments())
            }.getOrNull() as? JsonObject,
        )
    }

    @JvmStatic
    fun build(toolName: String, arguments: String?): List<ToolCallContent> {
        if (arguments.isNullOrBlank()) return emptyList()
        return try {
            val args = Json.jsonMapper { jsonMapper ->
                jsonMapper.readValue(arguments, object : TypeReference<MutableMap<String, Any?>>() {})
            } ?: return fallback(arguments)
            when (toolName) {
                "edit_file" -> buildEditFileDiff(args, arguments)
                "write_file" -> buildWriteFileDiff(args, arguments)
                "smart_edit" -> buildSmartEditDiff(args, arguments)
                else -> fallback(arguments)
            }
        } catch (e: Exception) {
//            logger.warn(e) { "Failed to build tool call content for $toolName" }
            fallback(arguments)
        }
    }

    private fun buildEditFileDiff(args: Map<String, Any?>, rawArguments: String): List<ToolCallContent> {
        val filePath = args["filePath"] as? String ?: return fallback(rawArguments)
        val oldText = args["oldText"] as? String ?: ""
        val newText = args["newText"] as? String ?: ""
        if (oldText.isEmpty() && newText.isEmpty()) return fallback(rawArguments)
        return listOf(ToolCallContent.Diff(filePath, newText, oldText.ifEmpty { null }, null))
    }

    private fun buildWriteFileDiff(args: Map<String, Any?>, rawArguments: String): List<ToolCallContent> {
        val filePath = args["filePath"] as? String ?: return fallback(rawArguments)
        val content = args["content"] as? String ?: return fallback(rawArguments)
        return listOf(ToolCallContent.Diff(filePath, content, null, null))
    }

    private fun buildSmartEditDiff(args: Map<String, Any?>, rawArguments: String): List<ToolCallContent> {
        val edits = args["edits"] as? List<*>
        if (edits.isNullOrEmpty()) return fallback(rawArguments)
        val results = mutableListOf<ToolCallContent>()
        for (edit in edits) {
            if (edit !is Map<*, *>) continue
            val filePath = edit["filePath"] as? String ?: continue
            val mode = edit["mode"] as? String ?: continue
            when (mode) {
                "search_replace" -> {
                    val searchText = edit["searchText"] as? String ?: ""
                    val replaceText = edit["replaceText"] as? String ?: ""
                    if (searchText.isNotEmpty() || replaceText.isNotEmpty()) {
                        results.add(ToolCallContent.Diff(filePath, replaceText, searchText.ifEmpty { null }, null))
                    }
                }

                "insert_at_line" -> {
                    val newContent = edit["newContent"] as? String ?: ""
                    if (newContent.isNotEmpty()) {
                        results.add(ToolCallContent.Diff(filePath, newContent, null, null))
                    }
                }
            }
        }
        return results.ifEmpty { fallback(rawArguments) }
    }

    private fun fallback(arguments: String): List<ToolCallContent> {
        return listOf(ToolCallContent.Content(ContentBlock.Text(arguments)))
    }

    @JvmStatic
    fun getLine(location: ToolCallLocation): Int {
        return location.line?.toInt() ?: 0
    }

    // ========== 工厂方法（Java 侧无法直接构造 Kotlin data/value class）==========

    /** 创建 UserMessageChunk */
    @OptIn(UnstableApi::class)
    @JvmStatic
    fun createUserMessageChunk(text: String): SessionUpdate.UserMessageChunk {
        return SessionUpdate.UserMessageChunk(ContentBlock.Text(text), null)
    }

    /** 创建 AgentMessageChunk */
    @OptIn(UnstableApi::class)
    @JvmStatic
    fun createAgentMessageChunk(text: String): SessionUpdate.AgentMessageChunk {
        return SessionUpdate.AgentMessageChunk(ContentBlock.Text(text), null)
    }

    /** 创建 AgentThoughtChunk */
    @OptIn(UnstableApi::class)
    @JvmStatic
    fun createAgentThoughtChunk(text: String): SessionUpdate.AgentThoughtChunk {
        return SessionUpdate.AgentThoughtChunk(ContentBlock.Text(text), null)
    }

    /** 创建 ToolCallId */
    @JvmStatic
    fun createToolCallId(value: String): com.agentclientprotocol.model.ToolCallId {
        return com.agentclientprotocol.model.ToolCallId(value)
    }

    /** 创建 ToolCall */
    @JvmStatic
    fun createToolCall(
        toolCallId: com.agentclientprotocol.model.ToolCallId,
        title: String,
        content: List<com.agentclientprotocol.model.ToolCallContent>
    ): SessionUpdate.ToolCall {
        return SessionUpdate.ToolCall(toolCallId, title, null, null, content, emptyList(), null, null, null)
    }

    /** 创建 ToolCallUpdate */
    @JvmStatic
    fun createToolCallUpdate(
        toolCallId: com.agentclientprotocol.model.ToolCallId,
        status: com.agentclientprotocol.model.ToolCallStatus?,
        content: List<com.agentclientprotocol.model.ToolCallContent>?
    ): SessionUpdate.ToolCallUpdate {
        return SessionUpdate.ToolCallUpdate(toolCallId, null, null, status, content, null, null, null, null)
    }

    /** 创建 ToolCallContent.Content */
    @JvmStatic
    fun createToolCallContentText(text: String): com.agentclientprotocol.model.ToolCallContent {
        return com.agentclientprotocol.model.ToolCallContent.Content(ContentBlock.Text(text))
    }

    /** 获取 ToolCall.title */
    @JvmStatic
    fun getToolCallTitle(toolCall: SessionUpdate.ToolCall): String {
        return toolCall.title
    }

    /** 获取 ToolCall.toolCallId.value */
    @JvmStatic
    fun getToolCallIdValue(toolCall: SessionUpdate.ToolCall): String {
        return toolCall.toolCallId.value
    }

    /** 获取 ToolCallUpdate.toolCallId.value */
    @JvmStatic
    fun getToolCallUpdateIdValue(update: SessionUpdate.ToolCallUpdate): String {
        return update.toolCallId.value
    }

    /** 获取 ToolCallUpdate.status */
    @JvmStatic
    fun getToolCallUpdateStatus(update: SessionUpdate.ToolCallUpdate): com.agentclientprotocol.model.ToolCallStatus? {
        return update.status
    }

    /** 获取 ToolCallUpdate.title */
    @JvmStatic
    fun getToolCallUpdateTitle(update: SessionUpdate.ToolCallUpdate): String? {
        return update.title
    }
}
