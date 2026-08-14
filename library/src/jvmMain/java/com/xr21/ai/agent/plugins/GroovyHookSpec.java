package com.xr21.ai.agent.plugins;

import groovy.lang.Closure;
import lombok.Builder;
import lombok.Data;

/**
 * 插件钩子规格：脚本返回的插件描述 Map 中 hooks[] 一条的解析结果。
 *
 * @param name     钩子名（须唯一）
 * @param position 执行位置：BEFORE_AGENT / AFTER_AGENT / BEFORE_MODEL / AFTER_MODEL
 * @param run      钩子闭包，签名 run(OverAllState state, RunnableConfig config) 返回 Map 合并进状态
 */
@Data
@Builder
public class GroovyHookSpec {
    private final String name;
    private final String position;
    private final Closure<?> run;
}
