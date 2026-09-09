@file:OptIn(UnstableApi::class)

package com.xr21.ai.agent.acp

import com.agentclientprotocol.annotations.UnstableApi
import com.agentclientprotocol.agent.v2.AgentInfo
import com.agentclientprotocol.agent.v2.AgentSession
import com.agentclientprotocol.agent.v2.AgentSupport
import com.agentclientprotocol.agent.v2.ClientOperations
import com.agentclientprotocol.agent.v2.SessionCreationParameters
import com.agentclientprotocol.client.v2.ClientInfo
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.model.Implementation
import com.agentclientprotocol.model.PositionEncodingKind
import com.agentclientprotocol.model.SessionConfigId
import com.agentclientprotocol.model.SessionId
import com.agentclientprotocol.model.v2.AgentAuthCapabilities
import com.agentclientprotocol.model.v2.AgentCapabilities
import com.agentclientprotocol.model.v2.ContentBlock
import com.agentclientprotocol.model.v2.McpCapabilities
import com.agentclientprotocol.model.v2.McpHttpCapabilities
import com.agentclientprotocol.model.v2.PromptCapabilities
import com.agentclientprotocol.model.v2.PromptImageCapabilities
import com.agentclientprotocol.model.v2.SessionCapabilities
import com.agentclientprotocol.model.v2.SessionConfigKind
import com.agentclientprotocol.model.v2.SessionConfigOption
import com.agentclientprotocol.model.v2.SessionConfigOptionValue
import com.agentclientprotocol.model.v2.SessionConfigSelectOptions
import com.agentclientprotocol.model.v2.SessionUpdate
import com.agentclientprotocol.model.v2.conversion.toV1
import com.agentclientprotocol.model.v2.conversion.toV2
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonElement

private val v2Logger = KotlinLogging.logger {}

/**
 * v2 协议 AgentSupport 适配器。
 *
 * 复用现有 v1 的 [AgiAgent] 会话逻辑（[AgiAgentSession]），把 v2 的请求/事件桥接到 v1：
 * - initialize：返回 v2 的 [AgentInfo]
 * - createSession：复用 [AgiAgent.createSession]，产出 v1 会话，再包装成 v2 会话
 * - prompt：v2 ContentBlock -> v1 ContentBlock，v1 Event 流 -> v2 SessionUpdate 流
 */
class AgiV2AgentSupport(private val v1: AgiAgent) : AgentSupport {

    override suspend fun initialize(clientInfo: ClientInfo): AgentInfo {
        v2Logger.info { "[v2] initialize clientInfo=$clientInfo" }
        return AgentInfo(
            implementation = Implementation("XCoding", "1.0.0", "agi coding"),
            capabilities = AgentCapabilities(
                session = SessionCapabilities(
                    prompt = PromptCapabilities(
                        image = PromptImageCapabilities(),
                        audio = null,
                        embeddedContext = null
                    ),
                    mcp = McpCapabilities(
                        stdio = null,
                        http = McpHttpCapabilities(),
                        acp = null
                    ),
                    delete = null,
                    additionalDirectories = null,
                    fork = null
                ),
                auth = AgentAuthCapabilities(),
                providers = null,
                nes = null,
                positionEncoding = PositionEncodingKind.UTF_8
            ),
            authMethods = emptyList(),
            _meta = null
        )
    }

    override suspend fun createSession(
        params: SessionCreationParameters,
        clientOperations: ClientOperations
    ): AgentSession {
        v2Logger.info { "[v2] createSession cwd=${params.cwd} mcp=${params.mcpServers}" }
        val v1Params = com.agentclientprotocol.common.SessionCreationParameters(
            cwd = params.cwd,
            additionalDirectories = params.additionalDirectories,
            mcpServers = params.mcpServers?.map { it.toV1() }.orEmpty(),
            _meta = params._meta
        )
        val v1Session = v1.createSession(v1Params)
        return AgiV2AgentSession(v1Session)
    }

    /** 包装 v1 会话，使其满足 v2 AgentSession 接口。 */
    private inner class AgiV2AgentSession(
        private val delegate: com.agentclientprotocol.agent.AgentSession
    ) : AgentSession {

        override val sessionId: SessionId = delegate.sessionId

        override fun prompt(
            content: List<ContentBlock>,
            _meta: JsonElement?
        ): Flow<SessionUpdate> = flow {
            val v1Content = content.map { it.toV1() }
            val events: Flow<Event> = delegate.prompt(v1Content, _meta)
            events.collect { event ->
                when (event) {
                    is Event.SessionUpdateEvent -> emit(event.update.toV2())
                    is Event.PromptResponseEvent -> Unit // v2 以流结束表示 prompt 完成
                }
            }
        }

        override suspend fun cancel() = delegate.cancel()

        override suspend fun setConfigOption(
            configId: SessionConfigId,
            value: SessionConfigOptionValue,
            _meta: JsonElement?
        ): List<SessionConfigOption> {
            val v1Value = when (value) {
                is SessionConfigOptionValue.Boolean ->
                    com.agentclientprotocol.model.SessionConfigOptionValue.BoolValue(value.value)
                is SessionConfigOptionValue.Id ->
                    com.agentclientprotocol.model.SessionConfigOptionValue.StringValue(value.value.value)
                is SessionConfigOptionValue.Unknown ->
                    com.agentclientprotocol.model.SessionConfigOptionValue.UnknownValue(value.value)
            }
            delegate.setConfigOption(configId, v1Value, _meta)
            return delegate.configOptions.map { it.toV2ConfigOption() }
        }

        override val configOptions: List<SessionConfigOption> =
            delegate.configOptions.map { it.toV2ConfigOption() }
    }

    private fun com.agentclientprotocol.model.SessionConfigOption.toV2ConfigOption(): SessionConfigOption =
        SessionConfigOption(
            configId = id,
            name = name,
            description = description,
            category = null,
            kind = when (this) {
                is com.agentclientprotocol.model.SessionConfigOption.Select ->
                    SessionConfigKind.Select(
                        currentValue = currentValue,
                        options = SessionConfigSelectOptions.Ungrouped(
                            options = when (val opts = options) {
                                is com.agentclientprotocol.model.SessionConfigSelectOptions.Flat -> opts.options
                                is com.agentclientprotocol.model.SessionConfigSelectOptions.Grouped ->
                                    opts.groups.flatMap { it.options }
                            }
                        )
                    )
                is com.agentclientprotocol.model.SessionConfigOption.BooleanOption ->
                    SessionConfigKind.Boolean(currentValue = currentValue)
                else ->
                    SessionConfigKind.Unknown(
                        type = "unknown",
                        fields = kotlinx.serialization.json.buildJsonObject { }
                    )
            },
            _meta = null
        )
}