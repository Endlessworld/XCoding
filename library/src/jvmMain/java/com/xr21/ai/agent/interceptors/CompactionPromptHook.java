package com.xr21.ai.agent.interceptors;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.TokenCounter;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import com.xr21.ai.agent.utils.AcpNotifyHelper;
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

    private static final String DEFAULT_MARKER = "[__COMPACTION_PROMPT__]";
    private static final String DEFAULT_PROMPT = """
            %s
            当前对话的上下文 token 数已达到指定阈值，继续累积可能导致模型上下文溢出或成本飙升。
            请立即调用 compact_conversation 工具压缩会话：
            - summary：
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
              供压缩后继续工作使用。尽量精简
            - keep_last：保留最近 0 条消息即可。
            压缩完成后请基于 summary 继续完成当前任务。
            """.formatted(DEFAULT_MARKER);

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
                    && userMessage.getText().contains(DEFAULT_MARKER)) {
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
        private String prompt = DEFAULT_PROMPT;

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
