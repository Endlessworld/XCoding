package com.xr21.ai.agent;

import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.extension.file.LocalFilesystemBackend;
import com.alibaba.cloud.ai.graph.agent.extension.interceptor.LargeResultEvictionInterceptor;
import com.alibaba.cloud.ai.graph.agent.hook.hip.HumanInTheLoopHook;
import com.alibaba.cloud.ai.graph.agent.hook.hip.ToolConfig;
import com.alibaba.cloud.ai.graph.agent.interceptor.Interceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.toolerror.ToolErrorInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.toolretry.ToolRetryInterceptor;
import com.alibaba.cloud.ai.graph.agent.tools.ShellTool;
import com.alibaba.cloud.ai.graph.checkpoint.savers.file.FileSystemSaver;
import com.xr21.ai.agent.config.AiModels;
import com.xr21.ai.agent.interceptors.ContextEditingInterceptor;
import com.xr21.ai.agent.interceptors.FilesystemInterceptor;
import com.xr21.ai.agent.utils.DefaultTokenCounter;
import com.xr21.ai.agent.utils.Json;
import org.slf4j.Logger;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.lang.NonNull;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 本地智能体
 */
public class LocalAgent {

    public static final String fileSystemSaverFolder = System.getProperty("user.home") + File.separator + ".agi_working" + File.separator + "SystemSaver";
    public static final FileSystemSaver fileSystemSaver = FileSystemSaver.builder()
            .targetFolder(Path.of(fileSystemSaverFolder))
            .build();

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(LocalAgent.class);

    public static String WORKSPACE_ROOT = "D:\\IdeaProjects\\agi_working";
    private ChatModel chatModel;

    public LocalAgent() {
        this.initializeChatModel();
    }

    public static Agent createAgent(String cwd) {
        LocalAgent localAgent = new LocalAgent();
        return localAgent.buildAgent(cwd);
    }

    private static List<ToolCallback> getTools() {
        List<String> includes = List.of("grep", "glob", "edit_file", "write_file", "read_file", "ls", "execute_terminal_command");//"execute_terminal_command"
        var tools = new ArrayList<ToolCallback>();
        var filesystemInterceptor = FilesystemInterceptor.builder().build();
        tools.addAll(filesystemInterceptor.getTools()
                .stream()
                .filter(toolCallback -> includes.contains(toolCallback.getToolDefinition().name()))
                .toList());
        return tools;
    }

    private static @NonNull List<Interceptor> getInterceptors() {
        ContextEditingInterceptor contextEditingInterceptor = ContextEditingInterceptor.builder()
                .trigger(500500)  // 设置触发清理的令牌数阈值
                .clearAtLeast(2000)  // 每次清理至少清除2000个令牌
                .keep(8)  // 保留最近3条工具消息
                .tokenCounter(new DefaultTokenCounter())
                .clearToolInputs(true)  // 清理工具输入（虽然当前未实现）
                .build();
        List<Interceptor> interceptors = new ArrayList<>();
        interceptors.add(new ToolErrorInterceptor());
        interceptors.add(contextEditingInterceptor);
        var largeResultEvictionInterceptor = LargeResultEvictionInterceptor.builder()
                .toolTokenLimitBeforeEvict(100000)
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

    public static ToolCallback executeShellCommand() {
        // Use ShellTool with a temporary workspace directory
        String shellDescription = """
                Execute a shell command inside a persistent session. Before running a command, \
                confirm the working directory is correct (e.g., inspect with `ls` or `pwd`) and ensure \
                any parent directories exist. Prefer absolute paths and quote paths containing spaces, \
                such as `cd "/path/with spaces"`. Chain multiple commands with `&&` or `;` instead of \
                embedding newlines. Avoid unnecessary `cd` usage unless explicitly required so the \
                session remains stable. Outputs may be truncated when they become very large, and long \
                running commands will be terminated once their configured timeout elapses.\
                """;
        return ShellTool.builder(WORKSPACE_ROOT)
                .withName("execute_shell_command")
                .withEnvironment(System.getenv())
                .withShellCommand(List.of("cmd.exe"))
                .withDescription(shellDescription)
                .build();
    }

    /**
     * 初始化聊天模型，延迟加载以避免启动时依赖环境变量
     */
    public void initializeChatModel() {
        try {
            this.chatModel = AiModels.DEEPSEEK_V3_2.createChatModel();
            log.info("ChatModel initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize ChatModel: {}", e.getMessage());
            // 创建一个简单的回退模型，不依赖外部API
            this.chatModel = createFallbackChatModel();
        }
    }

    /**
     * 创建回退聊天模型
     */
    private ChatModel createFallbackChatModel() {
        try {
            return AiModels.KIMI_K2_5.createChatModel();
        } catch (Exception e) {
            log.error("Failed to create fallback ChatModel: {}", e.getMessage());
            throw new RuntimeException(e);

        }
    }


    public Agent buildAgent(String cwd) {
        WORKSPACE_ROOT = cwd;
        var tools = getTools();
        List<Interceptor> interceptors = getInterceptors();
        Map<String, ToolConfig> approvalOn = Map.of("feed_back_tool", ToolConfig.builder()
                .description("请确认信息收集工具执行")
                .build());
        ReactAgent agent = ReactAgent.builder()
                .name("agent")
                .model(chatModel)
                .tools(tools)
                .saver(fileSystemSaver)
                .hooks(HumanInTheLoopHook.builder().approvalOn(approvalOn).build())
//                .hooks(ShellToolAgentHook.builder()
//                        .shellToolName("execute_shell_command")
//                        .build())
                .description("本地文件操作智能体，主要负责文件创建，编辑,命令执行")
                .instruction("""
                        你是一个本地文件操作智能体，主要负责文件/内容查找，读取，文件创建，编辑,
                        当前工作目录: %s 所有文件操作仅限与工作目录之内
                        当前时间: %s
                        使用grep查找内容并定位问题(禁止执行**/*类似搜索，使用明确的关键字进行检索)
                        使用read_file读取详细内容
                        使用edit_file修改文件内容（内容修改以行为单位，如果需要修改的内容较多分成多次修改，每次最多修改3行内容）
                        使用write_file创建并写入文件内容
                        使用ls查看指定目录的文件列表
                        禁止使用ls逐步探索目录
                        直接使用grep搜索文件内容
                        """.formatted(cwd, LocalDateTime.now().toString()))
                .interceptors(interceptors)
                .outputKey("agent_output")
                .returnReasoningContents(true)
                .build();
//        ReactAgent checkAgent = ReactAgent.builder()
//                .name("check_agent")
//                .model(chatModel)
//                .saver(new MemorySaver())
//                .description("任务检查智能体，负责检查当前用户任务是否已经完成")
//                .instruction("""
//                        你是任务检查智能体，负责检查当前用户任务是否已经完成。
//                        判断标准不用过于严谨，80%以上的完成度即可视为完成。
//                        完成任务后输出：FINISH
//                        未完成时输出：NOT_DONE
//                        """)
//                .outputKey("check_agent")
//                .build();
//        ReactAgent fallbackAgent = ReactAgent.builder()
//                .name("fallback_agent")
//                .model(chatModel)
//                .saver(new MemorySaver())
//                .description("后备智能体，负责处理其他Agent无法处理的输入或简单的问候")
//                .instruction("你是AI小助手可以帮助用户解答问题")
//                .outputKey("fallback_agent")
//                .build();
//        ReactAgent supervisor = ReactAgent.builder()
//                .name("content_supervisor")
//                .description("你是一个超级智能体，可以调用子智能体完成用户任务")
//                .systemPrompt(getSystemPrompt())
//                .saver(new MemorySaver())
//                .model(chatModel)
//                .tools(List.of(AgentTool.create(writerAgent), AgentTool.create(checkAgent), AgentTool.create(fallbackAgent)))
//                .build();
        return agent;
    }

    private String getSystemPrompt() {
        return """
                你是一个智能的内容管理监督者，负责协调和管理多个专业Agent来完成用户的内容处理需求。
                
                ## 你的职责
                1. 分析用户需求，将其分解为合适的子任务
                2. 根据任务特性，选择合适的Agent进行处理
                3. 监控任务执行状态，决定是否需要继续处理或完成任务
                4. 当所有任务完成时，调用check_agent对任务结果进行检查，如果检测通过 返回FINISH结束流程
                
                ## 可用的子Agent及其职责
                
                ### writer_agent
                - **功能**: 本地文件操作助手,可以将创作的内容写入本地文件
                - **适用场景**:
                  * 可以通过工具操作本地文件，例如创建，编辑，查找，读取，目录探索、文件
                  * 控制台命令执行 完成各种本地任务
                - **输出**: writer_output
                ### check_agent
                - **功能**： 负责检查当前用户任务是否已经完成
                  其他助手执行结束后通过该助手进行任务结果检查，判断用户任务是否已经完成
                ### fallback_agent
                - **功能**:后备智能体，处理简单问候或其他Agent无法处理的问题
                - **适用场景**:
                   处理其他Agent无法处理的问题 仅作为后备使用
                ## 决策规则
                    你需要根据用户问题、当前子智能体处理结果 判断当前用户问题/任务是否已回复/已解决/执行完成
                    - 如果已回复/已解决/执行完成 返回FINISH
                2. **任务完成判断**:
                   - 当用户的输入已回复/已解决/执行完成，返回FINISH
                   - 如果还有未完成的任务，继续分配合适的Agent进行处理
                ## 响应格式
                只返回Agent名称（writer_agent、check_agent、fallback_agent）或FINISH，不要包含其他解释。
                你的返回结果必须是以下枚举内容之一：writer_agent、check_agent、fallback_agent、FINISH 禁止输出其他任何信息
                """;
    }
}

