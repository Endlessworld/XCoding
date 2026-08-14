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

package com.xr21.ai.agent.model

import com.fasterxml.jackson.annotation.JsonGetter
import com.fasterxml.jackson.annotation.JsonSetter
import lombok.AllArgsConstructor
import lombok.Data
import lombok.NoArgsConstructor

class Config {
    /**
     * 供应商配置列表
     */
    val providers: MutableList<ProviderConfig?> = ArrayList()

    /**
     * 模型配置列表
     */
    val models: MutableList<ModelConfig?> = ArrayList()


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
        @get:JsonGetter("isDefault")
        @set:JsonSetter("isDefault")
        var isDefault: Boolean? = false,
        /**
         * 是否禁用
         */
        var disabled: Boolean? = false,
        /**
         * 推理强度（如 "low", "medium", "high"），用于支持 reasoning 模型
         */
        var reasoningEffort: String? = null,
        /**
         * 是否启用并行工具调用
         */
        var parallelToolCalls: Boolean? = null,
        /**
         * 是否启用流式用量统计
         */
        var streamUsage: Boolean? = null,
        /**
         * 工具选择策略（如 "auto", "none", "required"）
         */
        var toolChoice: String? = null,
        /**
         * 额外的请求体参数，用于传递供应商特有的配置
         */
        var extraBody: MutableMap<String, Any?>? = null,
        /**
         * 模型上下文窗口大小（token 数），用于 ACP usage_update 的 size 字段
         * 未配置时由 AgiAgentSession 使用默认值
         */
        var contextWindow: Long? = null
    ) {
        /**
         * 校验模型配置的有效性
         *
         * @return 错误信息列表，为空表示校验通过
         */
        fun validate(): List<String> {
            val errors = mutableListOf<String>()
            if (modelId.isNullOrBlank()) errors.add("模型ID不能为空")
            if (modelName.isNullOrBlank()) errors.add("模型名称不能为空")
            if (providerId.isNullOrBlank() && baseUrl.isNullOrBlank()) {
                errors.add("模型 '$modelId' 必须指定 providerId 或 baseUrl")
            }
            if (providerId.isNullOrBlank() && apiKey.isNullOrBlank()) {
                errors.add("模型 '$modelId' 必须指定 providerId 或 apiKey")
            }
            if (temperature != null && (temperature!! < 0.0 || temperature!! > 2.0)) {
                errors.add("模型 '$modelId' 的 temperature 必须在 0.0 到 2.0 之间")
            }
            if (maxTokens != null && maxTokens!! <= 0) {
                errors.add("模型 '$modelId' 的 maxTokens 必须大于 0")
            }
            return errors
        }
    }
}
