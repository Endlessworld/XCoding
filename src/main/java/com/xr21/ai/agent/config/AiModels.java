package com.xr21.ai.agent.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

public enum AiModels {
    DEEPSEEK_V3_2_TERMINUS("Pro/deepseek-ai/DeepSeek-V3.2", 0.75, 5000, System.getenv("AI_OPENAPI_BASE_URL"), System.getenv("AI_OPENAPI_API_KEY")), //
    GLM_4_7("Pro/zai-org/GLM-4.7", 0.5, 4000, System.getenv("AI_OPENAPI_BASE_URL"), System.getenv("AI_OPENAPI_API_KEY")),//
    MINIMAX_M2_1("MiniMax-M2.1", 0.65, 3000, System.getenv("AI_MINIMAX_BASE_URL"), System.getenv("AI_MINIMAX_API_KEY")),//
    MINIMAX_M2_1_LIGHTNING("MiniMax-M2.1-lightning", 0.65, 3000, System.getenv("AI_MINIMAX_BASE_URL"), System.getenv("AI_MINIMAX_API_KEY")),//
    MIMO_V2_FLASH("mimo-v2-flash", 0.65, 3000, System.getenv("AI_XIAOMI_BASE_URL"), System.getenv("AI_XIAOMI_API_KEY")),//
    DEEPSEEK_FUNCTION_CALL("deepseek-function_call", 0.65, 3000, "http://211.95.48.170:1025", "api_key");

    private final String modelName;
    private final double temperature;
    private final int maxTokens;
    private final String baseUrl;
    private final String apiKey;

    AiModels(String modelName, double temperature, int maxTokens, String baseUrl, String apiKey) {
        this.modelName = modelName;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }

    public String getModelName() {
        return modelName;
    }

    public double getTemperature() {
        return temperature;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public ChatModel createChatModel() {
        OpenAiApi api = OpenAiApi.builder().baseUrl(baseUrl).apiKey(apiKey).build();
        return OpenAiChatModel.builder()
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(modelName)
                        .temperature(temperature)
                        .maxTokens(maxTokens)
                        .build())
                .openAiApi(api)
                .build();
    }
}