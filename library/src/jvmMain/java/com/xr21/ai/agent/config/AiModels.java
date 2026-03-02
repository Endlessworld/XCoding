package com.xr21.ai.agent.config;

import com.agentclientprotocol.sdk.spec.AcpSchema;
import com.xr21.ai.agent.config.ModelsConfig.ModelConfig;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

@Getter
@AllArgsConstructor
@Slf4j
public enum AiModels {
    ;
    private final String modelId;
    private final String modelName;
    private final double temperature;
    private final Integer maxTokens;
    private final Supplier<String> baseUrlSupplier;
    private final Supplier<String> apiKeySupplier;


    public static List<AcpSchema.ModelInfo> availableModels() {
        List<AcpSchema.ModelInfo> list = new ArrayList<>();
        List<ModelConfig> configs = ModelConfigLoader.loadConfigs();
        for (ModelConfig model : configs) {
            list.add(new AcpSchema.ModelInfo(model.getModelId(), model.getModelName(), model.getModelName()));
        }
        return list;
    }

    public static String defaultModel() {
        List<ModelConfig> configs = ModelConfigLoader.loadConfigs();
        return Objects.requireNonNull(ModelConfigLoader.getDefaultConfig(configs)).getModelId();
    }

    /**
     * 从 JSON 配置文件创建 ChatModel
     * @param modelId 模型名称
     * @return ChatModel 实例
     */
    public static ChatModel createChatModelFromJson(String modelId) {
        List<ModelConfig> configs = ModelConfigLoader.loadConfigs();
        ModelConfig config = ModelConfigLoader.findConfigByModelId(modelId, configs);
        config = config == null ? ModelConfigLoader.getDefaultConfig(configs) : config;
        if (config != null) {
            String effectiveBaseUrl = config.getBaseUrl();
            String effectiveApiKey = config.getApiKey();
            String effectiveModelName = config.getModelId();
            Double temperature = config.getTemperature() != null ? config.getTemperature() : 0.65;
            Integer maxTokens = config.getMaxTokens();
            OpenAiApi api = OpenAiApi.builder()
                    .baseUrl(effectiveBaseUrl)
                    .completionsPath(effectiveBaseUrl.endsWith("v3") ? "/chat/completions" : "v1/chat/completions")
                    .apiKey(effectiveApiKey)
                    .build();
            return OpenAiChatModel.builder()
                    .defaultOptions(OpenAiChatOptions.builder()
                            .model(effectiveModelName)
                            .temperature(temperature)
                            .maxTokens(maxTokens)
                            .parallelToolCalls(true)
                            .extraBody(Map.of("thinking", Map.of("type", "enabled")))
                            .reasoningEffort("medium")
                            .build())
                    .openAiApi(api)
                    .build();
        }
        throw new RuntimeException("Model configuration not found in JSON for:  " + modelId);
    }
}