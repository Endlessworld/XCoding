package com.xr21.ai.agent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xr21.ai.agent.config.ModelsConfig.ModelConfig;
import com.xr21.ai.agent.config.ModelsConfig.ProviderConfig;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 模型配置加载器，负责从 JSON 文件加载模型配置
 */
@Slf4j
public class ModelConfigLoader {

    private static final String DEFAULT_CONFIG_DIR = System.getProperty("user.home") + File.separator + ".agi_working";
    private static final String CONFIG_FILE_NAME = "models.json";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 从默认路径加载模型配置
     * @return 模型配置列表，如果文件不存在或解析失败返回空列表
     */
    public static List<ModelConfig> loadConfigs() {
        Path configPath = Paths.get(DEFAULT_CONFIG_DIR, CONFIG_FILE_NAME);
        return loadConfigs(configPath);
    }

    /**
     * 从指定路径加载模型配置
     * @param configPath 配置文件路径
     * @return 模型配置列表，如果文件不存在或解析失败返回空列表
     */
    public static List<ModelConfig> loadConfigs(Path configPath) {
        if (!Files.exists(configPath)) {
            log.info("Model config file not found at: {}, creating default config file", configPath);
            createDefaultConfigFile(configPath);
            return new ArrayList<>();
        }

        try {
            String content = Files.readString(configPath, StandardCharsets.UTF_8);

            // 解析为新格式（ModelsConfig 对象）
            ModelsConfig modelsConfig = objectMapper.readValue(content, ModelsConfig.class);
            List<ModelConfig> resolvedConfigs = resolveModelConfigs(modelsConfig);
            log.info("Loaded {} model configurations from: {}", resolvedConfigs.size(), configPath);
            return resolvedConfigs;
        } catch (IOException e) {
            log.error("Failed to load model configurations from {}: {}", configPath, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 解析模型配置，将 providerId 引用解析为实际的 baseUrl 和 apiKey
     * @param modelsConfig 模型配置容器
     * @return 解析后的模型配置列表
     */
    private static List<ModelConfig> resolveModelConfigs(ModelsConfig modelsConfig) {
        List<ModelConfig> resolvedConfigs = new ArrayList<>();

        for (ModelConfig model : modelsConfig.getModels()) {
            ModelConfig resolved = resolveModelConfig(model, modelsConfig.getProviders());
            resolvedConfigs.add(resolved);
        }

        return resolvedConfigs;
    }

    /**
     * 解析单个模型配置，将 providerId 引用解析为实际的 baseUrl 和 apiKey
     * @param model 模型配置
     * @param providers 供应商配置列表
     * @return 解析后的模型配置
     */
    private static ModelConfig resolveModelConfig(ModelConfig model, List<ProviderConfig> providers) {
        // 如果没有 providerId，直接返回原配置
        if (model.getProviderId() == null || model.getProviderId().isEmpty()) {
            return model;
        }

        // 查找供应商配置
        ProviderConfig provider = findProvider(model.getProviderId(), providers);
        if (provider == null) {
            log.warn("Provider not found: {}, using model's own baseUrl and apiKey", model.getProviderId());
            return model;
        }

        // 创建新的配置，使用供应商的 baseUrl 和 apiKey
        String baseUrl = model.getBaseUrl() != null ? model.getBaseUrl() : provider.getBaseUrl();
        String apiKey = model.getApiKey() != null ? model.getApiKey() : provider.getApiKey();

        return new ModelConfig(
                model.getModelId(),
                model.getModelName(),
                model.getTemperature(),
                model.getMaxTokens(),
                model.getProviderId(),
                baseUrl,
                apiKey,
                model.getIsDefault()
        );
    }

    /**
     * 根据 providerId 查找供应商配置
     * @param providerId 供应商标识符
     * @param providers 供应商配置列表
     * @return 找到的供应商配置，未找到返回 null
     */
    private static ProviderConfig findProvider(String providerId, List<ProviderConfig> providers) {
        if (providers == null || providerId == null) {
            return null;
        }

        for (ProviderConfig provider : providers) {
            if (providerId.equals(provider.getProviderId())) {
                return provider;
            }
        }

        return null;
    }

    /**
     * 创建默认配置文件
     * 优先从项目资源中读取 models.json，如果读取失败则使用硬编码的默认配置
     * @param configPath 配置文件路径
     */
    private static void createDefaultConfigFile(Path configPath) {
        try {
            // 确保目录存在
            Path parentDir = configPath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
                log.info("Created config directory: {}", parentDir);
            }

            // 首先尝试从项目资源中读取 models.json
            String jsonContent = null;
            try {
                // 尝试从类路径资源中读取 models.json
                InputStream resourceStream = ModelConfigLoader.class.getClassLoader()
                        .getResourceAsStream("models.json");
                if (resourceStream != null) {
                    // 读取资源文件内容
                    byte[] resourceBytes = resourceStream.readAllBytes();
                    jsonContent = new String(resourceBytes, StandardCharsets.UTF_8);
                    log.info("Successfully loaded models.json from project resources");
                } else {
                    log.warn("models.json not found in project resources, using hardcoded defaults");
                }
            } catch (Exception e) {
                log.warn("Failed to load models.json from project resources: {}, using hardcoded defaults", e.getMessage());
            }
            if (jsonContent != null) {
                // 写入文件
                Files.writeString(configPath, jsonContent, StandardCharsets.UTF_8);
                log.info("Created default model config file at: {}", configPath);
                log.info("Please edit the config file and update the apiKey field with your actual API key");
            }
        } catch (IOException e) {
            log.error("Failed to create default config file at {}: {}", configPath, e.getMessage());
        }
    }

    /**
     * 获取默认模型配置
     * @return 默认的 ModelConfig，如果没有标记为默认的则返回第一个，没有配置则返回 null
     */
    public static ModelConfig getDefaultConfig(List<ModelConfig> configs) {
        if (configs.isEmpty()) {
            return null;
        }

        // 查找标记为默认的配置
        for (ModelConfig config : configs) {
            if (config.getIsDefault() != null && config.getIsDefault()) {
                return config;
            }
        }

        // 如果没有默认配置，返回第一个
        return configs.get(0);
    }

    /**
     * 根据模型名称查找配置
     * @param modelId 模型名称
     * @param configs 配置列表
     * @return 找到的配置，未找到返回 null
     */
    public static ModelConfig findConfigByModelId(String modelId, List<ModelConfig> configs) {
        if (configs.isEmpty() || modelId == null) {
            return null;
        }

        for (ModelConfig config : configs) {
            if (modelId.equals(config.getModelId())) {
                return config;
            }
        }

        return null;
    }

    /**
     * 获取配置文件路径
     * @return 配置文件的完整路径
     */
    public static String getConfigFilePath() {
        return Paths.get(DEFAULT_CONFIG_DIR, CONFIG_FILE_NAME).toString();
    }
}