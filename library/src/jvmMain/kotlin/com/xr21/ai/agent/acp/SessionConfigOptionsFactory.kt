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
package com.xr21.ai.agent.acp

import com.agentclientprotocol.client.ClientInfo
import com.agentclientprotocol.model.*
import com.xr21.ai.agent.config.AiModels

/**
 * Creates session config options for [AgiAgentSession].
 * Extracted from [AgiAgentSession.configOptions] for single responsibility.
 */
object SessionConfigOptionsFactory {

    /**
     * Agent 运行模式选项枚举。
     */
    enum class AgentMode(
        val valueId: String,
        val label: String,
        val description: String
    ) {
        AGENT("Agent", "Agent", "单智能体模式"),
        WORKERS("Workers", "Workers", "动态并行子代理");

        fun toSelectOption(): SessionConfigSelectOption =
            SessionConfigSelectOption(SessionConfigValueId(valueId), label, description)
    }

    /**
     * 思考深度级别选项枚举。
     */
    enum class ThoughtLevel(
        val valueId: String,
        val label: String,
        val description: String
    ) {
        DISABLED("disabled", "Disabled", "关闭思考"),
        LOW("low", "Low", "轻度思考"),
        MEDIUM("medium", "Medium", "中度思考"),
        HIGH("high", "High", "深度思考"),
        MAX("max", "Max", "最高程度思考");

        fun toSelectOption(): SessionConfigSelectOption =
            SessionConfigSelectOption(SessionConfigValueId(valueId), label, description)
    }

    fun create(clientInfo: ClientInfo?): List<SessionConfigOption> {
        val options = arrayListOf<SessionConfigOption>()

        if (!isIntelliJ2026(clientInfo)) {
            options.add(
                SessionConfigOption.select(
                    id = "mode",
                    name = "mode",
                    currentValue = AgentMode.AGENT.valueId,
                    description = "mode",
                    options = SessionConfigSelectOptions.Flat(
                        AgentMode.entries.map { it.toSelectOption() }
                    ),
                    category = SessionConfigOptionCategory.MODE
                )
            )
        }

        options.add(
            SessionConfigOption.boolean(
                id = "auto_approve",
                name = "Auto Approve",
                currentValue = true,
                description = "Automatically approve all tool calls"
            )
        )

        options.add(
            SessionConfigOption.select(
                id = "thought_level",
                name = "Thought",
                currentValue = ThoughtLevel.LOW.valueId,
                description = "思考深度级别",
                options = SessionConfigSelectOptions.Flat(
                    ThoughtLevel.entries.map { it.toSelectOption() }
                ),
                category = SessionConfigOptionCategory.THOUGHT_LEVEL
            )
        )

        if (!isIntelliJ2026(clientInfo)) {
            options.add(
                SessionConfigOption.select(
                    id = "model",
                    name = "model",
                    currentValue = AiModels.defaultModel(),
                    description = "model",
                    options = SessionConfigSelectOptions.Flat(
                        AiModels.availableModels().map { model ->
                            SessionConfigSelectOption(
                                SessionConfigValueId(model.modelId ?: ""),
                                model.modelName ?: "",
                                model.modelName ?: ""
                            )
                        }
                    ),
                    category = SessionConfigOptionCategory.MODEL
                )
            )
        }

        return options
    }

    private fun isIntelliJ2026(clientInfo: ClientInfo?): Boolean {
        val impl = clientInfo?.implementation ?: return false
        return impl.name.contains("IntelliJ") && impl.version.contains("2026")
    }
}
