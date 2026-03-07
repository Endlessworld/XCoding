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
package com.xr21.ai.agent.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 模型配置容器类，用于从 JSON 文件加载完整的模型配置
 * <p>
 * 新格式支持：
 * - providers: 定义供应商配置（baseUrl 和 apiKey）
 * - models: 定义模型配置，通过 providerId 引用供应商
 * <p>
 * 这样可以避免在多个模型中重复配置相同的 baseUrl 和 apiKey
 *
 * @author Endless
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelsConfig {
    /**
     * 供应商配置列表
     */
    private List<ProviderConfig> providers = new ArrayList<>();

    /**
     * 模型配置列表
     */
    private List<ModelConfig> models = new ArrayList<>();


    /**
     * 供应商配置数据类，用于定义 API 供应商的通用配置信息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProviderConfig {
        /**
         * 供应商标识符（如 "volcengine", "deepseek", "openai"）
         */
        private String providerId;

        /**
         * API 基础 URL
         */
        private String baseUrl;

        /**
         * API 密钥
         */
        private String apiKey;
    }

    /**
     * 模型配置数据类，用于从 JSON 文件加载模型配置
     * <p>
     * 新格式：通过 providerId 引用供应商配置，避免重复配置 baseUrl 和 apiKey
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelConfig {
        /**
         * 模型ID（用于客户端标识和选择模型）
         */
        private String modelId;

        /**
         * 模型名称（实际发送给API的模型名称）
         */
        private String modelName;

        /**
         * 温度参数
         */
        private Double temperature;

        /**
         * 最大令牌数
         */
        private Integer maxTokens;

        /**
         * 供应商标识符（引用 providers 中的 providerId）
         * 如果提供了此字段，baseUrl 和 apiKey 可以省略
         */
        private String providerId;

        /**
         * API 基础 URL（可选，如果 providerId 已指定）
         */
        private String baseUrl;

        /**
         * API 密钥（可选，如果 providerId 已指定）
         */
        private String apiKey;

        /**
         * 是否为默认模型
         */
        private Boolean isDefault;
    }
}
