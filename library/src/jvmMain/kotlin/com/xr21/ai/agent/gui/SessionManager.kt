package com.xr21.ai.agent.gui

import com.alibaba.cloud.ai.graph.RunnableConfig
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint
import com.xr21.ai.agent.LocalAgent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.springframework.ai.chat.messages.Message
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/**
 * UI 层会话管理封装
 * 使用 FileSystemSaver 的检查点功能实现会话管理
 */
class FileSessionManager private constructor() {

    private val checkpointCache = ConcurrentHashMap<String, Checkpoint>()

    companion object {
        @Volatile
        private var instance: FileSessionManager? = null

        fun getInstance(): FileSessionManager {
            return instance ?: synchronized(this) {
                instance ?: FileSessionManager().also { instance = it }
            }
        }

        /**
         * 本地时间格式（显示到秒）
         */
        private val LOCAL_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
    // Kotlin
    fun loadSessionsBlocking(): List<UiSessionInfo> = runBlocking {
        loadSessions()
    }
    /**
     * 加载所有会话列表
     */
    suspend fun loadSessions(): List<UiSessionInfo> = withContext(Dispatchers.IO) {
        try {
            val saverFolder = File(LocalAgent.fileSystemSaverFolder)
            if (!saverFolder.exists() || !saverFolder.isDirectory) {
                return@withContext emptyList()
            }

            val sessions = mutableListOf<UiSessionInfo>()
            val threadIdPattern = Regex("^thread-(.+)\\.saver$")

            saverFolder.listFiles { file ->
                file.isFile && file.name.endsWith(".saver")
            }?.forEach { file ->
                try {
                    val matchResult = threadIdPattern.find(file.name)
                    if (matchResult != null) {
                        val threadId = matchResult.groupValues[1]
                        val runnableConfig = RunnableConfig.builder().threadId(threadId).build()
                        val checkpoint = LocalAgent.fileSystemSaver.get(runnableConfig)
                        checkpoint.ifPresent {
                            val messages = extractMessagesFromCheckpoint(checkpoint.get())
                            val sessionInfo = createSessionInfo(threadId, messages, file.absolutePath)
                            sessions.add(sessionInfo)
                        }
                    }
                } catch (e: Exception) {
                    // 忽略解析错误的文件
                }
            }

            // 按最后更新时间排序
            sessions.sortedByDescending { it.lastUpdated }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    // Kotlin
    fun loadMessagesBlocking(sessionId: String): List<Message> = runBlocking {
        loadMessages(sessionId)
    }

    /**
     * 根据 ID 加载会话消息
     */
    suspend fun loadMessages(sessionId: String): List<Message> = withContext(Dispatchers.IO) {
        try {
            val runnableConfig = RunnableConfig.builder().threadId(sessionId).build()
            val checkpoint = LocalAgent.fileSystemSaver.get(runnableConfig)
            if (!checkpoint.isEmpty) {
                extractMessagesFromCheckpoint(checkpoint.get())
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 创建新会话
     */
    suspend fun createSession(): String = withContext(Dispatchers.IO) {
        val sessionId = "session-${System.nanoTime()}"


        sessionId
    }

    /**
     * 保存消息到会话（通过 FileSystemSaver 检查点）
     */
    suspend fun saveMessages(sessionId: String, messages: List<Message>) = withContext(Dispatchers.IO) {

    }

    /**
     * 删除会话
     */
    suspend fun deleteSession(sessionId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val runnableConfig = RunnableConfig.builder().threadId(sessionId).build()
            LocalAgent.fileSystemSaver.deleteFile(runnableConfig)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 从检查点提取消息列表，直接返回原始 Message 对象
     */
    private fun extractMessagesFromCheckpoint(checkpoint: Checkpoint): List<Message> {
        val state = checkpoint.state
        val messagesList = state["messages"] as? List<*> ?: return emptyList()
        return messagesList.filterIsInstance<Message>()
    }

    /**
     * 创建会话信息对象
     */
    private fun createSessionInfo(threadId: String, messages: List<Message>, filePath: String): UiSessionInfo {
        val firstUserMessage =
            messages.firstOrNull { it.messageType == org.springframework.ai.chat.messages.MessageType.USER }?.text
        val briefDescription = firstUserMessage?.take(50) ?: "新会话"

        // 解析时间戳并格式化为本地时间格式
        val lastUpdated: String
        val timestamp: Long
        val lastMessageTimestamp = messages.lastOrNull()?.metadata?.get("timestamp")?.toString()
        val parsedTimestamp = try {
            if (lastMessageTimestamp != null) {
                // 尝试解析时间戳（毫秒或秒）
                lastMessageTimestamp.toLongOrNull()?.let {
                    if (it > 1_000_000_000_000) it else it * 1000 // 转换为毫秒
                } ?: System.currentTimeMillis()
            } else {
                System.currentTimeMillis()
            }
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
        timestamp = parsedTimestamp

        lastUpdated = LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(timestamp),
            java.time.ZoneId.systemDefault()
        ).format(LOCAL_TIME_FORMATTER)

        return UiSessionInfo(
            sessionId = threadId,
            filePath = filePath,
            messageCount = messages.size,
            createdAt = LocalDateTime.now().format(LOCAL_TIME_FORMATTER),
            lastUpdated = lastUpdated,
            briefDescription = briefDescription,
            timestamp = timestamp
        )
    }

    /**
     * 清除所有会话历史
     */
    suspend fun clearAllSessions(): Boolean = withContext(Dispatchers.IO) {
        try {
            val saverFolder = File(LocalAgent.fileSystemSaverFolder)
            if (saverFolder.exists()) {
                saverFolder.listFiles()?.forEach { file ->
                    if (file.name.endsWith(".saver")) {
                        file.delete()
                    }
                }
            }
            checkpointCache.clear()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
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
    val briefDescription: String,
    /**
     * 原始时间戳（毫秒），用于排序
     */
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 清除所有会话历史（全局函数，保持向后兼容）
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
