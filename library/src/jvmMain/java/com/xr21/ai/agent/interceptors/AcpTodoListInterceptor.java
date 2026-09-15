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
import com.xr21.ai.agent.utils.Prompts;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.util.List;

/**
 *
 * @author Endless
 */
public class AcpTodoListInterceptor extends ModelInterceptor {

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
        private String systemPrompt = Prompts.TODO_LIST_SYSTEM_PROMPT;

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
