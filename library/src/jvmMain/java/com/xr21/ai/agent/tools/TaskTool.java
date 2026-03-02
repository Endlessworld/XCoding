/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.xr21.ai.agent.tools;

import com.agentclientprotocol.sdk.agent.SyncPromptContext;
import com.agentclientprotocol.sdk.spec.AcpSchema;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.xr21.ai.agent.entity.AgentOutput;
import com.xr21.ai.agent.utils.SinksUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * Tool that enables invoking subagents to handle complex, isolated tasks.
 *
 * This tool allows the main agent to delegate work to specialized subagents,
 * each with their own context and capabilities.
 */
@Slf4j
public class TaskTool implements BiFunction<TaskTool.TaskRequest, ToolContext, String> {

    private final Map<String, ReactAgent> subAgents;

    public TaskTool(Map<String, ReactAgent> subAgents) {
        this.subAgents = subAgents;
    }

    /**
     * Create a ToolCallback for the task tool.
     */
    public static ToolCallback createTaskToolCallback(Map<String, ReactAgent> subAgents, String description) {
        return FunctionToolCallback.builder("task", new TaskTool(subAgents))
                .description(description)
                .inputType(TaskRequest.class)
                .build();
    }

    @Override
    public String apply(TaskRequest request, ToolContext context) {
        // Validate subagent type
        if (!subAgents.containsKey(request.subagentType)) {
            return "Error: invoked agent of type " + request.subagentType + ", the only allowed types are " + subAgents.keySet();
        }
        // Get the subagent
        ReactAgent subAgent = subAgents.get(request.subagentType);
        // Invoke the subagent with the task description
        log.info("subAgents task" + request.description);
        try {
            // Return the subagent's response
            if (context.getContext().get("_AGENT_CONFIG_") instanceof RunnableConfig config) {
                log.info("config context {}", config.context());
                log.info("config context PromptRequest {}", config.context().get("PromptRequest"));
                log.info("config context SyncPromptContext {}", config.context().get("SyncPromptContext"));
                if (config.context().get("SyncPromptContext") instanceof SyncPromptContext syncPromptContext) {
                    if (config.context().get("PromptRequest") instanceof AcpSchema.PromptRequest promptRequest) {
                        StringBuilder builder = new StringBuilder();
                        Flux<AgentOutput<Object>> flux = SinksUtil.sinksOutput(subAgent.stream(request.description));
                        AgentOutput<Object> blockLast = flux.doOnNext(output -> {
                            if (StringUtils.hasText(output.getChunk())) {
                                builder.append(output.getChunk());
                                syncPromptContext.sendThought(output.getChunk());
                            }
                            if (StringUtils.hasText(output.getThink())) {
                                syncPromptContext.sendThought(output.getThink());
                            }
                        }).doOnComplete(() -> {
                            log.info("subAgents task complete");
                            syncPromptContext.sendThought("子智能体任务执行结束");
                        }).doOnError(e -> {
                            log.error("subAgents task failed", e);
                            syncPromptContext.sendThought("子智能体任务执行失败" + e.getMessage());
                        }).blockLast();
                        return builder.toString();
                    }
                }
                return subAgent.call(request.description).getText();
            } else {
                return subAgent.call(request.description).getText();
            }
        } catch (Exception e) {
            return "Error executing subagent task: " + e.getMessage();
        }
    }

    /**
     * Request structure for the task tool.
     */
    public static class TaskRequest {

        @JsonProperty(required = true)
        @JsonPropertyDescription("Detailed description of the task to be performed by the subagent")
        public String description;

        @JsonProperty(required = true, value = "subagent_type")
        @JsonPropertyDescription("The type of subagent to use for this task")
        public String subagentType;

        public TaskRequest() {
        }

        public TaskRequest(String description, String subagentType) {
            this.description = description;
            this.subagentType = subagentType;
        }
    }
}

