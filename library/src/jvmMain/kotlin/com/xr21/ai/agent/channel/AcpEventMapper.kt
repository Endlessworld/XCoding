@file:OptIn(UnstableApi::class)

package com.xr21.ai.agent.channel

import com.agentclientprotocol.annotations.UnstableApi
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.model.*
import com.agentclientprotocol.model.SessionUpdate.ToolCallUpdate
import io.agentscope.core.event.*
import io.agentscope.core.message.GenerateReason
import io.github.oshai.kotlinlogging.KotlinLogging

private val log = KotlinLogging.logger {}

/**
 * Bidirectional mapper between AgentScope [AgentEvent] and ACP [Event].
 *
 * Mapping table:
 * AgentScope                                ACP
 * ──────────────────                        ─────────────────────
 * TextBlockDeltaEvent                       SessionUpdate.AgentMessageChunk
 * ThinkingBlockDeltaEvent                   SessionUpdate.AgentThoughtChunk
 * ToolCallStartEvent (name, id)             SessionUpdate.ToolCall (PENDING)
 * ToolCallEndEvent                          (terminal marker, no direct emit)
 * ToolResultTextDeltaEvent (delta)           SessionUpdate.ToolCallUpdate (streaming)
 * ToolResultDataDeltaEvent                   SessionUpdate.ToolCallUpdate
 * ToolResultEndEvent (state)                 SessionUpdate.ToolCallUpdate (COMPLETED/FAILED)
 * ModelCallEndEvent (usage)                  SessionUpdate.UsageUpdate
 * AgentEndEvent                             → caller emits PromptResponseEvent(END_TURN)
 * RequireUserConfirmEvent (toolCalls)       → caller invokes requestPermissions()
 * ExceedMaxItersEvent                       → caller emits PromptResponseEvent(REFUSAL)
 * SubagentExposedEvent                      → forwarded via deliver(OutboundAddress)
 */
object AcpEventMapper {

    /**
     * Converts a single [AgentEvent] into a list of ACP [Event]s.
     * Most events map to a single [SessionUpdate] wrapped in [Event.SessionUpdateEvent];
     * some produce zero events (markers consumed by the caller).
     *
     * @param event the AgentScope agent event
     * @return list of ACP events to emit (may be empty for marker events)
     */
    fun toAcpEvents(event: AgentEvent): List<Event> {
        return when (event) {
            is TextBlockDeltaEvent -> listOf(
                Event.SessionUpdateEvent(
                    SessionUpdate.AgentMessageChunk(
                        content = ContentBlock.Text(event.delta ?: ""),
                        messageId = null
                    )
                )
            )

            is ThinkingBlockDeltaEvent -> listOf(
                Event.SessionUpdateEvent(
                    SessionUpdate.AgentThoughtChunk(
                        content = ContentBlock.Text(event.delta ?: ""),
                        messageId = null
                    )
                )
            )

            is ToolCallStartEvent -> {
                // ToolCallStart → ACP ToolCall with PENDING status
                val toolName = event.toolCallName ?: "unknown"
                val toolId = event.toolCallId ?: ""
                listOf(
                    Event.SessionUpdateEvent(
                        SessionUpdate.ToolCall(
                            toolCallId = ToolCallId(toolId),
                            title = toolName,
                            kind = null, // caller can enrich via ToolKindFind
                            status = ToolCallStatus.PENDING,
                            content = emptyList()
                        )
                    )
                )
            }

            is ToolCallEndEvent -> {
                // ToolCallEnd is a terminal marker — no separate ACP event needed.
                // The companion ToolResultEndEvent carries COMPLETED/FAILED status.
                emptyList()
            }

            is ToolResultTextDeltaEvent -> {
                // Streaming text result delta → ToolCallUpdate
                listOf(
                    Event.SessionUpdateEvent(
                        ToolCallUpdate(
                            toolCallId = ToolCallId(event.toolCallId ?: ""),
                            title = event.toolCallName,
                            status = ToolCallStatus.COMPLETED,
                            content = listOf(
                                ToolCallContent.Content(ContentBlock.Text(event.delta ?: ""))
                            )
                        )
                    )
                )
            }

            is ToolResultDataDeltaEvent -> {
                // Binary/data result → ToolCallUpdate with data content
                listOf(
                    Event.SessionUpdateEvent(
                        ToolCallUpdate(
                            toolCallId = ToolCallId(event.toolCallId ?: ""),
                            title = event.toolCallName,
                            status = ToolCallStatus.COMPLETED,
                            content = listOf(
                                ToolCallContent.Content(ContentBlock.Text((event.data?.toString() ?: byteArrayOf()) as String))

                            )
                        )
                    )
                )
            }

            is ToolResultEndEvent -> {
                // Final result → COMPLETED or FAILED
                val status = when (event.state?.name?.lowercase()) {
                    "success", "completed" -> ToolCallStatus.COMPLETED
                    "error", "failed" -> ToolCallStatus.FAILED
                    else -> ToolCallStatus.COMPLETED
                }
                listOf(
                    Event.SessionUpdateEvent(
                        ToolCallUpdate(
                            toolCallId = ToolCallId(event.toolCallId ?: ""),
                            title = event.toolCallName,
                            status = status
                        )
                    )
                )
            }

            is ModelCallEndEvent -> {
                // Model call completed → UsageUpdate
                val usage = event.usage
                if (usage != null) {
                    listOf(
                        Event.SessionUpdateEvent(
                            SessionUpdate.UsageUpdate(
                                used = (usage.totalTokens ?: 0).toLong(),
                                size = 0L,
                                cost = null
                            )
                        )
                    )
                } else {
                    emptyList()
                }
            }

            is AgentResultEvent -> {
                // AgentResult contains the final response Msg.
                // The ACP layer should detect generateReason == PERMISSION_ASKING
                // and trigger requestPermissions().
                // Pure text result → AgentMessageChunk
                val text = event.result?.textContent
                if (!text.isNullOrBlank()) {
                    listOf(
                        Event.SessionUpdateEvent(
                            SessionUpdate.AgentMessageChunk(
                                content = ContentBlock.Text(text)
                            )
                        )
                    )
                } else {
                    emptyList()
                }
            }

            is RequireUserConfirmEvent -> {
                // HITL: ACP layer must intercept this and call requestPermissions().
                // The mapper emits ToolCallUpdate with PENDING status for each tool.
                event.toolCalls?.map { toolUse ->
                    Event.SessionUpdateEvent(
                        SessionUpdate.ToolCall(
                            toolCallId = ToolCallId(toolUse.id ?: ""),
                            title = toolUse.name ?: "unknown",
                            kind = null,
                            status = ToolCallStatus.PENDING,
                            content = listOf(
                                ToolCallContent.Content(ContentBlock.Text(toolUse.input.toString() ?: "{}"))
                            )
                        )
                    )
                } ?: emptyList()
            }

            is AgentStartEvent -> {
                // AgentStart: no direct ACP equivalent, can be ignored
                emptyList()
            }

            is AgentEndEvent -> {
                // AgentEnd: caller should emit PromptResponseEvent(END_TURN)
                // No SessionUpdate needed here.
                emptyList()
            }

            is ExceedMaxItersEvent -> {
                // Max iterations exceeded → REFUSAL
                listOf(
                    Event.SessionUpdateEvent(
                        SessionUpdate.AgentMessageChunk(
                            content = ContentBlock.Text(
                                "Agent exceeded maximum iterations (${event.maxIters})"
                            )
                        )
                    )
                )
            }

            is SubagentExposedEvent -> {
                // Subagent exposed: forwarded as hint to ACP client
                listOf(
                    Event.SessionUpdateEvent(
                        SessionUpdate.AgentMessageChunk(
                            content = ContentBlock.Text(
                                "Subagent '${event.label ?: event.subagentId}' " +
                                        "exposed (agentId=${event.agentId}, " +
                                        "sessionId=${event.sessionId})"
                            )
                        )
                    )
                )
            }

            is AllToolsDeniedEvent -> {
                listOf(
                    Event.SessionUpdateEvent(
                        SessionUpdate.AgentMessageChunk(
                            content = ContentBlock.Text("All tool calls were denied by permission rules")
                        )
                    )
                )
            }

            is HintBlockEvent -> {
                listOf(
                    Event.SessionUpdateEvent(
                        SessionUpdate.AgentMessageChunk(
                            content = ContentBlock.Text(event.hint ?: "")
                        )
                    )
                )
            }

            is RequestStopEvent -> {
                listOf(
                    Event.SessionUpdateEvent(
                        SessionUpdate.AgentThoughtChunk(
                            content = ContentBlock.Text(
                                "Agent paused: ${event.reason ?: "stop requested"}"
                            )
                        )
                    )
                )
            }

            else -> {
                log.debug { "Unmapped AgentEvent type: ${event.type}" }
                emptyList()
            }
        }
    }

    /**
     * Checks whether an [AgentEvent] indicates that HITL permission is needed.
     * The caller should pause consumption and invoke [requestPermissions].
     */
    fun isPermissionRequired(event: AgentEvent): Boolean {
        return event is RequireUserConfirmEvent || (
                event is AgentResultEvent &&
                        event.result?.generateReason == GenerateReason.PERMISSION_ASKING
                )
    }

    /**
     * Checks whether an [AgentEvent] signals the end of the agent stream.
     */
    fun isStreamEnd(event: AgentEvent): Boolean {
        return event is AgentEndEvent ||
                event is ExceedMaxItersEvent ||
                (event is AgentResultEvent &&
                        event.result?.generateReason != GenerateReason.PERMISSION_ASKING)
    }

    /**
     * Extracts tool calls from a [RequireUserConfirmEvent] for permission request.
     */
    fun extractToolCalls(event: RequireUserConfirmEvent): List<ACPUserConfirmTool> {
        return event.toolCalls?.map { toolUse ->
            ACPUserConfirmTool(
                toolCallId = toolUse.id ?: "",
                toolName = toolUse.name ?: "unknown",
                arguments = toolUse.input.toString() ?: "{}"
            )
        } ?: emptyList()
    }
}

/**
 * Lightweight data class for permission requests.
 */
data class ACPUserConfirmTool(
    val toolCallId: String,
    val toolName: String,
    val arguments: String
)
