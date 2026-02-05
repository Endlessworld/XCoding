package com.xr21.ai.agent.gui

import cn.hutool.core.util.IdUtil
import com.alibaba.cloud.ai.graph.agent.Agent
import com.xr21.ai.agent.LocalAgent
import com.xr21.ai.agent.entity.AgentOutput
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import org.springframework.ai.chat.messages.AssistantMessage
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

/**
 * 基于 LocalAgent 的聊天服务
 * 使用 SharedFlow 保持流式响应，支持页面切换时继续接收
 */
class LocalChatService {
    private val localAgent: LocalAgent

    // 存储每个会话的 SharedFlow，支持多订阅者
    private val sessionFlows = ConcurrentHashMap<String, MutableSharedFlow<AgentOutput<*>>>()

    // 存储每个会话的状态（使用 StateFlow 支持状态监听）
    private val sessionStatusFlow = MutableStateFlow<Map<String, SessionStreamingState>>(emptyMap())
    val streamingState: StateFlow<Map<String, SessionStreamingState>> = sessionStatusFlow.asStateFlow()

    // 存储每个会话的处理协程，用于取消
    private val processingJobs = ConcurrentHashMap<String, Job>()

    // 服务范围内的协程作用域
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        localAgent = LocalAgent()
        localAgent.initializeChatModel()
    }

    /**
     * 数据类：会话流式状态
     */
    data class SessionStreamingState(
        val sessionId: String,
        val isStreaming: Boolean = false,
        val userInput: String = "",
        val hasStarted: Boolean = false
    )

    /**
     * 发送消息并接收 AgentOutput 流
     * 为每个会话创建独立的 SharedFlow，支持多页面订阅
     */
    fun sendMessage(userInput: String, threadId: String? = null): Flow<AgentOutput<*>> {
        val newSessionCreated = threadId == null
        val currentThreadId = threadId ?: IdUtil.getSnowflakeNextIdStr()

        // 创建或获取该会话的 SharedFlow，只保留最新的少量数据
        val sharedFlow = sessionFlows.getOrPut(currentThreadId) {
            MutableSharedFlow(replay = 10, extraBufferCapacity = 64)
        }

        // 更新会话状态
        val current = sessionStatusFlow.value[currentThreadId]
        val newState = SessionStreamingState(
            sessionId = currentThreadId,
            isStreaming = true,
            userInput = userInput,
            hasStarted = true
        )
        sessionStatusFlow.value = sessionStatusFlow.value + (currentThreadId to newState)

        // 在服务作用域中启动处理协程（不取消收集者）
        val job = processingJobs.getOrPut(currentThreadId) {
            serviceScope.launch {
                val responseBuilder = StringBuilder()
                try {
                    // 发送 THINKING 状态
                    val thinkingOutput = createThinkingOutput(newSessionCreated, currentThreadId)
                    sharedFlow.emit(thinkingOutput)

                    val agent = localAgent.buildAgent()
                    val stateUpdate = HashMap<String, Any>()

                    processAgentOutput(agent, userInput, currentThreadId, stateUpdate) { output ->
                        sharedFlow.tryEmit(output)
                    }.collect { output ->
                        // 收集器只是为了保持流活跃
                    }
                } catch (e: CancellationException) {
                    // 处理取消
                    if (responseBuilder.isNotEmpty()) {
                        try {
                            val cancelledOutput = AgentOutput.builder<Any>()
                                .chunk(responseBuilder.toString())
                                .message(AssistantMessage.builder().content(responseBuilder.toString()).build())
                                .metadata(mapOf( "final" to true, "cancelled" to true))
                                .build()
                            sharedFlow.emit(cancelledOutput)
                        } catch (e: Throwable) {
                            e.printStackTrace()
                        }
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                    try {
                        val errorOutput = AgentOutput.builder<Any>()
                            .chunk("发生错误: ${e.message}")
                            .message(AssistantMessage.builder().content("发生错误: ${e.message}").build())
                            .metadata(mapOf("id" to UUID.randomUUID().toString(), "error" to true))
                            .build()
                        sharedFlow.emit(errorOutput)
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                } finally {
                    // 更新状态为已完成
                    val currentState = sessionStatusFlow.value[currentThreadId]
                    if (currentState != null) {
                        sessionStatusFlow.value = sessionStatusFlow.value + (currentThreadId to currentState.copy(isStreaming = false))
                    }
                    processingJobs.remove(currentThreadId)
                }
            }
        }

        // 返回 SharedFlow，支持多订阅者
        return sharedFlow
    }

    /**
     * 获取会话的 SharedFlow，支持延迟订阅
     */
    fun getSessionFlow(sessionId: String): Flow<AgentOutput<*>> {
        return sessionFlows[sessionId] ?: emptyFlow()
    }

    /**
     * 获取会话的 MutableSharedFlow（用于直接订阅）
     */
    fun getSessionMutableFlow(sessionId: String): MutableSharedFlow<AgentOutput<*>>? {
        return sessionFlows[sessionId]
    }

    /**
     * 检查会话是否有活跃的 SharedFlow
     */
    fun hasSessionFlow(sessionId: String): Boolean {
        return sessionFlows.containsKey(sessionId)
    }

    /**
     * 检查会话是否正在流式传输
     */
    fun isSessionStreaming(sessionId: String): Boolean {
        return sessionStatusFlow.value[sessionId]?.isStreaming == true
    }

    /**
     * 获取会话流式状态
     */
    fun getSessionStreamingState(sessionId: String): SessionStreamingState? {
        return sessionStatusFlow.value[sessionId]
    }

    /**
     * 清理会话流（当会话完全结束时调用）
     */
    fun clearSessionFlow(sessionId: String) {
        processingJobs[sessionId]?.cancel()
        processingJobs.remove(sessionId)
        sessionFlows.remove(sessionId)
        sessionStatusFlow.value = sessionStatusFlow.value - sessionId
    }

    /**
     * 取消会话流处理
     */
    fun cancelSessionProcessing(sessionId: String) {
        processingJobs[sessionId]?.cancel()
    }

    /**
     * 创建 THINKING 状态的 AgentOutput
     */
    private fun createThinkingOutput(newSessionCreated: Boolean, threadId: String): AgentOutput<Any> {
        return AgentOutput.builder<Any>().chunk("正在思考...")
            .message(AssistantMessage.builder().content("正在思考...").build())
            .metadata(
                mapOf(
                    "reasoningContent" to "正在思考...",
                    "session_id" to if (newSessionCreated) threadId else ""
                )
            ).build()
    }

    /**
     * 处理代理输出
     */
    @Suppress("UNCHECKED_CAST")
    private fun processAgentOutput(
        agent: Any, input: String, threadId: String, stateUpdate: HashMap<String, Any>,
        onOutput: (AgentOutput<*>) -> Unit
    ): Flow<Unit> = callbackFlow {
        val flux = localAgent.toFlux(agent as Agent, input, threadId, null, stateUpdate)
        val subscription = flux.subscribe({ output: AgentOutput<*> ->
            onOutput(output)
            trySend(Unit)
        }, { error ->
            error.printStackTrace()
            close(error)
        }, {
            // 流结束时发送最终输出标记
            val finalOutput = AgentOutput.builder<Any>()
                .metadata(mapOf("final" to true, "finishReason" to "COMPLETED"))
                .build()
            onOutput(finalOutput)
            close()
        })
        awaitClose {
            try {
                subscription.dispose()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
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
