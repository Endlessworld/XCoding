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
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.function.BiFunction;

@Slf4j
public class FeedBackTool implements BiFunction<FeedBackTool.AskRequest, ToolContext, Map<String, Object>> {

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
    public Map<String, Object> apply(AskRequest request, ToolContext toolContext) {
        Map<String, Object> result = new HashMap<>();
        log.info("\n[系统提示] {}", request.getPrompt());

        // 如果需要用户确认
        if (request.isRequireConfirmation()) {
            log.info("是否确认执行? (y/n): ");
            String confirmation = SCANNER.nextLine().trim().toLowerCase();
            if (!confirmation.equals("y") && !confirmation.equals("yes")) {
                result.put("message", "用户取消了操作");
                result.put("confirmed", false);
                return result;
            }
            result.put("confirmed", true);
        }

        // 如果需要用户输入
        if (request.isRequireInput()) {
            log.info("请输入: ");
            String userInput = SCANNER.nextLine().trim();
            result.put("userInput", userInput);
            return result;
        }

        // 如果只是显示信息
        if (request.getMessage() != null && !request.getMessage().isEmpty()) {
            result.put("message", "已显示消息给用户: " + request.getMessage());
            return result;
        }

        result.put("message", "操作完成");
        return result;
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

        @JsonProperty("prompt")
        public void setPrompt(String prompt) {
            this.prompt = prompt;
        }

        public String getMessage() {
            return this.message;
        }

        @JsonProperty("message")
        public void setMessage(String message) {
            this.message = message;
        }

        public boolean isRequireConfirmation() {
            return this.requireConfirmation;
        }

        @JsonProperty("require_confirmation")
        public void setRequireConfirmation(boolean requireConfirmation) {
            this.requireConfirmation = requireConfirmation;
        }

        public boolean isRequireInput() {
            return this.requireInput;
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
