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

import com.agentclientprotocol.model.*
import com.xr21.ai.agent.config.ModelConfigLoader
import com.xr21.ai.agent.model.Config
import com.xr21.ai.agent.utils.Json
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

private val logger = KotlinLogging.logger {}

/**
 * Manages provider configuration persistence (load/list/set/disable).
 * Extracted from [AgiAgent] for single responsibility.
 */
object ProviderConfigManager {

    fun loadAllProviderConfigs(): Map<String, String> {
        val configPath = Paths.get(ModelConfigLoader.getConfigFilePath())
        if (!Files.exists(configPath)) {
            logger.warn { "Config file not found at: $configPath" }
            return emptyMap()
        }
        return try {
            val content = Files.readString(configPath, StandardCharsets.UTF_8)
            val config = Json.to(content, Config::class.java)
            config.providers
                .filterNotNull()
                .filter { it.providerId != null && it.baseUrl != null }
                .associate { it.providerId!! to it.baseUrl!! }
        } catch (e: Exception) {
            logger.error(e) { "Failed to load provider configs" }
            emptyMap()
        }
    }

    fun listProviders(): List<ProviderInfo> {
        val providerConfigs = loadAllProviderConfigs()
        return providerConfigs.map { (id, baseUrl) ->
            ProviderInfo(
                id = id,
                supported = listOf(LlmProtocol(LlmProtocol.OPENAI.value)),
                required = false,
                current = ProviderCurrentConfig(
                    apiType = LlmProtocol(LlmProtocol.OPENAI.value),
                    baseUrl = baseUrl
                )
            )
        }
    }

    fun setProvider(id: String, baseUrl: String, headers: Map<String, String>?) {
        try {
            val configPath = Paths.get(ModelConfigLoader.getConfigFilePath())
            if (!Files.exists(configPath)) {
                logger.warn { "Config file not found at: $configPath" }
                return
            }
            val content = Files.readString(configPath, StandardCharsets.UTF_8)
            val config = Json.to(content, Config::class.java)

            var provider = config.providers.find { it?.providerId == id }
            if (provider == null) {
                provider = Config.ProviderConfig()
                provider.providerId = id
                config.providers.add(provider)
            }
            provider!!.baseUrl = baseUrl
            if (headers != null && headers.containsKey("Authorization")) {
                provider.apiKey = headers["Authorization"]!!.removePrefix("Bearer ")
            }

            val updatedJson = Json.toPrettyJson(config)
            Files.writeString(configPath, updatedJson, StandardCharsets.UTF_8)
            logger.info { "Provider $id updated successfully" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to set provider: $id" }
        }
    }

    fun disableProvider(id: String) {
        try {
            val configPath = Paths.get(ModelConfigLoader.getConfigFilePath())
            if (!Files.exists(configPath)) {
                logger.warn { "Config file not found at: $configPath" }
                return
            }
            val content = Files.readString(configPath, StandardCharsets.UTF_8)
            val config = Json.to(content, Config::class.java)

            config.providers.removeAll { it?.providerId == id }
            config.models.forEach { model ->
                if (model?.providerId == id) {
                    model.disabled = true
                }
            }

            val updatedJson = Json.toPrettyJson(config)
            Files.writeString(configPath, updatedJson, StandardCharsets.UTF_8)
            logger.info { "Provider $id disabled successfully" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to disable provider: $id" }
        }
    }
}
