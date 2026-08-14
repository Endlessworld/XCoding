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

import com.xr21.ai.agent.model.Config;
import com.xr21.ai.agent.model.Config.ModelConfig;
import com.xr21.ai.agent.model.Config.ProviderConfig;
import com.xr21.ai.agent.utils.Json;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模型配置加载器，负责从 JSON 文件加载模型配置
 * 支持缓存、校验、环境变量解析和热重载
 *
 * @author Endless
 */
@Slf4j
public class ModelConfigLoader {

    private static final String DEFAULT_CONFIG_DIR = System.getProperty("user.home") + File.separator + ".agi_working";
    private static final String CONFIG_FILE_NAME = "models.json";
    private static final Pattern ENV_VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    // 线程安全的配置缓存
    private static volatile List<ModelConfig> cachedConfigs;
    private static volatile long lastModified = -1;
    private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private static final long CACHE_TTL_MS = 30_000; // 30秒热重载检查间隔

    /**
     * 从默认路径加载模型配置（带缓存）
     *
     * @return 模型配置列表，如果文件不存在或解析失败返回空列表
     */
    public static List<ModelConfig> loadConfigs() {
        return loadConfigs(Paths.get(DEFAULT_CONFIG_DIR, CONFIG_FILE_NAME));
    }

    /**
     * 从指定路径加载模型配置（带缓存和热重载）
     *
     * @param configPath 配置文件路径
     * @return 模型配置列表，如果文件不存在或解析失败返回空列表
     */
    public static List<ModelConfig> loadConfigs(Path configPath) {
        // 快速路径：读缓存
        lock.readLock().lock();
        try {
            if (cachedConfigs != null && !isStale(configPath)) {
                return cachedConfigs;
            }
        } finally {
            lock.readLock().unlock();
        }

        // 慢速路径：重新加载
        lock.writeLock().lock();
        try {
            // 获取写锁后再次检查
            if (cachedConfigs != null && !isStale(configPath)) {
                return cachedConfigs;
            }
            cachedConfigs = doLoadConfigs(configPath);
            lastModified = getFileLastModified(configPath);
            return cachedConfigs;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 强制重新加载配置，绕过缓存
     *
     * @return 重新加载后的模型配置列表
     */
    public static List<ModelConfig> reloadConfigs() {
        return reloadConfigs(Paths.get(DEFAULT_CONFIG_DIR, CONFIG_FILE_NAME));
    }

    /**
     * 从指定路径强制重新加载配置
     *
     * @param configPath 配置文件路径
     * @return 重新加载后的模型配置列表
     */
    public static List<ModelConfig> reloadConfigs(Path configPath) {
        lock.writeLock().lock();
        try {
            cachedConfigs = doLoadConfigs(configPath);
            lastModified = getFileLastModified(configPath);
            return cachedConfigs;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 判断缓存是否过期
     */
    private static boolean isStale(Path configPath) {
        long currentModified = getFileLastModified(configPath);
        if (currentModified > lastModified) {
            return true;
        }
        // 也检查 TTL 是否过期（用于环境变量变更场景）
        return System.currentTimeMillis() - lastModified > CACHE_TTL_MS;
    }

    /**
     * 获取文件的最后修改时间
     */
    private static long getFileLastModified(Path configPath) {
        try {
            return Files.getLastModifiedTime(configPath).toMillis();
        } catch (IOException e) {
            return -1;
        }
    }

    /**
     * 实际执行配置加载
     */
    private static List<ModelConfig> doLoadConfigs(Path configPath) {
        if (!Files.exists(configPath)) {
            log.info("模型配置文件不存在: {}，正在创建默认配置文件", configPath);
            createDefaultConfigFile(configPath);
            return Collections.emptyList();
        }
        try {
            String content = Files.readString(configPath, StandardCharsets.UTF_8);
            // 解析环境变量占位符
            content = resolveEnvVars(content);
            Config modelsConfig = Json.to(content, Config.class);
            // 解析供应商引用
            List<ModelConfig> resolvedConfigs = resolveModelConfigs(modelsConfig);
            // 校验配置
            List<String> errors = validateConfigs(resolvedConfigs, modelsConfig);
            if (!errors.isEmpty()) {
                log.error("模型配置校验失败（{} 个错误）:", errors.size());
                errors.forEach(e -> log.error("  - {}", e));
                return Collections.emptyList();
            }
            log.info("成功加载 {} 个模型配置，来源: {}", resolvedConfigs.size(), configPath);
            return Collections.unmodifiableList(resolvedConfigs);
        } catch (Exception e) {
            log.error("加载模型配置失败 {}: {}", configPath, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 解析配置内容中的 ${ENV_VAR} 环境变量占位符
     *
     * @param content 原始配置内容
     * @return 替换后的配置内容
     */
    static String resolveEnvVars(String content) {
        Matcher matcher = ENV_VAR_PATTERN.matcher(content);
        StringBuilder sb = new StringBuilder(content.length());
        while (matcher.find()) {
            String envName = matcher.group(1);
            String envValue = System.getenv(envName);
            if (envValue != null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(envValue));
            } else {
                log.warn("环境变量 '{}' 未找到，保留占位符", envName);
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 校验所有已解析的配置
     */
    private static List<String> validateConfigs(List<ModelConfig> configs, Config rawConfig) {
        List<String> errors = new ArrayList<>();

        // 校验每个模型配置
        for (ModelConfig config : configs) {
            errors.addAll(config.validate());
        }

        // 检查重复的 modelId
        Map<String, Integer> idCounts = new LinkedHashMap<>();
        for (ModelConfig config : configs) {
            String id = config.getModelId();
            if (id != null) {
                idCounts.merge(id, 1, Integer::sum);
            }
        }
        idCounts.forEach((id, count) -> {
            if (count > 1) {
                errors.add("重复的 modelId '" + id + "' 出现了 " + count + " 次");
            }
        });

        // 检查重复的 providerId
        if (rawConfig.getProviders() != null) {
            Map<String, Integer> providerCounts = new LinkedHashMap<>();
            for (ProviderConfig provider : rawConfig.getProviders()) {
                if (provider != null && provider.getProviderId() != null) {
                    providerCounts.merge(provider.getProviderId(), 1, Integer::sum);
                }
            }
            providerCounts.forEach((id, count) -> {
                if (count > 1) {
                    errors.add("重复的 providerId '" + id + "' 出现了 " + count + " 次");
                }
            });
        }

        // 检查是否至少有一个启用的模型
        if (configs.isEmpty()) {
            errors.add("配置中没有启用的模型");
        }

        return errors;
    }

    /**
     * 解析模型配置，将 providerId 引用解析为实际的 baseUrl 和 apiKey
     *
     * @param modelsConfig 模型配置容器
     * @return 解析后的模型配置列表
     */
    private static List<ModelConfig> resolveModelConfigs(Config modelsConfig) {
        List<ModelConfig> resolvedConfigs = new ArrayList<>();
        if (modelsConfig.getModels() == null) {
            return resolvedConfigs;
        }
        for (ModelConfig model : modelsConfig.getModels()) {
            if (model != null) {
                ModelConfig resolved = resolveModelConfig(model, modelsConfig.getProviders());
                if (resolved.getDisabled() == null || !resolved.getDisabled()) {
                    resolvedConfigs.add(resolved);
                }
            }
        }
        return resolvedConfigs;
    }

    /**
     * 解析单个模型配置，将 providerId 引用解析为实际的 baseUrl 和 apiKey
     *
     * @param model     模型配置
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
            log.warn("供应商未找到: {}，使用模型自身的 baseUrl 和 apiKey", model.getProviderId());
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
                model.isDefault(),
                model.getDisabled(),
                model.getReasoningEffort(),
                model.getParallelToolCalls(),
                model.getStreamUsage(),
                model.getToolChoice(),
                model.getExtraBody(),
                model.getContextWindow()
        );
    }

    /**
     * 根据 providerId 查找供应商配置
     *
     * @param providerId 供应商标识符
     * @param providers  供应商配置列表
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
     *
     * @param configPath 配置文件路径
     */
    private static void createDefaultConfigFile(Path configPath) {
        try {
            // 确保目录存在
            Path parentDir = configPath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
                log.info("已创建配置目录: {}", parentDir);
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
                    log.info("成功从项目资源中加载 models.json");
                } else {
                    log.warn("项目资源中未找到 models.json，使用硬编码默认配置");
                }
            } catch (Exception e) {
                log.warn("从项目资源加载 models.json 失败: {}，使用硬编码默认配置", e.getMessage());
            }
            if (jsonContent != null) {
                // 写入文件
                Files.writeString(configPath, jsonContent, StandardCharsets.UTF_8);
                log.info("已在 {} 创建默认模型配置文件", configPath);
                log.info("请编辑配置文件，将 apiKey 字段更新为您的实际 API 密钥");
            }
        } catch (IOException e) {
            log.error("创建默认配置文件失败 {}: {}", configPath, e.getMessage());
        }
    }

    /**
     * 获取默认模型配置
     *
     * @param configs 配置列表
     * @return 默认的 ModelConfig，如果没有标记为默认的则返回第一个，没有配置则返回 null
     */
    public static ModelConfig getDefaultConfig(List<ModelConfig> configs) {
        if (configs == null || configs.isEmpty()) {
            return null;
        }

        // 查找标记为默认的配置
        for (ModelConfig config : configs) {
            if (config.isDefault() != null && config.isDefault()) {
                return config;
            }
        }

        // 如果没有默认配置，返回第一个
        return configs.get(0);
    }

    /**
     * 根据模型名称查找配置（支持 modelId 和 modelName 匹配）
     *
     * @param modelName 模型名称
     * @param configs   配置列表
     * @return 找到的配置，未找到返回 null
     */
    public static ModelConfig findConfigByModelName(String modelName, List<ModelConfig> configs) {
        if (configs == null || configs.isEmpty() || modelName == null) {
            return null;
        }

        for (ModelConfig config : configs) {
            if (modelName.equals(config.getModelId()) || modelName.equals(config.getModelName())) {
                return config;
            }
        }

        return null;
    }

    /**
     * 获取配置文件路径
     *
     * @return 配置文件的完整路径
     */
    public static String getConfigFilePath() {
        return Paths.get(DEFAULT_CONFIG_DIR, CONFIG_FILE_NAME).toString();
    }
}
