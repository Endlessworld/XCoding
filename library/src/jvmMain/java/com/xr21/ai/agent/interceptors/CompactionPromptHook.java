package com.xr21.ai.agent.interceptors;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.TokenCounter;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import com.xr21.ai.agent.utils.AcpNotifyHelper;
import com.xr21.ai.agent.utils.Prompts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 会话压缩提示 Hook：在上下文 token 达到指定阈值时，向消息列表注入一条引导消息，
 * 促使模型主动调用 {@code compact_conversation} 工具完成压缩。
 * <p>
 * 参考 {@link SummarizationHook} 的阈值判定方式（优先使用 AgiAgent 上报的
 * {@code lastInputTokens} 实时用量），但本 Hook 不做自动摘要，而是把压缩决策留给模型：
 * 仅追加一条带唯一标记的 UserMessage 指令；模型完成压缩后，该指令会随旧消息一起被
 * {@code compact_conversation} 截断，不会残留。为避免压缩后仍残留指令造成重复触发，
 * 每次注入前都会先清除历史遗留的同类指令。
 * <p>
 * 示例：
 * CompactionPromptHook hook = CompactionPromptHook.builder()
 *         .maxTokensBeforePrompt(128 * 1024)
 *         .prompt("上下文已接近上限，请调用 compact_conversation 工具...")
 *         .build();
 */
@HookPositions({HookPosition.BEFORE_MODEL})
public class CompactionPromptHook extends MessagesModelHook {

    private static final Logger log = LoggerFactory.getLogger(CompactionPromptHook.class);

    private final Integer maxTokensBeforePrompt;
    private final TokenCounter tokenCounter;
    private final String prompt;

    private CompactionPromptHook(Builder builder) {
        this.maxTokensBeforePrompt = builder.maxTokensBeforePrompt;
        this.tokenCounter = builder.tokenCounter;
        this.prompt = builder.prompt;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
        if (maxTokensBeforePrompt == null) {
            return new AgentCommand(previousMessages);
        }
        // 清除上次注入的压缩提示，避免压缩后残留导致反复触发
        List<Message> cleaned = removeInjectedPrompt(previousMessages);
        if (cleaned == null) {
            cleaned = previousMessages;
        }
        int totalTokens = resolveTotalTokens(cleaned, config);
        if (totalTokens < maxTokensBeforePrompt) {
            return new AgentCommand(cleaned);
        }

        log.info("Token count {} exceeds threshold {}, injecting compaction prompt", totalTokens, maxTokensBeforePrompt);
        AcpNotifyHelper.sendProgress(config, "Token count %s exceeds threshold %s, injecting compaction prompt"
                .formatted(totalTokens, maxTokensBeforePrompt));
        List<Message> newMessages = new ArrayList<>(cleaned);
        newMessages.add(new UserMessage(prompt));
        return new AgentCommand(newMessages, com.alibaba.cloud.ai.graph.agent.hook.messages.UpdatePolicy.REPLACE);
    }

    /**
     * 解析当前输入 token 数：优先使用上一次模型调用上报的实时值，否则回退到近似计数器。
     */
    private int resolveTotalTokens(List<Message> messages, RunnableConfig config) {
        Object live = config.context().get("lastInputTokens");
        if (live instanceof Number n && n.longValue() > 0) {
            return n.intValue();
        }
        return tokenCounter.countTokens(messages);
    }

    /**
     * 移除历史注入的压缩提示消息（按标记匹配，保留其它消息）。
     * 无残留时返回 null 表示无需调整。
     */
    private List<Message> removeInjectedPrompt(List<Message> messages) {
        boolean found = false;
        List<Message> result = new ArrayList<>(messages.size());
        for (Message message : messages) {
            if (message instanceof UserMessage userMessage
                    && userMessage.getText() != null
                    && userMessage.getText().contains(Prompts.COMPACTION_PROMPT_MARKER)) {
                found = true;
                continue;
            }
            result.add(message);
        }
        return found ? result : null;
    }

    @Override
    public String getName() {
        return "CompactionPrompt";
    }

    public static class Builder {
        private Integer maxTokensBeforePrompt;
        private TokenCounter tokenCounter = TokenCounter.approximateMsgCounter();
        private String prompt = Prompts.COMPACTION_PROMPT.formatted(Prompts.COMPACTION_PROMPT_MARKER);

        public Builder maxTokensBeforePrompt(Integer maxTokens) {
            this.maxTokensBeforePrompt = maxTokens;
            return this;
        }

        public Builder tokenCounter(TokenCounter counter) {
            this.tokenCounter = counter;
            return this;
        }

        public Builder prompt(String prompt) {
            this.prompt = prompt;
            return this;
        }

        public CompactionPromptHook build() {
            return new CompactionPromptHook(this);
        }
    }
}
