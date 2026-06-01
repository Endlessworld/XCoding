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
import com.alibaba.cloud.ai.graph.agent.interceptor.Interceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.modelretry.ModelRetryInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.toolerror.ToolErrorInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.toolretry.ToolRetryInterceptor;
import com.alibaba.cloud.ai.graph.checkpoint.savers.file.FileSystemSaver;
import com.alibaba.cloud.ai.graph.serializer.plain_text.jackson.SpringAIJacksonStateSerializer;
import com.alibaba.cloud.ai.graph.skills.registry.filesystem.FileSystemSkillRegistry;
import com.xr21.ai.agent.config.AiModels;
import com.xr21.ai.agent.config.ModelConfigLoader;
import com.xr21.ai.agent.interceptors.AcpTodoListInterceptor;
import com.xr21.ai.agent.interceptors.ContextEditingInterceptor;
import com.xr21.ai.agent.interceptors.FilesystemInterceptor;
import com.xr21.ai.agent.interceptors.WorkerInterceptor;
import com.xr21.ai.agent.model.Config;
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
    public static final String DEFAULT_WORKSPACE_ROOT = "D:\\IdeaProjects\\agi_working";
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
            你是一个编码智能体 XAgent，通过文件/内容查找、读取、文件创建、编辑等工具进行项目代码编辑
            The current working directory is：{cwd} 所有文件操作仅限于工作目录之内
            当前系统：{osName} 您只能执行当前系统平台默认存在的命令，使用当前用户系统语言:{language}回复用户
            对于编码任务 如果工作目录下存在 AGENTS.md 或 README.md 可以通过它们快速了解当前项目
            使用Bash编译项目时只输出编译错误或成功信息
            禁止使用过write_file编辑或重写已有文件！ 禁止使用Bash编辑文件
            你只能使用edit_file_with_git_patch编辑文件！如果patch内容不正确 你需要重新生成新的patch直到成功应用patch
            如果patch 应用有残留 使用git命令回退patch合并
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
        ContextEditingInterceptor contextEditingInterceptor = ContextEditingInterceptor.builder().trigger(262144)  // 优化：降低到32k，提前触发优化
                .clearAtLeast(15000)  // 优化：至少清理15k，确保效果明显
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
        var filesystemInterceptor = FilesystemInterceptor.builder().withWorkspaceRoot(WORKSPACE_ROOT).readOnly(false).withDefaultSecurity().build();

        // 创建Worker拦截器
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
                .backoffMultiplier(2.0)
                .build();
        List<Interceptor> interceptors = new ArrayList<>();
        interceptors.add(contextEditingInterceptor);
        interceptors.add(largeResultEvictionInterceptor);
        interceptors.add(toolRetryInterceptor);
        interceptors.add(filesystemInterceptor);
//        interceptors.add(retryInterceptor);
        interceptors.add(new ToolErrorInterceptor());
        interceptors.add(AcpTodoListInterceptor.builder().build());
        if (runnableConfig.context().get("mode") instanceof String mode && mode.equalsIgnoreCase("Workers")) {
            interceptors.add(workerInterceptor);
            log.info("Workers mode use workerInterceptor");
        }
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
        if (runnableConfig == null) {
            throw new IllegalArgumentException("RunnableConfig cannot be null");
        }
        log.info("Building LocalAgent for workspace: {}", cwd);
        WORKSPACE_ROOT = cwd;
        log.debug("Setting workspace root to: {}", WORKSPACE_ROOT);
        ChatModel chatModel = getChatModel(runnableConfig);
        List<Interceptor> interceptors = new ArrayList<>(getInterceptors(runnableConfig, chatModel));
        List<Hook> hooks = getHooks(runnableConfig);
        // 使用 PromptTemplate 渲染指令
        Locale locale = Locale.getDefault();
        String displayName = locale.getDisplayLanguage();
        var instruction = PromptTemplate.builder().template(SYSTEM_PROMPT_TEMPLATE).variables(Map.of("cwd", cwd, "osName", System.getProperty("os.name").toLowerCase(),"language",displayName)).build().render();
        var chatOptions = OpenAiChatOptions.builder().streamUsage(true);
        if (chatModel.getDefaultOptions().getModel().contains("deepseek-v4")) {
            chatOptions.extraBody(Map.of("thinking", Map.of("type", "disabled")));
        }
        var staticToolCallbackProvider = staticToolCallbackProvider(mcpServers);
        var tools = List.of(staticToolCallbackProvider.getToolCallbacks());
        var agent = ReactAgent.builder().name("agent").chatOptions(chatOptions.build()).model(chatModel).tools(tools).parallelToolExecution(true).saver(FILE_SYSTEM_SAVER).hooks(hooks).enableLogging(true).description("本地文件操作智能体，主要负责文件创建，编辑,命令执行").systemPrompt(instruction).interceptors(interceptors).outputKey("agent_output").returnReasoningContents(true).build();
        log.info("LocalAgent built successfully with {} tools and {} interceptors", tools.size(), interceptors.size());
        return agent;
    }

    @NotNull
    private static List<Hook> getHooks(RunnableConfig runnableConfig) {
        List<Hook> hooks = new ArrayList<>();
        Map<String, ToolConfig> approvalOn = Map.of("feed_back_tool", ToolConfig.builder().description("请确认信息收集工具执行").build(), "Bash", ToolConfig.builder().description("是否允许执行命令").build());
        if (runnableConfig.context().get("auto_approve") instanceof Boolean autoApprove && !autoApprove) {
            HumanInTheLoopHook humanInTheLoopHook = HumanInTheLoopHook.builder().approvalOn(approvalOn).build();
            hooks.add(humanInTheLoopHook);
        }
        FileSystemSkillRegistry registry = FileSystemSkillRegistry.builder().userSkillsDirectory(FILE_SYSTEM_SKILL_DIR.toAbsolutePath().toString()).projectSkillsDirectory(WORKSPACE_ROOT + File.pathSeparator + ".skills").autoLoad(true).build();
        SkillsAgentHook hook = SkillsAgentHook.builder().skillRegistry(registry).autoReload(true).build();
        hooks.add(hook);
        return hooks;
    }

    @NotNull
    private static ChatModel getChatModel(RunnableConfig runnableConfig) {
        ChatModel chatModel = null;
        if (runnableConfig.context().get("model") instanceof String modelId) {
            try {
                chatModel = AiModels.createChatModelFromJson(modelId);
                log.info("Using model from JSON config: {}", modelId);
            } catch (Exception e) {
                log.error("Failed to create chat model from config: {}", modelId, e);
                throw new RuntimeException("Failed to initialize chat model", e);
            }
        } else {
            List<Config.ModelConfig> configs = ModelConfigLoader.loadConfigs();
            chatModel = AiModels.createChatModelFromJson(ModelConfigLoader.getDefaultConfig(configs).getModelId());
            log.info("No specific model configuration found, using default model : {}", chatModel);
        }
        return chatModel;
    }

}

