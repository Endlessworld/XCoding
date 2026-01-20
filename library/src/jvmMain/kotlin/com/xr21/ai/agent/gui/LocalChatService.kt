package com.xr21.ai.agent.gui

import com.alibaba.cloud.ai.graph.agent.Agent
import com.xr21.ai.agent.LocalAgent
import com.xr21.ai.agent.session.ConversationSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
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

            // 使用 emitAll 直接转发 chunks，而不是用 collect 阻塞
            emitAll(
                processWithAgent(agent, userInput, currentThreadId, interruptionMetadata, stateUpdate).map { chunk ->
                    responseBuilder.append(chunk)
                    ChatResponse(
                        type = ResponseType.STREAMING, content = chunk, messageId = messageId
                    )
                })

            if (responseBuilder.isNotEmpty()) {
                sessionManager.addAssistantMessage(currentThreadId, responseBuilder.toString())
            }
        } catch (e: CancellationException) {
            // 处理取消异常 - 发送取消响应以完成UI的collect循环
            if (responseBuilder.isNotEmpty()) {
                try {
                    emit(
                        ChatResponse(
                            type = ResponseType.ASSISTANT, content = responseBuilder.toString(), messageId = messageId
                        )
                    )
                } catch (e: Throwable) {
                    e.printStackTrace();
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            try {
                emit(
                    ChatResponse(
                        type = ResponseType.ERROR,
                        content = "发生错误: ${e.message}",
                        messageId = UUID.randomUUID().toString()
                    )
                )
            } catch (e: Throwable) {
                e.printStackTrace();
            }

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
        var flux = localAgent.toFlux(
            agent as Agent, input, threadId, null, stateUpdate
        )
        val subscription = flux
            .mapNotNull { output -> output.data()?.getChunk() } // 使用 mapNotNull 替代 filter
            .subscribe(
                { chunk ->
                    // 发送数据到 Flow
                    chunk?.let {
                        trySend(it)
                    }
                },
                { error ->
                    // 发生错误时关闭 Flow
                    error.printStackTrace();
                    close(error)
                },
                {
                    // 完成时关闭 Flow
                    close()
                }
            )
        // 当 Flow 被取消时，取消 Flux 的订阅
        awaitClose {
            try {
                subscription.dispose();
            } catch (e: Exception) {
                // 忽略释放异常
                e.printStackTrace();
            }
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
