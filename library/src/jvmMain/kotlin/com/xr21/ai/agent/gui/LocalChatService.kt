package com.xr21.ai.agent.gui

import com.alibaba.cloud.ai.graph.agent.Agent
import com.xr21.ai.agent.LocalAgent
import com.xr21.ai.agent.session.ConversationSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException

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
        localAgent.initializeChatModel()
    }

    /**
     * 发送消息并接收响应
     */
    fun sendMessage(userInput: String, threadId: String? = null): Flow<ChatResponse> = flow {
        val newSessionCreated = threadId == null
        val currentThreadId = threadId ?: sessionManager.getOrCreateSession("gui-${System.nanoTime()}")

        // 第一个响应包含新会话ID（如果创建了新会话）
        emit(ChatResponse(
            type = ResponseType.THINKING,
            content = "正在思考...",
            sessionId = if (newSessionCreated) currentThreadId else null
        ))

        // 处理对话的变量移到try外部，以便在catch中访问
        val responseBuilder = StringBuilder()
        val messageId = UUID.randomUUID().toString()

        try {
            // 记录用户消息
            sessionManager.addUserMessage(currentThreadId, userInput)

            // 构建 Agent
            val agent = localAgent.buildAgent()

            val interruptionMetadata = AtomicReference<Any?>()
            val stateUpdate = HashMap<String, Any>()

            // 调用 LocalAgent 处理
            processWithAgent(agent, userInput, currentThreadId, interruptionMetadata, stateUpdate)
                .collect { chunk ->
                    // 检查是否被取消 - 确保在处理每个chunk前都检查
                    responseBuilder.append(chunk)
                    emit(ChatResponse(
                        type = ResponseType.STREAMING,
                        content = chunk,
                        messageId = messageId
                    ))
                }

            // 只有在未取消且没有流式响应的情况下才发送 ASSISTANT 响应
            // 注意：流式响应已经在 UI 中显示内容，这里不需要额外的 ASSISTANT 响应
            if (responseBuilder.isNotEmpty()) {
                sessionManager.addAssistantMessage(currentThreadId, responseBuilder.toString())
            }

        } catch (e: CancellationException) {
            // 处理取消异常 - 发送取消响应以完成UI的collect循环
            if (responseBuilder.isNotEmpty()) {
                emit(ChatResponse(
                    type = ResponseType.ASSISTANT,
                    content = responseBuilder.toString(),
                    messageId = messageId
                ))
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
     * 处理对话 - 统一使用 LocalAgent 处理
     */

    private fun processWithAgent(
        agent: Any,
        input: String,
        threadId: String,
        interruptionMetadata: AtomicReference<Any?>,
        stateUpdate: HashMap<String, Any>
    ): Flow<String> = callbackFlow {

       var  flux = localAgent.toFlux(
            agent as Agent, input, threadId, null, stateUpdate
        );
        var subscriptionRef: Any? = null
        val subscription = flux
            .mapNotNull { output -> output.data()?.getChunk() } // 使用 mapNotNull 替代 filter
            .subscribe(
                { chunk ->
                    // 发送数据到 Flow
                    chunk?.let {
                        val result = trySend(it)
                        result.onFailure { error ->
                            // 发送失败时关闭 Flow
                            close(error)
                        }
                    }
                },
                { error ->
                    // 发生错误时关闭 Flow
                    close(error)
                },
                {
                    // 完成时关闭 Flow
                    close()
                }
            )
        subscriptionRef = subscription

        // 当 Flow 被取消时，取消 Flux 的订阅
        awaitClose {
            try {
                subscriptionRef?.let { sub ->
                    // 尝试调用 dispose 方法
                    val disposeMethod = sub.javaClass.getMethod("dispose")
                    disposeMethod.invoke(sub)
                }
            } catch (e: Exception) {
                // 忽略释放异常
            }
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
    val messageId: String = UUID.randomUUID().toString(),
    val sessionId: String? = null  // 新创建的会话ID
)

/**
 * 响应类型
 */
enum class ResponseType {
    THINKING,
    STREAMING,  // 流式响应（追加到当前消息）
    ASSISTANT,  // 完整响应（独立消息）
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
