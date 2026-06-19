/*
 * Copyright (c) 2026 XR21 Team. All rights reserved.
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
package com.xr21.ai.agent.config;

import com.xr21.ai.agent.model.Config.ModelConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 模型工厂，负责从 JSON 配置创建 ChatModel 实例
 * 支持 ChatModel 实例缓存，配置变更时自动重建
 *
 * @author Endless
 */
@Slf4j
public class AiModels {

    // ChatModel 实例缓存，按 modelId 索引
    private static final Map<String, ChatModel> chatModelCache = new ConcurrentHashMap<>();
    // 记录每个缓存实例对应的配置指纹，用于检测配置变更
    private static final Map<String, Integer> configFingerprints = new ConcurrentHashMap<>();

    /**
     * 获取所有可用的模型配置（不含已禁用的）
     *
     * @return 模型配置列表
     */
    public static List<ModelConfig> availableModels() {
        return ModelConfigLoader.loadConfigs();
    }

    /**
     * 获取默认模型 ID
     *
     * @return 默认模型的 modelId
     */
    public static String defaultModel() {
        List<ModelConfig> configs = ModelConfigLoader.loadConfigs();
        ModelConfig defaultConfig = ModelConfigLoader.getDefaultConfig(configs);
        return Objects.requireNonNull(defaultConfig, "未找到默认模型配置").getModelId();
    }

    /**
     * 从 JSON 配置文件创建（或从缓存获取）ChatModel
     * 如果配置发生变更，自动重建实例
     *
     * @param modelName 模型ID或模型名称
     * @return ChatModel 实例
     */
    public static ChatModel createChatModelFromJson(String modelName) {
        // 先加载最新配置（ModelConfigLoader 内部有缓存，不会重复读磁盘）
        List<ModelConfig> configs = ModelConfigLoader.loadConfigs();
        ModelConfig config = ModelConfigLoader.findConfigByModelName(modelName, configs);
        if (config == null) {
            config = ModelConfigLoader.getDefaultConfig(configs);
        }
        if (config == null) {
            throw new RuntimeException("未找到模型配置: " + modelName);
        }

        // 计算当前配置的指纹
        int fingerprint = configFingerprint(config);

        // 检查缓存：存在且指纹未变则复用
        ChatModel cached = chatModelCache.get(modelName);
        Integer cachedFingerprint = configFingerprints.get(modelName);
        if (cached != null && cachedFingerprint != null && cachedFingerprint == fingerprint) {
            return cached;
        }

        // 指纹不匹配或缓存不存在，重建
        if (cached != null) {
            log.info("模型配置已变更，重建 ChatModel: {}", modelName);
        }

        ChatModel chatModel = buildChatModel(config);
        chatModelCache.put(modelName, chatModel);
        configFingerprints.put(modelName, fingerprint);
        // 同时按 modelId 缓存，方便直接查找
        if (config.getModelId() != null && !config.getModelId().equals(modelName)) {
            chatModelCache.put(config.getModelId(), chatModel);
            configFingerprints.put(config.getModelId(), fingerprint);
        }
        return chatModel;
    }

    /**
     * 计算配置指纹，用于检测配置是否变更
     * 覆盖所有影响 ChatModel 构建的关键字段
     */
    private static int configFingerprint(ModelConfig config) {
        return Objects.hash(
                config.getModelId(),
                config.getModelName(),
                config.getBaseUrl(),
                config.getApiKey(),
                config.getTemperature(),
                config.getMaxTokens(),
                config.getReasoningEffort(),
                config.getParallelToolCalls(),
                config.getStreamUsage(),
                config.getToolChoice(),
                config.getExtraBody()
        );
    }

    /**
     * 根据 ModelConfig 构建 ChatModel 实例
     */
    private static ChatModel buildChatModel(ModelConfig config) {
        String effectiveBaseUrl = config.getBaseUrl();
        String effectiveApiKey = config.getApiKey();
        String effectiveModelName = config.getModelId();
        Double temperature = config.getTemperature();

        // 根据 URL 结构自动判断 completions 路径
        String completionsPath = determineCompletionsPath(effectiveBaseUrl);

        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(effectiveBaseUrl)
                .completionsPath(completionsPath)
                .apiKey(effectiveApiKey)
                .build();

        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                .model(effectiveModelName)
                .temperature(temperature);

        // 应用可选的配置字段
        if (config.getReasoningEffort() != null) {
            optionsBuilder.reasoningEffort(config.getReasoningEffort());
        }
        if (config.getParallelToolCalls() != null) {
            optionsBuilder.parallelToolCalls(config.getParallelToolCalls());
        }
        if (config.getStreamUsage() != null) {
            optionsBuilder.streamUsage(config.getStreamUsage());
        }
        if (config.getToolChoice() != null) {
            optionsBuilder.toolChoice(config.getToolChoice());
        }
//        optionsBuilder.serviceTier("auto");
//        optionsBuilder.reasoningEffort("max");
//        if (!config.getModelName().contains("deepseek")) {
//           optionsBuilder.reasoningEffort("low");
//        }
//        // 构建 extraBody：合并默认的 thinking 配置和自定义 extraBody
//        Map<String, Object> extraBody = new HashMap<>();
//        extraBody.put("thinking", Map.of("type", "enabled"));
//        if (config.getExtraBody() != null) {
//            extraBody.putAll(config.getExtraBody());
//        }
//        optionsBuilder.extraBody(extraBody);

        return OpenAiChatModel.builder()
                .defaultOptions(optionsBuilder.build())
                .openAiApi(api)
                .build();
    }

    /**
     * 根据 baseUrl 自动判断 completions 路径
     * 兼容不同供应商的 URL 格式
     */
    private static String determineCompletionsPath(String baseUrl) {
        if (baseUrl == null) {
            return "v1/chat/completions";
        }
        // 火山引擎格式: /api/v3 -> /chat/completions
        if (baseUrl.endsWith("/v3") || baseUrl.contains("/v3")) {
            return "/chat/completions";
        }
        // OpenRouter 格式: /api -> /v1/chat/completions
        if (baseUrl.endsWith("/api")) {
            return "v1/chat/completions";
        }
        // 默认 OpenAI 兼容格式
        return "v1/chat/completions";
    }

    /**
     * 清空 ChatModel 缓存，下次调用将重新创建
     */
    public static void clearCache() {
        chatModelCache.clear();
        configFingerprints.clear();
        log.info("ChatModel 缓存已清空");
    }

    /**
     * 使指定模型的缓存失效
     *
     * @param modelName 模型名称
     */
    public static void invalidateModel(String modelName) {
        chatModelCache.remove(modelName);
        configFingerprints.remove(modelName);
        log.info("ChatModel 缓存已失效: {}", modelName);
    }
}
