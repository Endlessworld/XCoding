/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 */
package com.xr21.ai.agent.tui

import com.agentclientprotocol.annotations.UnstableApi
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.model.*
import com.xr21.ai.agent.tui.AppState
import com.xr21.ai.agent.tui.acp.AcpClientManager
import com.xr21.ai.agent.tui.acp.AcpLifecycleEvent
import com.xr21.ai.agent.tui.acp.AcpLifecycleState
import com.xr21.ai.agent.tui.acp.ReconnectStrategy
import com.xr21.ai.agent.tui.config.ACPConnectConfig
import kotlinx.coroutines.*
import com.xr21.ai.agent.tui.AppState as JavaAppState

/**
 * Tamboui TUI 的 ACP 桥接层
 *
 * 将 Kotlin 协程的 ACP SDK 封装为 Java 友好的回调接口，
 * 供 [TuiApp] 使用。
 */
class TambouiAcpBridge(private val javaAppState: JavaAppState) : TuiApp.AcpBridge {

    private val ktAppState = AppState()
    private val acpClient = AcpClientManager(ktAppState)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var callback: TuiApp.ConnectionCallback? = null
    private var config: ACPConnectConfig = ACPConnectConfig()

    init {
        // 监听生命周期事件，转发到 Java 侧
        acpClient.addLifecycleListener { event ->
            onLifecycleEvent(event)
        }
    }

    override fun connect(args: Array<String>, callback: TuiApp.ConnectionCallback) {
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

    @OptIn(UnstableApi::class)
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

        callback?.onEvent(object : TuiApp.AcpEvent {
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

    /** 设置重连策略 */
    override fun setReconnectStrategy(strategy: Any?) {
        if (strategy is ReconnectStrategy) {
            acpClient.setReconnectStrategy(strategy)
        }
    }

    /** 启动重连 */
    override fun startReconnect() {
        acpClient.startReconnect()
    }

    /** 停止重连 */
    override fun stopReconnect() {
        acpClient.stopReconnect()
    }

    /** 认证 */
    override fun authenticate(provider: String, token: String) {
        scope.launch {
            val result = acpClient.authenticate(provider, token)
            if (result.isFailure) {
                callback?.onError("认证失败: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    /** 登出 */
    override fun logout() {
        scope.launch {
            acpClient.logout()
        }
    }

    /** 关闭当前会话（不关闭连接） */
    override fun closeSession() {
        scope.launch {
            acpClient.closeSession()
        }
    }

    /** 加载已存在的会话 */
    override fun loadSession(sessionId: String) {
        scope.launch {
            val result = acpClient.loadSession(sessionId)
            if (result.isFailure) {
                callback?.onError("加载会话失败: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    /** 分支会话 */
    override fun forkSession(sourceSessionId: String) {
        scope.launch {
            val result = acpClient.forkSession(sourceSessionId)
            if (result.isFailure) {
                callback?.onError("分支会话失败: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    /** 恢复会话 */
    override fun resumeSession(sessionId: String) {
        scope.launch {
            val result = acpClient.resumeSession(sessionId)
            if (result.isFailure) {
                callback?.onError("恢复会话失败: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    /** 切换活动会话 */
    override fun switchSession(sessionId: String) {
        scope.launch {
            val result = acpClient.switchSession(sessionId)
            if (result.isFailure) {
                callback?.onError("切换会话失败: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    /** 按 ID 关闭指定会话 */
    override fun closeSessionById(sessionId: String) {
        scope.launch {
            val result = acpClient.closeSessionById(sessionId)
            if (result.isFailure) {
                callback?.onError("关闭会话失败: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    /** 获取活跃会话 ID 列表 */
    override fun getActiveSessionIds(): Array<String> {
        return acpClient.activeSessionIds.toTypedArray()
    }

    /** 获取当前活动会话 ID */
    override fun getActiveSessionId(): String? {
        return acpClient.activeSessionId
    }

    /** 销毁客户端 */
    override fun destroy() {
        acpClient.destroy()
    }

    /** 获取当前生命周期状态 */
    val lifecycleState: AcpLifecycleState get() = acpClient.lifecycleState.value

    private fun onLifecycleEvent(event: AcpLifecycleEvent) {
        when (event) {
            is AcpLifecycleEvent.Disconnected -> {
                callback?.onDisconnected()
            }
            is AcpLifecycleEvent.ErrorOccurred -> {
                callback?.onError(event.error.message ?: "生命周期错误")
            }
            is AcpLifecycleEvent.Reconnecting -> {
                callback?.onReconnecting(event.attempt, event.delayMs)
            }
            is AcpLifecycleEvent.Connected -> {
                // 如果是重连成功（状态从 DISCONNECTED 变为 SESSION_ACTIVE）
                if (acpClient.lifecycleState.value == AcpLifecycleState.SESSION_ACTIVE) {
                    callback?.onReconnected()
                }
            }
            else -> {}
        }
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

    @OptIn(UnstableApi::class)
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
            is Event.PromptResponseEvent -> PromptResponseEventAdapter(event.response)
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
 *
 * 覆盖 ACP SDK 全部 SessionUpdate 子类型（12 种 + 1 兜底），其中 messageId 字段为 Unstable，
 * 仅记录到 AppState.lastMessageId，不参与消息合并（最小实现）。
 */
@OptIn(com.agentclientprotocol.annotations.UnstableApi::class)
class AcpEventAdapter(private val update: SessionUpdate) : TuiApp.AcpEvent {
    override fun apply(state: JavaAppState) {
        when (update) {
            is SessionUpdate.UserMessageChunk -> {
                val text = (update.content as? ContentBlock.Text)?.text ?: ""
                if (text.isNotEmpty()) {
                    state.currentSession().messages.add(
                        ChatMessage(
                            MessageRole.USER, text
                        )
                    )
                }
            }

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

            is SessionUpdate.AvailableCommandsUpdate -> {
                val cmds = update.availableCommands.map { c ->
                    val hint = (c.input as? AvailableCommandInput.Unstructured)
                    AvailableCommand(c.name, c.description, hint)
                }
                state.setAvailableCommands(cmds)
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

            is SessionUpdate.SessionInfoUpdate -> {
                update.title?.takeIf { it.isNotBlank() }?.let { state.currentSession().name = it }
            }

            is SessionUpdate.UsageUpdate -> {
                val tu = com.xr21.ai.agent.tui.TokenUsage().apply {
                    totalTokens = update.used
                    contextWindowSize = update.size
                    update.cost?.let { c ->
                        costUsd = c.amount
                        costCurrency = c.currency
                    }
                }
                state.setTokenUsage(tu)
            }

            is SessionUpdate.UnknownSessionUpdate -> {
                state.setLastUnknownUpdateType(update.sessionUpdateType)
                println("[ACP] Received unknown SessionUpdate type: ${update.sessionUpdateType}")
            }
        }

        // 记录 messageId（Unstable；除 UnknownSessionUpdate 外，所有子类型都可能携带）
        // 提取逻辑统一放在 when 之后，避免每个分支重复
        val messageIdValue: String? = when (update) {
            is SessionUpdate.UserMessageChunk -> update.messageId?.value
            is SessionUpdate.AgentMessageChunk -> update.messageId?.value
            is SessionUpdate.AgentThoughtChunk -> update.messageId?.value
            is SessionUpdate.ToolCall -> null  // SDK 未为 ToolCall 定义 messageId
            is SessionUpdate.ToolCallUpdate -> null  // SDK 未为 ToolCallUpdate 定义 messageId
            is SessionUpdate.PlanUpdate -> null  // SDK 未为 PlanUpdate 定义 messageId
            is SessionUpdate.AvailableCommandsUpdate -> null
            is SessionUpdate.CurrentModeUpdate -> null
            is SessionUpdate.ConfigOptionUpdate -> null
            is SessionUpdate.SessionInfoUpdate -> null
            is SessionUpdate.UsageUpdate -> null
            is SessionUpdate.UnknownSessionUpdate -> null
        }
        // 简化：仅在 messageId 实际非空时写入，避免覆盖
        if (messageIdValue != null) state.setLastMessageId(messageIdValue)
    }

    private fun extractText(content: List<ToolCallContent>): String {
        return content.firstOrNull()?.let { c ->
            (c as? ToolCallContent.Content)?.let { (it.content as? ContentBlock.Text)?.text }
        } ?: ""
    }
}

/**
 * PromptResponse 事件适配器，将 ACP PromptResponse 转换为 Java 侧的 AppState 更新
 *
 * 覆盖 PromptResponse 全部字段（stopReason / userMessageId / usage 全字段）；_meta 为协议扩展，暂不消费。
 */
@OptIn(com.agentclientprotocol.annotations.UnstableApi::class)
class PromptResponseEventAdapter(private val response: com.agentclientprotocol.model.PromptResponse) : TuiApp.AcpEvent {
    override fun apply(state: JavaAppState) {
        // 1. stopReason
        state.setStopReason(response.stopReason.name)

        // 2. userMessageId（Unstable；用于客户端发出消息的确认回执）
        state.setLastUserMessageId(response.userMessageId?.value)

        // 3. usage（Unstable；全字段填入 TokenUsage）
        response.usage?.let { usage ->
            val tu = com.xr21.ai.agent.tui.TokenUsage().apply {
                promptTokens = usage.inputTokens
                completionTokens = usage.outputTokens
                totalTokens = usage.totalTokens
                thoughtTokens = usage.thoughtTokens ?: 0L
                cachedReadTokens = usage.cachedReadTokens ?: 0L
                cachedWriteTokens = usage.cachedWriteTokens ?: 0L
            }
            state.setTokenUsage(tu)
        }
    }
}
