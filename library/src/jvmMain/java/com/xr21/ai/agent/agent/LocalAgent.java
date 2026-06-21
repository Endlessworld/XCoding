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
package com.xr21.ai.agent.agent;

import com.agentclientprotocol.model.McpServer;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.extension.file.LocalFilesystemBackend;
import com.alibaba.cloud.ai.graph.agent.extension.interceptor.LargeResultEvictionInterceptor;
import com.alibaba.cloud.ai.graph.agent.hook.Hook;
import com.alibaba.cloud.ai.graph.agent.hook.hip.HumanInTheLoopHook;
import com.alibaba.cloud.ai.graph.agent.hook.hip.ToolConfig;
import com.alibaba.cloud.ai.graph.agent.hook.skills.SkillsAgentHook;
import com.alibaba.cloud.ai.graph.agent.hook.summarization.SummarizationHook;
import com.alibaba.cloud.ai.graph.agent.interceptor.Interceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.modelretry.ModelRetryInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.toolerror.ToolErrorInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.toolretry.ToolRetryInterceptor;
import com.alibaba.cloud.ai.graph.checkpoint.savers.file.FileSystemSaver;
import com.alibaba.cloud.ai.graph.serializer.plain_text.jackson.SpringAIJacksonStateSerializer;
import com.alibaba.cloud.ai.graph.skills.registry.filesystem.FileSystemSkillRegistry;
import com.xr21.ai.agent.acp.SessionConfigOptionsFactory;
import com.xr21.ai.agent.config.AiModels;
import com.xr21.ai.agent.interceptors.AcpTodoListInterceptor;
import com.xr21.ai.agent.interceptors.ContextEditingInterceptor;
import com.xr21.ai.agent.interceptors.FilesystemInterceptor;
import com.xr21.ai.agent.interceptors.WorkerInterceptor;
import com.xr21.ai.agent.tools.ContextCacheTool;
import com.xr21.ai.agent.tools.ShellTools;
import com.xr21.ai.agent.tools.WebTool;
import com.xr21.ai.agent.utils.DefaultTokenCounter;
import com.xr21.ai.agent.utils.Json;
import com.xr21.ai.agent.utils.ToolsUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.StaticToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.lang.NonNull;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * LocalAgent 类负责创建和配置本地文件操作智能体。
 * <p>
 * 该类提供了创建智能体的工厂方法，配置了文件操作工具、拦截器和其他相关组件。
 * 智能体主要用于代码编辑、文件操作和命令执行等任务。
 * </p>
 *
 * <p>主要功能包括：</p>
 * <ul>
 *   <li>创建配置了文件操作工具的智能体</li>
 *   <li>支持 MCP 服务器工具集成</li>
 *   <li>配置上下文编辑拦截器以管理令牌使用</li>
 *   <li>提供错误重试和结果清理机制</li>
 * </ul>
 *
 * @author Endless
 * @version 1.0
 */
@Slf4j
public class LocalAgent {

    /**
     * 默认工作空间根目录
     */
    public static final String DEFAULT_WORKSPACE_ROOT = Path.of(System.getProperty("user.home"), ".agi_working", "workspace", System.currentTimeMillis() + "").toAbsolutePath().toString();
    /**
     * 文件系统保存器的存储目录路径
     */
    private static final Path FILE_SYSTEM_SAVER_FOLDER = Path.of(System.getProperty("user.home"), ".agi_working", "SystemSaver");
    /**
     * 文件系统保存器实例，用于持久化智能体状态
     */
    public static final FileSystemSaver FILE_SYSTEM_SAVER = FileSystemSaver.builder().targetFolder(FILE_SYSTEM_SAVER_FOLDER).stateSerializer(new SpringAIJacksonStateSerializer(OverAllState::new)).build();
    private static final Path FILE_SYSTEM_SKILL_DIR = Path.of(System.getProperty("user.home"), ".agi_working", "skills");
    private static final String SYSTEM_PROMPT_TEMPLATE = """
            你是一个编码智能体 XAgent
            通过文件/内容查找、读取、文件创建、编辑等工具进行项目代码编辑
            The current working directory is：{cwd} 所有文件操作仅限于工作目录之内
            当前系统：{osName}
            当前系统换行符：{lineSeparator}
            当前系统语言:{language}
            您只能执行当前系统平台默认存在的命令
            请使用当前系统语言:{language}回复用户
            - 使用批量编辑 一次修改多处进行高效修改
            - 如果工作目录下存在 AGENTS.md 或 README.md 可以通过它们快速了解当前项目
            <编码指南>
            ## 1.写代码前先思考
                **别假设。不要掩饰困惑。表面权衡。** 
                在实施之前：
                - 明确陈述你的假设。如果不确定，可以问。
                - 如果存在多种解读，就提出来——不要默默选择。
                - 如果存在更简单的方法，请说明。必要时反驳。
                - 如果有什么不清楚的，就停。说出什么让人困惑。问吧。
            ## 2.简洁优先
                **解决问题的最低代码。没有任何推测性内容。**
                - 没有超出要求的特征。
                - 一次性代码不进行抽象。
                - 没有“灵活性”或“可配置性”，除非是被要求的。
                - 不处理不可能的错误处理。
                - 如果你写了200行，可能有50行能解决问题，就重写。
                问问自己：“高级工程师会说这太复杂了吗？”如果是，那就简化。
            ## 3.手术变更
                **只触碰你必须触碰的。只收拾你自己的烂摊子。**
                编辑现有代码时：
                - 不要“改进”相邻的代码、注释或格式。
                - 不要重构没坏掉的东西。
                - 要符合现有风格，即使你会用不同的方式。
                - 如果你发现了无关的死代码，要提及——不要删除。 
                当你的更改产生孤儿时：
                    - 移除你的更改导致未使用的导入/变量/函数。
                    - 除非被要求，不要删除已有的死代码。   
                测试：每一行更改的线条都应直接追踪到用户的请求。
            ## 4.目标驱动执行    
            **定义成功标准。循环直到确认。**
            将任务转化为可验证的目标：
            - “添加验证”→“为无效输入写测试，然后使其通过”
            - “修复漏洞”→“编写一个复现该漏洞的测试，然后使其通过”
            - “重构X”→“确保测试在之前和之后通过”
            对于多步骤任务，请提出简要计划：
            ```
            1. [步骤] → 验证：[检查]
            2. [步骤] →验证：[检查]
            3. [步骤] →验证：[检查]
            ```
            **这些指南有效条件是：** 
                差异中不必要的更改减少，因过度复杂而减少重写，澄清问题应在实施前而非错误之后。   
           </编码指南>        
           """;
    /**
     * 当前工作空间根目录，可在运行时更新
     */
    public static String WORKSPACE_ROOT = DEFAULT_WORKSPACE_ROOT;

    /**
     * 创建本地智能体的工厂方法。
     * <p>
     * 这是创建智能体的主要入口点，包装了构建过程并提供了异常处理。
     * </p>
     *
     * @param cwd            工作目录路径，智能体将在此目录下执行文件操作
     * @param mcpServers     MCP服务器列表，用于集成额外的工具
     * @param runnableConfig 运行配置，包含模型配置和上下文信息
     * @return 配置完成的智能体实例
     * @throws RuntimeException         如果智能体创建失败
     * @throws IllegalArgumentException 如果参数无效
     */
    public static Agent createAgent(String cwd, List<McpServer> mcpServers, RunnableConfig runnableConfig) {
        try {
            if (!StringUtils.isNotBlank(cwd)) {
                String tempDir = System.getProperty("java.io.tmpdir");
                System.out.println("系统临时目录: " + tempDir);
                cwd = tempDir + File.separator + "cwd_" + System.currentTimeMillis();
                log.error("create agent with cwd tmpdir: {} ", cwd);
            }
            return buildAgent(cwd, mcpServers, runnableConfig);
        } catch (Exception e) {
            log.error("Failed to create agent with cwd: {}, mcpServers: {}", cwd, mcpServers != null ? mcpServers.size() : 0, e);
            throw new RuntimeException("Failed to create LocalAgent", e);
        }
    }

    private static StaticToolCallbackProvider staticToolCallbackProvider(List<McpServer> mcpServers) {
        var toolCallbackProvider = MethodToolCallbackProvider.builder().toolObjects(ShellTools.builder().build(), new WebTool()).build();
        List<ToolCallback> tools = new ArrayList<>(List.of(toolCallbackProvider.getToolCallbacks()));
        log.debug("Loaded {} base tools", tools.size());
        // 添加 MCP 工具
        if (!CollectionUtils.isEmpty(mcpServers)) {
            List<ToolCallback> mcpTools = ToolsUtil.getMcpTools(mcpServers);
            tools.addAll(mcpTools);
            log.info("Added {} MCP tools from {} servers", mcpTools.size(), mcpServers.size());
        }
        return new StaticToolCallbackProvider(tools);
    }

    private static @NonNull List<Interceptor> getInterceptors(RunnableConfig runnableConfig, ChatModel chatModel) {
        ContextEditingInterceptor contextEditingInterceptor = ContextEditingInterceptor.builder().trigger(64 * 1024)  // 优化：降低到21k，提前触发优化
                .clearAtLeast(10 * 1024)  // 优化：至少清理15k，确保效果明显
                .keep(5)  // 优化：保留最近5条，平衡上下文完整性
                .tokenCounter(new DefaultTokenCounter()).clearToolInputs(true)  // 清理工具输入
                .placeholder("[...]")  // 优化：更有意义的占位符
                .build();

        var largeResultEvictionInterceptor = LargeResultEvictionInterceptor.builder().toolTokenLimitBeforeEvict(30000).backend(new LocalFilesystemBackend(WORKSPACE_ROOT)).build();

        var toolRetryInterceptor = ToolRetryInterceptor.builder().maxRetries(2)   // 设置退避策略
                .initialDelay(1)  // 初始延迟1秒
                .backoffFactor(1.5)  // 退避因子1.5倍
                .maxDelay(5000)     // 最大延迟5秒
                .onFailure(ToolRetryInterceptor.OnFailureBehavior.RETURN_MESSAGE).errorFormatter(e -> Json.toJson(Map.of("error", "工具调用失败，请输出完整、严谨的JSON结构: " + e.getMessage()))).jitter(true)        // 启用抖动)
                .build();
        // 根据当前模式决定文件系统是否只读
        log.info(" runnableConfig.context() {}", runnableConfig.context().get("mode"));
        String currentMode = runnableConfig.context().get("mode") instanceof String mode ? mode : "accept_edits";
        boolean readOnly = "plan".equalsIgnoreCase(currentMode);
        var filesystemInterceptor = FilesystemInterceptor.builder().withWorkspaceRoot(WORKSPACE_ROOT).readOnly(readOnly).withDefaultSecurity().build();
        WorkerInterceptor workerInterceptor = WorkerInterceptor.builder().defaultModel(chatModel).defaultTools(filesystemInterceptor.getTools())
//                .addWorker(WorkerSpec.builder()
//                        .name("research-analyst")
//                        .description("用于对复杂主题进行深入研究")
//                        .systemPrompt("你是一名研究分析师，擅长收集、分析和综合信息...")
//                        .build())
//                .addWorker(WorkerSpec.builder()
//                        .name("content-reviewer")
//                        .description("用于审查创建的内容或文档")
//                        .systemPrompt("你是一名内容审查员，检查代码和文档的质量...")
//                        .build())
                .includeGeneralPurpose(true)  // 同时包含通用Worker
                .build();
        ModelRetryInterceptor retryInterceptor = ModelRetryInterceptor.builder()
                .maxAttempts(3)
                .initialDelay(1000)
                .maxDelay(10000)
                .retryableExceptionPredicate((e) -> true)
                .backoffMultiplier(2.0)
                .build();
        List<Interceptor> interceptors = new ArrayList<>();
//        interceptors.add(contextEditingInterceptor);
        interceptors.add(largeResultEvictionInterceptor);
        interceptors.add(toolRetryInterceptor);
        interceptors.add(filesystemInterceptor);
        interceptors.add(workerInterceptor);
//        interceptors.add(retryInterceptor);
        interceptors.add(new ToolErrorInterceptor());
        interceptors.add(AcpTodoListInterceptor.builder().build());
        log.info("Agent mode: {}, filesystem readOnly: {}", currentMode, readOnly);
        return interceptors;
    }

    /**
     * 构建智能体的核心方法。
     * <p>
     * 配置智能体的所有组件，包括工具、拦截器、钩子和指令。
     * </p>
     *
     * @param cwd            工作目录路径
     * @param mcpServers     MCP服务器列表
     * @param runnableConfig 运行配置
     * @return 构建完成的智能体
     * @throws IllegalArgumentException 如果参数无效
     * @throws RuntimeException         如果组件初始化失败
     */
    public static Agent buildAgent(String cwd, List<McpServer> mcpServers, RunnableConfig runnableConfig) {
        if (cwd == null || cwd.trim().isEmpty()) {
            throw new IllegalArgumentException("Workspace directory (cwd) cannot be null or empty");
        }
        log.info("Building LocalAgent for workspace: {}", cwd);
        log.info("Building LocalAgent for context: {}", runnableConfig.context());
        WORKSPACE_ROOT = cwd;
        ChatModel chatModel = getChatModel(runnableConfig);
        List<Interceptor> interceptors = new ArrayList<>(getInterceptors(runnableConfig, chatModel));
        List<Hook> hooks = getHooks(runnableConfig, chatModel);
        // 使用 PromptTemplate 渲染指令
        Locale locale = Locale.getDefault();
        String displayName = locale.getDisplayLanguage();
        var instruction = PromptTemplate.builder().template(SYSTEM_PROMPT_TEMPLATE).variables(Map.of("cwd", cwd, "osName", System.getProperty("os.name").toLowerCase(),
                "language", displayName,
                "lineSeparator", System.lineSeparator().replace("\r", "\\r").replace("\n", "\\n"))).build().render();
        var chatOptions = OpenAiChatOptions.builder().streamUsage(true);
        String thoughtLevel = SessionConfigOptionsFactory.ThoughtLevel.LOW.getValueId();
        if (runnableConfig.context().get("thought_level") instanceof String level) {
            log.info("thought_level: {}", level);
            thoughtLevel = level;
        }
        if (SessionConfigOptionsFactory.ThoughtLevel.DISABLED.getValueId().equals(thoughtLevel)) {
            chatOptions.extraBody(Map.of("thinking", Map.of("type", "disabled")));
        } else {
            chatOptions.extraBody(Map.of("thinking", Map.of("type", "enabled")));
            chatOptions.reasoningEffort(thoughtLevel);
        }
        var staticToolCallbackProvider = staticToolCallbackProvider(mcpServers);
        var tools = List.of(staticToolCallbackProvider.getToolCallbacks());
        var agent = ReactAgent.builder().name("agent")
                .tools(tools)
                .hooks(hooks)
                .model(chatModel)
                .interceptors(interceptors)
                .chatOptions(chatOptions.build())
                .parallelToolExecution(true)
                .saver(FILE_SYSTEM_SAVER)
                .enableLogging(true)
                .description("本地文件操作智能体，主要负责文件创建，编辑,命令执行")
                .systemPrompt(instruction)
                .outputKey("agent_output")
                .returnReasoningContents(true)
                .build();
        log.info("LocalAgent built successfully with {} tools and {} interceptors", tools.size(), interceptors.size());
        return agent;
    }

    @NotNull
    private static List<Hook> getHooks(RunnableConfig runnableConfig, ChatModel chatModel) {
        List<Hook> hooks = new ArrayList<>(3);
        String currentMode = runnableConfig.context().get("mode") instanceof String m ? m : "accept_edits";
        log.info("getHooks: currentMode {}", currentMode);
        if (!"yolo".equalsIgnoreCase(currentMode)) {
            log.info("approvalOn: currentMode {}", currentMode);
            String description = "是否允许执行命令";
            Map<String, ToolConfig> approvalOn = Map.of("Bash", ToolConfig.builder().description(description).build());
            HumanInTheLoopHook humanInTheLoopHook = HumanInTheLoopHook.builder().approvalOn(approvalOn).build();
            hooks.add(humanInTheLoopHook);
            log.info("{} mode: Bash commands require human approval",currentMode);
        } else {
            log.info("YOLO mode: all operations auto-approved");
        }
        hooks.add(SkillsAgentHook.builder()
                .skillRegistry(FileSystemSkillRegistry.builder()
                        .userSkillsDirectory(FILE_SYSTEM_SKILL_DIR.toAbsolutePath().toString())
                        .projectSkillsDirectory(WORKSPACE_ROOT + File.pathSeparator + ".skills")
                        .autoLoad(true)
                        .build())
                .autoReload(true)
                .build());

        hooks.add(SummarizationHook.builder()
                .model(chatModel)
                .maxTokensBeforeSummary(256 * 1024)
                .messagesToKeep(10)
                .keepFirstUserMessage(true)
                .build());

        return hooks;
    }

    @NotNull
    private static ChatModel getChatModel(RunnableConfig runnableConfig) {
        ChatModel chatModel;
        if (runnableConfig.context().get("model") instanceof String modelId) {
            try {
                chatModel = AiModels.createChatModelFromJson(modelId);
                log.info("Using model from JSON config: {}", modelId);
            } catch (Exception e) {
                log.error("Failed to create chat model from config: {}", modelId, e);
                throw new RuntimeException("Failed to initialize chat model", e);
            }
        } else {
            String defaultModelId = AiModels.defaultModel();
            chatModel = AiModels.createChatModelFromJson(defaultModelId);
            log.info("No specific model configuration found, using default model: {}", defaultModelId);
        }
        return chatModel;
    }

}


