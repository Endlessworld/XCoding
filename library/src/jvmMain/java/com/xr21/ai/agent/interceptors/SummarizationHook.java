/*
 * Copyright 2024-2026 the original author or authors.
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

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.JumpTo;
import com.alibaba.cloud.ai.graph.agent.hook.TokenCounter;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import com.alibaba.cloud.ai.graph.agent.hook.messages.UpdatePolicy;
import com.xr21.ai.agent.utils.AcpProgressUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatModel;

import java.util.*;

/**
 * Hook that summarizes conversation history when token limits are approached.
 * <p>
 * This hook monitors message token counts and automatically summarizes older
 * messages when a threshold is reached, preserving the first user message and
 * recent messages to maintain context continuity.
 * <p>
 * Example:
 * SummarizationHook summarizer = SummarizationHook.builder()
 * .model(chatModel)
 * .maxTokensBeforeSummary(4000)
 * .messagesToKeep(20)
 * .keepFirstUserMessage(true)  // Default: true
 * .build();
 */
@HookPositions({HookPosition.BEFORE_MODEL})
public class SummarizationHook extends MessagesModelHook {

    private static final Logger log = LoggerFactory.getLogger(SummarizationHook.class);

    private static final String DEFAULT_SUMMARY_PROMPT = """
            <role>
           上下文提取助手
            </role>
            <primary_objective>
            你在这项任务中的唯一目标是从下面的对话历史中提取最高质量/最相关的上下文。
            </primary_objective>
            <instructions>
            以下对话历史将被你在此步骤中提取的上下文所取代。
            从对话历史中提取并记录所有最重要的上下文。
            包含关键决策，路径，结论等等一切对于 后续对话对你有用的信息
            只用提取出来的上下文回复。请勿包含任何额外信息。
            </instructions>
           
            <messages>
            Messages to summarize:
            %s
            </messages>""";

    private static final String SUMMARY_PREFIX = "## Previous conversation summary:";
    private static final int DEFAULT_MESSAGES_TO_KEEP = 20;
    private static final int SEARCH_RANGE_FOR_TOOL_PAIRS = 5;
    private static final boolean DEFAULT_KEEP_FIRST_USER_MESSAGE = true;

    private final ChatModel model;
    private final Integer maxTokensBeforeSummary;
    private final int messagesToKeep;
    private final TokenCounter tokenCounter;
    private final String summaryPrompt;
    private final String summaryPrefix;
    private final boolean keepFirstUserMessage;

    private SummarizationHook(Builder builder) {
        this.model = builder.model;
        this.maxTokensBeforeSummary = builder.maxTokensBeforeSummary;
        this.messagesToKeep = builder.messagesToKeep;
        this.tokenCounter = builder.tokenCounter;
        this.summaryPrompt = builder.summaryPrompt;
        this.summaryPrefix = builder.summaryPrefix;
        this.keepFirstUserMessage = builder.keepFirstUserMessage;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
        if (maxTokensBeforeSummary == null) {
            return new AgentCommand(previousMessages);
        }
        int totalTokens = tokenCounter.countTokens(previousMessages);

        if (totalTokens < maxTokensBeforeSummary) {
            return new AgentCommand(previousMessages);
        }

        log.info("Token count {} exceeds threshold {}, triggering summarization", totalTokens, maxTokensBeforeSummary);
        AcpProgressUtil.sendProgress(config, "Token count %s exceeds threshold %s, triggering summarization".formatted(totalTokens, maxTokensBeforeSummary));
        int cutoffIndex = findSafeCutoff(previousMessages);

        if (cutoffIndex <= 0) {
            AcpProgressUtil.sendProgress(config, "⚠Cannot find safe cutoff point for summarization");
            log.warn("Cannot find safe cutoff point for summarization");
            return new AgentCommand(previousMessages);
        }

        UserMessage firstUserMessage = null;
        if (keepFirstUserMessage) {
            for (Message msg : previousMessages) {
                if (msg instanceof UserMessage) {
                    firstUserMessage = (UserMessage) msg;
                    break;
                }
            }
        }

        List<Message> toSummarize = new ArrayList<>();
        for (int i = 0; i < cutoffIndex; i++) {
            Message msg = previousMessages.get(i);
            if (msg != firstUserMessage) {
                toSummarize.add(msg);
            }
        }

        String summary = createSummary(toSummarize);
        AcpProgressUtil.sendProgress(config, "Summarized messages: %s".formatted(summary));
        List<Message> recentMessages = new ArrayList<>();
        for (int i = cutoffIndex; i < previousMessages.size(); i++) {
            recentMessages.add(previousMessages.get(i));
        }
        List<Message> newMessages = new ArrayList<>();
        Optional<Message> firstSystemMessage = previousMessages.stream().filter(message -> message instanceof SystemMessage).findFirst();
        SystemMessage summaryMessage = new SystemMessage(firstSystemMessage.map(Message::getText).orElse("\n") + summaryPrefix + "\n" + summary);
        if (firstUserMessage != null) {
            newMessages.add(firstUserMessage);
        }
        newMessages.add(summaryMessage);
        newMessages.addAll(recentMessages);

        if (firstUserMessage != null) {
            log.info("Summarized {} messages, keeping {} recent messages (First UserMessage preserved)", toSummarize.size(), recentMessages.size());
            AcpProgressUtil.sendProgress(config, "Summarized %s messages, keeping %s recent messages (First UserMessage preserved)".formatted(toSummarize.size(), recentMessages.size()));
        } else {
            log.info("Summarized {} messages, keeping {} recent messages", toSummarize.size(), recentMessages.size());
            AcpProgressUtil.sendProgress(config, "Summarized %s messages, keeping %s recent messages".formatted(toSummarize.size(), recentMessages.size()));
        }
        return new AgentCommand(newMessages, UpdatePolicy.REPLACE);
    }

    /**
     * 找到保持 AI/工具消息配对的安全截止点。
     * <p>
     * 返回可以安全切断消息且不分离的索引
     * 相关的AI和工具信息。如果找不到安全截止点，则返回0。
     */
    private int findSafeCutoff(List<Message> messages) {
        if (messages.size() <= messagesToKeep) {
            return 0;
        }

        int targetCutoff = messages.size() - messagesToKeep;

        // Search backwards from targetCutoff to find a safe cutoff point
        for (int i = targetCutoff; i >= 0; i--) {
            if (isSafeCutoffPoint(messages, i)) {
                return i;
            }
        }

        return 0;
    }

    /**
     * Check if cutting at index would separate AI/Tool message pairs.
     */
    private boolean isSafeCutoffPoint(List<Message> messages, int cutoffIndex) {
        if (cutoffIndex >= messages.size()) {
            return true;
        }

        int searchStart = Math.max(0, cutoffIndex - SEARCH_RANGE_FOR_TOOL_PAIRS);
        int searchEnd = Math.min(messages.size(), cutoffIndex + SEARCH_RANGE_FOR_TOOL_PAIRS);

        for (int i = searchStart; i < searchEnd; i++) {
            if (!hasToolCalls(messages.get(i))) {
                continue;
            }

            AssistantMessage aiMessage = (AssistantMessage) messages.get(i);
            Set<String> toolCallIds = extractToolCallIds(aiMessage);
            if (cutoffSeparatesToolPair(messages, i, cutoffIndex, toolCallIds)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Check if message is an AI message with tool calls.
     */
    private boolean hasToolCalls(Message message) {
        return message instanceof AssistantMessage assistantMessage && !assistantMessage.getToolCalls().isEmpty();
    }

    /**
     * Extract tool call IDs from an AI message.
     */
    private Set<String> extractToolCallIds(AssistantMessage aiMessage) {
        Set<String> toolCallIds = new HashSet<>();
        for (AssistantMessage.ToolCall toolCall : aiMessage.getToolCalls()) {
            String callId = toolCall.id();
            toolCallIds.add(callId);
        }
        return toolCallIds;
    }

    /**
     * Check if cutoff separates an AI message from its corresponding tool messages.
     */
    private boolean cutoffSeparatesToolPair(List<Message> messages, int aiMessageIndex, int cutoffIndex, Set<String> toolCallIds) {
        for (int j = aiMessageIndex + 1; j < messages.size(); j++) {
            Message message = messages.get(j);
            if (message instanceof ToolResponseMessage toolResponseMessage) {
                // Check if any response in this ToolResponseMessage matches our tool call IDs
                for (ToolResponseMessage.ToolResponse response : toolResponseMessage.getResponses()) {
                    if (toolCallIds.contains(response.id())) {
                        boolean aiBeforeCutoff = aiMessageIndex < cutoffIndex;
                        boolean toolBeforeCutoff = j < cutoffIndex;
                        if (aiBeforeCutoff != toolBeforeCutoff) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private String createSummary(List<Message> messages) {
        if (messages.isEmpty()) {
            return "No previous conversation.";
        }
        StringBuilder messageText = new StringBuilder();
        for (Message msg : messages) {
            String role = getRoleName(msg);
            messageText.append("\n-----------\n").append(role).append(": ").append(msg.getText()).append("\n");
            if (msg instanceof ToolResponseMessage tool) {
                for (ToolResponseMessage.ToolResponse response : tool.getResponses()) {
                    messageText.append("tool_call: ").append("\n id: ").append(response.id()).append("\n").append(response.name())
                            .append("responseData :").append(response.responseData()).append("\n");
                }

            }
        }
        try {
            String prompt = String.format(summaryPrompt, messageText.toString().replaceAll("\\{", "").replaceAll("}", ""));
            log.debug("create summary prompt ：{}", prompt);
            log.debug("create summary model default options ：{}", model.getDefaultOptions());
            var response = ChatClient.builder(model).defaultSystem(DEFAULT_SUMMARY_PROMPT).build().prompt().user(prompt).call().content();
            System.out.println("response "+response);
            log.debug("Summary generation success ：{}", response);
            return response;
        } catch (Exception e) {
            log.error("Failed to create summary ", e);
            return "Summary generation failed: " + e.getMessage();
        }
    }

    private String getRoleName(Message message) {
        if (message instanceof UserMessage) {
            return "Human";
        } else if (message instanceof AssistantMessage) {
            return "Assistant";
        } else if (message instanceof SystemMessage) {
            return "System";
        } else if (message instanceof ToolResponseMessage) {
            return "Tool";
        } else {
            return "Unknown";
        }
    }

    @Override
    public String getName() {
        return "Summarization";
    }

    @Override
    public List<JumpTo> canJumpTo() {
        return List.of();
    }

    public static class Builder {
        private ChatModel model;
        private Integer maxTokensBeforeSummary;
        private int messagesToKeep = DEFAULT_MESSAGES_TO_KEEP;
        private TokenCounter tokenCounter = TokenCounter.approximateMsgCounter();
        private String summaryPrompt = DEFAULT_SUMMARY_PROMPT;
        private String summaryPrefix = SUMMARY_PREFIX;
        private boolean keepFirstUserMessage = DEFAULT_KEEP_FIRST_USER_MESSAGE;

        public Builder model(ChatModel model) {
            this.model = model;
            return this;
        }

        public Builder maxTokensBeforeSummary(Integer maxTokens) {
            this.maxTokensBeforeSummary = maxTokens;
            return this;
        }

        public Builder messagesToKeep(int count) {
            this.messagesToKeep = count;
            return this;
        }

        public Builder summaryPrompt(String prompt) {
            this.summaryPrompt = prompt;
            return this;
        }

        public Builder summaryPrefix(String prefix) {
            this.summaryPrefix = prefix;
            return this;
        }

        public Builder tokenCounter(TokenCounter counter) {
            this.tokenCounter = counter;
            return this;
        }

        public Builder keepFirstUserMessage(boolean keep) {
            this.keepFirstUserMessage = keep;
            return this;
        }

        public SummarizationHook build() {
            if (model == null) {
                throw new IllegalArgumentException("model must be specified");
            }
            return new SummarizationHook(this);
        }
    }
}
