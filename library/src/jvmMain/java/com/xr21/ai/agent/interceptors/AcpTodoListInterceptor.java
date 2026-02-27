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

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import com.xr21.ai.agent.tools.AcpWriteTodosTool;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.tool.ToolCallback;

import java.util.Collections;
import java.util.List;

import static com.xr21.ai.agent.tools.AcpWriteTodosTool.DEFAULT_TOOL_DESCRIPTION;

/**
 * ACP-compatible Model interceptor that provides todo list management capabilities to agents.
 *
 * This interceptor enhances the system prompt to guide agents on using todo lists
 * for complex multi-step operations using ACP protocol's Plan and PlanEntry structures.
 *
 * The interceptor automatically injects system prompts that guide the agent on when
 * and how to use the todo functionality effectively with ACP protocol.
 */
public class AcpTodoListInterceptor extends ModelInterceptor {

    private static final String DEFAULT_SYSTEM_PROMPT = """
            ## `write_todos` (ACP Plan Support)
            
            You have access to the `write_todos` tool to help you manage and plan complex objectives using ACP protocol's Plan system.
            Use this tool for complex objectives to ensure that you are tracking each necessary step and giving the user visibility into your progress.
            
            This tool is very helpful for planning complex objectives, and for breaking down these larger complex objectives into smaller steps.
            
            ## ACP Plan Structure
            When using this tool, you will be working with ACP Plan entries that have:
            1. **Status**: pending, in_progress, completed (using ACP PlanEntryStatus)
            2. **Priority**: high, medium, low (using ACP PlanEntryPriority)
            3. **Content**: Clear description of the task
            
            ## Important Usage Notes
            - The `write_todos` tool should never be called multiple times in parallel.
            - Don't be afraid to revise the Plan as you go. New information may reveal new tasks that need to be done, or old tasks that are irrelevant.
            - Mark tasks as in_progress BEFORE beginning work on them.
            - Mark tasks as completed IMMEDIATELY after finishing them (don't batch completions).
            - For simple objectives that only require a few steps, it is better to just complete the objective directly and NOT use this tool.
            - Writing todos takes time and tokens, use it when it is helpful for managing complex many-step problems! But not for simple few-step requests.
            
            ## ACP Protocol Integration
            When you update the todo list, it will be sent as an ACP Plan update to the client,
            allowing the user to see your progress in real-time through the ACP protocol.
            """;

    private final List<ToolCallback> tools;
    private final String systemPrompt;
    private final String toolDescription;

    private AcpTodoListInterceptor(Builder builder) {
        // Create the write_todos tool with the custom description
        this.tools = Collections.singletonList(
                AcpWriteTodosTool.builder()
                        .withName("write_todos")
                        .withDescription(builder.toolDescription)
                        .build()
        );
        this.systemPrompt = builder.systemPrompt;
        this.toolDescription = builder.toolDescription;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public List<ToolCallback> getTools() {
        return tools;
    }

    @Override
    public String getName() {
        return "AcpTodoList";
    }

    @Override
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
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
        private String systemPrompt = DEFAULT_SYSTEM_PROMPT;
        private String toolDescription = DEFAULT_TOOL_DESCRIPTION;

        /**
         * Set a custom system prompt for guiding todo usage.
         */
        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        /**
         * Set a custom tool description for the write_todos tool.
         */
        public Builder toolDescription(String toolDescription) {
            this.toolDescription = toolDescription;
            return this;
        }

        public AcpTodoListInterceptor build() {
            return new AcpTodoListInterceptor(this);
        }
    }
}