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

package com.xr21.ai.agent.model

import lombok.AllArgsConstructor
import lombok.Data
import lombok.NoArgsConstructor

class Config {
    /**
     * 供应商配置列表
     */
    val providers: MutableList<ProviderConfig?> = ArrayList<ProviderConfig?>()

    /**
     * 模型配置列表
     */
    val models: MutableList<ModelConfig?> = ArrayList<ModelConfig?>()


    /**
     * 供应商配置数据类，用于定义 API 供应商的通用配置信息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    class ProviderConfig {
        /**
         * 供应商标识符（如 "volcengine", "deepseek", "openai"）
         */
        var providerId: String? = null

        /**
         * API 基础 URL
         */
        var baseUrl: String? = null

        /**
         * API 密钥
         */
        var apiKey: String? = null
    }

    /**
     * 模型配置类，用于从 JSON 文件加载模型配置
     *
     *
     * 新格式：通过 providerId 引用供应商配置，避免重复配置 baseUrl 和 apiKey
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    class ModelConfig(
        /**
         * 模型ID（用于客户端标识和选择模型）
         */
        var modelId: String? = null,
        /**
         * 模型名称（实际发送给API的模型名称）
         */
        var modelName: String? = null,
        /**
         * 温度参数
         */
        var temperature: Double? = 0.65,
        /**
         * 最大令牌数
         */
        var maxTokens: Int? = null,
        /**
         * 供应商标识符（引用 providers 中的 providerId）
         * 如果提供了此字段，baseUrl 和 apiKey 可以省略
         */
        var providerId: String? = null,
        /**
         * API 基础 URL（可选，如果 providerId 已指定）
         */
        var baseUrl: String? = null,
        /**
         * API 密钥（可选，如果 providerId 已指定）
         */
        var apiKey: String? = null,
        /**
         * 是否为默认模型 (JSON中为 isDefault)
         */
        @get:com.fasterxml.jackson.annotation.JsonGetter("isDefault")
        @set:com.fasterxml.jackson.annotation.JsonSetter("isDefault")
        var isDefault: Boolean? = false,
        /**
         * 是否禁用
         */
        var disabled: Boolean? = false
    )
}