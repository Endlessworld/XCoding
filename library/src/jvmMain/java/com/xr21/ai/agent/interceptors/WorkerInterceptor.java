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
package com.xr21.ai.agent.interceptors;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.extension.interceptor.SubAgentSpec;
import com.alibaba.cloud.ai.graph.agent.hook.Hook;
import com.alibaba.cloud.ai.graph.agent.hook.returndirect.ReturnDirectModelHook;
import com.alibaba.cloud.ai.graph.agent.interceptor.*;
import com.xr21.ai.agent.tools.MsgTool;
import com.xr21.ai.agent.tools.WorkerTool;
import com.xr21.ai.agent.utils.Prompts;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.tool.ToolCallback;

import java.util.*;

/**
 * Worker interceptor that provides worker invocation capabilities to agents.
 * <p>
 * This interceptor adds a `worker` tool to the agent that can be used to invoke workers.
 * Workers are useful for handling complex tasks that require multiple steps, or tasks
 * that require a lot of context to resolve.
 * <p>
 * A chief benefit of workers is that they can handle multi-step tasks, and then return
 * a clean, concise response to the main agent.
 * <p>
 * This interceptor comes with a default worker worker that can be used to
 * handle the same tasks as the main agent, but with isolated context.
 * <p>
 * Example:
 * <pre>
 * WorkerInterceptor interceptor = WorkerInterceptor.builder()
 *     .defaultModel(chatModel)
 *     .addWorker(WorkerSpec.builder()
 *         .name("research-analyst")
 *         .description("Use this agent to conduct thorough research on complex topics")
 *         .systemPrompt("You are a research analyst...")
 *         .build())
 *     .build();
 * </pre>
 *
 * @author Endless
 */
public class WorkerInterceptor extends ModelInterceptor {

    private final List<ToolCallback> tools;
    private final String systemPrompt;
    private final Map<String, ReactAgent> workers;
    private final boolean includeGeneralPurpose;

    private WorkerInterceptor(Builder builder) {
        this.systemPrompt = builder.systemPrompt != null ? builder.systemPrompt : Prompts.WORKER_SYSTEM_PROMPT;
        this.workers = new HashMap<>(builder.workers);
        this.includeGeneralPurpose = builder.includeGeneralPurpose;

        // Create worker tool using the factory method
        ToolCallback workerTool = WorkerTool.createWorkerToolCallback(
                this.workers,
                buildWorkerToolDescription()
        );

        this.tools = Collections.singletonList(workerTool);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 在 worker 工具列表基础上追加 msg 回传工具。
     */
    private static List<ToolCallback> withMsgTool(List<ToolCallback> tools) {
        List<ToolCallback> allTools = new ArrayList<>();
        if (tools != null) {
            allTools.addAll(tools);
        }
        allTools.add(MsgTool.createMsgToolCallback());
        return allTools;
    }

    private String buildWorkerToolDescription() {
        StringBuilder workerDescriptions = new StringBuilder();

        if (includeGeneralPurpose) {
            workerDescriptions.append("- worker: ")
                    .append(Prompts.WORKER_GENERAL_PURPOSE_DESCRIPTION)
                    .append("\n");
        }

        for (Map.Entry<String, ReactAgent> entry : workers.entrySet()) {
            if (!"worker".equals(entry.getKey())) {
                workerDescriptions.append("- ")
                        .append(entry.getKey())
                        .append(": ")
                        .append(entry.getValue().description() != null ?
                                entry.getValue().description() : "Custom worker")
                        .append("\n");
            }
        }

        return Prompts.WORKER_TOOL_DESCRIPTION.replace("{available_workers}", workerDescriptions.toString());
    }

    @Override
    public List<ToolCallback> getTools() {
        return tools;
    }

    @Override
    public String getName() {
        return "Worker";
    }

    @Override
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
        // Enhance the system prompt with worker guidance
        SystemMessage enhancedSystemMessage;

        if (request.getSystemMessage() == null) {
            enhancedSystemMessage = new SystemMessage(this.systemPrompt);
        } else {
            enhancedSystemMessage = new SystemMessage(request.getSystemMessage().getText() + "\n\n" + systemPrompt);
        }

        // Create enhanced request
        ModelRequest enhancedRequest = ModelRequest.builder(request)
                .systemMessage(enhancedSystemMessage)
                .build();

        // Call the handler with enhanced request
        return handler.call(enhancedRequest);
    }

    public static class Builder {
        private String systemPrompt;
        private ChatModel defaultModel;
        private List<ToolCallback> defaultTools;
        private List<Interceptor> defaultInterceptors;
        private List<Hook> defaultHooks;
        private final Map<String, ReactAgent> workers = new HashMap<>();
        private boolean includeGeneralPurpose = true;

        /**
         * Set custom system prompt to guide worker usage.
         */
        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        /**
         * Set the default model to use for workers.
         */
        public Builder defaultModel(ChatModel model) {
            this.defaultModel = model;
            return this;
        }

        /**
         * Set the default tools available to workers.
         */
        public Builder defaultTools(List<ToolCallback> tools) {
            this.defaultTools = tools;
            return this;
        }


        public Builder defaultInterceptors(Interceptor... interceptors) {
            this.defaultInterceptors = Arrays.asList(interceptors);
            return this;
        }

        /**
         * Set the default hooks to apply to workers.
         */
        public Builder defaultHooks(Hook... hooks) {
            this.defaultHooks = Arrays.asList(hooks);
            return this;
        }

        /**
         * Add a custom worker.
         */
        public Builder addWorker(String name, ReactAgent agent) {
            this.workers.put(name, agent);
            return this;
        }

        /**
         * Add a worker from specification.
         */
        public Builder addWorker(SubAgentSpec spec) {
            ReactAgent agent = createWorkerFromSpec(spec);
            this.workers.put(spec.getName(), agent);
            return this;
        }

        /**
         * Whether to include the default worker worker.
         */
        public Builder includeGeneralPurpose(boolean include) {
            this.includeGeneralPurpose = include;
            return this;
        }

        private ReactAgent createWorkerFromSpec(SubAgentSpec spec) {
            com.alibaba.cloud.ai.graph.agent.Builder builder = ReactAgent.builder()
                    .name(spec.getName())
                    .description(spec.getDescription())
                    .instruction(spec.getSystemPrompt())
                    .outputKey("worker")
                    .outputType(MsgTool.MsgRequest.class);
//                    .saver(new MemorySaver());

            ChatModel model = spec.getModel() != null ? spec.getModel() : defaultModel;
            if (model != null) {
                builder.model(model);
            }

            List<ToolCallback> tools = spec.getTools() != null ? spec.getTools() : defaultTools;
            // 为 worker 注入专有的 msg 回传工具，使 worker 可将执行成果回传给主智能体
            builder.tools(withMsgTool(tools));

            // Apply default interceptors first, then custom ones
            List<Interceptor> allInterceptors = new ArrayList<>();
            if (defaultInterceptors != null) {
                allInterceptors.addAll(defaultInterceptors);
            }
            if (spec.getInterceptors() != null) {
                allInterceptors.addAll(spec.getInterceptors());
            }

            if (!allInterceptors.isEmpty()) {
                builder.interceptors(allInterceptors);
            }

            if (defaultHooks != null) {
                builder.hooks(defaultHooks);
            }
            builder.hooks(new ReturnDirectModelHook());
            builder.enableLogging(spec.isEnableLoopingLog());
            builder.chatOptions(ChatOptions.builder().model("mimo-v2.5").build());
            return builder.build();
        }

        public WorkerInterceptor build() {
            // Add the default worker worker reusing the same creation path as custom
            // workers, so it also receives ReturnDirectModelHook and default hooks/interceptors.
            if (includeGeneralPurpose && defaultModel != null) {
                SubAgentSpec generalPurposeSpec = SubAgentSpec.builder()
                        .name("worker")
                        .description(Prompts.WORKER_GENERAL_PURPOSE_DESCRIPTION)
                        .systemPrompt(Prompts.WORKER_DEFAULT_PROMPT)
                        .model(defaultModel)
                        .tools(defaultTools)
                        .build();
                this.workers.put("worker", createWorkerFromSpec(generalPurposeSpec));
            }
            return new WorkerInterceptor(this);
        }
    }
}