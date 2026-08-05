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
import java.util.stream.Collectors;

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
            任务交接文档撰写专家
            </role>
            <scenario>
            当前会话的智能体因上下文窗口达到上限,无法继续保留全部历史记录。
            它需要将任务完整交接给一个全新的、没有任何历史记忆的智能体。
            你的输出将完全取代下方整段对话,成为新智能体看到的唯一信息。
            </scenario>
            <primary_objective>
            产出一份让"零上下文"新智能体能够无缝继续工作的交接摘要,
            使其仅凭这份摘要就能准确知道:用户最初要做什么、当前进展如何、
            还剩什么未完成、下一步应该从哪里继续。
            </primary_objective>
            <input>
            需要被摘要的完整对话历史(即上方各条 user / assistant / tool,不包括system消息)
            </input>
            <instructions>
            请按以下结构组织输出:
            1. 【用户原始目标】一句话概括用户最初提出的核心需求与期望结果。
            2. 【任务背景与关键约束】影响后续决策的上下文:技术栈、业务规则、用户偏好、硬性要求等。
            3. 【关键信息】枚举所有可能涉及的事实条目,确保新智能体不遗漏任何重要信息:
               - 用户与项目:用户身份、角色、偏好、组织/项目/团队名称
               - 路径与资源:文件路径(绝对/相对)、目录结构、资源 URL、API 端点、端口号
               - 代码标识符:类名、方法名、函数签名、变量名、字段名、包名、命名空间、注解
               - 配置项:环境变量、配置键值、开关标志、特性开关、默认参数
               - 标识与编号:ID(用户/订单/任务/工单)、UUID、版本号、commit hash、issue/PR 编号、分支名
               - 数值与参数:关键数值、阈值、限额、坐标、索引、参数取值、单位
               - 时间信息:截止日期、时间戳、超时时长、定时任务周期、时区
               - 错误与异常:错误码、异常类型、HTTP 状态码、失败原因描述
               - 环境与依赖:操作系统、语言/框架/中间件版本、依赖库及版本、外部服务名称
               - 命令与调用:关键命令、CLI 工具、调用接口名、Tool 名称、SQL 语句片段
               - 数据样本:输入输出样例、JSON/CSV/YAML 片段、Schema 定义、表结构
               - 凭证与权限(谨慎):涉及的账号、Token、权限范围(若必须保留请标注保密级别)
               - 其它你认为对新智能体比较重要或有参考价值的信息
            4. 【关键决策与已确定方案】已做出的重要选择(库、API、架构等),以及拒绝的备选方案与原因。
            5. 【执行进度】按时间顺序列出已完成的关键步骤、涉及的文件路径、产生的中间结果或数据。
            6. 【当前状态】会话在被打断时正处在哪一步、进行到何种程度、相关关键变量/文件内容快照。
            7. 【待办与下一步】按优先级明确指出接下来需要做什么,并给出建议的切入点。
            8. 【风险与注意事项】新智能体需要警惕的坑、依赖的外部资源、可能踩到的陷阱。

            写作要求:
            - 以新智能体视角写作,可使用"你需要..."、"接下来请..."等第二人称表达。
            - 保留所有关键标识符:文件路径、函数名、类名、变量名、ID、配置项、URL 等。
            - 对 Tool 调用输入输出中的关键数据要保留摘要(不要保留完整堆栈/日志)。
            - 删除寒暄、重复内容、失败尝试、错误堆栈等无助于推进任务的噪音。
            - 使用结构化、简洁的中文,仅输出交接文档本体,不要附加"以下是摘要"等额外说明。
            </instructions>""";

    private static final String SUMMARY_PREFIX = "## Previous conversation summary:";
    private static final int DEFAULT_MESSAGES_TO_KEEP = 20;
    private static final boolean DEFAULT_KEEP_FIRST_USER_MESSAGE = true;

    private final ChatModel model;
    private final Integer maxTokensBeforeSummary;
    private final int messagesToKeep;
    private final TokenCounter tokenCounter;
    private final String summaryPrompt;
    private final String summaryPrefix;

    private SummarizationHook(Builder builder) {
        this.model = builder.model;
        this.maxTokensBeforeSummary = builder.maxTokensBeforeSummary;
        this.messagesToKeep = builder.messagesToKeep;
        this.tokenCounter = builder.tokenCounter;
        this.summaryPrompt = builder.summaryPrompt;
        this.summaryPrefix = builder.summaryPrefix;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
        if (maxTokensBeforeSummary == null) {
            return new AgentCommand(previousMessages);
        }
        int totalTokens = resolveTotalTokens(previousMessages, config);

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

        // 定位最后一个 SystemMessage：首次为真实系统提示词，压缩后则为上一次注入的旧摘要。
        // 多次压缩时，0..lastSystemIndex 内的内容在压缩后保持不变，构成稳定的缓存前缀。
        int lastSystemIndex = -1;
        for (int i = 0; i < previousMessages.size(); i++) {
            if (previousMessages.get(i) instanceof SystemMessage) {
                lastSystemIndex = i;
            }
        }
        // 固定前缀 = 开头到最后一个 System（含旧摘要），内容不再变化，最省缓存。
        List<Message> fixedPrefix = previousMessages.subList(0, lastSystemIndex + 1);

        // 保留最近消息（不参与摘要）。
        List<Message> recentMessages = new ArrayList<>();
        for (int i = cutoffIndex; i < previousMessages.size(); i++) {
            recentMessages.add(previousMessages.get(i));
        }

        // 增量历史 = 最后一个 System 之后、截止点之前的部分，仅这部分需要浓缩成新摘要。
        // 旧摘要原样保留在前缀中，避免多轮压缩时反复重摘要导致信息逐层丢失。
        // 摘要调用的消息前缀必须从队首复用固定前缀（与主对话逐字节一致 → 命中缓存），
        // 仅增量历史与追加的摘要指令属于新增（详见 createSummary）。
        int summaryEnd = Math.min(cutoffIndex, previousMessages.size());
        List<Message> summaryInput = previousMessages.stream().limit(summaryEnd).collect(Collectors.toList());
        String summary = summaryInput.isEmpty()
                ? "No new conversation."
                : createSummary(summaryInput);
        AcpProgressUtil.sendProgress(config, summary);
        // 缓存前缀稳定性优化：DeepSeek 等提供商按“从消息开头开始的最长公共前缀”命中缓存。
        // 固定前缀从队首一直延伸到最后一个 System（含旧摘要），内容逐字节不变，
        // 每轮压缩后，队首到摘要边界这段最昂贵、最稳定的前缀仍能命中缓存。
        // 重组：固定前缀 + 当前摘要 + 最近消息。
        List<Message> newMessages = new ArrayList<>(fixedPrefix);
        SystemMessage summaryMessage = new SystemMessage(summaryPrefix + "\n" + summary);
        newMessages.add(summaryMessage);
        newMessages.addAll(recentMessages);
        int incrementalCount = summaryEnd - lastSystemIndex - 1;
        log.info("Summarized {} incremental messages, keeping {} recent messages (fixed prefix {} preserved)",
                incrementalCount, recentMessages.size(), fixedPrefix.size());
        AcpProgressUtil.sendProgress(config, "Summarized %s incremental messages, keeping %s recent messages"
                .formatted(incrementalCount, recentMessages.size()));
        return new AgentCommand(newMessages, UpdatePolicy.REPLACE);
    }

    /**
     * Resolves the current input token count, preferring the live value reported by the last
     * model call (written into the RunnableConfig context by AgiAgentSession) and falling back
     * to the approximate counter when no live value is available.
     */
    private int resolveTotalTokens(List<Message> previousMessages, RunnableConfig config) {
        Object live = config.context().get("lastInputTokens");
        if (live instanceof Number n && n.longValue() > 0) {
            return n.intValue();
        }
        return tokenCounter.countTokens(previousMessages);
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
     * Check if cutting at {@code cutoffIndex} would separate any AI message from its
     * corresponding tool responses.
     *
     * <p>Collects every tool-response ID that appears at/after the cutoff, then checks every
     * AI message before the cutoff: if any of its tool calls resolves to a response after the
     * cutoff, the pair would be split and the cutoff is unsafe. This scans the full history
     * (no bounded search window) so long-distance AI/tool pairs are never missed.
     */
    private boolean isSafeCutoffPoint(List<Message> messages, int cutoffIndex) {
        if (cutoffIndex >= messages.size()) {
            return true;
        }

        Set<String> toolResponseIdsAfterCutoff = new HashSet<>();
        for (int i = cutoffIndex; i < messages.size(); i++) {
            Message message = messages.get(i);
            if (message instanceof ToolResponseMessage toolResponseMessage) {
                for (ToolResponseMessage.ToolResponse response : toolResponseMessage.getResponses()) {
                    toolResponseIdsAfterCutoff.add(response.id());
                }
            }
        }

        for (int i = 0; i < cutoffIndex; i++) {
            Message message = messages.get(i);
            if (!(message instanceof AssistantMessage assistantMessage)
                    || assistantMessage.getToolCalls().isEmpty()) {
                continue;
            }
            for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {
                if (toolResponseIdsAfterCutoff.contains(toolCall.id())) {
                    return false;
                }
            }
        }
        return true;
    }

    private String createSummary(List<Message> messages) {
        if (messages.isEmpty()) {
            return "No previous conversation.";
        }
        try {
            // 缓存友好摘要生成：调用方传入的 messages = [固定前缀 + 增量历史]，其中固定前缀
            // 与主对话逐字节一致 → 从开头到增量历史处几乎整体命中缓存），再追加一条固定的
            // 摘要指令 user 消息。只有增量历史与追加的指令属于缓存未命中，从根本上避免
            // “把历史要摘要的消息与摘要提示词拼成一条新消息”导致前缀全失配、摘要调用价格飙升。
            var spec = ChatClient.builder(model).build().prompt();
            spec.messages(messages);
            // 历史已作为消息列表处于上下文中，指令仅作为追加的 user 消息发送。
            spec.user(summaryPrompt);
            log.debug("create summary instruction: {}", summaryPrompt);
            var response = spec.call().content();
            log.debug("Summary generation success: {}", response);
            return response;
        } catch (Exception e) {
            log.error("Failed to create summary", e);
            return "Summary generation failed: " + e.getMessage();
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

        public SummarizationHook build() {
            if (model == null) {
                throw new IllegalArgumentException("model must be specified");
            }
            return new SummarizationHook(this);
        }
    }
}
