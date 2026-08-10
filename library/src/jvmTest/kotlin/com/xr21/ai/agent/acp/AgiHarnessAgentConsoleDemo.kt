@file:OptIn(UnstableApi::class)

package com.xr21.ai.agent.acp

import com.agentclientprotocol.annotations.UnstableApi
import com.agentclientprotocol.client.Client
import com.agentclientprotocol.client.ClientInfo
import com.agentclientprotocol.client.ClientSession
import com.agentclientprotocol.common.ClientSessionOperations
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.common.SessionCreationParameters
import com.agentclientprotocol.model.*
import com.agentclientprotocol.protocol.ProtocolOptions
import com.agentclientprotocol.transport.acpProtocolOnClientWebSocket
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import java.util.concurrent.atomic.AtomicInteger
import java.io.File
import java.net.ServerSocket
import java.util.UUID

/**
 * AgiHarnessAgent 控制台调测 Demo。
 *
 * 启动流程：
 * 1. 内嵌 WebSocket 服务器（后台线程）
 * 2. ACP Client 连接并初始化
 * 3. 创建会话
 * 4. 进入 REPL 循环
 *
 * 命令：
 *   /exit      退出
 *   /help      帮助
 *   /mode      查看/切换模式
 *   /model     查看/切换模型
 *   /sessions  列出会话
 *
 * 运行: ./gradlew :library:runHarnessDemo
 */
private const val DEMO_PORT = 19999
private const val WS_URL = "ws://127.0.0.1:$DEMO_PORT/acp"

// ── ANSI 颜色工具 ─────────────────────────────────────────────
private object Color {
    const val RESET = "\u001B[0m"
    const val CYAN = "\u001B[36m"
    const val GREEN = "\u001B[32m"
    const val YELLOW = "\u001B[33m"
    const val MAGENTA = "\u001B[35m"
    const val RED = "\u001B[31m"
    const val BOLD = "\u001B[1m"
    const val DIM = "\u001B[2m"
}

/** 彩色打印辅助 */
private fun cprint(color: String, msg: String) = print("$color$msg${Color.RESET}")
private fun cprintln(color: String, msg: String) = println("$color$msg${Color.RESET}")
private fun ok(msg: String) = println("  ${Color.GREEN}ok${Color.RESET} $msg")
private fun info(msg: String) = println("  ${Color.DIM}$msg${Color.RESET}")
private fun err(msg: String) = cprintln(Color.RED, "  error: $msg")

fun main() = runBlocking {
    cprintln(Color.BOLD + Color.CYAN, "AgiHarnessAgent Console Demo")
    cprintln(Color.DIM, "─".repeat(40))
    println()

    ensurePortAvailable(DEMO_PORT)

    // 1. 启动内嵌 WebSocket 服务器
    info("[1/4] 启动 ACP WebSocket 服务器...")
    val agentSupport = AgiAgent()

    @Suppress("UNUSED")
    val serverThread = Thread {
        launchWebSocketServer(agentSupport, "127.0.0.1", DEMO_PORT)
    }.apply {
        isDaemon = true
        start()
    }
    delay(1500)
    ok("服务器已启动: ${Color.CYAN}ws://127.0.0.1:$DEMO_PORT/acp${Color.RESET}")
    println()

    // 2. 连接 ACP 客户端
    info("[2/4] 连接 ACP 客户端...")
    val httpClient = HttpClient { install(WebSockets) }
    val protocol = httpClient.acpProtocolOnClientWebSocket(WS_URL, ProtocolOptions())
    protocol.start()

    val acpClient = Client(protocol)
    val agentInfo = acpClient.initialize(
        ClientInfo(
            implementation = Implementation(
                "AgiHarnessAgentConsoleDemo", "1.0.0", "ACP console demo"
            )
        )
    )
    ok("Agent: ${Color.BOLD}${agentInfo.implementation?.name}${Color.RESET} v${agentInfo.implementation?.version}")
    ok("协议版本: ${agentInfo.protocolVersion}")

    agentInfo.authMethods?.forEach { auth ->
        info("  auth: ${auth.name}")
    }
    println()

    // 3. 创建会话
    info("[3/4] 创建 ACP 会话...")
    val session = acpClient.newSession(
        SessionCreationParameters(cwd = System.getProperty("user.dir"), mcpServers = emptyList())
    ) { _, _ -> DemoClientOperations() }
    ok("会话 ID: ${Color.CYAN}${session.sessionId}${Color.RESET}")
    ok("可用模型: ${session.availableModels.size} 个")
    ok("可用模式: ${session.availableModes.size} 个")
    println()

    // 4. REPL 循环
    info("[4/4] 进入交互模式")
    println("  输入消息发送给 Agent，输入 ${Color.YELLOW}/help${Color.RESET} 查看命令")
    cprintln(Color.DIM, "─".repeat(50))
    println()

    val reader = System.`in`.bufferedReader()
    var running = true

    while (running) {
        cprint(Color.CYAN, ">")
        print(" ")
        System.out.flush()
        val line = reader.readLine() ?: break
        val input = line.trim()

        when {
            input.startsWith("/") -> running = handleCommand(input, session, acpClient)
            input.isBlank() -> continue
            else -> sendPrompt(session, input)
        }
    }

    // 清理
    cprintln(Color.DIM, "清理中...")
    session.close()
    try {
        acpClient.logout()
    } catch (_: Exception) {
    }
    httpClient.close()
    cprintln(Color.GREEN, "再见！")
}

// ── 命令处理 ──────────────────────────────────────────────────
/** 处理命令，返回 false 表示退出 */
private suspend fun handleCommand(
    input: String,
    session: ClientSession,
    client: Client
): Boolean {
    val cmd = input.split("\\s+".toRegex(), 2)
    return when (cmd[0]) {
        "/exit", "/quit" -> false
        "/help" -> { printHelp(); true }
        "/sessions" -> { listSessions(client); true }
        "/mode" -> { handleModeCommand(session, cmd); true }
        "/model" -> { handleModelCommand(session, cmd); true }
        else -> {
            cprintln(Color.RED, "未知命令: ${cmd[0]}")
            printHelp()
            true
        }
    }
}

private fun printHelp() {
    println("""
  命令
  /exit      退出 Demo
  /help      显示此帮助
  /mode      查看当前模式
  /mode <id>  切换到指定模式
  /model     查看当前模型
  /model <id> 切换到指定模型
  /sessions  列出所有会话
  其他内容作为 prompt 发送给 Agent
    """.trimIndent())
}

private suspend fun listSessions(client: Client) {
    val sessions = client.listSessions(System.getProperty("user.dir"), null, null).toList()
    println("  会话列表 (${sessions.size} 个):")
    sessions.forEach { s ->
        println("    ${Color.CYAN}${s.sessionId}${Color.RESET}  cwd=${s.cwd}")
    }
}

// ── 模式 / 模型 切换 ─────────────────────────────────────────
private suspend fun handleModeCommand(session: ClientSession, parts: List<String>) {
    val modes = session.availableModes
    if (parts.size < 2) {
        val current: SessionModeId = session.currentMode.value
        println("  当前模式: ${Color.CYAN}${current}${Color.RESET}")
        println("  可用模式:")
        modes.forEach { m ->
            val mark = if (m.id == current) " ${Color.GREEN}<-${Color.RESET}" else ""
            println("    ${Color.YELLOW}${m.id}${Color.RESET} - ${m.description}$mark")
        }
        return
    }
    val targetId = parts[1]
    val match = modes.find { it.id.toString() == targetId }
    if (match == null) {
        cprintln(Color.RED, "模式 '$targetId' 不存在")
        return
    }
    session.setMode(match.id)
    ok("已切换到模式: ${Color.CYAN}${match.id}${Color.RESET}")
}

private suspend fun handleModelCommand(session: ClientSession, parts: List<String>) {
    val models = session.availableModels
    if (parts.size < 2) {
        val current: ModelId = session.currentModel.value
        println("  当前模型: ${Color.CYAN}${current}${Color.RESET}")
        println("  可用模型:")
        models.forEach { m ->
            val mark = if (m.modelId == current) " ${Color.GREEN}<-${Color.RESET}" else ""
            println("    ${Color.YELLOW}${m.modelId}${Color.RESET} - ${m.name}$mark")
        }
        return
    }
    val targetId = parts[1]
    val match = models.find { m -> m.modelId.toString() == targetId }
    if (match == null) {
        cprintln(Color.RED, "模型 '$targetId' 不存在")
        return
    }
    session.setModel(match.modelId)
    ok("已切换到模型: ${Color.CYAN}${match.modelId}${Color.RESET}")
}

// ── 流式输出 ──────────────────────────────────────────────────
/** 发送 prompt 并实时显示事件流。 */
private suspend fun sendPrompt(session: ClientSession, text: String) {
    println()
    val flow = session.prompt(content = listOf(ContentBlock.Text(text)))
    var hasThought = false
    var hasMessage = false

    try {
        flow.collect { event ->
            when (event) {
                is Event.SessionUpdateEvent -> {
                    when (val update = event.update) {
                        is SessionUpdate.AgentThoughtChunk -> {
                            val t = (update.content as? ContentBlock.Text)?.text ?: ""
                            if (t.isNotBlank()) {
                                if (!hasThought) {
                                    cprint(Color.DIM + Color.MAGENTA, " thought: ")
                                    hasThought = true
                                }
                                cprint(Color.DIM, t)
                            }
                        }

                        is SessionUpdate.AgentMessageChunk -> {
                            val t = (update.content as? ContentBlock.Text)?.text ?: ""
                            if (t.isNotBlank()) {
                                if (!hasMessage) {
                                    if (hasThought) println()
                                    cprint(Color.GREEN, "  response: ")
                                    hasMessage = true
                                }
                                print(t)
                                System.out.flush()
                            }
                        }

                        is SessionUpdate.ToolCall -> {
                            println()
                            println("  ${Color.YELLOW}tool: ${update.title}${Color.RESET} ${Color.RED}PENDING${Color.RESET}  (${update.rawInput})")
                        }

                        is SessionUpdate.ToolCallUpdate -> {
                            if (update.status != null) {
                                println("  ${Color.YELLOW}tool: ${update.title}${Color.RESET} ${update.status}  ${update.rawOutput ?: ""} ${update.content}")
                            }
                        }

                        is SessionUpdate.UsageUpdate -> {
                            println()
                            info("token: ${update.used}")
                        }

                        else -> {}
                    }
                }

                is Event.PromptResponseEvent -> {
                    val reason = event.response.stopReason
                    println()
                    info("stopReason: $reason")
                    event.response.usage?.let { u ->
                        info("usage: in=${u.inputTokens} out=${u.outputTokens} total=${u.totalTokens}")
                    }
                }
            }
        }
    } catch (e: Exception) {
        println()
        err(e.message ?: "unknown error")
    }
    println()
}

// ── 端口检测 ──────────────────────────────────────────────────
private fun ensurePortAvailable(port: Int) {
    var retries = 3
    while (retries-- > 0) {
        try {
            ServerSocket(port).use { return }
        } catch (_: java.io.IOException) {
            if (retries == 0) {
                err("端口 $port 被占用，无法启动服务器")
                throw java.io.IOException("Port $port is not available")
            }
            cprintln(Color.YELLOW, "端口 $port 被占用，等待释放... ($retries 次重试剩余)")
            Thread.sleep(2000)
        }
    }
}

// ── Demo 用的 ClientSessionOperations ────────────────────────
/** 自动批准权限，日志输出到控制台。 */
private class DemoClientOperations : ClientSessionOperations {
    private val activeTerminals = mutableMapOf<String, Process>()
    private val permCounter = AtomicInteger(0)

    override suspend fun requestPermissions(
        toolCall: SessionUpdate.ToolCallUpdate,
        permissions: List<PermissionOption>,
        _meta: JsonElement?
    ): RequestPermissionResponse {
        val n = permCounter.incrementAndGet()
        println("  ${Color.YELLOW}permission #$n: ${toolCall.title}${Color.RESET}")
        val selected = permissions.firstOrNull()
            ?: return RequestPermissionResponse(RequestPermissionOutcome.Cancelled)
        ok("   -> auto-approve: ${selected.name}")
        return RequestPermissionResponse(RequestPermissionOutcome.Selected(selected.optionId))
    }

    override suspend fun notify(notification: SessionUpdate, _meta: JsonElement?) {
        // ignore notifications in console demo
    }

    override suspend fun fsReadTextFile(
        path: String, line: UInt?, limit: UInt?, _meta: JsonElement?
    ): ReadTextFileResponse {
        return try {
            ReadTextFileResponse(File(path).readText())
        } catch (e: Exception) {
            ReadTextFileResponse("Error reading file: ${e.message}")
        }
    }

    override suspend fun fsWriteTextFile(
        path: String, content: String, _meta: JsonElement?
    ): WriteTextFileResponse {
        try {
            File(path).also { it.parentFile?.mkdirs() }.writeText(content)
        } catch (e: Exception) {
            err("写入文件失败: ${e.message}")
        }
        return WriteTextFileResponse()
    }

    override suspend fun terminalCreate(
        command: String, args: List<String>, cwd: String?,
        env: List<EnvVariable>, outputByteLimit: ULong?, _meta: JsonElement?
    ): CreateTerminalResponse {
        val pb = ProcessBuilder(listOf(command) + args)
        if (cwd != null) pb.directory(File(cwd))
        env.forEach { pb.environment()[it.name] = it.value }
        val proc = pb.start()
        val tid = UUID.randomUUID().toString()
        activeTerminals[tid] = proc
        return CreateTerminalResponse(tid)
    }

    override suspend fun terminalOutput(
        terminalId: String, _meta: JsonElement?
    ): TerminalOutputResponse {
        val proc = activeTerminals[terminalId]
            ?: error("Terminal $terminalId 不存在")
        val stdout = proc.inputStream.bufferedReader().readText()
        val stderr = proc.errorStream.bufferedReader().readText()
        return TerminalOutputResponse(
            if (stderr.isNotEmpty()) "$stdout\nSTDERR:\n$stderr" else stdout,
            truncated = false
        )
    }

    override suspend fun terminalWaitForExit(
        terminalId: String, _meta: JsonElement?
    ): WaitForTerminalExitResponse {
        val proc = activeTerminals[terminalId]
            ?: error("Terminal $terminalId 不存在")
        return WaitForTerminalExitResponse(proc.waitFor().toUInt())
    }

    override suspend fun terminalKill(
        terminalId: String, _meta: JsonElement?
    ): KillTerminalCommandResponse {
        activeTerminals[terminalId]?.destroy()
        return KillTerminalCommandResponse()
    }

    override suspend fun terminalRelease(
        terminalId: String, _meta: JsonElement?
    ): ReleaseTerminalResponse {
        activeTerminals.remove(terminalId)
        return ReleaseTerminalResponse()
    }

    override suspend fun createElicitation(
        request: CreateElicitationRequest
    ): CreateElicitationResponse {
        return CreateElicitationResponse(ElicitationAction.Accept(content = emptyMap()))
    }

    override suspend fun completeElicitation(
        notification: CompleteElicitationNotification
    ) {
    }
}
