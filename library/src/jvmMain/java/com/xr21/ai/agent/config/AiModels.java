package com.xr21.ai.agent.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

public enum AiModels {
    DEEPSEEK_V3_2_TERMINUS("Pro/deepseek-ai/DeepSeek-V3.2", 0.75, 5000, System.getenv("AI_OPENAPI_BASE_URL"), System.getenv("AI_OPENAPI_API_KEY")), //
    PRO_GLM_4_7("Pro/zai-org/GLM-4.7", 0.5, 4000, System.getenv("AI_OPENAPI_BASE_URL"), System.getenv("AI_OPENAPI_API_KEY")),//
    MINIMAX_M2_1("MiniMax-M2.1", 0.65, 50000, System.getenv("AI_MINIMAX_BASE_URL"), System.getenv("AI_MINIMAX_API_KEY")),//
    MINIMAX_M2_1_LIGHTNING("MiniMax-M2.1-lightning", 0.65, 50000, System.getenv("AI_MINIMAX_BASE_URL"), System.getenv("AI_MINIMAX_API_KEY")),//
    MIMO_V2_FLASH("mimo-v2-flash", 0.65, 3000, System.getenv("AI_XIAOMI_BASE_URL"), System.getenv("AI_XIAOMI_API_KEY")),//
    DEEPSEEK_FUNCTION_CALL("deepseek-function_call", 0.65, 3000, System.getenv("AI_VOLC_BASE_URL"), System.getenv("AI_VOLC_API_KEY")),//
    DOUBAO_SEED_CODE("doubao-seed-code", 0.65, 3000, System.getenv("AI_VOLC_BASE_URL"), System.getenv("AI_VOLC_API_KEY")),//
    GLM_4_7("glm-4.7", 0.65, 3000, System.getenv("AI_VOLC_BASE_URL"), System.getenv("AI_VOLC_API_KEY")),//
    DEEPSEEK_V3_2("deepseek-v3.2", 0.65, 3000, System.getenv("AI_VOLC_BASE_URL"), System.getenv("AI_VOLC_API_KEY")),//
    KIMI_K2_THINKING("kimi-k2-thinking", 0.65, 3000, System.getenv("AI_VOLC_BASE_URL"), System.getenv("AI_VOLC_API_KEY")),//
    KIMI_K2_5("kimi-k2.5", 0.65, 3000, System.getenv("AI_VOLC_BASE_URL"), System.getenv("AI_VOLC_API_KEY")),//
    QWEN3_CODER_NEXT("qwen/qwen3-coder-next", 0.75, 3000, System.getenv("AI_OPEN_ROUTER_BASE_URL"), System.getenv("AI_OPEN_ROUTER_API_KEY")), STEP_3_5_FLASH("stepfun/step-3.5-flash:free", 0.75, null, System.getenv("AI_OPEN_ROUTER_BASE_URL"), System.getenv("AI_OPEN_ROUTER_API_KEY"));//


    private final String modelName;
    private final double temperature;
    private final Integer maxTokens;
    private final String baseUrl;
    private final String apiKey;

    AiModels(String modelName, double temperature, Integer maxTokens, String baseUrl, String apiKey) {
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
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .completionsPath(baseUrl.endsWith("v3") ? "/chat/completions" : "v1/chat/completions")
                .apiKey(apiKey)
                .build();
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