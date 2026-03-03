package com.xr21.ai.agent.agent;

import com.agentclientprotocol.sdk.spec.AcpSchema;
import com.agentclientprotocol.sdk.spec.AcpSchema.McpServer;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.extension.file.LocalFilesystemBackend;
import com.alibaba.cloud.ai.graph.agent.extension.interceptor.LargeResultEvictionInterceptor;
import com.alibaba.cloud.ai.graph.agent.extension.interceptor.SubAgentSpec;
import com.alibaba.cloud.ai.graph.agent.hook.hip.HumanInTheLoopHook;
import com.alibaba.cloud.ai.graph.agent.hook.hip.ToolConfig;
import com.alibaba.cloud.ai.graph.agent.interceptor.Interceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.toolerror.ToolErrorInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.toolretry.ToolRetryInterceptor;
import com.alibaba.cloud.ai.graph.checkpoint.savers.file.FileSystemSaver;
import com.xr21.ai.agent.config.AiModels;
import com.xr21.ai.agent.config.ModelConfigLoader;
import com.xr21.ai.agent.config.ModelsConfig;
import com.xr21.ai.agent.interceptors.AcpTodoListInterceptor;
import com.xr21.ai.agent.interceptors.ContextEditingInterceptor;
import com.xr21.ai.agent.interceptors.FilesystemInterceptor;
import com.xr21.ai.agent.interceptors.SubAgentInterceptor;
import com.xr21.ai.agent.tools.FeedBackTool;
import com.xr21.ai.agent.tools.ShellTools;
import com.xr21.ai.agent.tools.WebSearchTool;
import com.xr21.ai.agent.utils.DefaultTokenCounter;
import com.xr21.ai.agent.utils.Json;
import com.xr21.ai.agent.utils.ToolsUtil;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.lang.NonNull;
import org.springframework.util.CollectionUtils;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
    public static final FileSystemSaver FILE_SYSTEM_SAVER = FileSystemSaver.builder()
            .targetFolder(FILE_SYSTEM_SAVER_FOLDER)
            .build();
    private static final String SYSTEM_PROMPT_TEMPLATE = """
            你是一个编码智能体 XAgent，通过文件/内容查找、读取、文件创建、编辑等工具进行项目代码编辑
            The current working directory is：{cwd} 所有文件操作仅限于工作目录之内
            当前时间：{currentTime}
            当前系统：{osName}
            对于编码任务 如果工作目录下存在 AGENTS.md 或 README.md 可以通过它们快速了解当前项目
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
            return buildAgent(cwd, mcpServers, runnableConfig);
        } catch (Exception e) {
            log.error("Failed to create agent with cwd: {}, mcpServers: {}", cwd, mcpServers != null ? mcpServers.size() : 0, e);
            throw new RuntimeException("Failed to create LocalAgent", e);
        }
    }

    private static List<ToolCallback> getTools() {
        var toolCallbackProvider = MethodToolCallbackProvider.builder()
                .toolObjects(ShellTools.builder().build(), new FeedBackTool(), new WebSearchTool())
                .build();
        return new ArrayList<>(List.of(toolCallbackProvider.getToolCallbacks()));
    }

    private static @NonNull List<Interceptor> getInterceptors(RunnableConfig runnableConfig, ChatModel chatModel) {
        ContextEditingInterceptor contextEditingInterceptor = ContextEditingInterceptor.builder()
                .trigger(60000)  // 优化：降低到60k，提前触发优化
                .clearAtLeast(15000)  // 优化：至少清理15k，确保效果明显
                .keep(5)  // 优化：保留最近5条，平衡上下文完整性
                .tokenCounter(new DefaultTokenCounter())
                .clearToolInputs(true)  // 清理工具输入
                .placeholder("[...]")  // 优化：更有意义的占位符
                .build();

        var largeResultEvictionInterceptor = LargeResultEvictionInterceptor.builder()
                .toolTokenLimitBeforeEvict(30000)
                .backend(new LocalFilesystemBackend(WORKSPACE_ROOT))
                .build();

        var toolRetryInterceptor = ToolRetryInterceptor.builder()
                .maxRetries(2)   // 设置退避策略
                .initialDelay(1)  // 初始延迟1秒
                .backoffFactor(1.5)  // 退避因子1.5倍
                .maxDelay(5000)     // 最大延迟10秒
                .onFailure(ToolRetryInterceptor.OnFailureBehavior.RETURN_MESSAGE)
                .errorFormatter(e -> Json.toJson(Map.of("error", "工具调用失败，请输出完整、严谨的JSON结构: " + e.getMessage())))
                .jitter(true)        // 启用抖动)
                .build();
        var filesystemInterceptor = FilesystemInterceptor.builder()
                .withWorkspaceRoot(WORKSPACE_ROOT)
                .readOnly(false)
                .withDefaultSecurity()
                .build();

        // 创建子代理拦截器
        SubAgentInterceptor subAgentInterceptor = SubAgentInterceptor.builder()
                .defaultModel(chatModel)
                .defaultTools(filesystemInterceptor.getTools())
                .addSubAgent(SubAgentSpec.builder()
                        .name("research-analyst")
                        .description("用于对复杂主题进行深入研究")
                        .systemPrompt("你是一名研究分析师，擅长收集、分析和综合信息...")
                        .build())
                .addSubAgent(SubAgentSpec.builder()
                        .name("content-reviewer")
                        .description("用于审查创建的内容或文档")
                        .systemPrompt("你是一名内容审查员，检查代码和文档的质量...")
                        .build())
                .includeGeneralPurpose(true)  // 同时包含通用子代理
                .build();
        List<Interceptor> interceptors = new ArrayList<>();

        interceptors.add(contextEditingInterceptor);
        interceptors.add(largeResultEvictionInterceptor);
        interceptors.add(toolRetryInterceptor);
        interceptors.add(filesystemInterceptor);
        interceptors.add(new ToolErrorInterceptor());
        interceptors.add(AcpTodoListInterceptor.builder().build());
        if (runnableConfig.context().containsKey("SetSessionModeRequest") && runnableConfig.context()
                .get("SetSessionModeRequest") instanceof AcpSchema.SetSessionModeRequest setSessionModeRequest) {
            if (setSessionModeRequest.modeId().equalsIgnoreCase("ForkAgent")) {
                interceptors.add(subAgentInterceptor);
                log.info("ForkAgent mode use subAgentInterceptor");
            }
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
        var tools = getTools();
        log.debug("Loaded {} base tools", tools.size());
        ChatModel chatModel = getChatModel(runnableConfig);
        // 添加 MCP 工具
        if (!CollectionUtils.isEmpty(mcpServers)) {
            List<ToolCallback> mcpTools = ToolsUtil.getMcpTools(mcpServers);
            tools.addAll(mcpTools);
            log.info("Added {} MCP tools from {} servers", mcpTools.size(), mcpServers.size());
        }
        List<Interceptor> interceptors = new ArrayList<>(getInterceptors(runnableConfig, chatModel));
        Map<String, ToolConfig> approvalOn = Map.of("feed_back_tool", ToolConfig.builder()
                .description("请确认信息收集工具执行")
                .build(), "Bash", ToolConfig.builder().description("是否允许执行命令").build());
        HumanInTheLoopHook humanInTheLoopHook = HumanInTheLoopHook.builder().approvalOn(approvalOn).build();
        // 使用 PromptTemplate 渲染指令
        var instruction = PromptTemplate.builder()
                .template(SYSTEM_PROMPT_TEMPLATE)
                .variables(Map.of("cwd", cwd, "currentTime", LocalDateTime.now()
                        .toString(), "osName", System.getProperty("os.name").toLowerCase()))
                .build()
                .render();
        var agent = ReactAgent.builder()
                .name("agent")
                .model(chatModel)
                .tools(tools)
                .saver(FILE_SYSTEM_SAVER)
                .hooks(humanInTheLoopHook)
                .enableLogging(true)
                .description("本地文件操作智能体，主要负责文件创建，编辑,命令执行")
                .systemPrompt(instruction)
                .interceptors(interceptors)
                .outputKey("agent_output")
                .returnReasoningContents(true)
                .build();
        log.info("LocalAgent built successfully with {} tools and {} interceptors", tools.size(), interceptors.size());

        return agent;
    }

    @NotNull
    private static ChatModel getChatModel(RunnableConfig runnableConfig) {
        ChatModel chatModel = null;
        if (runnableConfig.context().containsKey("SetSessionModelRequest") && runnableConfig.context()
                .get("SetSessionModelRequest") instanceof AcpSchema.SetSessionModelRequest setSessionModelRequest) {
            try {
                chatModel = AiModels.createChatModelFromJson(setSessionModelRequest.modelId());
                log.info("Using model from JSON config: {}", setSessionModelRequest.modelId());
            } catch (Exception e) {
                log.error("Failed to create chat model from config: {}", setSessionModelRequest.modelId(), e);
                throw new RuntimeException("Failed to initialize chat model", e);
            }
        } else {
            List<ModelsConfig.ModelConfig> configs = ModelConfigLoader.loadConfigs();
            chatModel = AiModels.createChatModelFromJson(ModelConfigLoader.getDefaultConfig(configs).getModelId());
            log.info("No specific model configuration found, using default model");
        }
        return chatModel;
    }

}

