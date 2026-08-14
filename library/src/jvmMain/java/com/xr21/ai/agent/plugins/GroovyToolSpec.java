package com.xr21.ai.agent.plugins;

import groovy.lang.Closure;
import lombok.Builder;
import lombok.Data;

/**
 * 插件工具规格：脚本返回的插件描述 Map 中 tools[] 一条的解析结果。
 *
 * @param name        工具名（须与内置/已注册工具不重名）
 * @param description 工具描述
 * @param inputSchema JSON Schema 字符串（模型工具集使用）
 * @param run         工具执行闭包，签名 run(Map args)
 */
@Data
@Builder
public class GroovyToolSpec {
    private final String name;
    private final String description;
    private final String inputSchema;
    private final Closure<?> run;
}
