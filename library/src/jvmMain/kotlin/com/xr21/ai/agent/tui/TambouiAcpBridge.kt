/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 */
package com.xr21.ai.agent.tui

import com.agentclientprotocol.common.Event
import com.agentclientprotocol.model.*
import com.xr21.ai.agent.tui.AppState
import com.xr21.ai.agent.tui.acp.AcpClientManager
import com.xr21.ai.agent.tui.config.ACPConnectConfig
import kotlinx.coroutines.*
import com.xr21.ai.agent.tui.AppState as JavaAppState

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
    private var config: ACPConnectConfig = ACPConnectConfig()

    override fun connect(args: Array<String>, callback: TambouiTuiApp.ConnectionCallback) {
        this.callback = callback
        this.config = parseConfig(args)

        scope.launch {
            val result = acpClient.connect(config)
            if (result.isSuccess) {
                callback.onConnected(
                    ktAppState.agentName, ktAppState.agentVersion, ktAppState.modelName
                )

                // Fetch initial capabilities and notify Java side
                notifyInitialConfig()

                acpClient.startEventCollection { event ->
                    handleEvent(event)
                }
            } else {
                callback.onError(result.exceptionOrNull()?.message ?: "未知错误")
            }
        }
    }

    private fun notifyInitialConfig() {
        val models = acpClient.availableModels?.map { ModelInfo(it.modelId.value, it.name) } ?: emptyList()
        val modes = acpClient.availableModes?.map { ModeInfo(it.id.value, it.name) } ?: emptyList()
        val configOpts = acpClient.configOptions?.map { opt ->
            when (opt) {
                is SessionConfigOption.BooleanOption -> {
                    ConfigOption(opt.id.value, opt.name, "boolean", opt.currentValue.toString(), emptyList())
                }

                is SessionConfigOption.Select -> {
                    val opts = when (val o = opt.options) {
                        is SessionConfigSelectOptions.Flat -> o.options.map { it.name }
                        is SessionConfigSelectOptions.Grouped -> o.groups.flatMap { it.options.map { s -> s.name } }
                    }
                    ConfigOption(opt.id.value, opt.name, "select", opt.currentValue.value, opts)
                }
            }
        } ?: emptyList()
        val currentMode = acpClient.currentModeId?.value ?: ""
        val currentModel = acpClient.currentModelId?.value ?: ""

        callback?.onEvent(object : TambouiTuiApp.AcpEvent {
            override fun apply(state: JavaAppState) {
                state.setAvailableModels(models)
                state.setAvailableModes(modes)
                state.setConfigOptions(configOpts)
                state.setCurrentModeId(currentMode)
                state.setCurrentModelId(currentModel)
                // Also update modelName for status bar
                if (currentModel.isNotEmpty()) {
                    val modelName = models.find { it.id == currentModel }?.name ?: currentModel
                    state.modelName = modelName
                }
            }
        })
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

    override fun setModel(modelId: String) {
        scope.launch {
            acpClient.setModel(ModelId(modelId))
        }
    }

    override fun setMode(modeId: String) {
        scope.launch {
            acpClient.setMode(SessionModeId(modeId))
        }
    }

    override fun setConfigOption(configId: String, value: String) {
        scope.launch {
            // Try boolean first, then string
            val configValue = when (value.lowercase()) {
                "true" -> SessionConfigOptionValue.BoolValue(true)
                "false" -> SessionConfigOptionValue.BoolValue(false)
                else -> SessionConfigOptionValue.StringValue(value)
            }
            acpClient.setConfigOption(SessionConfigId(configId), configValue)
        }
    }

    private fun handleEvent(event: Event) {
        val javaEvent = when (event) {
            is Event.SessionUpdateEvent -> AcpEventAdapter(event.update)
            else -> null
        }
        javaEvent?.let { callback?.onEvent(it) }
    }

    private fun parseConfig(args: Array<String>): ACPConnectConfig {
        var cfg = ACPConnectConfig()
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
                state.addToolCall(update.title, args, update.toolCallId.value)
            }

            is SessionUpdate.ToolCallUpdate -> {
                val toolCallId = update.toolCallId.value
                when (update.status) {
                    ToolCallStatus.COMPLETED, ToolCallStatus.FAILED -> {
                        val result = extractText(update.content ?: emptyList())
                        val status = if (update.status == ToolCallStatus.COMPLETED) "COMPLETED" else "FAILED"
                        state.updateToolCall(toolCallId, status, result)
                    }

                    else -> {
                        val content = extractText(update.content ?: emptyList())
                        if (content.isNotEmpty()) state.appendToolCallUpdate(content, toolCallId)
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

            is SessionUpdate.CurrentModeUpdate -> {
                state.setCurrentModeId(update.currentModeId.value)
            }

            is SessionUpdate.ConfigOptionUpdate -> {
                val options = update.configOptions.map { opt ->
                    when (opt) {
                        is SessionConfigOption.BooleanOption -> {
                            ConfigOption(
                                opt.id.value,
                                opt.name,
                                "boolean",
                                opt.currentValue.toString(),
                                emptyList()
                            )
                        }

                        is SessionConfigOption.Select -> {
                            val opts = when (val o = opt.options) {
                                is SessionConfigSelectOptions.Flat -> o.options.map { it.name }
                                is SessionConfigSelectOptions.Grouped -> o.groups.flatMap { it.options.map { s -> s.name } }
                            }
                            ConfigOption(
                                opt.id.value,
                                opt.name,
                                "select",
                                opt.currentValue.value,
                                opts
                            )
                        }
                    }
                }
                state.setConfigOptions(options)
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
