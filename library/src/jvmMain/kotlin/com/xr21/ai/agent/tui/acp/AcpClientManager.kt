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

import com.xr21.ai.agent.tui.state.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.io.BufferedReader

/**
 * ACP 客户端管理器
 *
 * 管理与 Agent 子进程的通信。
 * 通过 Stdio 与 Agent 建立 ACP 协议连接。
 *
 * 通信协议：
 * - 发送：NDJSON 格式的 JSON-RPC 请求到子进程的 stdin
 * - 接收：从子进程的 stdout 读取 NDJSON 格式的响应和事件
 *
 * 握手流程：
 * 1. 启动 Agent 子进程
 * 2. 发送 initialize 请求
 * 3. 接收 initialized 响应
 * 4. 发送 session/new 请求
 * 5. 接收 sessionId 响应
 */
class AcpClientManager(private val appState: AppState) {

    private var process: Process? = null
    private var isConnected = false
    private var reader: BufferedReader? = null
    private var eventCollectorJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    /** 当前会话 ID */
    var sessionId: String? = null
        private set

    /** 启动 Agent 子进程并建立连接 */
    suspend fun connect(command: List<String>): Result<Unit> {
        return try {
            appState.connectionState = ConnectionState.CONNECTING

            val pb = ProcessBuilder(command)
                .redirectErrorStream(true)

            process = pb.start()
            reader = process!!.inputStream.bufferedReader()
            isConnected = true

            // 执行 ACP 握手
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

    /**
     * ACP 握手流程
     */
    private suspend fun performHandshake(): Result<Unit> {
        return try {
            // 步骤1: 发送 initialize 请求
            val initializeRequest = buildJsonRpcRequest("initialize", mapOf(
                "protocolVersion" to "0.1.0",
                "clientInfo" to mapOf(
                    "name" to "XAgent TUI",
                    "version" to "0.1.0"
                )
            ))
            sendRaw(initializeRequest)

            // 等待 initialized 响应
            val initResponse = readResponse() ?: return Result.failure(Exception("未收到 initialize 响应"))

            // 步骤2: 发送 session/new 请求
            val sessionRequest = buildJsonRpcRequest("session/new", mapOf(
                "cwd" to System.getProperty("user.dir")
            ))
            sendRaw(sessionRequest)

            // 等待 session 创建响应
            val sessionResponse = readResponse() ?: return Result.failure(Exception("未收到 session/new 响应"))

            // 解析 sessionId
            val sessionId = extractSessionId(sessionResponse)
            this.sessionId = sessionId

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 发送 ACP prompt 消息 */
    suspend fun sendPrompt(content: String): Result<Unit> {
        return try {
            val sid = sessionId ?: return Result.failure(Exception("会话未创建"))
            val promptRequest = buildJsonRpcRequest("session/prompt", mapOf(
                "sessionId" to sid,
                "content" to listOf(mapOf(
                    "type" to "text",
                    "text" to content
                ))
            ))
            sendRaw(promptRequest)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 发送中断信号 */
    suspend fun sendCancel(): Result<Unit> {
        return try {
            val sid = sessionId ?: return Result.failure(Exception("会话未创建"))
            val cancelRequest = buildJsonRpcRequest("session/cancel", mapOf(
                "sessionId" to sid
            ))
            sendRaw(cancelRequest)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 接收 ACP 事件流 */
    fun receiveEvents(): Flow<String> = flow {
        val r = reader ?: return@flow
        while (isConnected) {
            try {
                val line = r.readLine() ?: break
                if (line.isNotBlank()) {
                    emit(line)
                }
            } catch (e: Exception) {
                break
            }
        }
    }

    /** 启动后台事件收集协程 */
    fun startEventCollection(onEvent: (String) -> Unit) {
        eventCollectorJob = scope.launch {
            receiveEvents().collect { event ->
                onEvent(event)
            }
        }
    }

    /** 断开连接 */
    fun disconnect() {
        isConnected = false
        eventCollectorJob?.cancel()
        eventCollectorJob = null
        try {
            process?.destroy()
        } catch (_: Exception) {}
        process = null
        reader = null
        sessionId = null
        appState.connectionState = ConnectionState.DISCONNECTED
    }

    /** 发送中断信号到子进程 */
    fun interrupt() {
        process?.let {
            // Windows: 使用 Ctrl+Break 信号
            if (System.getProperty("os.name").lowercase().contains("windows")) {
                it.destroyForcibly()
            } else {
                // Unix: 发送 SIGINT
                it.destroy()
            }
        }
    }

    /** 检查是否已连接 */
    val isActive: Boolean get() = isConnected && process?.isAlive == true

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
        // 尝试从 JSON 响应中提取 sessionId
        // 格式: {"jsonrpc":"2.0","id":1,"result":{"sessionId":"xxx",...}}
        val sessionIdMarker = "\"sessionId\":\""
        val start = response.indexOf(sessionIdMarker)
        if (start >= 0) {
            val valueStart = start + sessionIdMarker.length
            val end = response.indexOf('"', valueStart)
            if (end >= 0) {
                return response.substring(valueStart, end)
            }
        }
        return null
    }
}