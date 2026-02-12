package com.xr21.ai.agent.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

import java.util.function.Supplier;

public enum AiModels {
    DEEPSEEK_V3_2_TERMINUS("Pro/deepseek-ai/DeepSeek-V3.2", 0.75, 5000, () -> System.getenv("AI_OPENAPI_BASE_URL"), () -> System.getenv("AI_OPENAPI_API_KEY")), //
    PRO_GLM_4_7("Pro/zai-org/GLM-4.7", 0.5, 4000, () -> System.getenv("AI_OPENAPI_BASE_URL"), () -> System.getenv("AI_OPENAPI_API_KEY")),//
    MINIMAX_M2_1("MiniMax-M2.1", 0.65, 50000, () -> System.getenv("AI_MINIMAX_BASE_URL"), () -> System.getenv("AI_MINIMAX_API_KEY")),//
    MINIMAX_M2_1_LIGHTNING("MiniMax-M2.1-lightning", 0.65, 50000, () -> System.getenv("AI_MINIMAX_BASE_URL"), () -> System.getenv("AI_MINIMAX_API_KEY")),//
    MIMO_V2_FLASH("mimo-v2-flash", 0.65, 3000, () -> System.getenv("AI_XIAOMI_BASE_URL"), () -> System.getenv("AI_XIAOMI_API_KEY")),//
    DEEPSEEK_FUNCTION_CALL("deepseek-function_call", 0.65, 3000, () -> System.getenv("AI_VOLC_BASE_URL"), () -> System.getenv("AI_VOLC_API_KEY")),//
    DOUBAO_SEED_CODE("doubao-seed-code", 0.65, 3000, () -> System.getenv("AI_VOLC_BASE_URL"), () -> System.getenv("AI_VOLC_API_KEY")),//
    GLM_4_7("glm-4.7", 0.65, 3000, () -> System.getenv("AI_VOLC_BASE_URL"), () -> System.getenv("AI_VOLC_API_KEY")),//
    DEEPSEEK_V3_2("deepseek-v3.2", 0.65, 3000, () -> System.getenv("AI_VOLC_BASE_URL"), () -> System.getenv("AI_VOLC_API_KEY")),//
    KIMI_K2_THINKING("kimi-k2-thinking", 0.65, 3000, () -> System.getenv("AI_VOLC_BASE_URL"), () -> System.getenv("AI_VOLC_API_KEY")),//
    KIMI_K2_5("kimi-k2.5", 0.65, 3000, () -> System.getenv("AI_VOLC_BASE_URL"), () -> System.getenv("AI_VOLC_API_KEY")),//
    QWEN3_CODER_NEXT("qwen/qwen3-coder-next", 0.75, 3000, () -> System.getenv("AI_OPEN_ROUTER_BASE_URL"), () -> System.getenv("AI_OPEN_ROUTER_API_KEY")),
    STEP_3_5_FLASH("stepfun/step-3.5-flash:free", 0.75, null, () -> System.getenv("AI_OPEN_ROUTER_BASE_URL"), () -> System.getenv("AI_OPEN_ROUTER_API_KEY"));//

    // 默认模型设置（可通过 setDefaultModelSettings 设置）
    private static volatile ModelSettings defaultModelSettings = null;

    private final String modelName;
    private final double temperature;
    private final Integer maxTokens;
    private final Supplier<String> baseUrlSupplier;
    private final Supplier<String> apiKeySupplier;

    AiModels(String modelName, double temperature, Integer maxTokens, Supplier<String> baseUrlSupplier, Supplier<String> apiKeySupplier) {
        this.modelName = modelName;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.baseUrlSupplier = baseUrlSupplier;
        this.apiKeySupplier = apiKeySupplier;
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

    /**
     * 设置默认模型配置（优先级高于环境变量）
     */
    public static void setDefaultModelSettings(ModelSettings settings) {
        defaultModelSettings = settings;
    }

    /**
     * 获取当前的默认模型配置
     */
    public static ModelSettings getDefaultModelSettings() {
        return defaultModelSettings;
    }

    /**
     * 获取实际使用的 baseUrl（优先使用设置，否则使用环境变量）
     */
    public String getBaseUrl() {
        if (defaultModelSettings != null && defaultModelSettings.getBaseUrl() != null && !defaultModelSettings.getBaseUrl().isEmpty()) {
            return defaultModelSettings.getBaseUrl();
        }
        return baseUrlSupplier.get();
    }

    /**
     * 获取实际使用的 apiKey（优先使用设置，否则使用环境变量）
     */
    public String getApiKey() {
        if (defaultModelSettings != null && defaultModelSettings.getApiKey() != null && !defaultModelSettings.getApiKey().isEmpty()) {
            return defaultModelSettings.getApiKey();
        }
        return apiKeySupplier.get();
    }

    /**
     * 获取实际使用的模型名称（优先使用设置，否则使用枚举定义的名称）
     */
    public String getEffectiveModelName() {
        if (defaultModelSettings != null && defaultModelSettings.getModelName() != null && !defaultModelSettings.getModelName().isEmpty()) {
            return defaultModelSettings.getModelName();
        }
        return modelName;
    }

    public ChatModel createChatModel() {
        String effectiveBaseUrl = getBaseUrl();
        String effectiveApiKey = getApiKey();
        String effectiveModelName = getEffectiveModelName();

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
                        .build())
                .openAiApi(api)
                .build();
    }

    /**
     * 使用自定义设置创建 ChatModel
     */
    public ChatModel createChatModelWithSettings(String customModelName, String customBaseUrl, String customApiKey) {
        String effectiveBaseUrl = (customBaseUrl != null && !customBaseUrl.isEmpty()) ? customBaseUrl : getBaseUrl();
        String effectiveApiKey = (customApiKey != null && !customApiKey.isEmpty()) ? customApiKey : getApiKey();
        String effectiveModelName = (customModelName != null && !customModelName.isEmpty()) ? customModelName : getEffectiveModelName();

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
                        .build())
                .openAiApi(api)
                .build();
    }

    /**
     * 模型设置数据类
     */
    public static class ModelSettings {
        private final String modelName;
        private final String baseUrl;
        private final String apiKey;

        public ModelSettings() {
            this.modelName = null;
            this.baseUrl = null;
            this.apiKey = null;
        }

        public ModelSettings(String modelName, String baseUrl, String apiKey) {
            this.modelName = modelName;
            this.baseUrl = baseUrl;
            this.apiKey = apiKey;
        }

        public String getModelName() {
            return modelName;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public boolean isEmpty() {
            return (modelName == null || modelName.isEmpty()) &&
                   (baseUrl == null || baseUrl.isEmpty()) &&
                   (apiKey == null || apiKey.isEmpty());
        }
    }
}