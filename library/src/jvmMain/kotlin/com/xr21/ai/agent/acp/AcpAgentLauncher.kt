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
import com.agentclientprotocol.transport.acpProtocolOnServerWebSocket
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
 * Launches an ACP agent as a WebSocket server on the specified host and port.
 *
 * Starts a Ktor embedded Netty server with WebSocket support, binding the
 * ACP protocol to the "/acp" path. Clients can connect to this server
 * via WebSocket at "ws://{host}:{port}/acp".
 *
 * @param agentSupport the agent implementation
 * @param host the host address to bind to (default: "0.0.0.0")
 * @param port the port to listen on (default: 9988)
 * @param protocolOptions optional protocol configuration
 */
@JvmOverloads
fun launchWebSocketServer(
    agentSupport: AgentSupport,
    host: String = "0.0.0.0",
    port: Int = 9988,
    protocolOptions: ProtocolOptions = ProtocolOptions(),
) {
    runBlocking(Dispatchers.IO) {
        val server = embeddedServer(Netty, host = host, port = port) {
            install(ServerWebSockets)
            acpProtocolOnServerWebSocket(
                protocolOptions = protocolOptions,
                withAuth = null
            ) { protocol ->
                Agent(protocol, agentSupport)
                protocol.start()
            }
        }
        println("ACP WebSocket server started at ws://${host}:${port}/acp")
        server.start(wait = true)
    }
}