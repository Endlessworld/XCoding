//@file:OptIn(UnstableApi::class)
//
//package com.xr21.ai.agent.acp
//
//import com.agentclientprotocol.annotations.UnstableApi
//import com.agentclientprotocol.client.Client
//import com.agentclientprotocol.client.ClientInfo
//import com.agentclientprotocol.client.v2.Client as V2Client
//import com.agentclientprotocol.client.v2.ClientInfo as V2ClientInfo
//import com.agentclientprotocol.common.SessionCreationParameters
//import com.agentclientprotocol.model.Implementation
//import com.agentclientprotocol.model.PROTOCOL_VERSION_V2
//import com.agentclientprotocol.model.v2.ClientCapabilities as V2ClientCapabilities
//import com.agentclientprotocol.model.v2.RequestPermissionOutcome
//import com.agentclientprotocol.model.v2.RequestPermissionResponse
//import com.agentclientprotocol.protocol.ProtocolOptions
//import com.agentclientprotocol.transport.acpProtocolOnClientWebSocket
//import io.ktor.client.*
//import io.ktor.client.plugins.websocket.*
//import kotlinx.coroutines.delay
//import kotlinx.coroutines.runBlocking
//
///**
// * 单端点版本协商冒烟验证：
// * 1. 启动 launchWebSocketServer（同一 /acp 端点按客户端协议版本自动提供 v1 / v2）
// * 2. v1 客户端连 /acp：initialize + newSession
// * 3. v2 客户端也连同一个 /acp：initialize + newSession
// */
//fun main() = runBlocking {
//    val port = randomAvailablePort()
//    println("===== Dual-Protocol Smoke Test =====")
//    println("port=$port")
//
//    // 1. 启动单端点（自动协商 v1/v2）服务器
//    Thread {
//        launchWebSocketServer(
//            AgiAgent(),
//            AgiV2AgentSupport(AgiAgent()),
//            "127.0.0.1",
//            port
//        )
//    }.apply { isDaemon = true; start() }
//    delay(2000)
//
//    // 2. v1 客户端连 /acp
//    println("--- v1 client -> ws://127.0.0.1:$port/acp ---")
//    runCatching {
//        val http = HttpClient { install(WebSockets) }
//        val p1 = http.acpProtocolOnClientWebSocket("ws://127.0.0.1:$port/acp", ProtocolOptions())
//        p1.start()
//        val c1 = Client(p1)
//        val info1 = c1.initialize(ClientInfo(implementation = Implementation("SmokeTest", "1.0.0", "v1")))
//        println("v1 initialize OK: agent=${info1.implementation?.name}, protocolVersion=${info1.protocolVersion}")
//        val s1 = c1.newSession(
//            SessionCreationParameters(
//                cwd = System.getProperty("user.dir"),
//                mcpServers = emptyList()
//            )
//        ) { _, _ -> object : com.agentclientprotocol.common.ClientSessionOperations {} }
//        println("v1 newSession OK: sessionId=${s1.sessionId}")
//        p1.close()
//        http.close()
//    }.onFailure { println("v1 FAILED: ${it}"); it.printStackTrace() }
//
//    // 3. v2 客户端连同一个 /acp（自动协商到 v2）
//    println("--- v2 client -> ws://127.0.0.1:$port/acp ---")
//    runCatching {
//        val http = HttpClient { install(WebSockets) }
//        val p2 = http.acpProtocolOnClientWebSocket("ws://127.0.0.1:$port/acp", ProtocolOptions())
//        p2.start()
//        val c2 = V2Client(p2)
//        val info2 = c2.initialize(
//            V2ClientInfo(
//                protocolVersion = PROTOCOL_VERSION_V2,
//                implementation = Implementation("SmokeTest", "1.0.0", "v2"),
//                capabilities = V2ClientCapabilities()
//            )
//        )
//        println("v2 initialize OK: agent=${info2.implementation?.name}")
//        val ops = object : com.agentclientprotocol.client.v2.ClientSessionOperations {
//            override suspend fun requestPermission(
//                request: com.agentclientprotocol.model.v2.RequestPermissionRequest
//            ): RequestPermissionResponse =
//                RequestPermissionResponse(outcome = RequestPermissionOutcome.Cancelled)
//        }
//        val s2 = c2.newSession(
//            cwd = System.getProperty("user.dir"),
//            operations = ops
//        )
//        println("v2 newSession OK: sessionId=${s2.sessionId}")
//        p2.close()
//        http.close()
//    }.onFailure { println("v2 FAILED: ${it}"); it.printStackTrace() }
//
//    println("===== Smoke Test Done =====")
//    kotlin.system.exitProcess(0)
//}