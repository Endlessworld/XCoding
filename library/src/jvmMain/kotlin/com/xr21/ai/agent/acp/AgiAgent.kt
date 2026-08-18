/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:OptIn(UnstableApi::class)

package com.xr21.ai.agent.acp

import com.agentclientprotocol.agent.*
import com.agentclientprotocol.annotations.UnstableApi
import com.agentclientprotocol.client.ClientInfo
import com.agentclientprotocol.common.ClientSessionOperations
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.common.SessionCreationParameters
import com.agentclientprotocol.model.*
import com.alibaba.cloud.ai.graph.RunnableConfig
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata
import com.alibaba.cloud.ai.graph.agent.Agent
import com.xr21.ai.agent.acp.SessionConfigOptionsFactory.AgentMode
import com.xr21.ai.agent.agent.LocalAgent
import com.xr21.ai.agent.agent.LocalAgent.FILE_SYSTEM_SAVER_FOLDER
import com.xr21.ai.agent.bridge.BridgeKt
import com.xr21.ai.agent.config.AiModels
import com.xr21.ai.agent.config.ModelConfigLoader
import com.xr21.ai.agent.entity.AgentOutput
import com.xr21.ai.agent.entity.CancellableRequest
import com.xr21.ai.agent.tools.ToolKindFind
import com.xr21.ai.agent.utils.Json
import com.xr21.ai.agent.utils.PermissionSettings
import com.xr21.ai.agent.utils.SinksUtil
import com.xr21.ai.agent.utils.ToolsUtil
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.apache.commons.collections4.CollectionUtils
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.ToolResponseMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.metadata.EmptyUsage
import reactor.core.publisher.Flux
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference


private val logger = KotlinLogging.logger {}

/** 未在模型配置中声明 contextWindow 时的默认上下文窗口大小（token） */
private const val DEFAULT_CONTEXT_WINDOW = 128L * 1024

/** Session-level MCP server cache */
private val sessionMcpServers = ConcurrentHashMap<String, List<McpServer>>()
private val activeRequests = ConcurrentHashMap<String, CancellableRequest>()

/**
 * Context key for storing [ClientSessionOperations] in [RunnableConfig.context].
 * Tools running on non-coroutine threads can use this to send ACP notifications
 * back to the client via [CoroutineContext.client].
 */
const val CLIENT_SESSION_CONTEXT_KEY = "AcpClientSession"
const val SESSION_ID_CONTEXT_KEY = "sessionId"

/**
 * ACP Agent session backed by LocalAgent (ReactAgent).
 * Delegates prompt processing to the LocalAgent's agent pipeline
 * with streaming support via ACP protocol events.
 */
class AgiAgentSession(
    override val sessionId: SessionId,
    private var cwd: String,
    private val mcpServers: List<McpServer>?,
    private var runnableConfig: RunnableConfig,
    private val clientInfo: ClientInfo? = null
) : AgentSession {
    private val tokenUsageRef = AtomicReference(Usage(0, 0, 0, 0, 0, 0))
    private val sessionTokenUsageRef = AtomicReference(Usage(0, 0, 0, 0, 0, 0))

    private val startTime = AtomicLong(0L)
    override val configOptions: List<SessionConfigOption>
        get() = SessionConfigOptionsFactory.create(clientInfo)

    override val availableModes: List<SessionMode>
        get() = AgentMode.entries.map { it.toSessionMode() }

    override val defaultMode: SessionModeId
        get() = AgentMode.YOLO.toSessionMode().id

    override suspend fun postInitialize() {
        val clientOps = currentCoroutineContext().client
        clientOps.notify(
            notification = SessionUpdate.AvailableCommandsUpdate(
                availableCommands = listOf(
                    AvailableCommand("ls-model", "列出模型"),
                    AvailableCommand("init", "初始化AGENT.md文件"),
                )
            )
        )
    }


    override val availableModels: List<ModelInfo>
        get() = AiModels.availableModels().map { model ->
            ModelInfo(ModelId(model.modelId ?: ""), model.modelName ?: "", model.modelName ?: "", null)
        }

    override val defaultModel: ModelId
        get() = availableModels.get(0).modelId

    override suspend fun setConfigOption(
        configId: SessionConfigId, value: SessionConfigOptionValue, _meta: JsonElement?
    ): SetSessionConfigOptionResponse {
        if (value is SessionConfigOptionValue.BoolValue) {
            runnableConfig.context().put(configId.value, value.value)
        }
        if (value is SessionConfigOptionValue.StringValue) {
            runnableConfig.context().put(configId.value, value.value)
        }
        val context = runnableConfig.context();
        logger.info { "set config option ${configId.value} to $value  $context" }
        return SetSessionConfigOptionResponse(configOptions = configOptions)
    }

    override suspend fun setMode(modeId: SessionModeId, _meta: JsonElement?): SetSessionModeResponse {
        logger.info { "[AcpAgent] setMode ${modeId.value}" }
        runnableConfig.context().put("mode", modeId.value)
        return SetSessionModeResponse()
    }

    override suspend fun setModel(modelId: ModelId, _meta: JsonElement?): SetSessionModelResponse {
        logger.info { "[AcpAgent] setModel {} $modelId" }
        runnableConfig.context().put("model", modelId.value)
        return SetSessionModelResponse()
    }

    override suspend fun prompt(
        content: List<ContentBlock>,
        _meta: JsonElement?,
    ): Flow<Event> = flow {
        logger.info { "Processing prompt for session $sessionId" }
//        val authorized = AuthFlow.ensureAuthorized(currentCoroutineContext().client)
//        if (!authorized) {
        logger.warn { "Auth: 用户未授权，会话仍可继续但可能受限" }
//            throw JsonRpcException(JsonRpcErrorCode.AUTH_REQUIRED.code, "请授权登录后使用")
//        }
        // Reset per-turn token usage at the start of each prompt
        tokenUsageRef.set(Usage(0, 0, 0, 0, 0, 0))
        startTime.set(System.currentTimeMillis())
        val requestId = "request_${System.currentTimeMillis()}_$sessionId"
        try {
            val messages = content.toMutableList()
            // 检查第一个 ContentBlock 是否为命令（Text 类型且以 / 开头）
            if (content.isNotEmpty() && content.first() is ContentBlock.Text) {
                val firstText = (content.first() as ContentBlock.Text).text
                if (firstText.startsWith("/")) {
                    logger.info { "command: $firstText" }
                    if (firstText == "/ls-model ") {
                        emit(
                            Event.SessionUpdateEvent(
                                SessionUpdate.AgentMessageChunk(
                                    ContentBlock.Text(
                                        buildString {
                                            appendLine("| Model ID | Name |")
                                            appendLine("|---------|------|")
                                            AiModels.availableModels().forEach { model ->
                                                appendLine("| ${model.modelId ?: ""} | ${model.modelName ?: ""} |")
                                            }
                                        }.trimEnd()
                                    )
                                )
                            )
                        )
                        emit(Event.PromptResponseEvent(PromptResponse(StopReason.END_TURN)))
                        return@flow
                    }
                    if (firstText == "/exit ") {
                        logger.info { "Exiting JVM by user command" }
                        emit(
                            Event.SessionUpdateEvent(
                                SessionUpdate.AgentMessageChunk(
                                    ContentBlock.Text("正在退出...")
                                )
                            )
                        )
                        emit(Event.PromptResponseEvent(PromptResponse(StopReason.END_TURN)))
                        // 延迟一小段让事件发送完成后再正常退出 JVM
                        kotlinx.coroutines.delay(200)
                        System.exit(0)
                        return@flow
                    }
                    if (firstText == "/init ") {
                        messages[0] = ContentBlock.Text("扫描当前项目初始化AGENT.md文件")
                    }
                }
            }
            if (_meta is JsonObject) {
                val cwdValue = _meta["cwd"]
                if (cwdValue is JsonPrimitive && cwdValue.isString && cwdValue.content.isNotBlank()) {
                    cwd = cwdValue.content
                }
            }
            val userMessage = UserMessageBuilder.buildUserMessage(messages)
            emit(
                Event.SessionUpdateEvent(
                    SessionUpdate.AgentThoughtChunk(
                        ContentBlock.Text("✨✨✨")
                    )
                )
            )
            emit(
                Event.SessionUpdateEvent(
                    SessionUpdate.UsageUpdate(contextUsedTokens(), contextWindowSize(), Cost(calculateCost(tokenUsageRef.get()), "CNY"))
                )
            )
            val executionThread = Thread.currentThread()
            runnableConfig.context().put("requestId", requestId)
            runnableConfig.context().put(SESSION_ID_CONTEXT_KEY, sessionId)
            runnableConfig.context().put("executionThread", executionThread)
            runnableConfig.context().putIfAbsent("totalTokens", 0)
            runnableConfig.context().putIfAbsent("completionTokens", 0)
            runnableConfig.context().putIfAbsent("isFirst", AtomicBoolean(true))
            runnableConfig.context().putIfAbsent("isFirstMessage", AtomicBoolean(true))
            // Store client session operations in context so tools running on non-coroutine threads can use it
            runnableConfig.context().putIfAbsent(CLIENT_SESSION_CONTEXT_KEY, currentCoroutineContext().client)
            runnableConfig.context().putIfAbsent("mode", defaultMode.value)
            runnableConfig.context().putIfAbsent("thought_level", SessionConfigOptionsFactory.ThoughtLevel.LOW.valueId)
            val agent = LocalAgent.createAgent(cwd, mcpServers, runnableConfig,currentCoroutineContext().client)
            agent.setSystemPrompt(LocalAgent.getInstruction(cwd))
            val recursiveFlux = recursiveAgentFlux(agent, userMessage)
            val channel = Channel<AgentOutput<Any>>(Channel.UNLIMITED)
            // 直接将 Flux 消费为 Channel，无需中间 Sinks.Many 中转
            val disposable = SinksUtil.fluxToChannel(recursiveFlux, channel)
            // Register cancellable request so cancel() can find and cancel it
            val cancellableRequest = CancellableRequest(
                requestId, sessionId.value, executionThread, disposable, channel
            )
            activeRequests[requestId] = cancellableRequest
            try {
                while (!disposable.isDisposed && currentCoroutineContext().isActive && !cancellableRequest.cancelled) {
                    val result = channel.receiveCatching()
                    if (result.isClosed) {
                        result.exceptionOrNull()?.let { throw it }
                        break
                    }
                    val output = result.getOrNull() ?: break
                    if (cancellableRequest.cancelled || !currentCoroutineContext().isActive) break
                    emitOutput(output)
                }
            } finally {
                disposable.dispose()
                channel.close()
            }
            runnableConfig.context()["sessionTotalTokens"] = sessionTokenUsageRef.get().totalTokens
            runnableConfig.context().put("sessionCompletionTokens", sessionTokenUsageRef.get().outputTokens)
            val latency = System.currentTimeMillis() - startTime.get()
            val duration = latency / 1000.0
            val tokens = sessionTokenUsageRef.get().totalTokens
            val speed = if (duration > 0) String.format("%.2f", tokenUsageRef.get().outputTokens / duration) else "0.00"
            val chunk =
                "Token usage: sessionTotal=${tokens} ,total=${tokenUsageRef.get().totalTokens},outputTokens=${tokenUsageRef.get().outputTokens}, duration=${duration}s, speed=${speed} tokens/s"
            logger.info { chunk }
            emit(
                Event.SessionUpdateEvent(
                    SessionUpdate.AgentThoughtChunk(ContentBlock.Text(chunk))
                )
            )
            emit(
                Event.SessionUpdateEvent(
                    SessionUpdate.UsageUpdate(contextUsedTokens(), contextWindowSize(), Cost(calculateCost(tokenUsageRef.get()), "CNY"))
                )
            )
            logger.info { "events END_TURN" }
            val sessionUsage = sessionTokenUsageRef.get()
            val finalUsage = Usage(
                tokenUsageRef.get().inputTokens,
                tokenUsageRef.get().outputTokens,
                tokenUsageRef.get().totalTokens,
                tokenUsageRef.get().thoughtTokens,
                tokenUsageRef.get().cachedReadTokens,
                tokenUsageRef.get().cachedWriteTokens, JsonObject(
                    mapOf(
                        "sessionTotal" to JsonPrimitive(sessionUsage.totalTokens),
                        "completionTokens" to JsonPrimitive(sessionUsage.outputTokens),
                        "duration" to JsonPrimitive(duration),
                        "speed" to JsonPrimitive(speed)
                    )
                )
            )
            emit(Event.PromptResponseEvent(PromptResponse(StopReason.END_TURN, null, finalUsage)))
        } catch (e: Exception) {
            logger.error(e) { "Error processing prompt" }
            emit(
                Event.SessionUpdateEvent(
                    SessionUpdate.AgentMessageChunk(ContentBlock.Text("\nError: ${e.message}"))
                )
            )
            emit(Event.PromptResponseEvent(PromptResponse(StopReason.REFUSAL)))
        } finally {
            activeRequests.remove(requestId)
        }
    }


    override suspend fun close(_meta: JsonElement?): CloseSessionResponse {
        logger.info { "Closing session: $sessionId" }
        cancel()
        sessionMcpServers.remove(sessionId.toString())
        return CloseSessionResponse()
    }

    override suspend fun cancel() {
        logger.info { "Cancellation requested for session: $sessionId" }
        val requestIdsToCancel = activeRequests.filterValues { request ->
            request.sessionId == sessionId.value
        }.keys.toList()
        for (requestId in requestIdsToCancel) {
            val request = activeRequests[requestId]
            if (request != null) {
                // dispose 订阅会取消 recursiveFlux，同时 fluxToChannel 的 complete 回调会关闭 Channel
                // 从而终止 prompt() 中的 channel.receiveCatching() 阻塞等待
                request.cancel()
                logger.info { "Cancelled  request : ${request.sessionId}" }
                activeRequests.remove(requestId)
            }
        }
        logger.info { "Cancelled ${requestIdsToCancel.size} active request(s) for session: $sessionId" }
    }

    /**
     * Create a recursive agent Flux that handles human-in-the-loop interruptions.
     * Processing of outputs is handled by the caller (prompt method) via emitOutput.
     */
    private fun recursiveAgentFlux(
        agent: Agent, userMessage: UserMessage
    ): Flux<AgentOutput<Any>> {
        logger.info { "runnableConfig $runnableConfig" }
        // 清除上一次 HITL 审批残留的 HUMAN_FEEDBACK metadata，
        // 否则 GraphRunnerContext 会误判为 Resume 请求，直接跳到 __END__
        runnableConfig.metadata().ifPresent { it.remove(RunnableConfig.HUMAN_FEEDBACK_METADATA_KEY) }
        val initialFlux = SinksUtil.agentToFlux(agent, userMessage, runnableConfig)
        return initialFlux.expand { output ->
//            logger.info { "output" + Json.toJson(output) }
            if (output.interruptionMetadata != null) {
                logger.info { "Detected human intervention, requesting permission..." }
                val approvalMetadata = processHumanIntervention(output.interruptionMetadata)
                val toolFeedbacks = approvalMetadata.toolFeedbacks()
                logger.info { "Resuming agent flow with approval metadata $toolFeedbacks" }
                runnableConfig = RunnableConfig.builder(runnableConfig)
                    .addMetadata(RunnableConfig.HUMAN_FEEDBACK_METADATA_KEY, approvalMetadata)
                    .build();
                val allRejected = approvalMetadata.toolFeedbacks()
                    .all { it.result == InterruptionMetadata.ToolFeedback.FeedbackResult.REJECTED }
                if (allRejected) {
                    logger.info { "All tool calls were rejected, ending flow" }
                    Flux.empty()
                } else {
                    SinksUtil.agentToFlux(agent, userMessage, runnableConfig)
                }
            } else {
                Flux.empty()
            }
        }
    }

    /**
     * Process human intervention for tool call permissions.
     * Checks persisted permissions first, then requests user decision via ACP ClientSessionOperations.
     */
    fun processHumanIntervention(
        interruptionMetadata: InterruptionMetadata
    ): InterruptionMetadata {
        PermissionSettings.load(cwd)
        val feedbackBuilder = InterruptionMetadata.builder()
            .nodeId(interruptionMetadata.node())
            .state(interruptionMetadata.state())
        for (toolFeedback in interruptionMetadata.toolFeedbacks()) {
            val approvedFeedbackBuilder = InterruptionMetadata.ToolFeedback.builder(toolFeedback)
            val toolName = toolFeedback.name
            val toolArgs = toolFeedback.arguments
            val toolId = toolFeedback.id
            val toolPattern = buildToolPattern(toolName, toolArgs)
            val persistedAction = PermissionSettings.checkPermission(toolName, toolArgs)
            if (persistedAction != null) {
                val result = when (persistedAction) {
                    PermissionSettings.PermissionAction.ALLOW -> {
                        logger.info { "Auto-approved (persisted) for tool: $toolName" }
                        InterruptionMetadata.ToolFeedback.FeedbackResult.APPROVED
                    }

                    PermissionSettings.PermissionAction.REJECT -> {
                        logger.info { "Auto-rejected (persisted) for tool: $toolName" }
                        InterruptionMetadata.ToolFeedback.FeedbackResult.REJECTED
                    }
                }
                feedbackBuilder.addToolFeedback(approvedFeedbackBuilder.result(result).build())
                continue
            }

            val clientOps = runnableConfig.context()[CLIENT_SESSION_CONTEXT_KEY] as? ClientSessionOperations
            if (clientOps == null) {
                logger.warn { "No ClientSessionOperations available, auto-approving: $toolName" }
                feedbackBuilder.addToolFeedback(
                    approvedFeedbackBuilder.result(InterruptionMetadata.ToolFeedback.FeedbackResult.APPROVED).build()
                )
                continue
            }

            val toolCallUpdate = SessionUpdate.ToolCallUpdate(
                toolCallId = ToolCallId(toolId ?: ""),
                title = toolName,
                kind = ToolKindFind.find(toolName),
                status = ToolCallStatus.PENDING,
                content = BridgeKt.build(toolName ?: "", toolArgs)
            )

            val permissionOptions = PermissionOptionKind.values().map { kind ->
                PermissionOption(PermissionOptionId(kind.name), kind.name, kind)
            }

            val permissionResponse = runBlocking {
                clientOps.requestPermissions(toolCallUpdate, permissionOptions, null)
            }

            val result = when (val outcome = permissionResponse.outcome) {
                is RequestPermissionOutcome.Cancelled -> {
                    logger.info { "Permission cancelled for tool: $toolName" }
                    InterruptionMetadata.ToolFeedback.FeedbackResult.REJECTED
                }

                is RequestPermissionOutcome.Selected -> {
                    val optionKind = PermissionOptionKind.valueOf(outcome.optionId.value)
                    when (optionKind) {
                        PermissionOptionKind.ALLOW_ONCE -> {
                            logger.info { "Permission granted (once) for tool: $toolName" }
                            InterruptionMetadata.ToolFeedback.FeedbackResult.APPROVED
                        }

                        PermissionOptionKind.ALLOW_ALWAYS -> {
                            logger.info { "Permission granted (always) for tool: $toolName" }
                            PermissionSettings.addAllowPermission(cwd, toolPattern)
                            InterruptionMetadata.ToolFeedback.FeedbackResult.APPROVED
                        }

                        PermissionOptionKind.REJECT_ONCE -> {
                            logger.info { "Permission rejected (once) for tool: $toolName" }
                            InterruptionMetadata.ToolFeedback.FeedbackResult.REJECTED
                        }

                        PermissionOptionKind.REJECT_ALWAYS -> {
                            logger.info { "Permission rejected (always) for tool: $toolName" }
                            PermissionSettings.addRejectPermission(cwd, toolPattern)
                            InterruptionMetadata.ToolFeedback.FeedbackResult.REJECTED
                        }
                    }
                }
            }

            feedbackBuilder.addToolFeedback(approvedFeedbackBuilder.result(result).build())
        }

        return feedbackBuilder.build()
    }

    /**
     * Build a tool pattern string for permission persistence.
     * Format: "ToolName(arguments)" or just "ToolName" if no arguments.
     */
    fun buildToolPattern(toolName: String?, arguments: String?): String {
        if (toolName == null) return ""
        return if (arguments.isNullOrEmpty()) toolName else "$toolName($arguments)"
    }

    /**
     * 当前上下文窗口已使用的 token 数（usage_update.used）。
     * 以最近一次模型调用送入的 prompt tokens（真实上下文占用）为准；
     * 尚未发生模型调用时回退到会话累计 inputTokens（不超过窗口上限）。
     */
    private fun contextUsedTokens(): Long {
        val last = runnableConfig.context()["lastInputTokens"]
        if (last is Number && last.toLong() > 0) return last.toLong()
        return sessionTokenUsageRef.get().inputTokens.coerceAtMost(contextWindowSize())
    }

    /**
     * 模型上下文窗口总大小（token，usage_update.size）。
     * 优先取当前模型的 contextWindow 配置，未配置时使用默认值。
     */
    private fun contextWindowSize(): Long {
        val modelId = runnableConfig.context()["model"] as? String
        val configs = ModelConfigLoader.loadConfigs()
        val config = modelId?.let { ModelConfigLoader.findConfigByModelName(it, configs) }
            ?: ModelConfigLoader.getDefaultConfig(configs)
        return config?.contextWindow ?: DEFAULT_CONTEXT_WINDOW
    }

    /**
     * 按档位A价格计算费用（元/百万 tokens）：输入-缓存命中 0.02、输入-缓存未命中 1、输出 2。
     * 缓存命中输入取 cachedReadTokens，未命中输入 = inputTokens - cachedReadTokens。
     */
    private fun calculateCost(usage: Usage): Double {
        val cachedInput = (usage.cachedReadTokens ?: 0L).coerceAtLeast(0L)
        val inputTokens = usage.inputTokens ?: 0L
        val outputTokens = usage.outputTokens ?: 0L
        val freshInput = (inputTokens - cachedInput).coerceAtLeast(0L)
        return cachedInput / 1_000_000.0 * 0.02 +
                freshInput / 1_000_000.0 * 1 +
                outputTokens / 1_000_000.0 * 2
    }

    /**
     * Emit ACP events for a single AgentOutput.
     * Called from coroutine flow body, safe to use emit().
     * Unified output processing - replaces old processAgentOutput (SyncPromptContext mode).
     */
    private suspend fun FlowCollector<Event>.emitOutput(output: AgentOutput<Any>) {
        // Text chunks -> AgentMessageChunk events
        if (output.tokenUsage !is EmptyUsage &&  output.tokenUsage != null && output.tokenUsage.totalTokens != null) {
            // Publish the live input-token count of the latest model call so the
            // SummarizationHook can decide compaction from real provider-reported usage
            // instead of a rough character estimate.
            runnableConfig.context().put("lastInputTokens", output.tokenUsage.promptTokens ?: 0)
            val prevTurn = tokenUsageRef.get()
            tokenUsageRef.set(
                Usage(
                    inputTokens = prevTurn.inputTokens + (output.tokenUsage.promptTokens ?: 0).toLong(),
                    outputTokens = prevTurn.outputTokens + (output.tokenUsage.completionTokens ?: 0).toLong(),
                    totalTokens = prevTurn.totalTokens + (output.tokenUsage.totalTokens ?: 0).toLong(),
                    thoughtTokens = prevTurn.thoughtTokens,
                    cachedReadTokens = prevTurn.cachedReadTokens,
                    cachedWriteTokens = prevTurn.cachedWriteTokens
                )
            )
            val prev = sessionTokenUsageRef.get()
            sessionTokenUsageRef.set(
                Usage(
                    inputTokens = prev.inputTokens + (output.tokenUsage.promptTokens ?: 0).toLong(),
                    outputTokens = prev.outputTokens + (output.tokenUsage.completionTokens ?: 0).toLong(),
                    totalTokens = prev.totalTokens + (output.tokenUsage.totalTokens ?: 0).toLong(),
                    thoughtTokens = prev.thoughtTokens,
                    cachedReadTokens = prev.cachedReadTokens,
                    cachedWriteTokens = prev.cachedWriteTokens
                )
            )
        }
        if (output.chunk != null) {
            emit(
                Event.SessionUpdateEvent(
                    SessionUpdate.AgentMessageChunk(ContentBlock.Text(output.chunk))
                )
            )
        }

        // Thinking/reasoning content
        if (output.think != null) {
            emit(
                Event.SessionUpdateEvent(
                    SessionUpdate.AgentThoughtChunk(ContentBlock.Text(output.think))
                )
            )
        }

        // Tool call requests (AssistantMessage with ToolCalls)
        // Per ACP protocol: emit ToolCall ("tool_call") on first creation,
        // then ToolCallUpdate ("tool_call_update") for status updates.
        if (output.message is AssistantMessage) {
            val message = output.message
            if (CollectionUtils.isNotEmpty(message.toolCalls)) {
                message.toolCalls.forEach { toolCall ->
                    val content = BridgeKt.build(toolCall.name(), toolCall.arguments())
                    logger.info { "output.toolCalls $content" }
                    emit(
                        Event.SessionUpdateEvent(
                            SessionUpdate.ToolCall(
                                toolCallId = ToolCallId(toolCall.id()),
                                title = getTitle(toolCall),
                                kind = ToolKindFind.find(toolCall.name()),
                                status = ToolCallStatus.IN_PROGRESS,
                                content = content,
                                rawInput = runCatching {
                                    kotlinx.serialization.json.Json.parseToJsonElement(toolCall.arguments())
                                }.getOrNull() as? JsonObject,
                            )
                        )
                    )
                }
                emit(
                    Event.SessionUpdateEvent(
                        SessionUpdate.UsageUpdate(contextUsedTokens(), contextWindowSize(), Cost(calculateCost(tokenUsageRef.get()), "CNY"))
                    )
                )
            }
        }
        if (output.message is ToolResponseMessage) {
            val message = output.message
            if (CollectionUtils.isNotEmpty(message.responses)) {
                message.responses.forEach { response ->
                    val resultData = ToolsUtil.parseToolResult(response.responseData())
                    logger.debug { "output.responses $resultData" }
                    emit(
                        Event.SessionUpdateEvent(
                            SessionUpdate.ToolCallUpdate(
                                toolCallId = ToolCallId(response.id()),
                                kind = ToolKindFind.find(response.name()),
                                status = if (resultData.success) ToolCallStatus.COMPLETED
                                else ToolCallStatus.FAILED,
                                content = resultData.toolCallContents,
                                locations = resultData.locations,
                                rawOutput = runCatching {
                                    kotlinx.serialization.json.Json.parseToJsonElement(response.responseData())
                                }.getOrNull()
                            )
                        )
                    )
                }
            }
        }


    }

    private fun getTitle(toolCall: AssistantMessage.ToolCall): String {
        val args = runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(toolCall.arguments())
        }.getOrNull() as? JsonObject

        // 无法解析为 JSON 时，直接返回原始 arguments
        if (args == null) return toolCall.name

        val title = args.string("title").ifBlank { toolCall.name() }
        val command = args.string("command")
        val script = args.string("script")
        val input = args.string("input")
        val description = args.string("description")

        val suffix = listOf(command, script,description).filter { it.isNotBlank() }.joinToString("")

        // arguments 中不包含任何特定字段时，返回 arguments 本身
        if (suffix.isBlank() && input.isBlank()) return toolCall.name

        return if (suffix.isNotBlank()) "$title $suffix" else input
    }

    private fun JsonObject.string(key: String): String =
        (this[key] as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.content
            ?.takeIf { it.isNotBlank() }
            .orEmpty()


}

/**
 * AgiAgent - ACP Agent Support implementation backed by LocalAgent.
 *
 * Session lifecycle:
 * 1. initialize() - Reports agent capabilities
 * 2. createSession() - Creates session with RunnableConfig and MCP server config
 * 3. session.prompt() - Streams agent output via ACP events (text chunks, tool calls)
 * 4. session.cancel() - Cancels active requests
 */
class AgiAgent : AgentSupport {

    private val sessions = ConcurrentHashMap<String, AgiAgentSession>()

    /** 最近一次 initialize 的 clientInfo，用于创建 session 时传递 */
    private var lastClientInfo: ClientInfo? = null

    override suspend fun initialize(clientInfo: ClientInfo): AgentInfo {
        logger.info { "Initializing agent with protocol version ${Json.toJson(clientInfo)}" }
        lastClientInfo = clientInfo
        return AgentInfo(
            protocolVersion = LATEST_PROTOCOL_VERSION,
            capabilities = AgentCapabilities(
                loadSession = true,
                promptCapabilities = PromptCapabilities(
                    audio = false, image = false, embeddedContext = true
                ),
                mcpCapabilities = McpCapabilities(http = true, sse = true),
                sessionCapabilities = SessionCapabilities(
                    fork = SessionForkCapabilities(),
                    list = SessionListCapabilities(),
                    resume = SessionResumeCapabilities(),
                    close = SessionCloseCapabilities(),
                    additionalDirectories = null
                ),
                auth = AgentAuthCapabilities(logout = LogoutCapabilities()),
                positionEncoding = PositionEncodingKind.UTF_8,
                providers = ProvidersCapabilities()
            ),
            authMethods = listOf(AuthMethod.AgentAuth(AuthMethodId("login"), "login", "登录鉴权")),
            implementation = Implementation("XCoding", "1.0.0", "agi coding")
        )
    }

    override suspend fun listSessions(
        cwd: String?, additionalDirectories: List<String>?, _meta: JsonElement?
    ): Sequence<SessionInfo> {
        if (!Files.isDirectory(FILE_SYSTEM_SAVER_FOLDER)) return emptySequence()
        val stream = Files.list(FILE_SYSTEM_SAVER_FOLDER)
        try {
            return stream.map { it.fileName.toString() }
                .filter { it.startsWith("thread-") && it.endsWith(".saver") }
                .map { it.removePrefix("thread-").removeSuffix(".saver") }
                .map { sessionIdStr ->
                    SessionInfo(
                        SessionId(sessionIdStr),
                        cwd = cwd ?: ""
                    )
                }
                .toList()
                .asSequence()
        } finally {
            stream.close()
        }
    }

    override suspend fun createSession(sessionParameters: SessionCreationParameters): AgentSession {
        logger.info { "createSession ${Json.toJson(sessionParameters)}" }
        val sessionIdStr = "session-${System.currentTimeMillis()}"
        val sessionId = SessionId(sessionIdStr)
        val cwd = sessionParameters.cwd ?: System.getProperty("user.dir")
        val mcpServers = sessionParameters.mcpServers
        val runnableConfig =
            RunnableConfig.builder().threadId(sessionIdStr).addStateUpdate(emptyMap<String, Any>()).build()
        if (CollectionUtils.isNotEmpty(mcpServers)) {
            sessionMcpServers[sessionIdStr] = mcpServers
            logger.info { "Received ${mcpServers.size} MCP server(s) for session $sessionIdStr" }
        }
        val session = AgiAgentSession(sessionId, cwd, mcpServers, runnableConfig, lastClientInfo)
        sessions[sessionIdStr] = session
        logger.info { "Created session $sessionIdStr with cwd: $cwd" }
        return session
    }

    override suspend fun loadSession(
        sessionId: SessionId,
        sessionParameters: SessionCreationParameters,
    ): AgentSession {
        val existing = sessions[sessionId.toString()]
        if (existing != null) {
            logger.info { "Loaded existing session: $sessionId" }
            return existing
        }
        val cwd = sessionParameters.cwd
        val sessionIdStr = sessionId.value
        val runnableConfig = RunnableConfig.builder().threadId(sessionIdStr)
            .addStateUpdate(emptyMap<String, Any>()).build()
        val session = AgiAgentSession(sessionId, cwd, sessionParameters.mcpServers, runnableConfig, lastClientInfo)
        sessions[sessionIdStr] = session
        return session
    }

    override suspend fun authenticate(methodId: AuthMethodId, _meta: JsonElement?): AuthenticateResponse {
        logger.info { "Authenticate requested with method: $methodId" }
        return AuthenticateResponse()
    }

    override suspend fun logout(_meta: JsonElement?): LogoutResponse {
        logger.info { "Logout requested" }
        return LogoutResponse()
    }

    override suspend fun listProviders(_meta: JsonElement?): ListProvidersResponse {
        logger.info { "List providers requested" }
        val providers = ProviderConfigManager.listProviders()
        logger.info { "Returning ${providers.size} providers" }
        return ListProvidersResponse(providers = providers)
    }

    override suspend fun setProvider(
        id: String, apiType: LlmProtocol, baseUrl: String, headers: Map<String, String>?, _meta: JsonElement?
    ): SetProvidersResponse {
        logger.info { "Set provider: $id, type: $apiType, baseUrl: $baseUrl" }
        ProviderConfigManager.setProvider(id, baseUrl, headers)
        return SetProvidersResponse()
    }

    override suspend fun disableProvider(id: String, _meta: JsonElement?): DisableProvidersResponse {
        logger.info { "Disable provider: $id" }
        ProviderConfigManager.disableProvider(id)
        return DisableProvidersResponse()
    }

    override suspend fun forkSession(
        sessionId: SessionId,
        sessionParameters: SessionCreationParameters,
    ): AgentSession {
        logger.info { "Fork session: $sessionId" }
        val cwd = sessionParameters.cwd
        val sessionIdStr = "session-${System.currentTimeMillis()}"
        val newSessionId = SessionId(sessionIdStr)
        val runnableConfig =
            RunnableConfig.builder().threadId(sessionIdStr).addStateUpdate(emptyMap<String, Any>()).build()
        val session = AgiAgentSession(newSessionId, cwd, sessionParameters.mcpServers, runnableConfig, lastClientInfo)
        sessions[sessionIdStr] = session
        return session
    }

    override suspend fun resumeSession(
        sessionId: SessionId,
        sessionParameters: SessionCreationParameters,
    ): AgentSession {
        logger.info { "Resume session: $sessionId" }
        val existing = sessions[sessionId.toString()]
        if (existing != null) {
            return existing
        }
        val cwd = sessionParameters.cwd ?: System.getProperty("user.dir")
        val sessionIdStr = sessionId.toString()
        val runnableConfig = RunnableConfig.builder().threadId(sessionIdStr)
            .addStateUpdate(emptyMap<String, Any>()).build()
        val session = AgiAgentSession(sessionId, cwd, sessionParameters.mcpServers, runnableConfig, lastClientInfo)
        sessions[sessionIdStr] = session
        return session
    }

    override suspend fun createNesSession(request: StartNesRequest): NesAgentSession {
        logger.info { "Create NES session requested: $request" }
        throw NotImplementedError("createNesSession is not implemented. The capability is declared in AgentCapabilities.nes")
    }
}

