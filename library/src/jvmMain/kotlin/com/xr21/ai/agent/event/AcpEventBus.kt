@file:OptIn(com.agentclientprotocol.annotations.UnstableApi::class)

package com.xr21.ai.agent.event

import com.agentclientprotocol.common.Event
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.SessionUpdate
import reactor.core.publisher.Sinks

/**
 * ACP 事件总线，桥接工具（同步）与 Flow（协程）之间的事件传递。
 *
 * 工具通过 AcpEventBus 发射事件（如 Plan 更新、消息块等），
 * SimpleAgentSession 通过 Sinks.Many 消费并转发到 Flow<Event>。
 */
class AcpEventBus {

    companion object {
        const val CONTEXT_KEY = "AcpEventBus"
    }

    private val sink: Sinks.Many<Event> = Sinks.many().unicast().onBackpressureBuffer()

    fun sink(): Sinks.Many<Event> = sink

    fun emitText(text: String) {
        sink.tryEmitNext(
            Event.SessionUpdateEvent(
                SessionUpdate.AgentMessageChunk(
                    ContentBlock.Text(text, null, null)
                )
            )
        )
    }

    fun emitThought(thought: String) {
        sink.tryEmitNext(
            Event.SessionUpdateEvent(
                SessionUpdate.AgentMessageChunk(
                    ContentBlock.Text(thought, null, null)
                )
            )
        )
    }

    fun emit(event: Event) {
        sink.tryEmitNext(event)
    }
}
