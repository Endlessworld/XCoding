package com.xr21.ai.agent.gui

import cn.hutool.core.util.IdUtil
import com.alibaba.cloud.ai.graph.agent.Agent
import com.xr21.ai.agent.LocalAgent
import com.xr21.ai.agent.entity.AgentOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import org.springframework.ai.chat.messages.AssistantMessage
import java.util.*
import kotlin.coroutines.cancellation.CancellationException

/**
 * 基于 LocalAgent 的聊天服务
 * 直接使用 AgentOutput 对象流进行渲染
 */
class LocalChatService {
    private val localAgent: LocalAgent

    init {
        localAgent = LocalAgent()
        localAgent.initializeChatModel()
    }

    /**
     * 发送消息并接收 AgentOutput 流
     * 直接发射 AgentOutput 对象，在 UI 层直接使用
     */
    fun sendMessage(userInput: String, threadId: String? = null): Flow<AgentOutput<*>> = flow {
        val newSessionCreated = threadId == null
        val currentThreadId = threadId ?: IdUtil.getSnowflakeNextIdStr()

        // 发送 THINKING 状态（使用特殊标记的 AgentOutput）
        emit(
            createThinkingOutput(newSessionCreated, currentThreadId)
        )
        // 处理对话的变量移到try外部，以便在catch中访问
        val responseBuilder = StringBuilder()
        val messageId = UUID.randomUUID().toString()
        try {
            // 构建 Agent
            val agent = localAgent.buildAgent()
            val stateUpdate = HashMap<String, Any>()
            // 直接处理 AgentOutput 流
            @Suppress("UNCHECKED_CAST") emitAll(
                processWithAgent(
                    agent, userInput, currentThreadId, stateUpdate
                )
            )
        } catch (e: CancellationException) {
            // 处理取消异常 - 发送取消响应以完成UI的collect循环
            if (responseBuilder.isNotEmpty()) {
                try {
                    emit(
                        AgentOutput.builder<Any>().chunk(responseBuilder.toString()).message(
                            AssistantMessage.builder().content(responseBuilder.toString()).build()
                        ).metadata(mapOf("message_id" to messageId, "final" to true, "cancelled" to true)).build()
                    )
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            try {
                emit(
                    AgentOutput.builder<Any>().chunk("发生错误: ${e.message}").message(
                        AssistantMessage.builder().content("发生错误: ${e.message}").build()
                    ).metadata(mapOf("message_id" to UUID.randomUUID().toString(), "error" to true)).build()
                )
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 创建 THINKING 状态的 AgentOutput
     */
    private fun createThinkingOutput(newSessionCreated: Boolean, threadId: String): AgentOutput<Any> {
        return AgentOutput.builder<Any>().chunk("正在思考...").message(
            AssistantMessage.builder().content("正在思考...").build()
        ).metadata(
            mapOf(
                "thinking" to true, "session_id" to if (newSessionCreated) threadId else ""
            )
        ).build()
    }

    /**
     * 处理对话 - 直接发射 AgentOutput
     */
    @Suppress("UNCHECKED_CAST")
    private fun processWithAgent(
        agent: Any, input: String, threadId: String, stateUpdate: HashMap<String, Any>
    ): Flow<AgentOutput<*>> = callbackFlow {
        val flux = localAgent.toFlux(
            agent as Agent, input, threadId, null, stateUpdate
        )
        val subscription = flux.subscribe({ output: AgentOutput<*> ->
            trySend(output)
        }, { error ->
            error.printStackTrace()
            close(error)
        }, {
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
