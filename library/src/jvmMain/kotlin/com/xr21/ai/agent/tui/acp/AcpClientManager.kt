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
package com.xr21.ai.agent.tui.acp

import com.agentclientprotocol.agent.AgentInfo
import com.agentclientprotocol.client.Client
import com.agentclientprotocol.client.ClientInfo
import com.agentclientprotocol.client.ClientSession
import com.agentclientprotocol.common.ClientSessionOperations
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.common.SessionCreationParameters
import com.agentclientprotocol.model.*
import com.agentclientprotocol.protocol.Protocol
import com.agentclientprotocol.protocol.ProtocolOptions
import com.agentclientprotocol.transport.acpProtocolOnClientWebSocket
import com.xr21.ai.agent.acp.AgiAgent
import com.xr21.ai.agent.acp.launchWebSocketServer
import com.xr21.ai.agent.tui.AppState
import com.xr21.ai.agent.tui.config.ACPConnectConfig
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.JsonElement
import java.io.BufferedReader

/**
 * ACP 客户端管理器
 *
 * 支持两种连接模式：
 * 1. WebSocket 模式（默认）：通过 WebSocket 连接到 ACP Agent 服务
 *    - 如果未配置外部 ws-url，自动在后台启动内部 WebSocket 服务器
 * 2. Stdio 模式（兼容）：通过 --command 参数启动 Agent 子进程
 *
 * ## 生命周期
 *
 * 状态转换：CREATED → CONNECTING → INITIALIZED → SESSION_ACTIVE → DISCONNECTED → DESTROYED
 *
 * 通过 [lifecycleState] StateFlow 和 [lifecycleEvents] SharedFlow 监听生命周期变化。
 */
class AcpClientManager(private val appState: AppState) {

    // ========== 生命周期字段 ==========

    private val _lifecycleState = MutableStateFlow(AcpLifecycleState.CREATED)
    private val _lifecycleEvents = MutableSharedFlow<AcpLifecycleEvent>(extraBufferCapacity = 16)
    private val _listeners = mutableListOf<AcpLifecycleListener>()
    private var reconnectStrategy: ReconnectStrategy = ReconnectStrategy.NoReconnect
    private var reconnectJob: Job? = null
    private var configSnapshot: ACPConnectConfig? = null

    /** 当前生命周期状态（StateFlow，可收集） */
    val lifecycleState: StateFlow<AcpLifecycleState> = _lifecycleState.asStateFlow()

    /** 生命周期事件流（SharedFlow，不重复消费） */
    val lifecycleEvents: SharedFlow<AcpLifecycleEvent> = _lifecycleEvents.asSharedFlow()

    // ========== WebSocket 模式状态 ==========
    private var httpClient: HttpClient? = null
    private var protocol: Protocol? = null
    private var acpClient: Client? = null
    private var clientSession: ClientSession? = null
    private var serverThread: Thread? = null
    private var agentInfo: AgentInfo? = null

    // ========== 多会话管理 ==========
    private val sessions = mutableMapOf<String, ClientSession>()
    private var _activeSessionId: String? = null

    // ========== Stdio 模式状态（向后兼容） ==========
    private var process: Process? = null
    private var isConnected = false
    private var reader: BufferedReader? = null
    private var eventCollectorJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    /** 当前活动会话 ID */
    var sessionId: String? = null
        private set

    /** 当前活动会话 ID（与 sessionId 同步） */
    val activeSessionId: String?
        get() = _activeSessionId ?: sessionId

    /** 所有活跃会话的 ID 列表 */
    val activeSessionIds: List<String>
        get() = sessions.keys.toList()

    /** 活跃会话数量 */
    val sessionCount: Int
        get() = sessions.size

    private var eventHandler: ((Event) -> Unit)? = null

    // ========== 生命周期管理 ==========

    /** 设置重连策略 */
    fun setReconnectStrategy(strategy: ReconnectStrategy) {
        reconnectStrategy = strategy
    }

    /** 添加生命周期监听器 */
    fun addLifecycleListener(listener: AcpLifecycleListener) {
        _listeners.add(listener)
    }

    /** 移除生命周期监听器 */
    fun removeLifecycleListener(listener: AcpLifecycleListener) {
        _listeners.remove(listener)
    }

    private fun transitionTo(newState: AcpLifecycleState) {
        val oldState = _lifecycleState.value
        if (oldState == newState) return
        _lifecycleState.value = newState
        val event = AcpLifecycleEvent.StateChanged(oldState, newState)
        _lifecycleEvents.tryEmit(event)
        _listeners.forEach { it.onLifecycleEvent(event) }
    }

    private fun emitEvent(event: AcpLifecycleEvent) {
        _lifecycleEvents.tryEmit(event)
        _listeners.forEach { it.onLifecycleEvent(event) }
    }

    // ========== 连接管理 ==========

    /** 建立 ACP 连接（WebSocket 优先，回退到 Stdio） */
    suspend fun connect(config: ACPConnectConfig): Result<Unit> {
        configSnapshot = config
        transitionTo(AcpLifecycleState.CONNECTING)
        val result = if (config.agentCommand.isNotEmpty()) {
            connectStdio(config.agentCommand)
        } else {
            connectWebSocket(config)
        }
        if (result.isFailure) {
            transitionTo(AcpLifecycleState.DISCONNECTED)
        }
        return result
    }

    /** WebSocket 模式连接 */
    private suspend fun connectWebSocket(config: ACPConnectConfig): Result<Unit> {
        return try {
            appState.connectionState = ConnectionState.CONNECTING

            val wsUrl = if (config.webSocketUrl.isNotEmpty()) {
                config.webSocketUrl
            } else {
                val port = findAvailablePort(config.webSocketServerPort)
                startInternalServer(port)
                delay(800)
                "ws://127.0.0.1:$port/acp"
            }

            val client = HttpClient { install(WebSockets) }
            httpClient = client

            val proto = client.acpProtocolOnClientWebSocket(wsUrl, ProtocolOptions())
            proto.start()
            protocol = proto

            val acpClient = Client(proto)
            this.acpClient = acpClient

            val info = acpClient.initialize(
                ClientInfo(implementation = Implementation("XAgent TUI", "0.1.0", "XAgent TUI"))
            )
            agentInfo = info
            transitionTo(AcpLifecycleState.INITIALIZED)
            emitEvent(AcpLifecycleEvent.Connected(
                agentName = info.implementation?.name ?: "Unknown",
                agentVersion = info.implementation?.version ?: ""
            ))

            appState.agentName = info.implementation?.name ?: "Unknown"
            appState.agentVersion = info.implementation?.version ?: ""
            val session = acpClient.newSession(
                SessionCreationParameters(cwd = System.getProperty("user.dir"), mcpServers = emptyList())
            ) { _, _ -> TuiClientOperations() }

            clientSession = session
            sessionId = session.sessionId.toString()
            sessions[sessionId!!] = session
            _activeSessionId = sessionId
            transitionTo(AcpLifecycleState.SESSION_ACTIVE)
            emitEvent(AcpLifecycleEvent.SessionCreated(sessionId!!))

            appState.connectionState = ConnectionState.CONNECTED
            Result.success(Unit)
        } catch (e: Exception) {
            appState.connectionState = ConnectionState.DISCONNECTED_ERROR
            appState.errorMessage = "WebSocket 连接失败: ${e.message}"
            emitEvent(AcpLifecycleEvent.ErrorOccurred(e))
            Result.failure(e)
        }
    }

    /** 启动内部 WebSocket 服务器（后台线程） */
    private fun startInternalServer(port: Int) {
        val thread = Thread {
            launchWebSocketServer(AgiAgent(), "127.0.0.1", port)
        }
        thread.isDaemon = true
        thread.start()
        serverThread = thread
    }

    /** 查找可用端口（避免 BindException） */
    private fun findAvailablePort(startPort: Int): Int {
        for (port in startPort..startPort + 100) {
            try {
                java.net.ServerSocket(port).use { return port }
            } catch (_: java.io.IOException) {
            }
        }
        throw IllegalStateException("在范围 $startPort..${startPort + 100} 内未找到可用端口")
    }

    /** Stdio 模式连接（向后兼容） */
    private suspend fun connectStdio(command: List<String>): Result<Unit> {
        return try {
            appState.connectionState = ConnectionState.CONNECTING
            val pb = ProcessBuilder(command).redirectErrorStream(true)
            process = pb.start()
            reader = process!!.inputStream.bufferedReader()
            isConnected = true
            val handshakeResult = performHandshake()
            if (handshakeResult.isFailure) {
                disconnect()
                return handshakeResult
            }
            transitionTo(AcpLifecycleState.SESSION_ACTIVE)
            appState.connectionState = ConnectionState.CONNECTED
            Result.success(Unit)
        } catch (e: Exception) {
            appState.connectionState = ConnectionState.DISCONNECTED_ERROR
            appState.errorMessage = "连接失败: ${e.message}"
            emitEvent(AcpLifecycleEvent.ErrorOccurred(e))
            Result.failure(e)
        }
    }

    /** Stdio 模式握手 */
    private suspend fun performHandshake(): Result<Unit> {
        return try {
            sendRaw(
                buildJsonRpcRequest(
                    "initialize", mapOf(
                        "protocolVersion" to "0.1.0",
                        "clientInfo" to mapOf("name" to "XAgent TUI", "version" to "0.1.0")
                    )
                )
            )
            readResponse() ?: return Result.failure(Exception("未收到 initialize 响应"))
            sendRaw(buildJsonRpcRequest("session/new", mapOf("cwd" to System.getProperty("user.dir"))))
            val sessionResponse = readResponse() ?: return Result.failure(Exception("未收到 session/new 响应"))
            sessionId = extractSessionId(sessionResponse)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ========== 认证管理 ==========

    /**
     * Agent 在 [initialize] 时声明的可用认证方式（[AuthMethod] 列表）。
     * 仅在 WebSocket 模式下可用。
     */
    val availableAuthMethods: List<AuthMethod>
        get() = agentInfo?.authMethods ?: emptyList()

    /**
     * 通过 SDK 标准 [Client.authenticate] 协议发起认证。
     *
     * Agent 在 [availableAuthMethods] 中声明认证方式，调用本方法启动对应流程。
     * 具体的 token/凭据输入由 Agent 端（URL 模式 / 终端模式 / env-var 模式）处理。
     *
     * @param methodId Agent 在 [availableAuthMethods] 中声明的认证方式 ID
     * @return 成功时返回 SDK 的 [AuthenticateResponse]；失败时包装异常
     */
    suspend fun authenticate(methodId: AuthMethodId): Result<AuthenticateResponse> {
        return try {
            val client = acpClient
                ?: return Result.failure(Exception("客户端未初始化，无法认证"))
            if (agentInfo == null) {
                return Result.failure(Exception("尚未初始化，无法认证"))
            }
            Result.success(client.authenticate(methodId))
        } catch (e: Exception) {
            emitEvent(AcpLifecycleEvent.ErrorOccurred(e))
            Result.failure(e)
        }
    }

    /**
     * 便捷重载：按 [provider] 字符串匹配 [availableAuthMethods] 中的首个匹配项并发起认证。
     * 仅在 WebSocket 模式下支持。
     *
     * 注意：ACP 标准认证流程中，token/凭据由 Agent 端在 [methodId] 对应的认证子流程中收集，
     * 不由客户端直接传递。本方法保留旧签名以兼容现有调用方，[token] 参数在标准协议下不使用。
     */
    suspend fun authenticate(provider: String, token: String): Result<Unit> {
        @Suppress("UNUSED_PARAMETER") val ignored = token
        val method = availableAuthMethods.firstOrNull { it.id.value == provider }
            ?: return Result.failure(Exception("未找到认证方式: $provider"))
        return authenticate(method.id).map { }
    }
    /**
     * 登出当前认证状态（Unstable API）。
     * 调用后所有新会话将需要重新认证。
     * 仅在 WebSocket 模式下支持。
     */
    @OptIn(com.agentclientprotocol.annotations.UnstableApi::class)
    suspend fun logout(): Result<Unit> {
        return try {
            val client = acpClient
                ?: return Result.failure(Exception("客户端未初始化，无法登出"))
            if (agentInfo == null) {
                return Result.failure(Exception("尚未初始化，无法登出"))
            }
            client.logout()
            Result.success(Unit)
        } catch (e: Exception) {
            emitEvent(AcpLifecycleEvent.ErrorOccurred(e))
            Result.failure(e)
        }
    }

    // ========== Provider 管理（Unstable API） ==========

    /**
     * 列出 Agent 支持的可配置 LLM Provider。
     * 仅在 WebSocket 模式下支持。
     */
    @OptIn(com.agentclientprotocol.annotations.UnstableApi::class)
    suspend fun listProviders(): Result<ListProvidersResponse> {
        return try {
            val client = acpClient
                ?: return Result.failure(Exception("客户端未初始化，无法列出 Provider"))
            if (agentInfo == null) {
                return Result.failure(Exception("尚未初始化，无法列出 Provider"))
            }
            Result.success(client.listProviders())
        } catch (e: Exception) {
            emitEvent(AcpLifecycleEvent.ErrorOccurred(e))
            Result.failure(e)
        }
    }

    /**
     * 配置指定 Provider 的连接参数（Unstable API）。
     * 仅在 WebSocket 模式下支持。
     */
    @OptIn(com.agentclientprotocol.annotations.UnstableApi::class)
    suspend fun setProvider(
        id: String,
        apiType: LlmProtocol,
        baseUrl: String,
        headers: Map<String, String>? = null
    ): Result<Unit> {
        return try {
            val client = acpClient
                ?: return Result.failure(Exception("客户端未初始化，无法设置 Provider"))
            if (agentInfo == null) {
                return Result.failure(Exception("尚未初始化，无法设置 Provider"))
            }
            client.setProvider(id, apiType, baseUrl, headers)
            Result.success(Unit)
        } catch (e: Exception) {
            emitEvent(AcpLifecycleEvent.ErrorOccurred(e))
            Result.failure(e)
        }
    }

    /**
     * 禁用指定 Provider（Unstable API）。
     * 仅在 WebSocket 模式下支持。
     */
    @OptIn(com.agentclientprotocol.annotations.UnstableApi::class)
    suspend fun disableProvider(id: String): Result<Unit> {
        return try {
            val client = acpClient
                ?: return Result.failure(Exception("客户端未初始化，无法禁用 Provider"))
            if (agentInfo == null) {
                return Result.failure(Exception("尚未初始化，无法禁用 Provider"))
            }
            client.disableProvider(id)
            Result.success(Unit)
        } catch (e: Exception) {
            emitEvent(AcpLifecycleEvent.ErrorOccurred(e))
            Result.failure(e)
        }
    }

    // ========== 会话管理 ==========

    /**
     * 关闭当前会话（不关闭连接）。
     * 关闭后可通过 [connect] 重新创建会话。
     */
    suspend fun closeSession(): Result<Unit> {
        return try {
            val sid = _activeSessionId ?: sessionId
            if (sid != null) {
                sessions.remove(sid)?.close()
            }
            clientSession?.let { session ->
                if (session.sessionId.toString() != sid) {
                    session.close()
                }
            }
            clientSession = null
            sessionId = null
            _activeSessionId = null
            emitEvent(AcpLifecycleEvent.SessionClosed)
            if (_lifecycleState.value == AcpLifecycleState.SESSION_ACTIVE) {
                transitionTo(AcpLifecycleState.INITIALIZED)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            emitEvent(AcpLifecycleEvent.ErrorOccurred(e))
            Result.failure(e)
        }
    }

    // ========== 多会话管理 ==========

    /**
     * 加载已存在的会话。
     * 仅在 WebSocket 模式下支持。
     */
    suspend fun loadSession(sessionId: String): Result<Unit> {
        return try {
            val client = acpClient
                ?: return Result.failure(Exception("客户端未初始化，无法加载会话"))
            val sid = SessionId(sessionId)
            val session = client.loadSession(
                sid,
                SessionCreationParameters(cwd = System.getProperty("user.dir"), mcpServers = emptyList())
            ) { _, _ -> TuiClientOperations() }
            val sidStr = session.sessionId.toString()
            sessions[sidStr] = session
            _activeSessionId = sidStr
            this.sessionId = sidStr
            clientSession = session
            transitionTo(AcpLifecycleState.SESSION_ACTIVE)
            emitEvent(AcpLifecycleEvent.SessionCreated(sidStr))
            Result.success(Unit)
        } catch (e: Exception) {
            emitEvent(AcpLifecycleEvent.ErrorOccurred(e))
            Result.failure(e)
        }
    }

    /**
     * 分支（fork）已有会话，创建基于该会话上下文的新会话。
     * 仅在 WebSocket 模式下支持。
     */
    suspend fun forkSession(sourceSessionId: String): Result<Unit> {
        return try {
            val client = acpClient
                ?: return Result.failure(Exception("客户端未初始化，无法分支会话"))
            val sourceSid = SessionId(sourceSessionId)
            val session = client.forkSession(
                sourceSid,
                SessionCreationParameters(cwd = System.getProperty("user.dir"), mcpServers = emptyList())
            ) { _, _ -> TuiClientOperations() }
            val sidStr = session.sessionId.toString()
            sessions[sidStr] = session
            _activeSessionId = sidStr
            this.sessionId = sidStr
            clientSession = session
            transitionTo(AcpLifecycleState.SESSION_ACTIVE)
            emitEvent(AcpLifecycleEvent.SessionCreated(sidStr))
            Result.success(Unit)
        } catch (e: Exception) {
            emitEvent(AcpLifecycleEvent.ErrorOccurred(e))
            Result.failure(e)
        }
    }

    /**
     * 恢复已存在的会话（不重放消息历史）。
     * 仅在 WebSocket 模式下支持。
     */
    suspend fun resumeSession(sessionId: String): Result<Unit> {
        return try {
            val client = acpClient
                ?: return Result.failure(Exception("客户端未初始化，无法恢复会话"))
            val sid = SessionId(sessionId)
            val session = client.resumeSession(
                sid,
                SessionCreationParameters(cwd = System.getProperty("user.dir"), mcpServers = emptyList())
            ) { _, _ -> TuiClientOperations() }
            val sidStr = session.sessionId.toString()
            sessions[sidStr] = session
            _activeSessionId = sidStr
            this.sessionId = sidStr
            clientSession = session
            transitionTo(AcpLifecycleState.SESSION_ACTIVE)
            emitEvent(AcpLifecycleEvent.SessionCreated(sidStr))
            Result.success(Unit)
        } catch (e: Exception) {
            emitEvent(AcpLifecycleEvent.ErrorOccurred(e))
            Result.failure(e)
        }
    }

    /**
     * 列出所有会话。
     * 仅在 WebSocket 模式下支持。
     */
    val sessionList: List<String>
        get() = sessions.keys.toList()

    /**
     * 切换当前活动会话。
     */
    suspend fun switchSession(sessionId: String): Result<Unit> {
        val session = sessions[sessionId]
            ?: return Result.failure(Exception("会话 $sessionId 不存在"))
        clientSession = session
        _activeSessionId = sessionId
        this.sessionId = sessionId
        emitEvent(AcpLifecycleEvent.SessionCreated(sessionId))
        return Result.success(Unit)
    }

    /**
     * 按 ID 关闭指定会话。
     */
    suspend fun closeSessionById(sessionId: String): Result<Unit> {
        return try {
            val session = sessions.remove(sessionId)
                ?: return Result.failure(Exception("会话 $sessionId 不存在"))
            session.close()
            if (_activeSessionId == sessionId) {
                _activeSessionId = sessions.keys.firstOrNull()
                this.sessionId = _activeSessionId
                clientSession = sessions.values.firstOrNull()
                if (_activeSessionId == null) {
                    transitionTo(AcpLifecycleState.INITIALIZED)
                }
            }
            emitEvent(AcpLifecycleEvent.SessionClosed)
            Result.success(Unit)
        } catch (e: Exception) {
            emitEvent(AcpLifecycleEvent.ErrorOccurred(e))
            Result.failure(e)
        }
    }

    // ========== 消息发送 ==========

    /** 发送 ACP prompt 消息 */
    suspend fun sendPrompt(content: String): Result<Unit> {
        return try {
            val session = clientSession
            if (session != null) {
                val handler = eventHandler ?: return Result.failure(Exception("事件处理器未设置"))
                session.prompt(listOf(ContentBlock.Text(content))).collect { event ->
                    // 处理 PromptResponseEvent，发出生命周期事件
                    if (event is Event.PromptResponseEvent) {
                        emitEvent(AcpLifecycleEvent.PromptCompleted(
                            stopReason = event.response.stopReason.name,
                            usage = event.response.usage?.toString() ?: ""
                        ))
                    }
                    handler(event)
                }
                Result.success(Unit)
            } else if (isConnected && process != null) {
                val sid = sessionId ?: return Result.failure(Exception("会话未创建"))
                val promptRequest = buildJsonRpcRequest(
                    "session/prompt", mapOf(
                        "sessionId" to sid,
                        "content" to listOf(mapOf("type" to "text", "text" to content))
                    )
                )
                sendRaw(promptRequest)
                Result.success(Unit)
            } else {
                Result.failure(Exception("未连接"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 发送中断信号 */
    suspend fun sendCancel(): Result<Unit> {
        return try {
            val session = clientSession
            if (session != null) {
                session.cancel()
                Result.success(Unit)
            } else if (isConnected) {
                val sid = sessionId ?: return Result.failure(Exception("会话未创建"))
                val cancelRequest = buildJsonRpcRequest("session/cancel", mapOf("sessionId" to sid))
                sendRaw(cancelRequest)
                Result.success(Unit)
            } else {
                Result.failure(Exception("未连接"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 接收 ACP 事件流（Stdio 模式） */
    fun receiveEvents(): Flow<String> = flow {
        val r = reader ?: return@flow
        while (isConnected) {
            try {
                val line = r.readLine() ?: break
                if (line.isNotBlank()) emit(line)
            } catch (e: Exception) {
                break
            }
        }
    }

    /** 启动后台事件收集协程 */
    fun startEventCollection(onEvent: (Event) -> Unit) {
        eventHandler = onEvent
        eventCollectorJob = scope.launch {
            receiveEvents().collect { line ->
                onEvent(Event.SessionUpdateEvent(SessionUpdate.AgentMessageChunk(ContentBlock.Text(line))))
            }
        }
    }

    // ========== 模型/模式/配置管理 ==========

    /** 获取可用模型列表（WebSocket模式） */
    val availableModels: List<ModelInfo>?
        get() = clientSession?.availableModels

    /** 获取可用模式列表（WebSocket模式） */
    val availableModes: List<SessionMode>?
        get() = clientSession?.availableModes

    /** 获取当前配置选项（WebSocket模式） */
    val configOptions: List<SessionConfigOption>?
        get() = clientSession?.configOptions?.value

    /** 获取当前模式ID（WebSocket模式） */
    val currentModeId: SessionModeId?
        get() = clientSession?.currentMode?.value

    /** 获取当前模型ID（WebSocket模式） */
    val currentModelId: ModelId?
        get() = clientSession?.currentModel?.value

    /** 设置当前模型 */
    suspend fun setModel(modelId: ModelId): Result<Unit> {
        return try {
            clientSession?.setModel(modelId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 设置当前模式 */
    suspend fun setMode(modeId: SessionModeId): Result<Unit> {
        return try {
            clientSession?.setMode(modeId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 设置配置选项 */
    suspend fun setConfigOption(configId: SessionConfigId, value: SessionConfigOptionValue): Result<Unit> {
        return try {
            clientSession?.setConfigOption(configId, value)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ========== 断开连接 ==========

    /** 断开连接 */
    fun disconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        isConnected = false
        eventCollectorJob?.cancel()
        eventCollectorJob = null

        // WebSocket 模式清理
        runBlocking {
            try {
                clientSession?.close()
            } catch (_: Exception) {}
        }
        try {
            protocol?.close()
        } catch (_: Exception) {}
        try {
            httpClient?.close()
        } catch (_: Exception) {}
        serverThread?.interrupt()
        serverThread = null
        // 清理所有会话
        sessions.values.forEach { session ->
            try { runBlocking { session.close() } } catch (_: Exception) {}
        }
        sessions.clear()
        _activeSessionId = null
        clientSession = null
        acpClient = null
        protocol = null
        httpClient = null

        // Stdio 模式清理
        try {
            process?.destroy()
        } catch (_: Exception) {}
        process = null
        reader = null
        sessionId = null

        transitionTo(AcpLifecycleState.DISCONNECTED)
        emitEvent(AcpLifecycleEvent.Disconnected("客户端主动断开"))
        appState.connectionState = ConnectionState.DISCONNECTED
    }

    /** 发送中断信号到子进程（Stdio 模式） */
    fun interrupt() {
        process?.let {
            if (System.getProperty("os.name").lowercase().contains("windows")) {
                it.destroyForcibly()
            } else {
                it.destroy()
            }
        }
    }

    /** 检查是否已连接 */
    val isActive: Boolean get() = (clientSession != null) || (isConnected && process?.isAlive == true)

    // ========== 重连机制 ==========

    /**
     * 启动自动重连。
     * 仅在当前状态为 DISCONNECTED 且重连策略不是 NoReconnect 时生效。
     */
    fun startReconnect() {
        if (reconnectStrategy is ReconnectStrategy.NoReconnect) return
        if (_lifecycleState.value != AcpLifecycleState.DISCONNECTED) return
        val config = configSnapshot ?: return

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            var attempt = 0
            while (isActive) {
                attempt++
                val delayMs = when (val strategy = reconnectStrategy) {
                    is ReconnectStrategy.FixedInterval -> strategy.intervalMs
                    is ReconnectStrategy.ExponentialBackoff -> {
                        val delay = (strategy.initialIntervalMs *
                                Math.pow(strategy.multiplier, (attempt - 1).toDouble()))
                            .toLong()
                        minOf(delay, strategy.maxIntervalMs)
                    }
                    is ReconnectStrategy.Custom -> strategy.strategy(attempt)
                    is ReconnectStrategy.NoReconnect -> return@launch
                }
                emitEvent(AcpLifecycleEvent.Reconnecting(attempt, delayMs))
                delay(delayMs)
                val result = connect(config)
                if (result.isSuccess) return@launch
            }
        }
    }

    /**
     * 停止自动重连。
     */
    fun stopReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
    }

    // ========== 销毁 ==========

    /**
     * 销毁客户端，释放所有资源。
     * 调用后不可再使用。
     */
    fun destroy() {
        stopReconnect()
        disconnect()
        scope.cancel()
        transitionTo(AcpLifecycleState.DESTROYED)
        emitEvent(AcpLifecycleEvent.Destroyed)
    }

    // ========== 私有辅助方法 ==========

    fun sendRaw(json: String) {
        val writer = process?.outputStream ?: return
        writer.write((json + "\n").toByteArray())
        writer.flush()
    }

    private fun readResponse(): String? {
        return try {
            reader?.readLine()
        } catch (_: Exception) {
            null
        }
    }

    private fun buildJsonRpcRequest(method: String, params: Map<String, Any?>): String {
        val sb = StringBuilder()
        sb.append("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"")
        sb.append(method)
        sb.append("\",\"params\":")
        sb.append(toJson(params))
        sb.append("}")
        return sb.toString()
    }

    private fun toJson(obj: Any?): String {
        return when (obj) {
            null -> "null"
            is Map<*, *> -> {
                val entries = obj.entries.joinToString(",") { (k, v) ->
                    "\"$k\":${toJson(v)}"
                }
                "{$entries}"
            }
            is List<*> -> {
                val items = obj.joinToString(",") { toJson(it) }
                "[$items]"
            }
            is String -> "\"${obj.replace("\"", "\\\"")}\""
            is Number, is Boolean -> obj.toString()
            else -> "\"$obj\""
        }
    }

    private fun extractSessionId(response: String): String? {
        val sessionIdMarker = "\"sessionId\":\""
        val start = response.indexOf(sessionIdMarker)
        if (start >= 0) {
            val valueStart = start + sessionIdMarker.length
            val end = response.indexOf('"', valueStart)
            if (end >= 0) return response.substring(valueStart, end)
        }
        return null
    }
}

/** TUI 客户端操作实现（WebSocket 模式） */
@OptIn(com.agentclientprotocol.annotations.UnstableApi::class)
private class TuiClientOperations : ClientSessionOperations {
    private val activeTerminals = mutableMapOf<String, Process>()

    override suspend fun requestPermissions(
        toolCall: SessionUpdate.ToolCallUpdate,
        permissions: List<PermissionOption>,
        _meta: JsonElement?
    ): RequestPermissionResponse {
        return RequestPermissionResponse(RequestPermissionOutcome.Selected(permissions.first().optionId))
    }

    override suspend fun notify(notification: SessionUpdate, _meta: JsonElement?) {}

    override suspend fun fsReadTextFile(
        path: String,
        line: UInt?,
        limit: UInt?,
        _meta: JsonElement?
    ): ReadTextFileResponse {
        return ReadTextFileResponse(java.io.File(path).readText())
    }

    override suspend fun fsWriteTextFile(path: String, content: String, _meta: JsonElement?): WriteTextFileResponse {
        java.io.File(path).writeText(content)
        return WriteTextFileResponse()
    }

    override suspend fun terminalCreate(
        command: String, args: List<String>, cwd: String?, env: List<EnvVariable>,
        outputByteLimit: ULong?, _meta: JsonElement?
    ): CreateTerminalResponse {
        val pb = ProcessBuilder(listOf(command) + args)
        if (cwd != null) pb.directory(java.io.File(cwd))
        env.forEach { pb.environment()[it.name] = it.value }
        val process = pb.start()
        val terminalId = java.util.UUID.randomUUID().toString()
        activeTerminals[terminalId] = process
        return CreateTerminalResponse(terminalId)
    }

    override suspend fun terminalOutput(terminalId: String, _meta: JsonElement?): TerminalOutputResponse {
        val process = activeTerminals[terminalId] ?: error("Terminal not found")
        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val output = if (stderr.isNotEmpty()) "$stdout\nSTDERR:\n$stderr" else stdout
        return TerminalOutputResponse(output, truncated = false)
    }

    override suspend fun terminalRelease(terminalId: String, _meta: JsonElement?): ReleaseTerminalResponse {
        activeTerminals.remove(terminalId)
        return ReleaseTerminalResponse()
    }

    override suspend fun terminalWaitForExit(terminalId: String, _meta: JsonElement?): WaitForTerminalExitResponse {
        val process = activeTerminals[terminalId] ?: error("Terminal not found")
        val exitCode = process.waitFor()
        return WaitForTerminalExitResponse(exitCode.toUInt())
    }

    override suspend fun terminalKill(terminalId: String, _meta: JsonElement?): KillTerminalCommandResponse {
        activeTerminals[terminalId]?.destroy()
        return KillTerminalCommandResponse()
    }

    override suspend fun createElicitation(params: CreateElicitationRequest): CreateElicitationResponse {
        return CreateElicitationResponse(
            ElicitationAction.Accept(content = emptyMap())
        )
    }

    override suspend fun completeElicitation(params: CompleteElicitationNotification) {}
}
