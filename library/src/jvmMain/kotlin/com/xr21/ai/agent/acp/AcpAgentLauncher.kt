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
@file:JvmName("AcpAgentLauncher")
package com.xr21.ai.agent.acp

import com.agentclientprotocol.agent.Agent
import com.agentclientprotocol.agent.AgentSupport
import com.agentclientprotocol.protocol.Protocol
import com.agentclientprotocol.protocol.ProtocolOptions
import com.agentclientprotocol.transport.StdioTransport
import com.agentclientprotocol.transport.acpProtocolOnClientWebSocket
import io.ktor.client.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.server.websocket.WebSockets as ServerWebSockets
import com.agentclientprotocol.model.PROTOCOL_VERSION_V2
import com.agentclientprotocol.rpc.ACPJson
import com.agentclientprotocol.rpc.JsonRpcMessage
import com.agentclientprotocol.rpc.decodeJsonRpcMessage
import com.agentclientprotocol.transport.BaseTransport
import io.ktor.server.routing.routing
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.coroutines.cancellation.CancellationException

/**
 * Launches an ACP agent over STDIO transport.
 *
 * Reads NDJSON messages from stdin and writes responses to stdout.
 * This is the standard transport for command-line agent processes.
 */
@JvmOverloads
fun launchStdioAgent(
    agentSupport: AgentSupport,
    transportName: String = "stdio-agent"
) {
    runBlocking(Dispatchers.IO) {
        val transport = StdioTransport(
            this, Dispatchers.IO,
            System.`in`.asSource().buffered(),
            System.out.asSink().buffered(),
            transportName
        )

        val protocol = Protocol(this, transport)
        Agent(protocol, agentSupport)
        protocol.start()
    }
}

/**
 * Creates a Ktor [HttpClient] with WebSockets plugin installed.
 *
 * Convenience function for Java interop.
 */
@JvmName("createWebSocketClient")
fun createWebSocketClient(): HttpClient = HttpClient {
    install(ClientWebSockets.Plugin)
}

/**
 * Launches an ACP agent over WebSocket transport using the
 * acp-ktor-client library's [acpProtocolOnClientWebSocket] extension.
 *
 * Connects to a remote ACP server via WebSocket at the specified URL
 * and communicates using NDJSON messages.
 *
 * @param agentSupport the agent implementation
 * @param url the WebSocket URL to connect to (e.g. "ws://localhost:8080/acp")
 * @param client the Ktor [HttpClient] with WebSockets plugin installed
 * @param protocolOptions optional protocol configuration
 */
@JvmOverloads
fun launchWebSocketAgent(
    agentSupport: AgentSupport,
    url: String,
    client: HttpClient,
    protocolOptions: ProtocolOptions = ProtocolOptions(),
) {
    runBlocking(Dispatchers.IO) {
        val protocol = client.acpProtocolOnClientWebSocket(url, protocolOptions)
        Agent(protocol, agentSupport)
        protocol.start()
        println("start " + url)
    }
}

/**
 * Launches a single-endpoint ACP WebSocket server that serves BOTH protocol v1 and v2
 * clients on the single path `ws://{host}:{port}/acp`.
 *
 * The protocol version for each connection is chosen automatically from the client's
 * `initialize` `protocolVersion`: version 2 when [v2AgentSupport] is provided and the client
 * asks for v2, otherwise version 1 ([agentSupport]). No separate `/acp/v2` endpoint is used.
 *
 * @param agentSupport v1 agent implementation (e.g. [AgiAgent])
 * @param v2AgentSupport v2 agent implementation (e.g. [AgiV2AgentSupport]); pass null to serve v1 only
 * @param host the host address to bind to (default: "0.0.0.0")
 * @param port the port to listen on (default: 9988)
 * @param protocolOptions optional protocol configuration
 */
@JvmOverloads
@OptIn(com.agentclientprotocol.annotations.UnstableApi::class)
fun launchWebSocketServer(
    agentSupport: AgentSupport,
    v2AgentSupport: com.agentclientprotocol.agent.v2.AgentSupport?,
    host: String = "0.0.0.0",
    port: Int = 9988,
    protocolOptions: ProtocolOptions = ProtocolOptions(),
) {
    runBlocking(Dispatchers.IO) {
        val server = embeddedServer(Netty, host = host, port = port) {
            install(ServerWebSockets)
            routing {
                webSocket("/acp") {
                    // 1) 先读取该连接的首条报文以嗅探客户端声明的协议版本
                    val firstFrame: io.ktor.websocket.Frame? =
                        runCatching { incoming.receive() }.getOrNull()
                    val firstText: String? =
                        (firstFrame as? io.ktor.websocket.Frame.Text)?.readText()
                    val requested = sniffProtocolVersion(firstText)
                    val useV2 = v2AgentSupport != null && requested == PROTOCOL_VERSION_V2

                    // 2) 构造可回放首条报文的传输，并基于同一 Protocol 挂载对应版本 Agent
                    val firstMessage: JsonRpcMessage? =
                        firstText?.let { runCatching { decodeJsonRpcMessage(it) }.getOrNull() }
                    val transport = ReplayingWebSocketTransport(this, this, firstMessage)
                    val protocol = Protocol(this, transport, protocolOptions)

                    if (useV2) {
                        com.agentclientprotocol.agent.v2.Agent(protocol, v2AgentSupport!!)
                    } else {
                        Agent(protocol, agentSupport)
                    }
                    protocol.start()
                    kotlinx.coroutines.awaitCancellation()
                }
            }
        }
        println("ACP WebSocket server started (v1+v2 auto on single /acp): ws://${host}:${port}/acp")
        server.start(wait = true)
    }
}

/**
 * 兼容旧调用的便捷入口：仅以 v1 方式服务，仍绑定唯一 /acp 端点。
 */
@JvmOverloads
fun launchWebSocketServer(
    agentSupport: AgentSupport,
    host: String = "0.0.0.0",
    port: Int = 9988,
    protocolOptions: ProtocolOptions = ProtocolOptions(),
) {
    launchWebSocketServer(agentSupport, null, host, port, protocolOptions)
}

/** 解析客户端首条 NDJSON 文本中的 `params.protocolVersion`；解析不到时默认按 v1 处理。 */
private fun sniffProtocolVersion(text: String?): Int {
    if (text == null) return 1
    val root = runCatching { ACPJson.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return 1
    val params = root["params"] as? JsonObject ?: return 1
    val primitive = params["protocolVersion"] as? JsonPrimitive ?: return 1
    return primitive.content.toIntOrNull() ?: 1
}

/**
 * 可“回放首条报文”的 WebSocket 传输。
 *
 * [firstMessage] 已在外部被预先读取用于版本嗅探；此处先把该首条报文投递给
 * Protocol（完成握手），随后才开始消费连接上剩余的帧。
 */
private class ReplayingWebSocketTransport(
    parentScope: CoroutineScope,
    private val wss: io.ktor.websocket.WebSocketSession,
    private val firstMessage: JsonRpcMessage? = null,
) : BaseTransport() {
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))
    private val sendChannel = Channel<JsonRpcMessage>(Channel.UNLIMITED)

    override fun start() {
        // 出站
        scope.launch {
            try {
                for (message in sendChannel) {
                    val jsonText = runCatching { ACPJson.encodeToString(message) }
                        .getOrElse { fireError(it); continue }
                    wss.send(io.ktor.websocket.Frame.Text(jsonText))
                    wss.flush()
                }
                wss.close()
                wss.flush()
            } catch (ce: CancellationException) {
                wss.close(io.ktor.websocket.CloseReason(io.ktor.websocket.CloseReason.Codes.NORMAL, "Cancelled"))
            } catch (t: Throwable) {
                fireError(t)
                wss.close(io.ktor.websocket.CloseReason(io.ktor.websocket.CloseReason.Codes.INTERNAL_ERROR, t.message ?: "Internal error"))
                wss.flush()
            }
        }
        // 入站：先回放首条报文，再消费后续帧
        scope.launch {
            try {
                if (firstMessage != null) fireMessage(firstMessage)
                for (message in wss.incoming) {
                    if (message is io.ktor.websocket.Frame.Text) {
                        val text = message.readText()
                        val decoded = try {
                            decodeJsonRpcMessage(text)
                        } catch (e: SerializationException) {
                            fireError(e); continue
                        }
                        fireMessage(decoded)
                    }
                }
            } catch (ce: CancellationException) {
                // 忽略：连接已关闭
            } catch (t: Throwable) {
                fireError(t)
            } finally {
                close()
            }
        }
    }

    override fun send(message: JsonRpcMessage) {
        sendChannel.trySend(message)
    }

    override fun close() {
        if (sendChannel.close()) fireClose()
    }
}
