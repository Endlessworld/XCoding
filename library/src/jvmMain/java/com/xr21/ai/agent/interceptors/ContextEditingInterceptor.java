/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.xr21.ai.agent.interceptors;

import com.alibaba.cloud.ai.graph.agent.hook.TokenCounter;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import com.alibaba.cloud.ai.graph.serializer.AgentInstructionMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.*;

import static org.springframework.beans.BeanUtils.copyProperties;

/**
 *
 * @author Endless
 */
public class ContextEditingInterceptor extends ModelInterceptor {
    private static final Logger log = LoggerFactory.getLogger(ContextEditingInterceptor.class);
    private final int trigger;
    private final int clearAtLeast;
    private final int keep;
    private final boolean clearToolInputs;
    private final Set<String> excludeTools;
    private final String placeholder;
    private final TokenCounter tokenCounter;

    private ContextEditingInterceptor(Builder builder) {
        this.trigger = builder.trigger;
        this.clearAtLeast = builder.clearAtLeast;
        this.keep = builder.keep;
        this.clearToolInputs = builder.clearToolInputs;
        this.excludeTools = builder.excludeTools != null ? new HashSet<>(builder.excludeTools) : new HashSet();
        this.placeholder = builder.placeholder;
        this.tokenCounter = builder.tokenCounter;
    }

    public static Builder builder() {
        return new Builder();
    }


    /**
     * 合并连续的UserMessage
     *
     * @param messages 原始消息列表
     * @return 合并后的消息列表
     */
    public static List<Message> mergeConsecutiveUserMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return messages;
        }

        List<Message> result = new ArrayList<>();
        StringBuilder accumulatedContent = new StringBuilder();
        Message lastUserMessage = null;

        for (Message message : messages) {
            if (message instanceof UserMessage) {
                // 如果前一个消息也是UserMessage，累加内容
                if (lastUserMessage != null) {
                    // 可以在这里添加分隔符，比如换行
                    if (accumulatedContent.length() > 0) {
                        accumulatedContent.append("\n");
                    }
                    accumulatedContent.append(((UserMessage) message).getText());
                } else {
                    // 开始新的UserMessage序列
                    lastUserMessage = message;
                    accumulatedContent.setLength(0);
                    accumulatedContent.append(((UserMessage) message).getText());
                }
            } else {
                // 非UserMessage类型
                // 先将累积的UserMessage内容添加到结果
                if (lastUserMessage != null) {
                    // 创建合并后的UserMessage
                    UserMessage mergedMessage = new UserMessage(accumulatedContent.toString());
                    // 如果原消息有其他属性，可以复制过来
                    copyProperties(lastUserMessage, mergedMessage);
                    result.add(mergedMessage);

                    // 重置状态
                    lastUserMessage = null;
                    accumulatedContent.setLength(0);
                }

                // 添加当前非UserMessage消息
                result.add(message);
            }
        }

        // 处理列表末尾可能存在的UserMessage
        if (lastUserMessage != null) {
            UserMessage mergedMessage = new UserMessage(accumulatedContent.toString());
            copyProperties(lastUserMessage, mergedMessage);
            result.add(mergedMessage);
        }

        return result;
    }

    public boolean isValidJson(String str) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.readTree(str);
            return true;
        } catch (Exception e) {
            return false;
        }
    }


    private List<Message> patchDanglingToolCalls(List<Message> messages) {
        List<Message> patchedMessages = new ArrayList<>();
        boolean hasPatches = false;
        Set<String> existingToolResponseIds = new HashSet<>();

        for (Message msg : messages) {
            if (msg instanceof ToolResponseMessage toolResponseMsg) {
                for (ToolResponseMessage.ToolResponse response : toolResponseMsg.getResponses()) {
                    existingToolResponseIds.add(response.id());
                }
            }
        }
        for (int i = 0; i < messages.size(); ++i) {
            Message msg = messages.get(i);
            if (msg instanceof AssistantMessage assistantMsg) {
                List<AssistantMessage.ToolCall> toolCalls = assistantMsg.getToolCalls();
                List<AssistantMessage.ToolCall> tools = new ArrayList<>();

                if (!toolCalls.isEmpty()) {
                    for (AssistantMessage.ToolCall toolCall : toolCalls) {
                        if (!isValidJson(toolCall.arguments())) {
                            hasPatches = true;
                            try {
                                tools.add(new AssistantMessage.ToolCall(toolCall.id(), toolCall.type(), toolCall.name(), JsonMapper.builder()
                                        .build()
                                        .writeValueAsString(Map.of())));
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
                        } else {
                            tools.add(toolCall);
                        }
                    }
                }
                if (hasPatches) {
                    msg = AssistantMessage.builder()
                            .toolCalls(tools)
                            .content(assistantMsg.getText() != null ? assistantMsg.getText() : "")
                            .properties(assistantMsg.getMetadata())
                            .media(assistantMsg.getMedia())
                            .build();
                }
            }
            patchedMessages.add(msg);
            if (msg instanceof AssistantMessage assistantMsg) {
                List<AssistantMessage.ToolCall> toolCalls = assistantMsg.getToolCalls();
                if (!toolCalls.isEmpty()) {
                    List<ToolResponseMessage.ToolResponse> missingResponses = new ArrayList<>();
                    for (AssistantMessage.ToolCall toolCall : toolCalls) {
                        String toolCallId = toolCall.id();
                        boolean hasResponse = existingToolResponseIds.contains(toolCallId);
                        if (!hasResponse) {
                            String cancellationMsg = String.format("Tool call %s with id %s was cancelled - another message came in before it could be completed.", toolCall.name(), toolCallId);
                            missingResponses.add(new ToolResponseMessage.ToolResponse(toolCallId, toolCall.name(), cancellationMsg));
                            log.info("Patching dangling tool call: {} (id: {})", toolCall.name(), toolCallId);
                        }
                    }
                    if (!missingResponses.isEmpty()) {
                        Map<String, Object> metadata = new HashMap();
                        metadata.put("patched", true);
                        patchedMessages.add(ToolResponseMessage.builder()
                                .responses(missingResponses)
                                .metadata(metadata)
                                .build());
                        hasPatches = true;
                    }
                }
            }
        }

        return hasPatches ? patchedMessages : messages;
    }

    private Message clearMessageContent(Message msg) {
        if (msg instanceof ToolResponseMessage toolMsg) {
            List<ToolResponseMessage.ToolResponse> cleared = toolMsg.getResponses()
                    .stream()
                    .map(r -> new ToolResponseMessage.ToolResponse(r.id(), r.name(), this.placeholder))
                    .toList();
            return ToolResponseMessage.builder().responses(cleared).metadata(toolMsg.getMetadata()).build();
        }

        if (msg instanceof AssistantMessage assistantMsg && clearToolInputs) {
            List<AssistantMessage.ToolCall> clearedCalls = assistantMsg.getToolCalls()
                    .stream()
                    .map(tc -> new AssistantMessage.ToolCall(tc.id(), tc.type(), tc.name(), this.placeholder))
                    .toList();
            return AssistantMessage.builder()
                    .content(assistantMsg.getText())
                    .properties(assistantMsg.getMetadata())
                    .toolCalls(clearedCalls)
                    .build();
        }
        return msg;
    }

    private List<ClearableCandidate> findClearableCandidates(List<Message> messages) {
        List<ClearableCandidate> candidates = new ArrayList<>();
        // 倒序遍历以保留最近的 'keep' 个工具调用
        int toolMessageCount = 0;
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            boolean isToolResp = msg instanceof ToolResponseMessage;
            boolean isAssistantWithTools = msg instanceof AssistantMessage am && !am.getToolCalls().isEmpty();

            if (isToolResp || isAssistantWithTools) {
                toolMessageCount++;
                // 只有超出 'keep' 范围的旧消息才考虑清理
                if (toolMessageCount > this.keep && !isExcluded(msg)) {
                    candidates.add(new ClearableCandidate(i, tokenCounter.countTokens(List.of(msg))));
                }
            }
        }
        return candidates;
    }

    @Override
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
        List<Message> messages = new ArrayList<>();
        messages.addAll(request.getMessages().stream().limit(5).toList());
        messages.addAll(request.getMessages()
                .stream()
                .skip(5)
                .filter(e -> !(e instanceof AgentInstructionMessage))
                .toList());
        messages = mergeConsecutiveUserMessages(messages);
        messages = patchDanglingToolCalls(messages);
        int currentTokens = this.tokenCounter.countTokens(messages);
        // 1. 只有超过trigger阈值才处理
        if (currentTokens <= this.trigger) {
            log.info("Token count {} within limit (trigger: {})", currentTokens, this.trigger);
            return handler.call(request);
        }
        log.info("🚀 Token count {} exceeds trigger {}, starting context optimization", currentTokens, this.trigger);

        // 2. 找出所有可以被“物理删除”或“内容清空”的候选消息
        List<ClearableCandidate> candidates = this.findClearableCandidates(messages);
        if (candidates.isEmpty()) {
            return handler.call(request);
        }
        // 3. 计算清理目标：降到 trigger 的 80% 左右，确保有足够的 buffer (+20% 以内) 为了防止刚清理完下一轮又触发，我们至少清理到 trigger 的 90% 或减去 clearAtLeast
        int targetTokens = (int) (this.trigger * 0.8);
        int requiredReduction = currentTokens - targetTokens;
        int finalMinReduction = Math.max(requiredReduction, this.clearAtLeast);
        log.debug("上下文优化: 预计清理tokens {}", finalMinReduction);
        // 4. 贪心排序：优先清理 Token 占用最大的消息（优化：启用贪心算法）
        candidates.sort(Comparator.comparingInt((ClearableCandidate c) -> c.estimatedTokens).reversed());
        Set<Integer> indicesToClear = new HashSet<>();
        int projectedSavings = 0;

        for (ClearableCandidate candidate : candidates) {
            indicesToClear.add(candidate.index);
            projectedSavings += candidate.estimatedTokens;
            // 停止条件：已清理量达到目标，且剩余量已低于触发阈值
            if (projectedSavings >= finalMinReduction && (currentTokens - projectedSavings) <= this.trigger) {
                break;
            }
        }
        if (projectedSavings == 0) {
            return handler.call(request);
        }
        List<Message> updatedMessages = new ArrayList<>(messages.size());
        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);
            if (indicesToClear.contains(i)) {
                updatedMessages.add(clearMessageContent(msg));
            } else {
                updatedMessages.add(msg);
            }
        }
        var clearedTokens = this.tokenCounter.countTokens(updatedMessages);
        int savings = currentTokens - clearedTokens;
        double savingsPercent = (double) savings / currentTokens * 100;
        log.info("✅ 上下文优化完成: 原始={} | 清理={} ({}%) | 剩余={} | 目标={}",
                currentTokens, savings, savingsPercent, clearedTokens, this.trigger);
        return handler.call(ModelRequest.builder(request).messages(updatedMessages).build());
    }

    private boolean isExcluded(Message msg) {
        if (msg instanceof ToolResponseMessage trm) {
            return trm.getResponses()
                    .stream()
                    .anyMatch(r -> excludeTools.contains(r.name()) || placeholder.equals(r.responseData()));
        }
        if (msg instanceof AssistantMessage am) {
            return am.getToolCalls()
                    .stream()
                    .anyMatch(tc -> excludeTools.contains(tc.name()) || placeholder.equals(tc.arguments()));
        }
        return false;
    }

    @Override
    public String getName() {
        return "contextEditingInterceptor";
    }

    private record ClearableCandidate(int index, int estimatedTokens) {
    }

    // Builder class remains similar but with optimized defaults...
    public static class Builder {
        private int trigger = 100000;
        private int clearAtLeast = 10000; // 默认至少清理1万token，避免频繁触发
        private int keep = 3;
        private boolean clearToolInputs = true; // 默认开启，因为Tool Input往往很大
        private Set<String> excludeTools = Collections.emptySet();
        private String placeholder = "."; // 使用更短的占位符节省token
        private TokenCounter tokenCounter = TokenCounter.approximateMsgCounter();

        public Builder trigger(int t) {
            this.trigger = t;
            return this;
        }

        public Builder clearAtLeast(int c) {
            this.clearAtLeast = c;
            return this;
        }

        public Builder keep(int k) {
            this.keep = k;
            return this;
        }

        public Builder clearToolInputs(boolean c) {
            this.clearToolInputs = c;
            return this;
        }

        public Builder excludeTools(String... names) {
            this.excludeTools = Set.of(names);
            return this;
        }

        public Builder placeholder(String p) {
            this.placeholder = p;
            return this;
        }

        public Builder tokenCounter(TokenCounter tc) {
            this.tokenCounter = tc;
            return this;
        }

        public ContextEditingInterceptor build() {
            return new ContextEditingInterceptor(this);
        }
    }
}