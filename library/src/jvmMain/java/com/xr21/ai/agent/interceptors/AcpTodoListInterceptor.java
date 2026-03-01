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
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.util.List;

public class AcpTodoListInterceptor extends ModelInterceptor {

    private static final String DEFAULT_SYSTEM_PROMPT = """
            ## `write_todos`
            
            You have access to the `write_todos` tool to help you manage and plan complex objectives.
            Use this tool for complex objectives to ensure that you are tracking each necessary step and giving the user visibility into your progress.
            This tool is very helpful for planning complex objectives, and for breaking down these larger complex objectives into smaller steps.
            
            It is critical that you mark todos as completed as soon as you are done with a step. Do not batch up multiple steps before marking them as completed.
            For simple objectives that only require a few steps, it is better to just complete the objective directly and NOT use this tool.
            Writing todos takes time and tokens, use it when it is helpful for managing complex many-step problems! But not for simple few-step requests.
            
            ## Important To-Do List Usage Notes to Remember
            - The `write_todos` tool should never be called multiple times in parallel.
            - Don't be afraid to revise the To-Do list as you go. New information may reveal new tasks that need to be done, or old tasks that are irrelevant.
            """;

    private final List<ToolCallback> tools;
    private final String systemPrompt;

    private AcpTodoListInterceptor(Builder builder) {
        var methodToolCallbackProvider = MethodToolCallbackProvider.builder()
                .toolObjects(new AcpWriteTodosTool())
                .build();
        this.tools = List.of(methodToolCallbackProvider.getToolCallbacks());
        this.systemPrompt = builder.systemPrompt;
    }

    public static Builder builder() {
        return new Builder();
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

    @Override
    public List<ToolCallback> getTools() {
        return tools;
    }

    @Override
    public String getName() {
        return "write_todos";
    }

    public static class Builder {
        private String systemPrompt = DEFAULT_SYSTEM_PROMPT;

        /**
         * Set a custom system prompt for guiding todo usage.
         */
        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public AcpTodoListInterceptor build() {
            return new AcpTodoListInterceptor(this);
        }
    }
}
