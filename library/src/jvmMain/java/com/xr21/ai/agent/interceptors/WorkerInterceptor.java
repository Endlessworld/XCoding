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

    private static final String DEFAULT_WORKER_PROMPT = "In order to complete the objective that the user asks of you, you have access to a number of standard tools. You should focus on user-assigned tasks and not do anything other than tasks";

    private static final String DEFAULT_SYSTEM_PROMPT = """
            ## `worker` (worker mode)
                  You are currently in worker mode !
            You have access to a `worker` tool to launch short-lived workers that handle isolated tasks. These workers are ephemeral — they live only for the duration of the task and return a single result.
            
            When to use the worker tool:
            - When a task is complex and multi-step, and can be fully delegated in isolation
            - When a task is independent of other tasks and can run in parallel
            - When a task requires focused reasoning or heavy token/context usage that would bloat the orchestrator thread
            - When sandboxing improves reliability (e.g. code execution, structured searches, data formatting)
            - When you only care about the output of the worker, and not the intermediate steps (ex. performing a lot of research and then returned a synthesized report, performing a series of computations or lookups to achieve a concise, relevant answer.)
            
            Worker lifecycle:
            1. **Spawn** → Provide clear role, instructions, and expected output
            2. **Run** → The worker completes the task autonomously
            3. **Return** → The worker provides a single structured result
            4. **Reconcile** → Incorporate or synthesize the result into the main thread
            
            When NOT to use the worker tool:
            - If you need to see the intermediate reasoning or steps after the worker has completed (the worker tool hides them)
            - If the task is trivial (a few tool calls or simple lookup)
            - If delegating does not reduce token usage, complexity, or context switching
            - If splitting would add latency without benefit
            
            ## Important Worker Tool Usage Notes to Remember
            - Whenever possible, parallelize the work that you do. This is true for both tool_calls, and for tasks. Whenever you have independent steps to complete - make tool_calls, or kick off workers in parallel to accomplish them faster. This saves time for the user, which is incredibly important.
            - Remember to use the `worker` tool to silo independent tasks within a multi-part objective.
            - You should use the `worker` tool whenever you have a complex task that will take multiple steps, and is independent from other tasks that the agent needs to complete. These workers are highly competent and efficient.
            """;

    private static final String DEFAULT_GENERAL_PURPOSE_DESCRIPTION =
            "worker worker for researching complex questions, searching for files and content, " +
                    "and executing multi-step tasks. This worker has access to all tools as the main agent.";

    private static final String WORKER_TOOL_DESCRIPTION = """
            Launch an ephemeral worker to handle complex, multi-step independent tasks with isolated context.
            
            Available worker types and the tools they have access to:
            {available_workers}
            
            When using the Worker tool, you must specify a worker_type parameter to select which worker type to use.
            
            ## Usage notes:
            1. Launch multiple workers concurrently whenever possible to maximize performance
            2. When the worker is done, it will return a single message back to you
            3. Each worker invocation is stateless - provide a highly detailed task description
            4. The worker's outputs should generally be trusted
            5. Clearly tell the worker whether you expect it to create content, perform analysis, or just do research
            6. If the worker description mentions that it should be used proactively, then you should try your best to use it without the user having to ask for it first. Use your judgement.
            7. When only the worker worker is provided, you should use it for all tasks. It is great for isolating context and token usage, and completing specific, complex tasks, as it has all the same capabilities as the main agent.
            8. Optional params when dispatching a task (作为上下文提示下发给 worker，最终由 worker 自行决定如何输出、是否写文件)：
               - file_name: 期望 worker 将执行成果写入的目标文件路径（可选）。仅作为上下文提示下发给 worker，由 worker 自行判断是否写文件；worker 若决定写文件，会在 msg 工具中指定 file_name 或 result_type=file。
               - result_type: 期望 worker 返回的格式，可选 text(默认)/boolean/json/file（可选）。仅作为上下文提示下发给 worker，由 worker 自行决定实际回传格式。
               - 每个 worker 完成后都会通过 msg 工具回传 JSON：{success, file_name,worker_type, result_type, content 或 filePath}，可在 run_groovy_script 中解析以进行并行/分支编排。
               - 回传 JSON字段说明 success : worker执行是否完成期望目标,content : worker 执行成果内容，需要回传给主智能体的结果,
            ### Example usage of the worker worker:
            
            <example_worker_descriptions>
            "worker": use this worker for general purpose tasks, it has access to all tools as the main agent.
            </example_worker_descriptions>
            
            <example>
            User: "I want to conduct research on the accomplishments of Lebron James, Michael Jordan, and Kobe Bryant, and then compare them."
            Assistant: *Uses the worker tool in parallel to conduct isolated research on each of the three players*
            Assistant: *Synthesizes the results of the three isolated research tasks and responds to the User*
            <commentary>
            Research is a complex, multi-step task in it of itself.
            The research of each individual player is not dependent on the research of the other players.
            The assistant uses the worker tool to break down the complex objective into three isolated tasks.
            Each research task only needs to worry about context and tokens about one player, then returns synthesized information about each player as the Tool Result.
            This means each research task can dive deep and spend tokens and context deeply researching each player, but the final result is synthesized information, and saves us tokens in the long run when comparing the players to each other.
            </commentary>
            </example>
            
            <example>
            User: "Analyze a single large code repository for security vulnerabilities and generate a report."
            Assistant: *Launches a single `worker` for the repository analysis*
            Assistant: *Receives report and integrates results into final summary*
            <commentary>
            Worker is used to isolate a large, context-heavy task, even though there is only one. This prevents the main thread from being overloaded with details.
            If the user then asks followup questions, we have a concise report to reference instead of the entire history of analysis and tool calls, which is good and saves us time and money.
            </commentary>
            </example>
            
            <example>
            User: "Schedule two meetings for me and prepare agendas for each."
            Assistant: *Calls the worker tool in parallel to launch two `worker` (one per meeting) to prepare agendas*
            Assistant: *Returns final schedules and agendas*
            <commentary>
            Tasks are simple individually, but workers help silo agenda preparation.
            Each worker only needs to worry about the agenda for one meeting.
            </commentary>
            </example>
            
            <example>
            User: "I want to order a pizza from Dominos, order a burger from McDonald's, and order a salad from Subway."
            Assistant: *Calls tools directly in parallel to order a pizza from Dominos, a burger from McDonald's, and a salad from Subway*
            <commentary>
            The assistant did not use the worker tool because the objective is super simple and clear and only requires a few trivial tool calls.
            It is better to complete the task directly and NOT use the `worker` tool.
            </commentary>
            </example>
            
            ### Example usage with custom workers:
            
            <example_worker_descriptions>
            "content-reviewer": use this worker after you are done creating significant content or documents
            "greeting-responder": use this worker when to respond to user greetings with a friendly joke
            "research-analyst": use this worker to conduct thorough research on complex topics
            </example_worker_description>
            
            <example>
            user: "Please write a function that checks if a number is prime"
            assistant: Sure let me write a function that checks if a number is prime
            assistant: First let me use the Write tool to write a function that checks if a number is prime
            assistant: I'm going to use the Write tool to write the following code:
            <code>
            function isPrime(n) {{
              if (n <= 1) return false
              for (let i = 2; i * i <= n; i++) {{
                if (n % i === 0) return false
              }}
              return true
            }}
            </code>
            <commentary>
            Since significant content was created and the task is completed, now use the content-reviewer worker to review the work
            </commentary>
            assistant: Now let me use the content-reviewer worker to review the code
            assistant: Uses the Worker tool to launch with the content-reviewer worker
            </example>
            
            <example>
            user: "Can you help me research the environmental impact of different renewable energy sources and create a comprehensive report?"
            <commentary>
            This is a complex research task that would benefit from using the research-analyst worker to conduct thorough analysis
            </commentary>
            assistant: I'll help you research the environmental impact of renewable energy sources. Let me use the research-analyst worker to conduct comprehensive research on this topic.
            assistant: Uses the Worker tool to launch with the research-analyst worker, providing detailed instructions about what research to conduct and what format the report should take
            </example>
            
             
            """;

    private final List<ToolCallback> tools;
    private final String systemPrompt;
    private final Map<String, ReactAgent> workers;
    private final boolean includeGeneralPurpose;

    private WorkerInterceptor(Builder builder) {
        this.systemPrompt = builder.systemPrompt != null ? builder.systemPrompt : DEFAULT_SYSTEM_PROMPT;
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
                    .append(DEFAULT_GENERAL_PURPOSE_DESCRIPTION)
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

        return WORKER_TOOL_DESCRIPTION.replace("{available_workers}", workerDescriptions.toString());
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
                        .description(DEFAULT_GENERAL_PURPOSE_DESCRIPTION)
                        .systemPrompt(DEFAULT_WORKER_PROMPT)
                        .model(defaultModel)
                        .tools(defaultTools)
                        .build();
                this.workers.put("worker", createWorkerFromSpec(generalPurposeSpec));
            }
            return new WorkerInterceptor(this);
        }
    }
}