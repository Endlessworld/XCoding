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

import com.agentclientprotocol.client.Client
import com.agentclientprotocol.client.ClientInfo
import com.agentclientprotocol.client.ClientSession
import com.agentclientprotocol.common.ClientSessionOperations
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.common.SessionCreationParameters
import com.agentclientprotocol.launcher.AgiAgent
import com.agentclientprotocol.launcher.launchWebSocketServer
import com.agentclientprotocol.model.*
import com.agentclientprotocol.protocol.Protocol
import com.agentclientprotocol.protocol.ProtocolOptions
import com.agentclientprotocol.transport.acpProtocolOnClientWebSocket
import com.xr21.ai.agent.tui.config.ACPConnectConfig
import com.xr21.ai.agent.tui.java.AppState
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonElement
import java.io.BufferedReader

/**
 * ACP 客户端管理器
 *
 * 支持两种连接模式：
 * 1. WebSocket 模式（默认）：通过 WebSocket 连接到 ACP Agent 服务
 *    - 如果未配置外部 ws-url，自动在后台启动内部 WebSocket 服务器
 * 2. Stdio 模式（兼容）：通过 --command 参数启动 Agent 子进程
 */
class AcpClientManager(private val appState: AppState) {

    // WebSocket 模式状态
    private var httpClient: HttpClient? = null
    private var protocol: Protocol? = null
    private var acpClient: Client? = null
    private var clientSession: ClientSession? = null
    private var serverThread: Thread? = null

    // Stdio 模式状态（向后兼容）
    private var process: Process? = null
    private var isConnected = false
    private var reader: BufferedReader? = null
    private var eventCollectorJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    /** 当前会话 ID */
    var sessionId: String? = null
        private set

    private var eventHandler: ((Event) -> Unit)? = null

    /** 建立 ACP 连接（WebSocket 优先，回退到 Stdio） */
    suspend fun connect(config: ACPConnectConfig): Result<Unit> {
        return if (config.agentCommand.isNotEmpty()) {
            connectStdio(config.agentCommand)
        } else {
            connectWebSocket(config)
        }
    }

    /** WebSocket 模式连接 */
    private suspend fun connectWebSocket(config: ACPConnectConfig): Result<Unit> {
        return try {
            appState.connectionState = ConnectionState.CONNECTING

            val wsUrl = if (config.webSocketUrl.isNotEmpty()) {
                config.webSocketUrl
            } else {
                startInternalServer(config.webSocketServerPort)
                delay(800)
                "ws://127.0.0.1:${config.webSocketServerPort}/acp"
            }

            val client = HttpClient { install(WebSockets) }
            httpClient = client

            val proto = client.acpProtocolOnClientWebSocket(wsUrl, ProtocolOptions())
            proto.start()
            protocol = proto

            val acpClient = Client(proto)
            this.acpClient = acpClient

            val agentInfo = acpClient.initialize(
                ClientInfo(implementation = Implementation("XAgent TUI", "0.1.0", "XAgent TUI"))
            )
            appState.agentName = agentInfo.implementation?.name ?: "Unknown"
            appState.agentVersion = agentInfo.implementation?.version ?: ""

            val session = acpClient.newSession(
                SessionCreationParameters(cwd = System.getProperty("user.dir"), mcpServers = emptyList())
            ) { _, _ -> TuiClientOperations() }

            clientSession = session
            sessionId = session.sessionId.toString()

            appState.connectionState = ConnectionState.CONNECTED
            Result.success(Unit)
        } catch (e: Exception) {
            appState.connectionState = ConnectionState.DISCONNECTED_ERROR
            appState.errorMessage = "WebSocket 连接失败: ${e.message}"
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
            appState.connectionState = ConnectionState.CONNECTED
            Result.success(Unit)
        } catch (e: Exception) {
            appState.connectionState = ConnectionState.DISCONNECTED_ERROR
            appState.errorMessage = "连接失败: ${e.message}"
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

    /** 发送 ACP prompt 消息 */
    suspend fun sendPrompt(content: String): Result<Unit> {
        return try {
            val session = clientSession
            if (session != null) {
                // WebSocket 模式：收集事件流
                val handler = eventHandler ?: return Result.failure(Exception("事件处理器未设置"))
                session.prompt(listOf(ContentBlock.Text(content))).collect { event ->
                    handler(event)
                }
                Result.success(Unit)
            } else if (isConnected && process != null) {
                // Stdio 模式
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

    /** 断开连接 */
    fun disconnect() {
        isConnected = false
        eventCollectorJob?.cancel()
        eventCollectorJob = null

        // WebSocket 模式清理
        try {
            protocol?.close()
        } catch (_: Exception) {
        }
        try {
            httpClient?.close()
        } catch (_: Exception) {
        }
        serverThread?.interrupt()
        serverThread = null
        clientSession = null
        acpClient = null
        protocol = null
        httpClient = null

        // Stdio 模式清理
        try {
            process?.destroy()
        } catch (_: Exception) {
        }
        process = null
        reader = null
        sessionId = null
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

    // ========== 私有辅助方法 ==========

    private fun sendRaw(json: String) {
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