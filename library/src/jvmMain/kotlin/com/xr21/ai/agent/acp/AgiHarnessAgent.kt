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
import com.xr21.ai.agent.acp.SessionConfigOptionsFactory.AgentMode
import com.xr21.ai.agent.agent.HarnessCodingAgent
import com.xr21.ai.agent.bridge.BridgeKt
import com.xr21.ai.agent.channel.AcpChannel
import com.xr21.ai.agent.channel.AcpEventMapper
import com.xr21.ai.agent.config.AiModels
import com.xr21.ai.agent.tools.ToolKindFind
import com.xr21.ai.agent.utils.Json
import com.xr21.ai.agent.utils.PermissionSettings
import io.agentscope.core.event.AgentEvent
import io.agentscope.core.event.ModelCallEndEvent
import io.agentscope.core.event.RequireUserConfirmEvent
import io.agentscope.core.message.Msg
import io.agentscope.core.message.MsgRole
import io.agentscope.harness.agent.gateway.ChannelManager
import io.agentscope.harness.agent.gateway.HarnessGateway
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.apache.commons.lang3.StringUtils
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

private val logger = KotlinLogging.logger {}

/** Session-level MCP server cache (used in bootstrap, not per-prompt) */
private val sessionMcpServers = ConcurrentHashMap<String, List<com.agentclientprotocol.model.McpServer>>()

/**
 * ACP Agent session backed by AgentScope HarnessGateway via AcpChannel.
 * Delegates prompt processing to the Gateway/Agent execution layer
 * with streaming support via ACP protocol events.
 *
 * Unlike AgiAgentSession (which creates a new LocalAgent per prompt),
 * this session reuses a pre-configured HarnessAgent+Gateway via AcpChannel.
 */
class AgiHarnessAgentSession(
    override val sessionId: SessionId,
    private var cwd: String,
    private val acpChannel: AcpChannel,
    private val clientInfo: ClientInfo? = null
) : AgentSession {

    /** Context keys */
    private val CLIENT_SESSION_CONTEXT_KEY = "AcpClientSession"
    private val SESSION_ID_CONTEXT_KEY = "sessionId"

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
        logger.info { "set config option ${configId.value} to $value" }
        return SetSessionConfigOptionResponse(configOptions = configOptions)
    }

    override suspend fun setMode(modeId: SessionModeId, _meta: JsonElement?): SetSessionModeResponse {
        logger.info { "[AgiHarnessAgent] setMode ${modeId.value}" }
        return SetSessionModeResponse()
    }

    override suspend fun setModel(modelId: ModelId, _meta: JsonElement?): SetSessionModelResponse {
        logger.info { "[AgiHarnessAgent] setModel $modelId" }
        return SetSessionModelResponse()
    }

    override suspend fun prompt(
        content: List<ContentBlock>,
        _meta: JsonElement?,
    ): Flow<Event> = flow {
        logger.info { "Processing prompt for session $sessionId" }
        tokenUsageRef.set(Usage(0, 0, 0, 0, 0, 0))
        startTime.set(System.currentTimeMillis())
        val requestId = "request_${System.currentTimeMillis()}_$sessionId"

        try {
            // 1. Commands (/ls-model, /init)
            val messages = content.toMutableList()
            if (content.isNotEmpty() && content.first() is ContentBlock.Text) {
                val firstText = (content.first() as ContentBlock.Text).text
                if (firstText.startsWith("/")) {
                    logger.info { "command: $firstText" }
                    if (firstText == "/ls-model ") {
                        emit(Event.SessionUpdateEvent(
                            SessionUpdate.AgentMessageChunk(ContentBlock.Text(
                                buildString {
                                    appendLine("| Model ID | Name |")
                                    appendLine("|---------|------|")
                                    AiModels.availableModels().forEach { model ->
                                        appendLine("| ${model.modelId ?: ""} | ${model.modelName ?: ""} |")
                                    }
                                }.trimEnd()
                            ))
                        ))
                        emit(Event.PromptResponseEvent(PromptResponse(StopReason.END_TURN)))
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

            // 2. Build agentscope Msg from ACP ContentBlock
            val userMessage = UserMessageBuilder.buildUserMessage(messages)
            emit(Event.SessionUpdateEvent(
                SessionUpdate.AgentThoughtChunk(ContentBlock.Text("✨✨✨\r\n<br/>"))
            ))

            val msg = Msg.builder()
                .role(MsgRole.USER)
                .name("user")
                .textContent(userMessage.text)
                .build()

            // 3. Build extra map — agentId is used to resolve the per-session HarnessAgent
            // registered via registerAgent(sessionIdStr, harnessAgent) in createSession().
            val extra = mutableMapOf(
                "agentId" to sessionId.value,
                "cwd" to cwd,
                SESSION_ID_CONTEXT_KEY to sessionId.value,
                "requestId" to requestId,
                "isFirst" to "true",
                "isFirstMessage" to "true"
            )
            // Store client session ops so tools can send ACP notifications
            runBlocking {
                extra[CLIENT_SESSION_CONTEXT_KEY] = sessionId.value
            }

            // 4. Send via AcpChannel → Gateway.runStream() → Flux<AgentEvent>
            val eventFlux = acpChannel.sendStream(sessionId.value, listOf(msg), extra)
            // 5. Consume events via Channel bridge (Reactor → Coroutine)
            val acpEventChannel = kotlinx.coroutines.channels.Channel<Event>(kotlinx.coroutines.channels.Channel.UNLIMITED)
            var hasPermissionRequired = false
            val pendingConfirmTools = mutableListOf<RequireUserConfirmEvent>()
            val disposable = eventFlux.subscribe(
                { agentEvent ->
                    // Track token usage from ModelCallEndEvent
                    if (agentEvent is ModelCallEndEvent && agentEvent.usage != null) {
                        val usage = agentEvent.usage
                        val prevTurn = tokenUsageRef.get()
                        tokenUsageRef.set(Usage(
                            inputTokens = prevTurn.inputTokens + (usage.inputTokens ?: 0).toLong(),
                            outputTokens = prevTurn.outputTokens + (usage.outputTokens ?: 0).toLong(),
                            totalTokens = prevTurn.totalTokens + (usage.totalTokens ?: 0).toLong(),
                            thoughtTokens = prevTurn.thoughtTokens,
                            cachedReadTokens = prevTurn.cachedReadTokens,
                            cachedWriteTokens = prevTurn.cachedWriteTokens
                        ))
                        val prev = sessionTokenUsageRef.get()
                        sessionTokenUsageRef.set(Usage(
                            inputTokens = prev.inputTokens + (usage.inputTokens ?: 0).toLong(),
                            outputTokens = prev.outputTokens + (usage.outputTokens ?: 0).toLong(),
                            totalTokens = prev.totalTokens + (usage.totalTokens ?: 0).toLong(),
                            thoughtTokens = prev.thoughtTokens,
                            cachedReadTokens = prev.cachedReadTokens,
                            cachedWriteTokens = prev.cachedWriteTokens
                        ))
                    }

                    // Detect HITL permission requirement
                    if (AcpEventMapper.isPermissionRequired(agentEvent)) {
                        hasPermissionRequired = true
                        if (agentEvent is RequireUserConfirmEvent) {
                            pendingConfirmTools.add(agentEvent)
                        }
                    }

                    // Map AgentEvent → ACP Event(s) and push to channel
                    val acpEvents = AcpEventMapper.toAcpEvents(agentEvent)
                    acpEvents.forEach { acpEventChannel.trySend(it) }

                    // Check if stream ended
                    if (AcpEventMapper.isStreamEnd(agentEvent)) {
                        acpEventChannel.close()
                    }
                },
                { error ->
                    error.printStackTrace()
                    logger.error(error) { "Agent stream error" }
                    val errorMsg = error.message ?: error.javaClass.name ?: "Unknown error"
                    acpEventChannel.trySend(
                        Event.SessionUpdateEvent(
                            SessionUpdate.AgentMessageChunk(
                                content = ContentBlock.Text(errorMsg),
                                messageId = null
                            )
                        )
                    )
                    acpEventChannel.close(error)
                },
                {
                    logger.info { "Agent stream completed for session $sessionId" }
                    acpEventChannel.close()
                }
            )

            // Receive from Channel and emit to coroutine flow
            try {
                while (!disposable.isDisposed && currentCoroutineContext().isActive) {
                    val result = acpEventChannel.receiveCatching()
                    if (result.isClosed) {
                        val cause = result.exceptionOrNull()
                        if (cause != null) {
                            throw cause
                        }
                        break
                    }
                    val acpEvent = result.getOrNull() ?: break
                    emit(acpEvent)
                }
            } finally {
                disposable.dispose()
                acpEventChannel.close()
            }

            // 6. Handle HITL if needed
            if (hasPermissionRequired && pendingConfirmTools.isNotEmpty()) {
                val confirmEvent = pendingConfirmTools.first()
                handleHitlConfirmation(confirmEvent)
            }

            // 7. Token usage summary
            val latency = System.currentTimeMillis() - startTime.get()
            val duration = latency / 1000.0
            val tokens = sessionTokenUsageRef.get().totalTokens
            val speed = if (duration > 0) String.format("%.2f", tokenUsageRef.get().outputTokens / duration) else "0.00"
            val chunk = "Token usage: sessionTotal=$tokens ,total=${tokenUsageRef.get().totalTokens},outputTokens=${tokenUsageRef.get().outputTokens}, duration=${duration}s, speed=$speed tokens/s"
            logger.info { chunk }

            emit(Event.SessionUpdateEvent(
                SessionUpdate.AgentThoughtChunk(ContentBlock.Text(chunk))
            ))
            emit(Event.SessionUpdateEvent(
                SessionUpdate.UsageUpdate(tokens, 0, Cost(0.0, "CNY"))
            ))

            logger.info { "events END_TURN" }
            val finalUsage = Usage(
                tokenUsageRef.get().inputTokens,
                tokenUsageRef.get().outputTokens,
                tokenUsageRef.get().totalTokens,
                tokenUsageRef.get().thoughtTokens,
                tokenUsageRef.get().cachedReadTokens,
                tokenUsageRef.get().cachedWriteTokens
            )
            emit(Event.PromptResponseEvent(PromptResponse(StopReason.END_TURN, null, finalUsage)))

        } catch (e: Exception) {
            e.printStackTrace()
            logger.error(e) { "Error processing prompt" }
            val errorDetail = if (e.message != null) e.message!! else "${e.javaClass.name}: (no message)"
            emit(Event.SessionUpdateEvent(
                SessionUpdate.AgentMessageChunk(ContentBlock.Text("\nError: $errorDetail"))
            ))
            emit(Event.PromptResponseEvent(PromptResponse(StopReason.REFUSAL)))
        }
    }

    override suspend fun close(_meta: JsonElement?): CloseSessionResponse {
        logger.info { "Closing session: $sessionId" }
        cancel()
        sessionMcpServers.remove(sessionId.toString())
        acpChannel.unregisterOutboundSession(sessionId.value)
        return CloseSessionResponse()
    }

    override suspend fun cancel() {
        logger.info { "Cancellation requested for session: $sessionId" }
        // AcpChannel sends messages via Gateway.runStream which returns a Flux.
        // Cancellation means unregistering the session — the running stream
        // will be terminated by gateway's SessionTurnGate mechanism.
        // If we tracked Disposables per request, we would dispose them here.
        logger.info { "Cancelled active requests for session: $sessionId" }
    }

    /**
     * Handle HITL permission request by asking the user via ACP protocol.
     * This is called after the stream completes with PERMISSION_ASKING reason.
     * In a full implementation, this would:
     * 1. Call clientOps.requestPermissions() to ask the user
     * 2. Build a ConfirmResult Msg
     * 3. Call acpChannel.sendStream() again with the ConfirmResult
     * 4. Continue consuming events recursively
     */
    private suspend fun handleHitlConfirmation(event: RequireUserConfirmEvent) {
        val clientOps: ClientSessionOperations? = currentCoroutineContext().client
        if (clientOps == null) {
            logger.warn { "No ClientSessionOperations available for HITL" }
            return
        }

        val toolCalls = event.toolCalls
        if (toolCalls.isNullOrEmpty()) {
            return
        }

        // Check persisted permissions
        PermissionSettings.load(cwd)

        for (toolUse in toolCalls) {
            val toolName = toolUse.name ?: "unknown"
            val toolArgs = toolUse.input.toString() ?: "{}"
            val toolPattern = buildToolPattern(toolName, toolArgs)

            val persistedAction = PermissionSettings.checkPermission(toolName, toolArgs)
            if (persistedAction != null) {
                logger.info { "Persisted permission ${persistedAction.name} for $toolName" }
                continue
            }

            // Ask user via ACP protocol
            val toolCallUpdate = SessionUpdate.ToolCallUpdate(
                toolCallId = ToolCallId(toolUse.id ?: ""),
                title = toolName,
                kind = ToolKindFind.find(toolName),
                status = ToolCallStatus.PENDING,
                content = BridgeKt.build(toolName, toolArgs)
            )

            val permissionOptions = PermissionOptionKind.values().map { kind ->
                PermissionOption(PermissionOptionId(kind.name), kind.name, kind)
            }

            val permissionResponse = runBlocking {
                clientOps.requestPermissions(toolCallUpdate, permissionOptions, null)
            }

            when (val outcome = permissionResponse.outcome) {
                is RequestPermissionOutcome.Selected -> {
                    val optionKind = PermissionOptionKind.valueOf(outcome.optionId.value)
                    when (optionKind) {
                        PermissionOptionKind.ALLOW_ALWAYS ->
                            PermissionSettings.addAllowPermission(cwd, toolPattern)
                        PermissionOptionKind.REJECT_ALWAYS ->
                            PermissionSettings.addRejectPermission(cwd, toolPattern)
                        else -> {}
                    }
                }
                else -> {}
            }
        }
    }

    private fun buildToolPattern(toolName: String?, arguments: String?): String {
        if (toolName == null) return ""
        return if (arguments.isNullOrEmpty()) toolName else "$toolName($arguments)"
    }

    /**
     * Emit ACP events from a single AgentOutput.
     * Maps AgentScope AgentEvent stream to ACP protocol events.
     */
    private suspend fun FlowCollector<Event>.emitAgentEvent(event: AgentEvent) {
        val acpEvents = AcpEventMapper.toAcpEvents(event)
        for (acpEvent in acpEvents) {
            emit(acpEvent)
        }
    }
}

/**
 * AgiHarnessAgent - ACP Agent Support implementation backed by HarnessGateway via AcpChannel.
 *
 * Created at bootstrap with a pre-configured AcpChannel that wraps
 * a HarnessGateway + HarnessAgent (built by HarnessCodingAgent).
 *
 * Session lifecycle:
 * 1. initialize() - Reports agent capabilities
 * 2. createSession() - Creates session with AcpChannel
 * 3. session.prompt() - Streams agent output via AcpEventMapper → ACP events
 * 4. session.cancel() - Cancels active requests
 */
class AgiHarnessAgent(
    private val acpChannel: AcpChannel
) : AgentSupport {

    private val sessions = ConcurrentHashMap<String, AgiHarnessAgentSession>()
    private var lastClientInfo: ClientInfo? = null
    private val harnessGateway: HarnessGateway
    private val channelManager: ChannelManager = ChannelManager()

    init {
        channelManager.register(acpChannel)
        harnessGateway = HarnessGateway.create(channelManager)
        channelManager.initAll(harnessGateway)
        channelManager.startAll()
        logger.info { "AgiHarnessAgent: Gateway initialized with AcpChannel" }
    }

    override suspend fun initialize(clientInfo: ClientInfo): AgentInfo {
        logger.info { "Initializing AgiHarnessAgent with protocol version ${Json.toJson(clientInfo)}" }
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
            implementation = Implementation("AgiHarnessAgent", "2.0.0", "agi coding with HarnessAgent")
        )
    }

    override suspend fun listSessions(
        cwd: String?, additionalDirectories: List<String>?, _meta: JsonElement?
    ): Sequence<SessionInfo> {
        return sessions.map { (key, _) ->
            SessionInfo(
                SessionId(key),
                cwd = cwd ?: "",
                title = StringUtils.abbreviate(key, 25)
            )
        }.asSequence()
    }

    override suspend fun createSession(sessionParameters: SessionCreationParameters): AgentSession {
        logger.info { "createSession ${Json.toJson(sessionParameters)}" }
        val sessionIdStr = "session-${System.currentTimeMillis()}"
        val sessionId = SessionId(sessionIdStr)
        val cwd = sessionParameters.cwd ?: System.getProperty("user.dir")
        val mcpServers = sessionParameters.mcpServers

        // Build a HarnessAgent for this session and bind as main agent
        val runnableConfig = RunnableConfig.builder()
            .threadId(sessionIdStr)
            .addStateUpdate(emptyMap<String, Any>())
            .build()
        val harnessAgent = HarnessCodingAgent.createAgent(cwd, mcpServers, runnableConfig)
        harnessGateway.registerAgent(sessionIdStr, harnessAgent)
        logger.info { "Registered HarnessAgent for session $sessionIdStr" }

        // Register outbound callback for proactive messages
        acpChannel.registerOutboundSession(sessionIdStr) { msgs ->
            logger.info { "Outbound ${msgs.size} message(s) for session $sessionIdStr" }
        }

        val session = AgiHarnessAgentSession(sessionId, cwd, acpChannel, lastClientInfo)
        sessions[sessionIdStr] = session
        logger.info { "Created Harness session $sessionIdStr with cwd: $cwd" }
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
        val sessionIdStr = sessionId.toString()
        val cwd = sessionParameters.cwd ?: System.getProperty("user.dir")
        val mcpServers = sessionParameters.mcpServers

        val runnableConfig = RunnableConfig.builder()
            .threadId(sessionIdStr)
            .addStateUpdate(emptyMap<String, Any>())
            .build()
        val harnessAgent = HarnessCodingAgent.createAgent(cwd, mcpServers, runnableConfig)
        harnessGateway.registerAgent(sessionIdStr, harnessAgent)
        logger.info { "Registered HarnessAgent for loaded session $sessionIdStr" }

        val session = AgiHarnessAgentSession(sessionId, cwd, acpChannel, lastClientInfo)
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
        sessionId: SessionId, sessionParameters: SessionCreationParameters,
    ): AgentSession {
        logger.info { "Fork session: $sessionId" }
        val cwd = sessionParameters.cwd ?: System.getProperty("user.dir")
        val mcpServers = sessionParameters.mcpServers
        val sessionIdStr = "session-${System.currentTimeMillis()}"
        val newSessionId = SessionId(sessionIdStr)

        val runnableConfig = RunnableConfig.builder()
            .threadId(sessionIdStr)
            .addStateUpdate(emptyMap<String, Any>())
            .build()
        val harnessAgent = HarnessCodingAgent.createAgent(cwd, mcpServers, runnableConfig)
        harnessGateway.registerAgent(sessionIdStr, harnessAgent)
        logger.info { "Registered HarnessAgent for forked session $sessionIdStr" }

        val session = AgiHarnessAgentSession(newSessionId, cwd, acpChannel, lastClientInfo)
        sessions[sessionIdStr] = session
        return session
    }

    override suspend fun resumeSession(
        sessionId: SessionId, sessionParameters: SessionCreationParameters,
    ): AgentSession {
        logger.info { "Resume session: $sessionId" }
        val existing = sessions[sessionId.toString()]
        if (existing != null) return existing
        val sessionIdStr = sessionId.toString()
        val cwd = sessionParameters.cwd ?: System.getProperty("user.dir")
        val mcpServers = sessionParameters.mcpServers

        val runnableConfig = RunnableConfig.builder()
            .threadId(sessionIdStr)
            .addStateUpdate(emptyMap<String, Any>())
            .build()
        val harnessAgent = HarnessCodingAgent.createAgent(cwd, mcpServers, runnableConfig)
        harnessGateway.registerAgent(sessionIdStr, harnessAgent)
        logger.info { "Registered HarnessAgent for resumed session $sessionIdStr" }

        val session = AgiHarnessAgentSession(sessionId, cwd, acpChannel, lastClientInfo)
        sessions[sessionIdStr] = session
        return session
    }

    override suspend fun createNesSession(request: StartNesRequest): NesAgentSession {
        logger.info { "Create NES session requested: $request" }
        throw NotImplementedError("createNesSession is not implemented")
    }
}
