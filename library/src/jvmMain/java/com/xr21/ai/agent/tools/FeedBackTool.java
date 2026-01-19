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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.Scanner;
import java.util.function.BiFunction;

public class FeedBackTool implements BiFunction<FeedBackTool.AskRequest, ToolContext, String> {

    private static final Scanner SCANNER = new Scanner(System.in);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static ToolCallback build(String name, FeedBackTool ticketTool) {
        return FunctionToolCallback.builder(name, ticketTool)
                .description("FeedBackTool 本工具用于收集用户信息，支持多轮对话和用户确认。可以用于获取用户输入、确认操作或收集反馈。")
                .inputType(AskRequest.class)
                .build();
    }

    /**
     * 创建需要用户确认的请求
     */
    public static AskRequest createConfirmationRequest(String prompt) {
        AskRequest request = new AskRequest();
        request.setPrompt(prompt);
        request.setRequireConfirmation(true);
        return request;
    }

    /**
     * 创建需要用户输入的请求
     */
    public static AskRequest createInputRequest(String prompt) {
        AskRequest request = new AskRequest();
        request.setPrompt(prompt);
        request.setRequireInput(true);
        return request;
    }

    /**
     * 创建只显示消息的请求
     */
    public static AskRequest createMessageRequest(String message) {
        AskRequest request = new AskRequest();
        request.setMessage(message);
        return request;
    }

    @SneakyThrows
    @Override
    public String apply(AskRequest request, ToolContext toolContext) {
        System.out.println("\n[系统提示] " + request.getPrompt());

        // 如果需要用户确认
        if (request.isRequireConfirmation()) {
            System.out.print("是否确认执行? (y/n): ");
            String confirmation = SCANNER.nextLine().trim().toLowerCase();
            if (!confirmation.equals("y") && !confirmation.equals("yes")) {
                return "用户取消了操作";
            }
        }

        // 如果需要用户输入
        if (request.isRequireInput()) {
            System.out.print("请输入: ");
            String userInput = SCANNER.nextLine().trim();
            return "用户输入: " + userInput;
        }

        // 如果只是显示信息
        if (request.getMessage() != null && !request.getMessage().isEmpty()) {
            return "已显示消息给用户: " + request.getMessage();
        }

        return "操作完成";
    }

    public static class AskRequest {
        /**
         * 提示信息
         */
        @JsonProperty("prompt")
        private String prompt;

        /**
         * 直接显示的消息
         */
        @JsonProperty("message")
        private String message;

        /**
         * 是否需要用户确认
         */
        @JsonProperty("require_confirmation")
        private boolean requireConfirmation = false;

        /**
         * 是否需要用户输入
         */
        @JsonProperty("require_input")
        private boolean requireInput = false;

        public AskRequest() {
        }

        public String getPrompt() {
            return this.prompt;
        }

        public String getMessage() {
            return this.message;
        }

        public boolean isRequireConfirmation() {
            return this.requireConfirmation;
        }

        public boolean isRequireInput() {
            return this.requireInput;
        }

        @JsonProperty("prompt")
        public void setPrompt(String prompt) {
            this.prompt = prompt;
        }

        @JsonProperty("message")
        public void setMessage(String message) {
            this.message = message;
        }

        @JsonProperty("require_confirmation")
        public void setRequireConfirmation(boolean requireConfirmation) {
            this.requireConfirmation = requireConfirmation;
        }

        @JsonProperty("require_input")
        public void setRequireInput(boolean requireInput) {
            this.requireInput = requireInput;
        }

        public boolean equals(final Object o) {
            if (o == this) return true;
            if (!(o instanceof AskRequest)) return false;
            final AskRequest other = (AskRequest) o;
            if (!other.canEqual((Object) this)) return false;
            final Object this$prompt = this.getPrompt();
            final Object other$prompt = other.getPrompt();
            if (this$prompt == null ? other$prompt != null : !this$prompt.equals(other$prompt)) return false;
            final Object this$message = this.getMessage();
            final Object other$message = other.getMessage();
            if (this$message == null ? other$message != null : !this$message.equals(other$message)) return false;
            if (this.isRequireConfirmation() != other.isRequireConfirmation()) return false;
            if (this.isRequireInput() != other.isRequireInput()) return false;
            return true;
        }

        protected boolean canEqual(final Object other) {
            return other instanceof AskRequest;
        }

        public int hashCode() {
            final int PRIME = 59;
            int result = 1;
            final Object $prompt = this.getPrompt();
            result = result * PRIME + ($prompt == null ? 43 : $prompt.hashCode());
            final Object $message = this.getMessage();
            result = result * PRIME + ($message == null ? 43 : $message.hashCode());
            result = result * PRIME + (this.isRequireConfirmation() ? 79 : 97);
            result = result * PRIME + (this.isRequireInput() ? 79 : 97);
            return result;
        }

        public String toString() {
            return "FeedBackTool.AskRequest(prompt=" + this.getPrompt() + ", message=" + this.getMessage() + ", requireConfirmation=" + this.isRequireConfirmation() + ", requireInput=" + this.isRequireInput() + ")";
        }
    }
}
