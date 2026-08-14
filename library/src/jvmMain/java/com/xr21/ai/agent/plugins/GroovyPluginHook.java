package com.xr21.ai.agent.plugins;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.AgentHook;
import com.alibaba.cloud.ai.graph.agent.hook.Hook;
import com.alibaba.cloud.ai.graph.agent.hook.ModelHook;
import groovy.lang.Closure;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 把插件 hooks[] 闭包适配为 {@link AgentHook} / {@link ModelHook}。
 * <p>
 * 闭包签名：run(OverAllState state, RunnableConfig config) 返回 Map 合并进状态；
 * position 决定执行时机（BEFORE_AGENT / AFTER_AGENT / BEFORE_MODEL / AFTER_MODEL），
 * 非对应时机返回空更新。
 */
@Slf4j
public final class GroovyPluginHook {

    private GroovyPluginHook() {
    }

    public static Hook create(GroovyHookSpec spec) {
        String pos = spec.getPosition() == null ? "BEFORE_AGENT" : spec.getPosition().toUpperCase();
        return switch (pos) {
            case "BEFORE_AGENT", "AFTER_AGENT" -> new AgentHookAdapter(spec, pos);
            case "BEFORE_MODEL", "AFTER_MODEL" -> new ModelHookAdapter(spec, pos);
            default -> throw new IllegalArgumentException("未知 hook position: " + pos + "（支持 BEFORE_AGENT/AFTER_AGENT/BEFORE_MODEL/AFTER_MODEL）");
        };
    }

    private static CompletableFuture<Map<String, Object>> invoke(Closure<?> run, OverAllState state, RunnableConfig config) {
        if (run == null) {
            return CompletableFuture.completedFuture(Map.of());
        }
        try {
            Object result = run.call(state, config);
            if (result instanceof Map<?, ?> m) {
                Map<String, Object> out = new LinkedHashMap<>();
                m.forEach((k, v) -> out.put(String.valueOf(k), v));
                return CompletableFuture.completedFuture(out);
            }
            return CompletableFuture.completedFuture(Map.of());
        } catch (Exception e) {
            log.warn("GroovyPluginHook execution failed", e);
            return CompletableFuture.completedFuture(Map.of());
        }
    }

    static class AgentHookAdapter extends AgentHook {
        private final GroovyHookSpec spec;
        private final String position;

        AgentHookAdapter(GroovyHookSpec spec, String position) {
            this.spec = spec;
            this.position = position;
        }

        @Override
        public String getName() {
            return spec.getName();
        }

        @Override
        public CompletableFuture<Map<String, Object>> beforeAgent(OverAllState state, RunnableConfig config) {
            return "BEFORE_AGENT".equals(position) ? invoke(spec.getRun(), state, config) : super.beforeAgent(state, config);
        }

        @Override
        public CompletableFuture<Map<String, Object>> afterAgent(OverAllState state, RunnableConfig config) {
            return "AFTER_AGENT".equals(position) ? invoke(spec.getRun(), state, config) : super.afterAgent(state, config);
        }
    }

    static class ModelHookAdapter extends ModelHook {
        private final GroovyHookSpec spec;
        private final String position;

        ModelHookAdapter(GroovyHookSpec spec, String position) {
            this.spec = spec;
            this.position = position;
        }

        @Override
        public String getName() {
            return spec.getName();
        }

        @Override
        public CompletableFuture<Map<String, Object>> beforeModel(OverAllState state, RunnableConfig config) {
            return "BEFORE_MODEL".equals(position) ? invoke(spec.getRun(), state, config) : super.beforeModel(state, config);
        }

        @Override
        public CompletableFuture<Map<String, Object>> afterModel(OverAllState state, RunnableConfig config) {
            return "AFTER_MODEL".equals(position) ? invoke(spec.getRun(), state, config) : super.afterModel(state, config);
        }
    }
}
