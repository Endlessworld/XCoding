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
 * Tool that enables invoking workers to handle complex, isolated tasks.
 *
 * This tool allows the main agent to delegate work to specialized workers,
 * each with their own context and capabilities.
 */
@Slf4j
public class WorkerTool implements BiFunction<WorkerTool.WorkerRequest, ToolContext, String> {

    private final Map<String, ReactAgent> workers;

    public WorkerTool(Map<String, ReactAgent> workers) {
        this.workers = workers;
    }

    /**
     * Create a ToolCallback for the worker tool.
     */
    public static ToolCallback createWorkerToolCallback(Map<String, ReactAgent> workers, String description) {
        return FunctionToolCallback.builder("worker", new WorkerTool(workers))
                .description(description)
                .inputType(WorkerRequest.class)
                .build();
    }

    @Override
    public String apply(WorkerRequest request, ToolContext context) {
        // Validate worker type
        if (!workers.containsKey(request.workerType)) {
            return "Error: invoked worker of type " + request.workerType + ", the only allowed types are " + workers.keySet();
        }
        // Get the worker
        ReactAgent worker = workers.get(request.workerType);
        // Invoke the worker with the task description
        log.info("Workers task" + request.description);
        try {
            // Return the worker's response
            if (context.getContext().get("_AGENT_CONFIG_") instanceof RunnableConfig config) {
                log.info("config context {}", config.context());
                log.info("config context PromptRequest {}", config.context().get("PromptRequest"));
                log.info("config context SyncPromptContext {}", config.context().get("SyncPromptContext"));
                if (config.context().get("SyncPromptContext") instanceof SyncPromptContext syncPromptContext) {
                    if (config.context().get("PromptRequest") instanceof AcpSchema.PromptRequest promptRequest) {
                        StringBuilder builder = new StringBuilder();
                        Flux<AgentOutput<Object>> flux = SinksUtil.sinksOutput(worker.stream(request.description));
                        AgentOutput<Object> blockLast = flux.doOnNext(output -> {
                            if (StringUtils.hasText(output.getChunk())) {
                                builder.append(output.getChunk());
//                                syncPromptContext.sendThought(output.getChunk());
                            }
                            if (StringUtils.hasText(output.getThink())) {
//                                syncPromptContext.sendThought(output.getThink());
                            }
                        }).doOnComplete(() -> {
                            log.info("Workers task complete");
//                            syncPromptContext.sendThought("Worker任务执行结束");
                        }).doOnError(e -> {
                            log.error("Workers task failed", e);
//                            syncPromptContext.sendThought("Worker任务执行失败" + e.getMessage());
                        }).blockLast();
                        return builder.toString();
                    }
                }
                return worker.call(request.description).getText();
            } else {
                return worker.call(request.description).getText();
            }
        } catch (Exception e) {
            return "Error executing worker task: " + e.getMessage();
        }
    }

    /**
     * Request structure for the worker tool.
     */
    public static class WorkerRequest {

        @JsonProperty(required = true)
        @JsonPropertyDescription("Detailed description of the task to be performed by the worker")
        public String description;

        @JsonProperty(required = true, value = "worker_type")
        @JsonPropertyDescription("The type of worker to use for this task")
        public String workerType;

        public WorkerRequest() {
        }

        public WorkerRequest(String description, String workerType) {
            this.description = description;
            this.workerType = workerType;
        }
    }
}