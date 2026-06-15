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
import com.xr21.ai.agent.agent.LocalAgent
import com.xr21.ai.agent.config.AiModels
import com.xr21.ai.agent.config.ModelConfigLoader
import com.xr21.ai.agent.entity.AgentOutput
import com.xr21.ai.agent.entity.CancellableRequest
import com.xr21.ai.agent.model.Config
import com.xr21.ai.agent.tools.ToolKindFind
import com.xr21.ai.agent.utils.Json
import com.xr21.ai.agent.utils.SinksUtil
import com.xr21.ai.agent.utils.ToolsUtil
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.apache.commons.lang3.StringUtils
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.ToolResponseMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.content.Media
import org.springframework.util.CollectionUtils
import org.springframework.util.MimeType
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference


private val logger = KotlinLogging.logger {}

/** Session-level MCP server cache */
private val sessionMcpServers = ConcurrentHashMap<String, List<McpServer>>()
private val sessionsRunnableConfig = ConcurrentHashMap<String, RunnableConfig>()
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
    private val cwd: String,
    private val mcpServers: List<McpServer>?,
    private val runnableConfig: RunnableConfig
) : AgentSession {
    private val responseBuilder = StringBuilder()
    private val tokenUsageRef = AtomicReference(Usage(0,0,0,0,0,0))
    private val sessionTokenUsageRef = AtomicReference(Usage(0,0,0,0,0,0))

    private val startTime = AtomicLong(0L)

    override val configOptions: List<SessionConfigOption>
        get() = listOf(
            SessionConfigOption.boolean(
                id = "auto_approve",
                name = "Auto Approve",
                currentValue = true,
                description = "Automatically approve all tool calls"
            ), SessionConfigOption.select(
                id = "mode",
                name = "mode",
                currentValue = "Agent",
                description = "mode",
                options = SessionConfigSelectOptions.Flat(
                    listOf(
                        SessionConfigSelectOption(
                            SessionConfigValueId("Agent"), "Agent", "单智能体模式"
                        ), SessionConfigSelectOption(
                            SessionConfigValueId("Workers"), "Workers", "动态并行子代理"
                        )
                    )
                )
            ), SessionConfigOption.select(
                id = "model",
                name = "model",
                currentValue = AiModels.defaultModel(),
                description = "model",
                options = SessionConfigSelectOptions.Flat(AiModels.availableModels().map { model ->
                    SessionConfigSelectOption(
                        SessionConfigValueId(model.modelId ?: ""),
                        model.modelName ?: "",
                        model.modelName ?: "",
                        null
                    )
                })
            )
        )
    override val availableModes: List<SessionMode>
        get() = listOf(
            SessionMode(SessionModeId("Agent"), "Agent", "单智能体模式"),
            SessionMode(SessionModeId("Workers"), "Workers", "动态并行子代理")
        )

    override suspend fun postInitialize() {
        currentCoroutineContext().client.notify(
            notification = SessionUpdate.CurrentModeUpdate(
                currentModeId = SessionModeId("Agent"),
            )
        )
        currentCoroutineContext().client.notify(
            notification = SessionUpdate.AvailableCommandsUpdate(
                availableCommands = listOf(
                    AvailableCommand("ls-model", "列出模型"),
                    AvailableCommand("init", "初始化AGENT.md文件"),
                )
            )
        )
    }

    override val defaultMode: SessionModeId
        get() = SessionModeId("Agent")

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
            runnableConfig.context()[configId.value] = value.value
        }
        val context = runnableConfig.context();
        logger.info { "set config option ${configId.value} to $value  $context" }
        return SetSessionConfigOptionResponse(configOptions = configOptions)
    }

    override suspend fun setMode(modeId: SessionModeId, _meta: JsonElement?): SetSessionModeResponse {
        runnableConfig.context().put("mode", modeId.value)
        logger.info { "AcpAgent] setMode $SessionModeId" }
        return SetSessionModeResponse()
    }

    override suspend fun setModel(modelId: ModelId, _meta: JsonElement?): SetSessionModelResponse {
        runnableConfig.context().put("model", modelId.value)
        logger.info { "[AcpAgent] setModel {} $modelId" }
        return SetSessionModelResponse()
    }

    override suspend fun prompt(
        content: List<ContentBlock>,
        _meta: JsonElement?,
    ): Flow<Event> = flow {
        logger.info { "Processing prompt for session $sessionId" }

        val cancelledFlag = AtomicBoolean(false)
        runnableConfig.context().put("cancelled", cancelledFlag)
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
                    if (firstText == "/init ") {
                        messages[0] = ContentBlock.Text("扫描当前项目初始化AGENT.md文件")
                    }
                }
            }
            val userMessage = buildUserMessage(messages)
            emit(
                Event.SessionUpdateEvent(
                    SessionUpdate.AgentThoughtChunk(
                        ContentBlock.Text("✨Processing request✨ \r\n<br/>")
                    )
                )
            )

            val agent = LocalAgent.createAgent(cwd, mcpServers, runnableConfig)
            val requestId = "request_${System.currentTimeMillis()}_$sessionId"
            val executionThread = Thread.currentThread()
            runnableConfig.context().put("requestId", requestId)
            runnableConfig.context().put(SESSION_ID_CONTEXT_KEY, sessionId)
            runnableConfig.context().put("executionThread", executionThread)
            runnableConfig.context().putIfAbsent("totalTokens", 0)
            runnableConfig.context().putIfAbsent("completionTokens", 0)
            runnableConfig.context().putIfAbsent("responseBuilder", responseBuilder)
            runnableConfig.context().putIfAbsent("isFirst", AtomicBoolean(true))
            runnableConfig.context().putIfAbsent("isFirstMessage", AtomicBoolean(true))
            // Store client session operations in context so tools running on non-coroutine threads can use it
            runnableConfig.context().putIfAbsent(CLIENT_SESSION_CONTEXT_KEY, currentCoroutineContext().client)
            val recursiveFlux = createRecursiveAgentFlux(agent, userMessage)
            val agentSink = Sinks.many().unicast().onBackpressureBuffer<AgentOutput<Any>>()
            val disposable = recursiveFlux.subscribe({ output -> agentSink.tryEmitNext(output) }, { error ->
                logger.error(error) { "Error in agent flux" }
                agentSink.tryEmitError(error)
            }, {
                logger.info { "disposable dispose" }
                agentSink.tryEmitComplete()
            })

            // Register cancellable request so cancel() can find and cancel it
            val cancellableRequest = CancellableRequest(
                requestId,
                sessionId.value,
                executionThread,
                recursiveFlux
            )
            cancellableRequest.setFluxDisposable(disposable)
            // 将 agentSink 注册到 CancellableRequest，以便 cancel() 时发送 complete 信号
            // 从而立即终止 toIterable() 迭代器的阻塞等待
            cancellableRequest.setAgentSink(agentSink)
            activeRequests[requestId] = cancellableRequest

            val agentIterator = agentSink.asFlux().toIterable().iterator()
            while (agentIterator.hasNext()
                && !disposable.isDisposed
                && !cancellableRequest.cancelled
                && currentCoroutineContext().isActive
            ) {
                val output = agentIterator.next()
                if (cancellableRequest.cancelled || !currentCoroutineContext().isActive) break
                emitAgentOutputEvents(output)
            }
            activeRequests.remove(requestId)
            runnableConfig.context()["sessionTotalTokens"] = sessionTokenUsageRef.get().totalTokens
            runnableConfig.context().put("sessionCompletionTokens", sessionTokenUsageRef.get().outputTokens)
            val latency = System.currentTimeMillis() - startTime.get()
            val duration = latency / 1000.0
            val tokens = sessionTokenUsageRef.get().totalTokens
            val speed = if (duration > 0) String.format("%.2f", tokenUsageRef.get().outputTokens / duration) else "0.00"
            val chunk = "Token usage: sessionTotal=${tokens} ,total=${tokenUsageRef.get().totalTokens},outputTokens=${tokenUsageRef.get().outputTokens }, duration=${duration}s, speed=${speed} tokens/s"
            logger.info { chunk }
            emit(
                Event.SessionUpdateEvent(
                    SessionUpdate.AgentThoughtChunk(ContentBlock.Text(chunk))
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
                tokenUsageRef.get().cachedWriteTokens,
                JsonObject(mapOf(
                    "sessionTotal" to JsonPrimitive(sessionUsage.totalTokens),
                    "completionTokens" to JsonPrimitive(sessionUsage.outputTokens),
                    "duration" to JsonPrimitive(duration),
                    "speed" to JsonPrimitive(speed)
                ))
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
        }
    }


    override suspend fun close(_meta: JsonElement?): CloseSessionResponse {
        logger.info { "Closing session: $sessionId" }
        cancel()
        sessionsRunnableConfig.remove(sessionId.toString())
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
                // 先 dispose 订阅（会级联取消 recursiveFlux），再发送 complete 信号给 agentSink
                // 确保上游 Flux 先停止发射，再通知下游迭代器结束
                request.cancel()
                logger.info { "Cancelled  request : ${request.sessionId}" }
                activeRequests.remove(requestId)
            }
        }
        logger.info { "Cancelled ${requestIdsToCancel.size} active request(s) for session: $sessionId" }
    }

    /**
     * Create a recursive agent Flux that handles human-in-the-loop interruptions.
     * Processing of outputs is handled by the caller (prompt method) via emitAgentOutputEvents.
     */
    private fun createRecursiveAgentFlux(
        agent: Agent, userMessage: UserMessage
    ): Flux<AgentOutput<Any>> {
        startTime.set(System.currentTimeMillis())
        val initialFlux = SinksUtil.toFlux(agent, userMessage, runnableConfig)
        return initialFlux.expand { output ->
            if (output.interruptionMetadata != null) {
                logger.info { "Detected human intervention, auto-approving..." }
                val approvalMetadata = InterruptionMetadata.builder().nodeId(output.interruptionMetadata.node())
                    .state(output.interruptionMetadata.state()).apply {
                        output.interruptionMetadata.toolFeedbacks().forEach { fb ->
                            addToolFeedback(
                                InterruptionMetadata.ToolFeedback.builder(fb)
                                    .result(InterruptionMetadata.ToolFeedback.FeedbackResult.APPROVED).build()
                            )
                        }
                    }.build()
                runnableConfig.metadata().ifPresent { metadata ->
                    metadata[RunnableConfig.HUMAN_FEEDBACK_METADATA_KEY] = approvalMetadata
                }
                SinksUtil.toFlux(agent, userMessage, runnableConfig)
            } else {
                Flux.empty()
            }
        }
    }

    /**
     * Emit ACP events for a single AgentOutput.
     * Called from coroutine flow body, safe to use emit().
     * Unified output processing - replaces old processAgentOutput (SyncPromptContext mode).
     */
    private suspend fun FlowCollector<Event>.emitAgentOutputEvents(output: AgentOutput<Any>) {
        // Text chunks -> AgentMessageChunk events
        if (output.tokenUsage != null && output.tokenUsage.totalTokens != null) {
            tokenUsageRef.set(Usage(output.tokenUsage.promptTokens.toLong(), output.tokenUsage.completionTokens.toLong(), output.tokenUsage.totalTokens.toLong(),0,0,0))
            val prev = sessionTokenUsageRef.get()
            sessionTokenUsageRef.set(Usage(
                inputTokens = prev.inputTokens + (output.tokenUsage.promptTokens ?: 0).toLong(),
                outputTokens = prev.outputTokens + (output.tokenUsage.completionTokens ?: 0).toLong(),
                totalTokens = prev.totalTokens + (output.tokenUsage.totalTokens ?: 0).toLong(),
                thoughtTokens = prev.thoughtTokens,
                cachedReadTokens = prev.cachedReadTokens,
                cachedWriteTokens = prev.cachedWriteTokens
            ))
        }
        if (output.chunk != null) {
            responseBuilder.append(output.chunk)
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
        if (output.message is AssistantMessage) {
            val message = output.message as AssistantMessage
            if (!CollectionUtils.isEmpty(message.toolCalls)) {
                message.toolCalls.forEach { toolCall ->
                    emit(
                        Event.SessionUpdateEvent(
                            SessionUpdate.ToolCallUpdate(
                                toolCallId = ToolCallId(toolCall.id()),
                                title = toolCall.name(),
                                kind = ToolKindFind.find(toolCall.name()) as ToolKind?,
                                status = ToolCallStatus.IN_PROGRESS,
                                content = if (StringUtils.isNotBlank(toolCall.arguments())) listOf(
                                    ToolCallContent.Content(ContentBlock.Text(toolCall.arguments()))
                                ) else emptyList()
                            )
                        )
                    )
                }
            }
        }

        // Tool execution results (ToolResponseMessage)
        if (output.message is ToolResponseMessage) {
            val message = output.message as ToolResponseMessage
            if (!CollectionUtils.isEmpty(message.responses)) {
                message.responses.forEach { response ->
                    val resultData = ToolsUtil.parseToolResult(response.responseData())
                    emit(
                        Event.SessionUpdateEvent(
                            SessionUpdate.ToolCallUpdate(
                                toolCallId = ToolCallId(response.id()),
                                title = response.name(),
                                kind = ToolKindFind.find(response.name()),
                                status = if (resultData.success) ToolCallStatus.COMPLETED
                                else ToolCallStatus.FAILED,
                                content = resultData.toolCallContents,
                                locations = resultData.locations,
                            )
                        )
                    )
                }
            }
        }


    }

    /**
     * Build a Spring AI UserMessage from ACP content blocks.
     * Supports text, image, audio, and resource content types.
     */
    private fun buildUserMessage(content: List<ContentBlock>): UserMessage {
        val builder = UserMessage.builder()
        val textParts = StringBuilder()

        for (block in content) {
            when (block) {
                is ContentBlock.Text -> {
                    if (block.text.isNotBlank()) textParts.append(block.text)
                }

                is ContentBlock.Image -> {
                    val imageData = block.data
                    val mediaType = block.mimeType ?: "image/png"
                    builder.media(Media.builder().data(imageData).mimeType(MimeType.valueOf(mediaType)).build());
                }

                else -> {
                    logger.warn { "Unsupported content block type: ${block::class.simpleName}" }
                }
            }
        }

        if (textParts.isNotBlank()) {
            builder.text(textParts.toString())
        }

        return builder.build()
    }
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

    override suspend fun initialize(clientInfo: ClientInfo): AgentInfo {
        logger.info { "Initializing agent with protocol version ${clientInfo.protocolVersion}" }

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
        return sessionsRunnableConfig.map { (key, config) ->
            SessionInfo(
                SessionId(key),
                cwd = config.context().get("cwd") as String,
                title = StringUtils.abbreviate(config.context().get("input") as String, 25)
            )
        }.asSequence()
    }

    override suspend fun createSession(sessionParameters: SessionCreationParameters): AgentSession {
        val sessionIdStr = "session-${System.currentTimeMillis()}"
        val sessionId = SessionId(sessionIdStr)
        val cwd = sessionParameters.cwd ?: System.getProperty("user.dir")
        val mcpServers = sessionParameters.mcpServers
        val runnableConfig =
            RunnableConfig.builder().threadId(sessionIdStr).addStateUpdate(emptyMap<String, Any>()).build()
        sessionsRunnableConfig[sessionIdStr] = runnableConfig
        if (mcpServers.isNotEmpty()) {
            sessionMcpServers[sessionIdStr] = mcpServers
            logger.info { "Received ${mcpServers.size} MCP server(s) for session $sessionIdStr" }
        }
        val session = AgiAgentSession(sessionId, cwd, mcpServers, runnableConfig)
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
        val sessionIdStr = sessionId.toString()
        val runnableConfig = sessionsRunnableConfig[sessionIdStr] ?: RunnableConfig.builder().threadId(sessionIdStr)
            .addStateUpdate(emptyMap<String, Any>()).build()

        val session = AgiAgentSession(sessionId, cwd, sessionParameters.mcpServers, runnableConfig)
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

    /**
     * Load all provider configurations from models.json.
     * Returns a map of providerId -> baseUrl.
     */
    private fun loadAllProviderConfigs(): Map<String, String> {
        val configPath = Paths.get(ModelConfigLoader.getConfigFilePath())
        if (!Files.exists(configPath)) {
            logger.warn { "Config file not found at: $configPath" }
            return emptyMap()
        }
        return try {
            val content = Files.readString(configPath, StandardCharsets.UTF_8)
            val config = Json.to(content, Config::class.java)
            config.providers
                .filterNotNull()
                .filter { it.providerId != null && it.baseUrl != null }
                .associate { it.providerId!! to it.baseUrl!! }
        } catch (e: Exception) {
            logger.error(e) { "Failed to load provider configs" }
            emptyMap()
        }
    }
    override suspend fun listProviders(_meta: JsonElement?): ListProvidersResponse {
        logger.info { "List providers requested" }
        val providerConfigs = loadAllProviderConfigs()
        val providers = providerConfigs.map { (id, baseUrl) ->
            ProviderInfo(
                id = id,
                supported = listOf(LlmProtocol(LlmProtocol.OPENAI.value)),
                required = false,
                current = ProviderCurrentConfig(
                    apiType = LlmProtocol(LlmProtocol.OPENAI.value),
                    baseUrl = baseUrl
                )
            )
        }
        logger.info { "Returning ${providers.size} providers" }
        return ListProvidersResponse(providers = providers)
    }

    override suspend fun setProvider(
        id: String,
        apiType: LlmProtocol,
        baseUrl: String,
        headers: Map<String, String>?,
        _meta: JsonElement?
    ): SetProvidersResponse {
        logger.info { "Set provider: $id, type: $apiType, baseUrl: $baseUrl" }
        try {
            val configPath = Paths.get(ModelConfigLoader.getConfigFilePath())
            if (!Files.exists(configPath)) {
                logger.warn { "Config file not found at: $configPath" }
                return SetProvidersResponse()
            }
            val content = Files.readString(configPath, StandardCharsets.UTF_8)
            val config = Json.to(content, Config::class.java)

            // Find or create provider config
            var provider = config.providers.find { it?.providerId == id }
            if (provider == null) {
                provider = Config.ProviderConfig()
                provider.providerId = id
                config.providers.add(provider)
            }
            provider!!.baseUrl = baseUrl
            // Preserve existing apiKey if headers don't contain authorization
            if (headers != null && headers.containsKey("Authorization")) {
                provider.apiKey = headers["Authorization"]!!.removePrefix("Bearer ")
            }

            val updatedJson = Json.toPrettyJson(config)
            Files.writeString(configPath, updatedJson, StandardCharsets.UTF_8)
            logger.info { "Provider $id updated successfully" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to set provider: $id" }
        }
        return SetProvidersResponse()
    }

    override suspend fun disableProvider(id: String, _meta: JsonElement?): DisableProvidersResponse {
        logger.info { "Disable provider: $id" }
        try {
            val configPath = Paths.get(ModelConfigLoader.getConfigFilePath())
            if (!Files.exists(configPath)) {
                logger.warn { "Config file not found at: $configPath" }
                return DisableProvidersResponse()
            }
            val content = Files.readString(configPath, StandardCharsets.UTF_8)
            val config = Json.to(content, Config::class.java)

            // Remove the provider from the list
            config.providers.removeAll { it?.providerId == id }
            // Also disable all models that reference this provider
            config.models.forEach { model ->
                if (model?.providerId == id) {
                    model.disabled = true
                }
            }

            val updatedJson = Json.toPrettyJson(config)
            Files.writeString(configPath, updatedJson, StandardCharsets.UTF_8)
            logger.info { "Provider $id disabled successfully" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to disable provider: $id" }
        }
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
        val runnableConfig = RunnableConfig.builder().threadId(sessionIdStr)
            .addStateUpdate(emptyMap<String, Any>()).build()
        sessionsRunnableConfig[sessionIdStr] = runnableConfig
        val session = AgiAgentSession(newSessionId, cwd, sessionParameters.mcpServers, runnableConfig)
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
        val runnableConfig = sessionsRunnableConfig[sessionIdStr] ?: RunnableConfig.builder().threadId(sessionIdStr)
            .addStateUpdate(emptyMap<String, Any>()).build()
        val session = AgiAgentSession(sessionId, cwd, sessionParameters.mcpServers, runnableConfig)
        sessions[sessionIdStr] = session
        return session
    }

    override suspend fun createNesSession(request: StartNesRequest): NesAgentSession {
        logger.info { "Create NES session requested: $request" }
        throw NotImplementedError("createNesSession is not implemented. The capability is declared in AgentCapabilities.nes")
    }
}

