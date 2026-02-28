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

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 本地智能体
 */
@Slf4j
public class LocalAgent {

    public static final String fileSystemSaverFolder = System.getProperty("user.home") + File.separator + ".agi_working" + File.separator + "SystemSaver";
    public static final FileSystemSaver fileSystemSaver = FileSystemSaver.builder()
            .targetFolder(Path.of(fileSystemSaverFolder))
            .build();

    public static String WORKSPACE_ROOT = "D:\\IdeaProjects\\agi_working";

    public static Agent createAgent(String cwd, List<McpServer> mcpServers, RunnableConfig runnableConfig) {
        LocalAgent localAgent = new LocalAgent();
        return localAgent.buildAgent(cwd, mcpServers, runnableConfig);
    }

    private static List<ToolCallback> getTools() {
        var tools = new ArrayList<ToolCallback>();
        var methodToolCallbackProvider = MethodToolCallbackProvider.builder()
                .toolObjects(ShellTools.builder()
                        .build(), new EditFileTool(), new FeedBackTool(), new GlobTool(), new GrepTool(), new ListFilesTool(), new ReadFileTool(), new WebSearchTool(), new WriteFileTool())
                .build();
        tools.addAll(List.of(methodToolCallbackProvider.getToolCallbacks()));
        return tools;
    }

    private static @NonNull List<Interceptor> getInterceptors() {
        ContextEditingInterceptor contextEditingInterceptor = ContextEditingInterceptor.builder()
                .trigger(100000)  // 设置触发清理的令牌数阈值
                .clearAtLeast(2000)  // 每次清理至少清除2000个令牌
                .keep(10)  // 保留最近3条工具消息
                .tokenCounter(new DefaultTokenCounter())
                .clearToolInputs(true)  // 清理工具输入（虽然当前未实现）
                .build();
        List<Interceptor> interceptors = new ArrayList<>();
        interceptors.add(new ToolErrorInterceptor());
        interceptors.add(contextEditingInterceptor);
        var largeResultEvictionInterceptor = LargeResultEvictionInterceptor.builder()
                .toolTokenLimitBeforeEvict(30000)
                .backend(new LocalFilesystemBackend(WORKSPACE_ROOT))
                .build();
        interceptors.add(largeResultEvictionInterceptor);
//        interceptors.add(ModelFallbackInterceptor.builder().fallbackModels(fallbackModels).build());
        interceptors.add(ToolRetryInterceptor.builder()
                .maxRetries(2)   // 设置退避策略
                .initialDelay(1)  // 初始延迟1秒
                .backoffFactor(1.5)  // 退避因子1.5倍
                .maxDelay(5000)     // 最大延迟10秒
                .onFailure(ToolRetryInterceptor.OnFailureBehavior.RETURN_MESSAGE)
                .errorFormatter(e -> Json.toJson(Map.of("error", "工具调用失败，请输出完整、严谨的JSON结构: " + e.getMessage())))
                .jitter(true)        // 启用抖动)
                .build());
        return interceptors;
    }

    public Agent buildAgent(String cwd, List<McpServer> mcpServers, RunnableConfig runnableConfig) {

        ChatModel chatModel = null;
        WORKSPACE_ROOT = cwd;
        var tools = getTools();
        if (runnableConfig.context().containsKey("SetSessionModelRequest") && runnableConfig.context()
                .get("SetSessionModelRequest") instanceof AcpSchema.SetSessionModelRequest setSessionModelRequest) {
            chatModel = AiModels.createChatModelFromJson(setSessionModelRequest.modelId());
            log.info("use model from JSON config: {}", setSessionModelRequest.modelId());
        }


        // 添加 MCP 工具
        if (!CollectionUtils.isEmpty(mcpServers)) {
            List<ToolCallback> mcpTools = ToolsUtil.getMcpTools(mcpServers);
            tools.addAll(mcpTools);
            log.info("Added {} MCP tools from {} servers", mcpTools.size(), mcpServers.size());
        }
        List<Interceptor> interceptors = getInterceptors();
        if (runnableConfig.context().containsKey("SetSessionModeRequest") && runnableConfig.context()
                .get("SetSessionModeRequest") instanceof AcpSchema.SetSessionModeRequest setSessionModeRequest) {
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
        Map<String, ToolConfig> approvalOn = Map.of("feed_back_tool", ToolConfig.builder()
                .description("请确认信息收集工具执行")
                .build());
        var instruction = """
                你是一个编码智能体 XAgent，通过文件/内容查找，读取，文件创建，编辑等工具进行项目代码编辑
                当前工作目录: %s 所有文件操作仅限与工作目录之内 定义改值为 cwd
                当前时间: %s
                使用grep查找内容并定位问题(禁止执行**/*类似搜索，使用明确的关键字进行检索)
                使用read_file读取详细内容
                使用edit_file修改文件内容（内容修改以行为单位，如果需要修改的内容较多分成多次修改，每次最多修改3行内容）
                使用write_file创建并写入文件内容
                使用ls查看指定目录的文件列表
                使用Bash执行命令,使用BashOutput获取执行结果，使用KillShell结束命令 当前系统：%s
                禁止使用ls逐步探索目录
                直接使用grep搜索文件内容
                """.formatted(cwd, LocalDateTime.now().toString(), System.getProperty("os.name").toLowerCase());
        return ReactAgent.builder()
                .name("agent")
                .model(chatModel)
                .tools(tools)
                .saver(fileSystemSaver)
                .hooks(HumanInTheLoopHook.builder().approvalOn(approvalOn).build())
                .description("本地文件操作智能体，主要负责文件创建，编辑,命令执行")
                .instruction(instruction)
                .interceptors(interceptors)
                .outputKey("agent_output")
                .returnReasoningContents(true)
                .build();
    }

}

