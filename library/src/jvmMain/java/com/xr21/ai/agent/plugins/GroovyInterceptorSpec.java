package com.xr21.ai.agent.plugins;

import groovy.lang.Closure;
import lombok.Builder;
import lombok.Data;

/**
 * 插件拦截器规格：脚本返回的插件描述 Map 中 interceptors[] 一条的解析结果。
 *
 * @param name  拦截器名（须唯一）
 * @param type  拦截器类型：model（interceptModel）或 tool（interceptToolCall）
 * @param apply 拦截闭包，model 型签名 apply(ModelRequest req, ModelCallHandler handler) 返回 ModelResponse；
 *              tool 型签名 apply(ToolCallRequest req, ToolCallHandler handler) 返回 ToolCallResponse
 */
@Data
@Builder
public class GroovyInterceptorSpec {
    private final String name;
    private final String type;
    private final Closure<?> apply;
}
