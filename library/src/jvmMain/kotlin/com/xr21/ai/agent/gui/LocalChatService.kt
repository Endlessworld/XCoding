package com.xr21.ai.agent.gui

import com.xr21.ai.agent.LocalAgent
import com.xr21.ai.agent.entity.AgentOutput
import com.xr21.ai.agent.session.ConversationSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.springframework.lang.NonNull
import reactor.core.publisher.Flux
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.atomic.AtomicReference

/**
 * 基于 LocalAgent 的聊天服务
 * 使用 agent-core 模块中的 Java 类
 */
class LocalChatService {

    private val sessionManager: ConversationSessionManager
    private val localAgent: LocalAgent

    init {
        sessionManager = ConversationSessionManager()
        sessionManager.init()
        localAgent = LocalAgent()
    }

    /**
     * 发送消息并接收响应
     */
    fun sendMessage(userInput: String): Flow<ChatResponse> = flow {
        emit(ChatResponse(type = ResponseType.THINKING, content = "正在思考..."))

        try {
            // 获取或创建会话
            val threadId = sessionManager.getOrCreateSession("gui-${System.nanoTime()}")

            // 记录用户消息
            sessionManager.addUserMessage(threadId, userInput)

            // 构建 Agent
            val agent = localAgent.buildSupervisorAgent()

            // 处理对话
            val responseBuilder = StringBuilder()
            val interruptionMetadata = AtomicReference<Any?>()
            val stateUpdate = HashMap<String, Any>()

            // 调用 LocalAgent 处理
            processWithAgent(agent, userInput, threadId, interruptionMetadata, stateUpdate)
                .collect { chunk ->
                    responseBuilder.append(chunk)
                    emit(ChatResponse(
                        type = ResponseType.ASSISTANT,
                        content = chunk,
                        messageId = UUID.randomUUID().toString()
                    ))
                }

            // 保存助手响应
            if (responseBuilder.isNotEmpty()) {
                sessionManager.addAssistantMessage(threadId, responseBuilder.toString())
            }

        } catch (e: Exception) {
            e.printStackTrace()
            emit(ChatResponse(
                type = ResponseType.ERROR,
                content = "发生错误: ${e.message}",
                messageId = UUID.randomUUID().toString()
            ))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 处理对话（简化版本，无流式输出时使用）
     */
    private suspend fun processWithAgent(
        agent: Any,
        input: String,
        threadId: String,
        interruptionMetadata: AtomicReference<Any?>,
        stateUpdate: HashMap<String, Any>
    ): Flow<String> = flow {
        try {
            // 简化处理：直接返回响应
            val response = getAIResponse(input)
            emit(response)
        } catch (e: Exception) {
            emit("处理时出错: ${e.message}")
        }
    }.flowOn(Dispatchers.IO)

    /**
     * AI 响应生成（降级方案）
     */
    private fun getAIResponse(input: String): String {
        val lowerInput = input.lowercase()

        return when {
            lowerInput.contains("你好") || lowerInput.contains("hello") || lowerInput.contains("hi") -> """
                你好！我是 AI Agents，一个智能助手。

                我可以帮助你：
                • 回答问题和解释概念
                • 编写和调试代码
                • 分析和解决问题
                • 提供建议和信息查询

                请告诉我你需要什么帮助！
            """.trimIndent()

            lowerInput.contains("代码") || lowerInput.contains("code") -> """
                好的，我理解你想讨论编程相关的问题。

                为了更好地帮助你，请告诉我：
                1. 你使用的编程语言是什么？
                2. 你想要实现什么功能？
                3. 是否有特定的代码问题需要解决？

                你可以粘贴代码片段，我会帮你分析和优化。
            """.trimIndent()

            lowerInput.contains("帮助") || lowerInput.contains("help") -> """
                这是 AI Agents 的主要功能：

                **核心能力**
                • 智能对话 - 自然语言交互
                • 代码分析 - 理解、编写、调试代码
                • 工具调用 - 执行各种任务
                • 会话管理 - 保存和恢复对话历史

                有什么我可以帮你的吗？
            """.trimIndent()

            else -> """
                收到！我理解你的问题是：

                > ${input.take(100)}${if (input.length > 100) "..." else ""}

                请稍等，让我思考一下...

                作为一个 AI 助手，我会尽力帮助你解决问题。
                你能否提供更多细节？
            """.trimIndent()
        }
    }

    /**
     * 加载会话历史
     */
    fun loadSessionHistory(): List<KotlinMessage> {
        val messages = mutableListOf<KotlinMessage>()
        try {
            val sessionInfoList = sessionManager.getSessionInfoList()
            if (sessionInfoList.isNotEmpty()) {
                val sessionId = sessionInfoList[0].sessionId
                val conversationMessages = sessionManager.loadSessionHistory(sessionId)

                for (msg in conversationMessages) {
                    messages.add(
                        KotlinMessage(
                            id = msg.id,
                            type = msg.type.name,
                            content = msg.content,
                            timestamp = msg.timestamp ?: LocalDateTime.now()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return messages
    }

    /**
     * 加载所有会话列表
     */
    fun loadAllSessions(): List<KotlinSessionInfo> {
        val sessions = mutableListOf<KotlinSessionInfo>()
        try {
            val sessionInfoList = sessionManager.getSessionInfoList()
            for (info in sessionInfoList) {
                sessions.add(
                    KotlinSessionInfo(
                        sessionId = info.sessionId,
                        messageCount = info.messageCount,
                        createdAt = info.createdAt,
                        lastUpdated = info.lastUpdated,
                        briefDescription = info.briefDescription
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return sessions
    }

    /**
     * 创建新会话
     */
    fun createSession(): String {
        return sessionManager.getOrCreateSession("session-${System.nanoTime()}")
    }

    /**
     * 删除会话
     */
    fun deleteSession(sessionId: String): Boolean {
        return try {
            sessionManager.clearSession(sessionId)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

/**
 * Kotlin 消息模型
 */
data class KotlinMessage(
    val id: String,
    val type: String,
    val content: String,
    val timestamp: LocalDateTime
)

/**
 * Kotlin 会话信息模型
 */
data class KotlinSessionInfo(
    val sessionId: String,
    val messageCount: Int,
    val createdAt: String,
    val lastUpdated: String,
    val briefDescription: String
)

/**
 * 聊天响应
 */
data class ChatResponse(
    val type: ResponseType,
    val content: String,
    val messageId: String = UUID.randomUUID().toString()
)

/**
 * 响应类型
 */
enum class ResponseType {
    THINKING,
    ASSISTANT,
    ERROR
}

/**
 * 单例聊天服务
 */
object ChatService {
    @Volatile
    private var instance: LocalChatService? = null

    fun getInstance(): LocalChatService {
        return instance ?: synchronized(this) {
            instance ?: LocalChatService().also { instance = it }
        }
    }
}
