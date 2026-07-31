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
package com.xr21.ai.agent.entity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.Toolkit;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Adapts a Spring AI {@link ToolCallback} (methods annotated with Spring's
 * {@code @Tool} + {@code @JsonProperty}) into an AgentScope {@link AgentTool},
 * so the ai-agents tool set can be loaded and executed by AgentScope's
 * {@link Toolkit}.
 *
 * <p>The wrapper reads name/description/input-schema from the Spring tool
 * definition and delegates execution by serialising the AgentScope input map
 * to JSON and invoking the Spring callback. Results are wrapped in a
 * {@link ToolResultBlock}.
 *
 * @author Endless
 */
public final class AgentScopeToolAdapter implements AgentTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Spring tools whose names map to read-only semantics in AgentScope.
     */
    private static final Set<String> READ_ONLY_NAMES =
            Set.of("read_file", "read", "ls", "list_files", "list", "glob", "grep");

    private final ToolCallback callback;
    private final ToolDefinition definition;
    private final Map<String, Object> parameters;

    public AgentScopeToolAdapter(ToolCallback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("ToolCallback must not be null");
        }
        this.callback = callback;
        this.definition = callback.getToolDefinition();
        this.parameters = parseSchema(definition.inputSchema());
    }

    private static Map<String, Object> parseSchema(String inputSchema) {
        if (inputSchema == null || inputSchema.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(inputSchema,
                    new TypeReference<>() {
                    });
        } catch (Exception e) {
            return Map.of();
        }
    }

    @Override
    public String getName() {
        return definition.name();
    }

    @Override
    public String getDescription() {
        return definition.description();
    }

    @Override
    public Map<String, Object> getParameters() {
        return parameters;
    }

    @Override
    public boolean isReadOnly() {
        return READ_ONLY_NAMES.contains(definition.name());
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        return Mono.fromCallable(() -> {
            String inputJson = MAPPER.writeValueAsString(param.getInput());
            String result = callback.call(inputJson,new ToolContext(Map.of()));
            return ToolResultBlock.builder().id(param.getToolUseBlock().getId())
                    .name(param.getToolUseBlock().getName())
                    .output(TextBlock.builder().text(result).build()).build();
        }).onErrorResume(e -> Mono.just(ToolResultBlock.error(errorMessage(e))));
    }

    private static String errorMessage(Throwable e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        return cause.getMessage() != null ? cause.getMessage() : cause.toString();
    }

    /**
     * Registers a collection of Spring AI {@link ToolCallback}s into an AgentScope
     * {@link Toolkit}, each wrapped as an {@link AgentScopeToolAdapter}.
     *
     * @param toolkit   the target AgentScope toolkit (must not be null)
     * @param callbacks the Spring AI tool callbacks to register
     */
    public static void registerSpringTools(Toolkit toolkit, List<ToolCallback> callbacks) {
        if (toolkit == null || callbacks == null) {
            return;
        }
        for (ToolCallback callback : callbacks) {
            toolkit.registerTool(new AgentScopeToolAdapter(callback));
        }
    }
}
