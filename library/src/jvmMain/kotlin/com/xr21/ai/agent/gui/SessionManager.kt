package com.xr21.ai.agent.gui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime
import java.util.*

/**
 * UI 层会话管理封装
 */
class SessionManager private constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // 确保会话目录存在
        val conversationDir = File(System.getProperty("user.home"), ".agi_working/conversations")
        if (!conversationDir.exists()) {
            conversationDir.mkdirs()
        }
    }

    companion object {
        @Volatile
        private var instance: SessionManager? = null

        fun getInstance(): SessionManager {

            return instance ?: synchronized(this) {

                instance ?: SessionManager().also { instance = it }
            }
        }
    }

    /**
     * 加载所有会话列表
     */
    fun loadSessions(): List<UiSessionInfo> {
        return try {
            val conversationDir = File(System.getProperty("user.home"), ".agi_working/conversations")
            if (!conversationDir.exists()) return emptyList()

            val sessions = mutableListOf<UiSessionInfo>()

            conversationDir.listFiles { file -> file.isDirectory }?.forEach { dir ->
                val sessionFile = File(dir, "session.json")
                if (sessionFile.exists()) {
                    try {
                        val content = sessionFile.readText()
                        // 简单解析 JSON 提取信息
                        val sessionId = dir.name
                        val briefDesc = extractBriefDescription(content)
                        val messageCount = extractMessageCount(content)
                        val lastUpdated = extractLastUpdated(content)

                        sessions.add(
                            UiSessionInfo(
                                sessionId = sessionId,
                                filePath = sessionFile.absolutePath,
                                messageCount = messageCount,
                                createdAt = LocalDateTime.now().toString(),
                                lastUpdated = lastUpdated,
                                briefDescription = briefDesc
                            )
                        )
                    } catch (e: Exception) {
                        // 忽略解析错误的文件
                    }
                }
            }

            // 按最后更新时间排序
            sessions.sortedByDescending { it.lastUpdated }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 根据 ID 加载会话消息
     */
    fun loadMessages(sessionId: String): List<UiChatMessage> {
        return try {
            val sessionDir = File(System.getProperty("user.home"), ".agi_working/conversations/$sessionId")
            val sessionFile = File(sessionDir, "session.json")

            if (!sessionFile.exists()) return emptyList()

            val content = sessionFile.readText()
            parseMessagesFromJson(content)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 创建新会话
     */
    fun createSession(): String {
        val sessionId = "session-${System.nanoTime()}"
        val sessionDir = File(System.getProperty("user.home"), ".gi_working/conversations/$sessionId")
        sessionDir.mkdirs()

        // 创建初始会话文件
        val sessionFile = File(sessionDir, "session.json")
        val initialContent = """
            {
                "sessionId": "$sessionId",
                "createdAt": "${LocalDateTime.now()}",
                "lastUpdated": "${LocalDateTime.now()}",
                "messageCount": 0,
                "messages": []
            }
        """.trimIndent()
        sessionFile.writeText(initialContent)

        return sessionId
    }

    /**
     * 保存消息到会话
     */
    fun saveMessages(sessionId: String, messages: List<UiChatMessage>) {
        scope.launch {
            try {
                val sessionDir = File(System.getProperty("user.home"), ".agi_working/conversations/$sessionId")
                if (!sessionDir.exists()) sessionDir.mkdirs()

                val sessionFile = File(sessionDir, "session.json")

                // 读取现有会话
                val existingMessages = if (sessionFile.exists()) {
                    loadMessages(sessionId).toMutableList()
                } else {
                    mutableListOf()
                }

                // 添加新消息（避免重复）
                val existingIds = existingMessages.map { it.id }.toSet()
                val newMessages = messages.filter { it.id !in existingIds }
                existingMessages.addAll(newMessages)

                // 重建 JSON
                val jsonMessages = existingMessages.map { msg ->
                    """
                    {
                        "id": "${msg.id}",
                        "content": "${msg.content.replace("\"", "\\\"")}",
                        "type": "${msg.type.name}",
                        "timestamp": "${msg.timestamp}"
                    }
                    """.trimIndent()
                }.joinToString(",\n")

                val jsonContent = """
                    {
                        "sessionId": "$sessionId",
                        "createdAt": "${LocalDateTime.now()}",
                        "lastUpdated": "${LocalDateTime.now()}",
                        "messageCount": ${existingMessages.size},
                        "messages": [
                $jsonMessages
                        ]
                    }
                """.trimIndent()

                sessionFile.writeText(jsonContent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 删除会话
     */
    fun deleteSession(sessionId: String): Boolean {
        return try {
            val sessionDir = File(System.getProperty("user.home"), ".agi_working/conversations/$sessionId")
            if (sessionDir.exists()) {
                sessionDir.deleteRecursively()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // 辅助函数：提取第一条用户消息作为描述
    private fun extractBriefDescription(content: String): String {
        val userMessageMatch = Regex(""""type":\s*"USER"""")
            .find(content)
        if (userMessageMatch != null) {
            val beforeMatch = content.substring(0, userMessageMatch.range.first)
            val contentMatch = Regex(""""content":\s*"([^"]*)"""").find(beforeMatch)
            if (contentMatch != null) {
                return contentMatch.groupValues[1].take(50)
            }
        }
        return "新会话"
    }

    private fun extractMessageCount(content: String): Int {
        val match = Regex(""""messageCount":\s*(\d+)""").find(content)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    private fun extractLastUpdated(content: String): String {
        val match = Regex(""""lastUpdated":\s*"([^"]*)"""").find(content)
        return match?.groupValues?.get(1) ?: LocalDateTime.now().toString()
    }

    private fun parseMessagesFromJson(content: String): List<UiChatMessage> {
        val messages = mutableListOf<UiChatMessage>()

        val messageRegex = Regex(
            """\{[^}]*"id":\s*"([^"]*)"[^}]*"content":\s*"([^"]*)"[^}]*"type":\s*"([^"]*)"[^}]*"timestamp":\s*"([^"]*)"[^}]*\},
            |""".trimMargin(),
            RegexOption.MULTILINE
        )

        // 简化解析
        val idMatches = Regex(""""id":\s*"([^"]*)"""").findAll(content)
        val contentMatches = Regex(""""content":\s*"([^"]*)"""").findAll(content)
        val typeMatches = Regex(""""type":\s*"([^"]*)"""").findAll(content)
        val timeMatches = Regex(""""timestamp":\s*"([^"]*)"""").findAll(content)

        val ids = idMatches.map { it.groupValues[1] }.toList()
        val contents = contentMatches.map { it.groupValues[1].replace("\\\"", "\"") }.toList()
        val types = typeMatches.map { it.groupValues[1] }.toList()
        val times = timeMatches.map { it.groupValues[1] }.toList()

        val minSize = minOf(ids.size, contents.size, types.size, times.size)
        for (i in 0 until minSize) {
            val type = try { UiMessageType.valueOf(types[i]) } catch (e: Exception) { UiMessageType.ASSISTANT }
            val timestamp = try { LocalDateTime.parse(times[i]) } catch (e: Exception) { LocalDateTime.now() }

            messages.add(
                UiChatMessage(
                    id = ids[i],
                    content = contents[i],
                    type = type,
                    timestamp = timestamp
                )
            )
        }

        return messages
    }
}

/**
 * UI 会话信息
 */
data class UiSessionInfo(
    val sessionId: String,
    val filePath: String,
    val messageCount: Int,
    val createdAt: String,
    val lastUpdated: String,
    val briefDescription: String
)

/**
 * UI 消息模型
 */
data class UiChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val type: UiMessageType,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

/**
 * 消息类型
 */
enum class UiMessageType {
    USER, ASSISTANT, SYSTEM, TOOL_CALL, TOOL_RESPONSE, ERROR
}

/**
 * 清除所有会话历史
 */
fun clearAllSessions(): Boolean {
    return try {
        val conversationDir = File(System.getProperty("user.home"), ".agi_working/conversations")
        if (conversationDir.exists()) {
            conversationDir.deleteRecursively()
        }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}
