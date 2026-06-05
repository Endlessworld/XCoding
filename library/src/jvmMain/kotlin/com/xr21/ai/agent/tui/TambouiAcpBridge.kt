/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 */
package com.xr21.ai.agent.tui

import com.agentclientprotocol.common.Event
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.model.ToolCallContent
import com.agentclientprotocol.model.ToolCallStatus
import com.xr21.ai.agent.tui.acp.AcpClientManager
import com.xr21.ai.agent.tui.config.TuiConfig
import com.xr21.ai.agent.tui.java.TambouiTuiApp
import com.xr21.ai.agent.tui.state.AppState
import kotlinx.coroutines.*
import com.xr21.ai.agent.tui.java.AppState as JavaAppState

/**
 * Tamboui TUI 的 ACP 桥接层
 *
 * 将 Kotlin 协程的 ACP SDK 封装为 Java 友好的回调接口，
 * 供 [TambouiTuiApp] 使用。
 */
class TambouiAcpBridge(private val javaAppState: JavaAppState) : TambouiTuiApp.AcpBridge {

    private val ktAppState = AppState()
    private val acpClient = AcpClientManager(ktAppState)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var callback: TambouiTuiApp.ConnectionCallback? = null
    private var config: TuiConfig = TuiConfig()

    override fun connect(args: Array<String>, callback: TambouiTuiApp.ConnectionCallback) {
        this.callback = callback
        this.config = parseConfig(args)

        scope.launch {
            val result = acpClient.connect(config)
            if (result.isSuccess) {
                callback.onConnected(
                    ktAppState.agentName,
                    ktAppState.agentVersion,
                    ktAppState.modelName
                )
                acpClient.startEventCollection { event ->
                    handleEvent(event)
                }
            } else {
                callback.onError(result.exceptionOrNull()?.message ?: "未知错误")
            }
        }
    }

    override fun sendMessage(message: String) {
        scope.launch {
            val result = acpClient.sendPrompt(message)
            if (result.isFailure) {
                callback?.onError("发送失败: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    override fun cancel() {
        scope.launch {
            acpClient.sendCancel()
        }
    }

    override fun disconnect() {
        scope.cancel()
        acpClient.disconnect()
    }

    private fun handleEvent(event: Event) {
        val javaEvent = when (event) {
            is Event.SessionUpdateEvent -> AcpEventAdapter(event.update)
            else -> null
        }
        javaEvent?.let { callback?.onEvent(it) }
    }

    private fun parseConfig(args: Array<String>): TuiConfig {
        var cfg = TuiConfig()
        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "--command" -> {
                    val parts = mutableListOf<String>()
                    var j = i + 1
                    while (j < args.size && !args[j].startsWith("-")) {
                        parts.add(args[j])
                        j++
                    }
                    if (parts.isNotEmpty()) {
                        cfg = cfg.copy(agentCommand = parts)
                    }
                    i = j - 1
                }
                "--ws-url" -> {
                    if (i + 1 < args.size && !args[i + 1].startsWith("-")) {
                        cfg = cfg.copy(webSocketUrl = args[i + 1])
                        i++
                    }
                }
                "--ws-server-port" -> {
                    if (i + 1 < args.size) {
                        args[i + 1].toIntOrNull()?.let { port ->
                            cfg = cfg.copy(webSocketServerPort = port)
                        }
                        i++
                    }
                }
            }
            i++
        }
        return cfg
    }
}

/**
 * ACP 事件适配器，将 Kotlin ACP 事件转换为 Java 侧的 AppState 更新
 */
class AcpEventAdapter(private val update: SessionUpdate) : TambouiTuiApp.AcpEvent {
    override fun apply(state: JavaAppState) {
        when (update) {
            is SessionUpdate.AgentMessageChunk -> {
                val text = (update.content as? ContentBlock.Text)?.text ?: ""
                state.appendStreamingContent(text)
            }
            is SessionUpdate.AgentThoughtChunk -> {
                val text = (update.content as? ContentBlock.Text)?.text ?: ""
                state.appendThoughtContent(text)
            }
            is SessionUpdate.ToolCall -> {
                val args = extractText(update.content)
                state.addToolCall(update.title, args)
            }
            is SessionUpdate.ToolCallUpdate -> {
                when (update.status) {
                    ToolCallStatus.COMPLETED -> {
                        val result = extractText(update.content ?: emptyList())
                        state.addToolResult(result)
                    }
                    else -> {
                        val content = extractText(update.content ?: emptyList())
                        if (content.isNotEmpty()) state.appendToolCallUpdate(content)
                    }
                }
            }
            is SessionUpdate.PlanUpdate -> {
                state.clearTodos()
                update.entries.forEach { entry ->
                    val priorityName = entry.priority.name
                    val statusName = entry.status.name
                    state.addTodo(entry.content, statusName, priorityName)
                }
            }
            is SessionUpdate.UsageUpdate -> {
                state.setTotalTokens(update.used)
            }
            else -> {}
        }
    }

    private fun extractText(content: List<ToolCallContent>): String {
        return content.firstOrNull()?.let { c ->
            (c as? ToolCallContent.Content)?.let { (it.content as? ContentBlock.Text)?.text }
        } ?: ""
    }
}
