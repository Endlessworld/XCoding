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
package com.xr21.ai.agent.interceptors;

import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.ModelHook;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static com.xr21.ai.agent.acp.AgiAgentKt.SESSION_ID_CONTEXT_KEY;

/**
 * 持久化状态 Hook：在每次模型请求结束后（AFTER_MODEL），把
 * runnableConfig.context() 中需要跨快照恢复的条目返回给框架，
 * 由框架经 OverAllState.updateState(state, values, channels) 合入 state，
 * 从而随 Checkpoint 被 FileSystemSaver 持久化。
 * <p>
 * 说明：
 * <ul>
 *   <li>仅同步 {@link #PERSISTED_KEYS} 中声明的 key，避免把 Thread /
 *       ClientSessionOperations 等不可序列化的运行时对象写入 state
 *       （state 序列化器为 SpringAIJacksonStateSerializer，非 Jackson 可序列化对象会导致保存失败）。</li>
 *   <li>value 为 null 的条目会被过滤（updateState 不接受 null 值）。</li>
 *   <li>所有 key 均注册 {@link ReplaceStrategy}：每轮以最新值覆盖旧值。</li>
 * </ul>
 */
@Slf4j
@HookPositions(HookPosition.AFTER_MODEL)
public class PersistedStateHook extends ModelHook {

    /**
     * 需要从 context 合入 OverAllState（随快照持久化）的 key 白名单
     */
    public static final Set<String> PERSISTED_KEYS = Set.of("mode", "thought_level", "requestId", "sessionTotalTokens", "sessionCompletionTokens", "totalTokens", "completionTokens", SESSION_ID_CONTEXT_KEY);

    private static final Map<String, KeyStrategy> KEY_STRATEGIES = buildKeyStrategies();

    private static Map<String, KeyStrategy> buildKeyStrategies() {
        Map<String, KeyStrategy> strategies = new LinkedHashMap<>();
        for (String key : PERSISTED_KEYS) {
            strategies.put(key, new ReplaceStrategy());
        }
        return Map.copyOf(strategies);
    }

    @Override
    public String getName() {
        return "persisted_state";
    }

    @Override
    public Map<String, KeyStrategy> getKeyStrategys() {
        return KEY_STRATEGIES;
    }

    @Override
    public CompletableFuture<Map<String, Object>> afterModel(OverAllState state, RunnableConfig config) {
        Map<String, Object> context = config.context();
        if (context == null || context.isEmpty()) {
            return CompletableFuture.completedFuture(Map.of());
        }
        Map<String, Object> updates = new LinkedHashMap<>();
        for (String key : PERSISTED_KEYS) {
            Object value = context.get(key);
            if (value != null) {
                updates.put(key, value);
            }
        }
        var updatedAt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withLocale(Locale.CHINESE).format(LocalDateTime.now());
        updates.put("updatedAt", updatedAt);
        log.debug("PersistedStateHook merging context entries into state: {}", updates.keySet());
        // 返回值由框架经 OverAllState.updateState(state, values, channels) 合入，
        // 下一个 Checkpoint（addCheckpoint -> cloneState(overallState.data())）即包含这些 key
        return CompletableFuture.completedFuture(updates);
    }
}
