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

import com.agentclientprotocol.common.ClientSessionOperations;
import com.agentclientprotocol.model.ContentBlock;
import com.agentclientprotocol.model.SessionUpdate;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.xr21.ai.agent.bridge.BridgeKt;
import com.xr21.ai.agent.entity.AgentOutput;
import com.xr21.ai.agent.utils.AcpProgressUtil;
import com.xr21.ai.agent.utils.SinksUtil;
import com.xr21.ai.agent.utils.SuspendKt;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static com.xr21.ai.agent.acp.AgiAgentKt.CLIENT_SESSION_CONTEXT_KEY;

/**
 * Tool that enables invoking workers to handle complex, isolated tasks.
 * <p>
 * This tool allows the main agent to delegate work to specialized workers,
 * each with their own context and capabilities.
 *
 * @author Endless
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
        return FunctionToolCallback.builder("worker", new WorkerTool(workers)).description(description).inputType(WorkerRequest.class).build();
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
        // 每次 worker 调用前清理上次的 msg 回传结果，避免跨调用串扰；
        // 将主智能体下达的可选参数（成果物文件名、返回格式）作为上下文并入任务描述，
        // 由 worker 自行决定如何输出、采用何种格式以及是否写入文件
        String taskPrompt = buildWorkerContext(request);
        try {
            if (context.getContext().get("_AGENT_CONFIG_") instanceof RunnableConfig config) {
                if (config.context().get(CLIENT_SESSION_CONTEXT_KEY) instanceof ClientSessionOperations clientSessionOperations) {
                    StringBuilder builder = new StringBuilder();
                    try {
                        String sessionId = "session-worker-" + System.currentTimeMillis();
                        RunnableConfig runnableConfig = RunnableConfig.builder().threadId(sessionId).build();
                        AgentOutput<@NotNull Object> blockedLast = worker.stream(taskPrompt, runnableConfig).map(SinksUtil.INSTANCE::buildContent).doOnNext(output -> {
                            if (StringUtils.hasText(output.getChunk())) {
                                builder.append(output.getChunk());
                                AcpProgressUtil.sendProgress(context, request.taskId, builder.toString());
                            }
                            if (StringUtils.hasText(output.getThink())) {
                                builder.append(output.getThink());
                                AcpProgressUtil.sendProgress(context, request.taskId, builder.toString());
                            }
                        }).doOnComplete(() -> {
                            SuspendKt.runSuspend((completion) -> {
                                log.info("Workers task complete");
                                SessionUpdate notification = BridgeKt.buildAgentThoughtChunk(new ContentBlock.Text("\n[Worker completed : " + request.title + "]", null, null));
                                clientSessionOperations.notify(notification, null, completion);
                                return null;
                            });
                        }).doOnError(e -> {
                            log.error("Workers task failed", e);
                        }).blockLast();
                        if (blockedLast != null && blockedLast.data.getOrDefault("messages", List.of()) instanceof List<?> msgs) {
                            for (int i = msgs.size() - 1; i >= 0; i--) {
                                if (msgs.get(i) instanceof AssistantMessage am) {
                                    return am.getText();
                                }
                            }
                        }
                        return builder.toString();
                    } catch (GraphRunnerException e) {
                        return "Error executing worker task: " + e.getMessage();
                    }
                }
            } else {
                AssistantMessage workerOutput = worker.call(taskPrompt);
                // 若 worker 通过 msg 工具显式回传了结果，则优先返回该结构化结果，
                // 使主智能体可在 run_groovy_script 中解析结果并进行并行/分支编排
                return workerOutput.getText();
            }
            return "Error executing worker task";
        } catch (Exception e) {
            return "Error executing worker task: " + e.getMessage();
        }

    }

    /**
     * 将主智能体下达的可选参数（成果物文件名、返回格式）与任务描述合并为 worker 的上下文，
     * 由 worker 智能体结合任务实际自行决定输出方式。fileName/resultType 仅作为期望提示，
     * 不强制 worker 遵守。
     */
    private static String buildWorkerContext(WorkerRequest request) {
        StringBuilder sb = new StringBuilder(request.description == null ? "" : request.description);
        boolean hasFileName = StringUtils.hasText(request.fileName);
        boolean hasResultType = StringUtils.hasText(request.resultType);
        boolean hasTaskId = StringUtils.hasText(request.taskId);
        if (hasFileName || hasResultType || hasTaskId) {
            sb.append("\n\n## 任务上下文（主智能体期望，仅作参考，由你根据任务实际决定如何回传）");
            if (hasTaskId) {
                sb.append("\n- 当前任务id: ").append(request.taskId).append("（调用msg工具时透传该任务id，以将结果返回给调用方）");
            }
            if (hasFileName) {
                sb.append("\n- 期望成果物文件路径: ").append(request.fileName);
            }
            if (hasResultType) {
                sb.append("\n- 期望返回格式: ").append(request.resultType);
            }
            sb.append("\n请结合任务实际自行决定：回传格式（text/boolean/json/file）、是否写入文件").append("（如需写入，请在 msg 工具中指定 file_name 或 result_type=file）以及最终回传内容。");
        }
        return sb.toString();
    }

    /**
     * Request structure for the worker tool.
     */
    public static class WorkerRequest {

        @JsonProperty(required = true, value = "task_id")
        @JsonPropertyDescription("""
                此工作程序调用的唯一任务ID，示例：task-001
                用于在多个工作程序并发运行时，将此工作程序的实时进度路由到其自己的ACP SessionUpdate
                """)
        public String taskId;

        @JsonProperty(required = true)
        @JsonPropertyDescription("Detailed description of the task to be performed by the worker")
        public String description;

        @JsonProperty(required = true, value = "worker_type")
        @JsonPropertyDescription("The type of worker to use for this task")
        public String workerType;

        @JsonProperty(required = true, value = "title")
        @JsonPropertyDescription("concise description of what this command does in 5-10 words, in active voice")
        String title;

        @JsonProperty(value = "file_name")
        @JsonPropertyDescription("(可选) 期望 worker 写入成果物的目标文件路径。作为上下文提示下发给 worker，由 worker 自行决定是否写文件及写入位置")
        String fileName;

        @JsonProperty(required = true, value = "result_type")
        @JsonPropertyDescription("(可选) 期望 worker 返回的结果格式：text(默认)/boolean/json/file。仅作为上下文提示下发给 worker，由 worker 自行决定实际回传格式")
        String resultType;

        public WorkerRequest() {
        }

        public WorkerRequest(String description, String workerType) {
            this.description = description;
            this.workerType = workerType;
        }
    }
}