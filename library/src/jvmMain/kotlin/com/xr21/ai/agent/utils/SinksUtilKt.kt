package com.xr21.ai.agent.utils

import com.alibaba.cloud.ai.graph.NodeOutput
import com.alibaba.cloud.ai.graph.RunnableConfig
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata
import com.alibaba.cloud.ai.graph.agent.Agent
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException
import com.alibaba.cloud.ai.graph.state.StateSnapshot
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput
import com.xr21.ai.agent.entity.AgentOutput
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.channels.Channel
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.metadata.DefaultUsage
import org.springframework.ai.openai.api.OpenAiApi
import org.springframework.util.StringUtils
import reactor.core.Disposable
import reactor.core.publisher.Flux

private val logger = KotlinLogging.logger {}

object SinksUtil {

    /**
     * 将 NodeOutput 转换为 AgentOutput。
     */
    fun buildContent(output: NodeOutput): AgentOutput<Any> {
        val builder =
            AgentOutput.builder<Any>().agent(output.agent()).data(output.state().data()).tokenUsage(output.tokenUsage())
                .node(output.node()).timestamp(System.currentTimeMillis())

        if (output is StreamingOutput<*>) {
            builder.message(output.message())
            if (output.message() != null) {
                builder.metadata(output.message().metadata)
                builder.metadata("timestamp", System.currentTimeMillis())
                val reasoningContent = output.message().metadata.getOrDefault("reasoningContent", "").toString()
                if (StringUtils.hasLength(reasoningContent)) {
                    builder.think(reasoningContent)
                }
                val finishReason = output.message().metadata["finishReason"]
                // 排除携带工具调用的 AssistantMessage：其文本（工具调用前的说明）会被
                // ToolCall 事件独立承载，且 graph 框架原生 chunk 也会跳过它；若此处再
                // 作为 AgentMessageChunk 发送，会造成工具调用前后出现重复消息。
                val isToolCallMessage = output.message() is AssistantMessage &&
                        (output.message() as AssistantMessage).hasToolCalls()
                if (StringUtils.hasLength(output.message().text)
                    && !isToolCallMessage
                    && OpenAiApi.ChatCompletionFinishReason.STOP.name != finishReason
                ) {
                    builder.chunk(output.message().text)
                }
                builder.originData(output.originData)
            }
            builder.subGraph(output.isSubGraph)
            if (output.message() is AssistantMessage) {
                val message = output.message() as AssistantMessage
                if (message.hasToolCalls()) {
                    logger.debug { "Tool calls: ${message.toolCalls}" }
                }
            }
        }
        if (output is InterruptionMetadata) {
            builder.interruptionMetadata(output)
            builder.subGraph(output.isSubGraph)
        }
        if (output is StateSnapshot) {
            builder.config(output.config())
        }
        if (!output.isEND) {
            val data = output.state().data()
            if (data.containsKey("chunk")) {
                builder.chunk(data.getOrDefault("chunk", "").toString())
            }
        }
        if (output.tokenUsage() is DefaultUsage) {
            logger.debug { "usage: ${output.tokenUsage()}" }
        }
        return builder.build()
    }

    /**
     * 将 Flux<AgentOutput> 直接消费为 Channel<AgentOutput>。
     * 
     * 在 Reactor 线程上订阅 Flux，通过回调将元素写入 Channel。
     * 返回 Channel 和 Disposable，调用者负责在结束时 dispose 和 close。
     * 
     * @param flux       源 Flux
     * @param channel    目标 Channel（调用者创建，便于控制取消）
     * @return Disposable 用于取消订阅
     */
    fun fluxToChannel(
        flux: Flux<AgentOutput<Any>>, channel: Channel<AgentOutput<Any>>
    ): Disposable {
        return flux.subscribe({ output -> channel.trySend(output) }, { error ->
            logger.error(error) { "Error in agent flux" }
            channel.close(error)
        }, {
            logger.info { "Agent flux completed" }
            channel.close()
        })
    }

    /**
     * 调用 agent.stream(input, runnableConfig) 获取 Flux<NodeOutput>，
     * 转换为 Flux<AgentOutput>（复用 buildContent 逻辑）。
     * 包含 GraphRunnerException 重试逻辑（与 Java 版 toFlux 一致）。
     */
    fun agentToFlux(
        agent: Agent, input: UserMessage, runnableConfig: RunnableConfig
    ): Flux<AgentOutput<Any>> {
        val nodeOutputFlux = try {
            agent.stream(input, runnableConfig)
        } catch (e: GraphRunnerException) {
            try {
                agent.stream(input, runnableConfig)
            } catch (ex: GraphRunnerException) {
                throw RuntimeException(ex)
            }
        }
        return nodeOutputFlux.map { buildContent(it) }
    }
}
