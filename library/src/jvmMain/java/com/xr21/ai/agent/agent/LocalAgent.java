package com.xr21.ai.agent.agent;

import com.agentclientprotocol.sdk.spec.AcpSchema;
import com.agentclientprotocol.sdk.spec.AcpSchema.McpServer;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.extension.file.LocalFilesystemBackend;
import com.alibaba.cloud.ai.graph.agent.extension.interceptor.LargeResultEvictionInterceptor;
import com.alibaba.cloud.ai.graph.agent.hook.hip.HumanInTheLoopHook;
import com.alibaba.cloud.ai.graph.agent.hook.hip.ToolConfig;
import com.alibaba.cloud.ai.graph.agent.interceptor.Interceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.todolist.TodoListInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.toolerror.ToolErrorInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.toolretry.ToolRetryInterceptor;
import com.alibaba.cloud.ai.graph.checkpoint.savers.file.FileSystemSaver;
import com.xr21.ai.agent.config.AiModels;
import com.xr21.ai.agent.interceptors.AcpTodoListInterceptor;
import com.xr21.ai.agent.interceptors.ContextEditingInterceptor;
import com.xr21.ai.agent.tools.*;
import com.xr21.ai.agent.utils.DefaultTokenCounter;
import com.xr21.ai.agent.utils.Json;
import com.xr21.ai.agent.utils.ToolsUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
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
     * 文件系统保存器的存储目录路径
     */
    private static final Path FILE_SYSTEM_SAVER_FOLDER = Path.of(System.getProperty("user.home"), ".agi_working", "SystemSaver");

    /**
     * 文件系统保存器实例，用于持久化智能体状态
     */
    public static final FileSystemSaver FILE_SYSTEM_SAVER = FileSystemSaver.builder().targetFolder(FILE_SYSTEM_SAVER_FOLDER).build();

    /**
     * 默认工作空间根目录
     */
    private static final String DEFAULT_WORKSPACE_ROOT = "D:\\IdeaProjects\\agi_working";

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
        var methodToolCallbackProvider = MethodToolCallbackProvider.builder().toolObjects(ShellTools.builder().build(), new EditFileTool(), new FeedBackTool(), new GlobTool(), new GrepTool(), new ListFilesTool(), new ReadFileTool(), new WebSearchTool(), new WriteFileTool()).build();

        return new ArrayList<>(List.of(methodToolCallbackProvider.getToolCallbacks()));
    }

    private static @NonNull List<Interceptor> getInterceptors() {
        ContextEditingInterceptor contextEditingInterceptor = ContextEditingInterceptor.builder().trigger(60000)  // 优化：降低到60k，提前触发优化
                .clearAtLeast(15000)  // 优化：至少清理15k，确保效果明显
                .keep(5)  // 优化：保留最近5条，平衡上下文完整性
                .tokenCounter(new DefaultTokenCounter()).clearToolInputs(true)  // 清理工具输入
                .placeholder("[...]")  // 优化：更有意义的占位符
                .build();

        var largeResultEvictionInterceptor = LargeResultEvictionInterceptor.builder().toolTokenLimitBeforeEvict(30000).backend(new LocalFilesystemBackend(WORKSPACE_ROOT)).build();

        var toolRetryInterceptor = ToolRetryInterceptor.builder().maxRetries(2)   // 设置退避策略
                .initialDelay(1)  // 初始延迟1秒
                .backoffFactor(1.5)  // 退避因子1.5倍
                .maxDelay(5000)     // 最大延迟10秒
                .onFailure(ToolRetryInterceptor.OnFailureBehavior.RETURN_MESSAGE).errorFormatter(e -> Json.toJson(Map.of("error", "工具调用失败，请输出完整、严谨的JSON结构: " + e.getMessage()))).jitter(true)        // 启用抖动)
                .build();

        return List.of(new ToolErrorInterceptor(), contextEditingInterceptor, largeResultEvictionInterceptor, toolRetryInterceptor);
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

        ChatModel chatModel = null;
        WORKSPACE_ROOT = cwd;
        log.debug("Setting workspace root to: {}", WORKSPACE_ROOT);

        var tools = getTools();
        log.debug("Loaded {} base tools", tools.size());
        if (runnableConfig.context().containsKey("SetSessionModelRequest") && runnableConfig.context().get("SetSessionModelRequest") instanceof AcpSchema.SetSessionModelRequest setSessionModelRequest) {
            try {
                chatModel = AiModels.createChatModelFromJson(setSessionModelRequest.modelId());
                log.info("Using model from JSON config: {}", setSessionModelRequest.modelId());
            } catch (Exception e) {
                log.error("Failed to create chat model from config: {}", setSessionModelRequest.modelId(), e);
                throw new RuntimeException("Failed to initialize chat model", e);
            }
        } else {
            log.info("No specific model configuration found, using default model");
        }


        // 添加 MCP 工具
        if (!CollectionUtils.isEmpty(mcpServers)) {
            List<ToolCallback> mcpTools = ToolsUtil.getMcpTools(mcpServers);
            tools.addAll(mcpTools);
            log.info("Added {} MCP tools from {} servers", mcpTools.size(), mcpServers.size());
        }
        List<Interceptor> interceptors = new ArrayList<>(getInterceptors());
        if (runnableConfig.context().containsKey("SetSessionModeRequest") && runnableConfig.context().get("SetSessionModeRequest") instanceof AcpSchema.SetSessionModeRequest setSessionModeRequest) {
            if (setSessionModeRequest.modeId().equalsIgnoreCase("plan")) {
                // Check if we're in ACP context
                boolean isAcpContext = runnableConfig.context().containsKey("SyncPromptContext");
                if (isAcpContext) {
                    // Use ACP-compatible TodoList interceptor for ACP context
                    interceptors.add(AcpTodoListInterceptor.builder().build());
                    log.info("plan mode use AcpTodoListInterceptor (ACP context)");
                } else {
                    // Use regular TodoList interceptor for non-ACP context
                    interceptors.add(TodoListInterceptor.builder().build());
                    log.info("plan mode use TodoListInterceptor (non-ACP context)");
                }
            }
        }
        Map<String, ToolConfig> approvalOn = Map.of("feed_back_tool", ToolConfig.builder().description("请确认信息收集工具执行").build());
        var instruction = String.format("""
                你是一个编码智能体 XAgent，通过文件/内容查找，读取，文件创建，编辑等工具进行项目代码编辑
                当前工作目录: %s 所有文件操作仅限与工作目录之内 定义改值为 cwd
                如果工作目录下存在AGENT.md或README.md可以通过它们快速了解当前项目
                当前时间: %s
                使用grep查找内容并定位问题(禁止执行**/*类似搜索，使用明确的关键字进行检索)
                使用read_file读取详细内容
                使用edit_file修改文件内容（内容修改以行为单位，如果需要修改的内容较多分成多次修改，每次最多修改3行内容）
                使用write_file创建并写入文件内容
                使用ls查看指定目录的文件列表
                使用Bash执行命令,使用BashOutput获取执行结果，使用KillShell结束命令 当前系统：%s
                禁止使用ls逐步探索目录
                直接使用grep搜索文件内容
                """, cwd, LocalDateTime.now(), System.getProperty("os.name").toLowerCase());
        var agent = ReactAgent.builder().name("agent").model(chatModel).tools(tools).saver(FILE_SYSTEM_SAVER).hooks(HumanInTheLoopHook.builder().approvalOn(approvalOn).build()).description("本地文件操作智能体，主要负责文件创建，编辑,命令执行").instruction(instruction).interceptors(interceptors).outputKey("agent_output").returnReasoningContents(true).build();

        log.info("LocalAgent built successfully with {} tools and {} interceptors", tools.size(), interceptors.size());
        return agent;
    }

}

