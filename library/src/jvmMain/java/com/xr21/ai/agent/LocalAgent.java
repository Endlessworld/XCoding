package com.xr21.ai.agent;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.cloud.ai.graph.agent.AgentTool;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.extension.file.LocalFilesystemBackend;
import com.alibaba.cloud.ai.graph.agent.extension.interceptor.LargeResultEvictionInterceptor;
import com.alibaba.cloud.ai.graph.agent.hook.hip.HumanInTheLoopHook;
import com.alibaba.cloud.ai.graph.agent.hook.hip.ToolConfig;
import com.alibaba.cloud.ai.graph.agent.interceptor.Interceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.toolerror.ToolErrorInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.toolretry.ToolRetryInterceptor;
import com.alibaba.cloud.ai.graph.agent.tools.ShellTool;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.xr21.ai.agent.config.AiModels;
import com.xr21.ai.agent.entity.AgentOutput;
import com.xr21.ai.agent.interceptors.ContextEditingInterceptor;
import com.xr21.ai.agent.interceptors.FilesystemInterceptor;
import com.xr21.ai.agent.session.ConversationSessionManager;
import com.xr21.ai.agent.tools.DefaultTokenCounter;
import com.xr21.ai.agent.tools.FeedBackTool;
import com.xr21.ai.agent.tools.WebSearchTool;
import com.xr21.ai.agent.utils.Json;
import com.xr21.ai.agent.utils.SinksUtil;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 本地智能体
 */
public class LocalAgent {

    protected static final List<ChatModel> fallbackModels = new ArrayList<>();
    public static final String WORKSPACE_ROOT = "D:\\local-github\\ai-agents";
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(LocalAgent.class);
    public ChatModel chatModel = AiModels.MINIMAX_M2_1.createChatModel();
    protected ConversationSessionManager sessionManager;

    public static void main(String[] args) {
        LocalAgent localAgent = new LocalAgent();
        localAgent.chatModel = AiModels.MINIMAX_M2_1.createChatModel();
        var agent = localAgent.buildSupervisorAgent();
        localAgent.startInteractiveSession(agent);
    }

    private static ArrayList<ToolCallback> getTools() {
        List<ToolCallback> mcpTools = getMcpTools();
        List<String> includes = List.of("grep", "glob", "edit_file", "write_file", "read_file", "ls", "execute_terminal_command");//"execute_terminal_command"
        var tools = new ArrayList<>(mcpTools.stream()
                .filter(toolCallback -> includes.contains(toolCallback.getToolDefinition().name()))
                .toList());
        var filesystemInterceptor = FilesystemInterceptor.builder().build();
        tools.addAll(filesystemInterceptor.getTools()
                .stream()
                .filter(toolCallback -> includes.contains(toolCallback.getToolDefinition().name()))
                .toList());
        tools.add(FeedBackTool.build("feed_back_tool", new FeedBackTool()));
        tools.add(WebSearchTool.createWebSearchToolCallback());
        return tools;
    }

    private static @NonNull List<ToolCallback> getMcpTools() {
        ServerParameters serverParameters = createMcpServerParameters();
        StdioClientTransport stdioClientTransport = new StdioClientTransport(serverParameters, McpJsonMapper.getDefault());
        McpSyncClient mcpSyncClient = McpClient.sync(stdioClientTransport).build();
        return List.of();
//        return McpToolUtils.getToolCallbacksFromSyncClients(mcpSyncClient);
    }

    /**
     * 创建MCP服务器参数
     */
    private static ServerParameters createMcpServerParameters() {
        String javaPath = "D:\\JetBrains\\IntelliJ IDEA 2025.1.3\\jbr\\bin\\java";
        String classpath = """
                C:\\Users\\Endless\\AppData\\Roaming\\JetBrains\\IntelliJIdea2025.3\\plugins\\mcpserver\\lib\\mcpserver.jar;\
                C:\\Users\\Endless\\AppData\\Roaming\\JetBrains\\IntelliJIdea2025.3\\plugins\\mcpserver\\lib\\io.modelcontextprotocol.kotlin.sdk.jar;\
                C:\\Users\\Endless\\AppData\\Roaming\\JetBrains\\IntelliJIdea2025.3\\plugins\\mcpserver\\lib\\io.github.oshai.kotlin.logging.jvm.jar;\
                D:\\JetBrains\\IntelliJ IDEA 2025.1.3\\lib\\util-8.jar;\
                D:\\JetBrains\\IntelliJ IDEA 2025.1.3\\lib\\module-intellij.libraries.ktor.client.cio.jar;\
                D:\\JetBrains\\IntelliJ IDEA 2025.1.3\\lib\\module-intellij.libraries.ktor.client.jar;\
                D:\\JetBrains\\IntelliJ IDEA 2025.1.3\\lib\\module-intellij.libraries.ktor.network.tls.jar;\
                D:\\JetBrains\\IntelliJ IDEA 2025.1.3\\lib\\module-intellij.libraries.ktor.io.jar;\
                D:\\JetBrains\\IntelliJ IDEA 2025.1.3\\lib\\module-intellij.libraries.ktor.utils.jar;\
                D:\\JetBrains\\IntelliJ IDEA 2025.1.3\\lib\\module-intellij.libraries.kotlinx.io.jar;\
                D:\\JetBrains\\IntelliJ IDEA 2025.1.3\\lib\\module-intellij.libraries.kotlinx.serialization.core.jar;\
                D:\\JetBrains\\IntelliJ IDEA 2025.1.3\\lib\\module-intellij.libraries.kotlinx.serialization.json.jar\
                """;

        return ServerParameters.builder(javaPath)
                .addEnvVar("IJ_MCP_SERVER_PORT", "64342")
                .arg("-classpath")
                .arg(classpath)
                .arg("com.intellij.mcpserver.stdio.McpStdioRunnerKt")
                .build();
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
     * 处理特殊命令
     *
     * @param command 用户输入的命令
     * @param history 对话历史
     * @return 如果是特殊命令返回true，否则返回false
     */
    private boolean handleSpecialCommands(String command, List<String> history, Agent agent, String threadId) {
        if (command.equalsIgnoreCase("exit") || command.equalsIgnoreCase("quit")) {
            System.out.println("感谢使用，再见！");
            System.exit(0);
            return true;
        } else if (command.equalsIgnoreCase("history")) {
            System.out.println("\n=== 对话历史 ===");
            for (int i = 0; i < history.size(); i++) {
                System.out.printf("%d. %s%n", i + 1, history.get(i));
            }
            System.out.println("==============\n");
            return true;
        } else if (command.equalsIgnoreCase("clear")) {
            history.clear();
            System.out.println("\n[系统提示] 对话历史已清空\n");
            return true;
        } else if (command.isEmpty()) {
            return true;
        } else if (command.startsWith("/")) {
            // 处理斜杠命令
            return handleSlashCommands(command, agent, threadId, history);
        }
        return false;
    }

    private void showHelp() {
        String helpContent = """
                \n=== 可用命令 ===
                /help - 显示帮助信息
                /feedback <message> - 发送反馈
                /save [filename] - 保存对话历史到文件
                /load [filename] - 从文件加载对话历史
                exit/quit - 退出程序
                history - 查看对话历史
                clear - 清空对话历史
                ================
                
                """;
        System.out.print(helpContent);
    }

    @SneakyThrows
    private void saveConversation(List<String> history, String filename) {
        if (filename.isEmpty()) {
            filename = "conversation_" + System.currentTimeMillis() + ".txt";
        }

        try (java.io.PrintWriter out = new java.io.PrintWriter(filename)) {
            for (String line : history) {
                out.println(line);
            }
            System.out.printf("\n[系统提示] 对话已保存到文件: %s\n\n", filename);
        } catch (Exception e) {
            System.err.printf("\n[错误] 保存对话失败: %s\n\n", e.getMessage());
        }
    }

    @SneakyThrows
    private void loadConversation(List<String> history, String filename) {
        if (filename.isEmpty()) {
            System.out.println("\n[错误] 请指定要加载的文件名\n");
            return;
        }

        try (Scanner scanner = new Scanner(new java.io.File(filename))) {
            history.clear();
            while (scanner.hasNextLine()) {
                history.add(scanner.nextLine());
            }
            System.out.printf("\n[系统提示] 已从文件加载对话: %s\n\n", filename);
            System.out.println("=== 加载的对话历史 ===");
            for (int i = 0; i < Math.min(5, history.size()); i++) {
                String displayLine = history.get(i).length() > 50 ? history.get(i)
                        .substring(0, 50) + "..." : history.get(i);
                System.out.printf("%d. %s%n", i + 1, displayLine);
            }
            if (history.size() > 5) {
                System.out.printf("... 以及 %d 条更多记录\n", history.size() - 5);
            }
            System.out.println("====================\n");
        } catch (Exception e) {
            System.err.printf("\n[错误] 加载对话失败: %s\n\n", e.getMessage());
        }
    }

    /**
     * 处理斜杠命令
     */
    private boolean handleSlashCommands(String command, Agent agent, String threadId, List<String> history) {
        String[] parts = command.substring(1).split("\\s+", 2);
        String cmd = parts[0].toLowerCase();
        String arg = parts.length > 1 ? parts[1] : "";

        switch (cmd) {
            case "help":
                showHelp();
                break;
            case "save":
                saveConversation(history, arg);
                break;
            case "load":
                loadConversation(history, arg);
                break;
            case "feedback":
                // 发送反馈给模型
                if (!arg.isEmpty()) {
                    processWithGraphV2(agent, "用户反馈: " + arg, threadId, null, new HashMap<>()).blockLast();
                    history.add("用户反馈: " + arg);
                    System.out.println("\n[系统提示] 反馈已发送\n");
                } else {
                    System.out.println("\n[错误] 请提供反馈内容，例如: /feedback 这里输入您的反馈\n");
                }
                break;
            default:
                System.out.printf("\n[错误] 未知命令: %s。输入 /help 查看可用命令\n\n", cmd);
                return false;
        }
        return true;
    }

    public Agent buildSupervisorAgent() {
        var tools = getTools();
        List<Interceptor> interceptors = getInterceptors();
        Map<String, ToolConfig> approvalOn = Map.of("feed_back_tool", ToolConfig.builder()
                .description("请确认信息收集工具执行")
                .build());
        ReactAgent writerAgent = ReactAgent.builder()
                .name("writer_agent")
                .model(chatModel)
                .tools(tools)
                .saver(new MemorySaver())
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
                        """.formatted(WORKSPACE_ROOT, LocalDateTime.now().toString()))
                .interceptors(interceptors)
                .outputKey("writer_output")
                .build();
        ReactAgent checkAgent = ReactAgent.builder()
                .name("check_agent")
                .model(chatModel)
                .saver(new MemorySaver())
                .description("任务检查智能体，负责检查当前用户任务是否已经完成")
                .instruction("""
                        你是任务检查智能体，负责检查当前用户任务是否已经完成。
                        判断标准不用过于严谨，80%以上的完成度即可视为完成。
                        完成任务后输出：FINISH
                        未完成时输出：NOT_DONE
                        """)
                .outputKey("check_agent")
                .build();
        ReactAgent fallbackAgent = ReactAgent.builder()
                .name("fallback_agent")
                .model(chatModel)
                .saver(new MemorySaver())
                .description("后备智能体，负责处理其他Agent无法处理的输入或简单的问候")
                .instruction("你是AI小助手可以帮助用户解答问题")
                .outputKey("fallback_agent")
                .build();
        var supervisor = ReactAgent.builder()
                .name("content_supervisor")
                .description("你是一个超级智能体，可以调用子智能体完成用户任务")
                .systemPrompt(getSystemPrompt())
                .saver(new MemorySaver())
                .model(chatModel)
                .tools(List.of(AgentTool.create(writerAgent), AgentTool.create(checkAgent), AgentTool.create(fallbackAgent)))
                .build();
        return writerAgent;
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
                  * 控制台命令执行 完成各种本地任务 (工作目录 D:/local-github/chinaunicom-standard-ai-agents)
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

    public Flux<ServerSentEvent<AgentOutput<Object>>> processWithGraphV2(Agent agent, String input, String threadId, InterruptionMetadata feedbackMetadata, Map<String, Object> stateUpdate) {
        var builder = RunnableConfig.builder().threadId(threadId);
        if (feedbackMetadata != null) {
            builder.addMetadata(RunnableConfig.HUMAN_FEEDBACK_METADATA_KEY, feedbackMetadata);
        }
        if (stateUpdate != null && !stateUpdate.isEmpty()) {
            builder.addStateUpdate(stateUpdate);
        }
        RunnableConfig runnableConfig = builder.build();
        Flux<NodeOutput> nodeOutputFlux = null;
        try {
            nodeOutputFlux = agent.stream(input, runnableConfig);
        } catch (GraphRunnerException e) {
            try {
                nodeOutputFlux = agent.stream(input, runnableConfig);
            } catch (GraphRunnerException ex) {
                throw new RuntimeException(ex);
            }
        }
        return SinksUtil.sinksOutput(nodeOutputFlux);
    }

    private Thread createInputThread(AtomicReference<String> userInputRef) {
        Thread thread = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Scanner scanner = new Scanner(System.in);
                    String input = scanner.nextLine().trim();
                    userInputRef.set(input);
                }
            } catch (Exception e) {
                // 线程被中断或其他异常，检查是否需要重新启动
                if (!Thread.currentThread().isInterrupted()) {
                    // 非中断异常，短暂等待后重启线程
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        });
        thread.setDaemon(true);
        return thread;
    }

    /**
     * 选择或创建会话
     *
     * @param scanner 扫描器
     * @return 会话ID
     */
    @SneakyThrows
    private String selectOrCreateSession(Scanner scanner) {
        // 获取所有会话列表
        var sessionInfoList = sessionManager.getSessionInfoList();
        String sessionId;

        if (sessionInfoList.isEmpty()) {
            // 没有会话记录，直接创建新会话
            sessionId = "local-agent-session-" + System.currentTimeMillis();
            sessionManager.getOrCreateSession(sessionId);
            System.out.println("\n[系统提示] 未找到历史会话，将创建新会话");
        } else {
            // 列出所有会话供用户选择
            System.out.println("\n" + "=".repeat(70));
            System.out.println("                            会话选择");
            System.out.println("=".repeat(70));
            System.out.println("  编号  | 会话ID                    | 消息数 | 创建时间          | 简要描述");
            System.out.println("-".repeat(70));

            // 显示所有会话（最多显示10个）
            int displayCount = Math.min(sessionInfoList.size(), 10);
            for (int i = 0; i < displayCount; i++) {
                var info = sessionInfoList.get(i);
                String briefDesc = info.getBriefDescription().length() > 20 ? info.getBriefDescription()
                        .substring(0, 20) + "..." : info.getBriefDescription();
                System.out.printf("  %-5d | %-24s | %-6d | %-16s | %s%n", (i + 1), info.getSessionId(), info.getMessageCount(), info.getCreatedAt(), briefDesc);
            }

            if (sessionInfoList.size() > 10) {
                System.out.printf("  ... 以及 %d 个更早的会话%n", sessionInfoList.size() - 10);
            }

            System.out.println("-".repeat(70));
            System.out.println("  0     | 创建新会话");
            System.out.println("=".repeat(70));

            // 获取用户选择
            int selectedIndex = -1;
            while (selectedIndex < 0 || selectedIndex > sessionInfoList.size()) {
                System.out.print("\n请选择会话编号 (0-" + Math.min(sessionInfoList.size(), 10) + "): ");
                try {
                    String input = scanner.nextLine().trim();
                    if (input.isEmpty()) {
                        // 默认选择第一个（最新的）会话
                        selectedIndex = 1;
                        break;
                    }
                    selectedIndex = Integer.parseInt(input);
                } catch (NumberFormatException e) {
                    System.out.print("无效输入，请输入数字: ");
                }
            }

            if (selectedIndex == 0 || selectedIndex > sessionInfoList.size()) {
                // 创建新会话
                sessionId = "local-agent-session-" + System.currentTimeMillis();
                sessionManager.getOrCreateSession(sessionId);
                System.out.println("\n[系统提示] 将创建新会话");
            } else {
                // 加载选中的会话
                var selectedSession = sessionInfoList.get(selectedIndex - 1);
                sessionId = selectedSession.getSessionId();
                boolean loaded = sessionManager.loadSessionById(sessionId);
                if (loaded) {
                    System.out.printf("\n[系统提示] 已加载会话: %s (%d 条消息)%n", sessionId, selectedSession.getMessageCount());
                } else {
                    System.out.println("\n[系统提示] 会话加载失败，将创建新会话");
                    sessionId = "local-agent-session-" + System.currentTimeMillis();
                    sessionManager.getOrCreateSession(sessionId);
                }
            }
        }

        System.out.println("会话ID: " + sessionId);
        return sessionId;
    }

    @SneakyThrows
    private void startInteractiveSession(Agent agent) {
        String threadId = "local-agent-session-" + System.currentTimeMillis();
        Scanner scanner = new Scanner(System.in);

        // 初始化会话管理器
        sessionManager = new ConversationSessionManager();
        sessionManager.init();

        // 加载或创建会话
        String sessionId = sessionManager.getOrCreateSession(threadId);
        System.out.println("会话ID: " + sessionId);

        System.out.println("""
                ========== 本地文件操作智能体已启动 ==========
                输入 'exit' 或 'quit' 退出
                输入 'history' 查看对话历史
                输入 'clear' 清空对话历史
                输入 '/help' 查看所有命令
                示例 @src\\test\\resources\\提示词-小说创作助手.md 帮我写个东方玄幻修仙小说，修炼体系参照凡人修仙传 主角自定义、爽文风格
                示例 帮我查找并修复分段导入接口的bug
                示例 详细解释ConversationPlanFlowFactory实现原理具体到每个节点
                示例 优化LocalAgent代码
                示例 智能体发布增加增加版本重复校验
                =============================================
                """);

        AtomicReference<InterruptionMetadata> interruptionMetadata = new AtomicReference<>();
        Map<String, Object> stateUpdate = new HashMap<>();
        StringBuilder assistantResponseBuilder = new StringBuilder();

        while (true) {
            try {
                System.out.print("\n您: ");
                String userInput = scanner.nextLine().trim();
                // 处理特殊命令
                if (handleSpecialCommands(userInput, null, agent, sessionId)) {
                    continue;
                }

                // 记录用户消息
                sessionManager.addUserMessage(sessionId, userInput);

                // 处理对话
                System.out.println("\n助手: ");
                assistantResponseBuilder.setLength(0); // 清空助手响应构建器
                String finalSessionId = sessionId;
                processWithGraphV2(agent, userInput, sessionId, interruptionMetadata.get(), stateUpdate).doOnNext(output -> {
                    if (output.data().getChunk() != null) {
                        String chunk = output.data().getChunk();
                        System.out.print(chunk);
                        System.out.flush();
                        assistantResponseBuilder.append(chunk);
                    }

                    // 处理工具反馈
                    if (!CollectionUtils.isEmpty(output.data().getToolFeedbacks())) {
                        for (InterruptionMetadata.ToolFeedback toolFeedback : output.data().getToolFeedbacks()) {
                            String feedbackMsg = String.format("\n[系统提示] %s: %s", toolFeedback.getName(), toolFeedback.getDescription());
                            System.err.println(feedbackMsg);
                            InterruptionMetadata.Builder newBuilder = InterruptionMetadata.builder()
                                    .nodeId(output.data().getNode())
                                    .state(new OverAllState(output.data().getData()));
                            InterruptionMetadata.ToolFeedback approvedFeedback = InterruptionMetadata.ToolFeedback.builder(toolFeedback)
                                    .description(toolFeedback.getDescription())
                                    .result(InterruptionMetadata.ToolFeedback.FeedbackResult.APPROVED)
                                    .build();
                            newBuilder.addToolFeedback(approvedFeedback);
                            interruptionMetadata.set(newBuilder.build());
                            // 记录系统消息
                            sessionManager.addSystemMessage(finalSessionId, feedbackMsg.trim());
                        }
                    }

                    // 处理工具调用
                    if (output.data().getMessage() instanceof AssistantMessage message) {
                        if (!CollectionUtils.isEmpty(message.getToolCalls())) {
                            for (AssistantMessage.ToolCall toolCall : message.getToolCalls()) {
                                System.err.print("\n[工具调用]:" + toolCall.name());
                                try {
                                    String arguments = toolCall.arguments();
                                    String displayArgs = (arguments != null && !arguments.trim()
                                            .isEmpty()) ? arguments : "无参数";
                                    System.err.print(" 参数: " + displayArgs + "\n");
                                    // 记录工具调用
                                    Map<String, Object> argumentsMap = new HashMap<>();
                                    if (arguments != null && !arguments.trim().isEmpty()) {
                                        try {
                                            argumentsMap = Json.to(arguments, Map.class);
                                        } catch (Exception jsonException) {
                                            log.warn("解析工具调用参数失败: {}", jsonException.getMessage());
                                            // 使用原始字符串作为参数
                                            argumentsMap.put("raw_arguments", arguments);
                                        }
                                    }
                                    sessionManager.addToolCallMessage(finalSessionId, toolCall.name(), argumentsMap, toolCall.id());
                                } catch (Exception e) {
                                    System.err.print(" 参数: [参数处理失败]");
                                }
                            }
                        }
                    }
                    // 处理工具调用响应
                    if (output.data().getMessage() instanceof ToolResponseMessage message) {
                        if (!CollectionUtils.isEmpty(message.getResponses())) {
                            for (var response : message.getResponses()) {
                                String responseStr = response.responseData() != null ? response.responseData() : "执行成功";
                                System.err.print(" ✅ 执行成功！\n");
                                System.err.flush();
                                // 记录工具响应
                                sessionManager.addToolResponseMessage(finalSessionId, response.name(), responseStr, true);
                            }
                        }
                    }
                }).doOnComplete(() -> {
                    // 保存助手响应
                    if (assistantResponseBuilder.length() > 0) {
                        sessionManager.addAssistantMessage(finalSessionId, assistantResponseBuilder.toString());
                    }
                    System.out.println("[本轮对话结束]");
                    System.out.println("[会话已自动保存]");
                }).doOnError(error -> {
                    String errorMsg = "\n[发生错误]: " + error.getMessage();
                    System.err.println(errorMsg);
                    // 记录错误消息
                    sessionManager.addErrorMessage(finalSessionId, error.getMessage());
                }).blockLast();
            } catch (Exception e) {
                String errorMsg = "\n[处理过程中发生未捕获的异常]: " + e.getMessage();
                System.err.println(errorMsg);
                // 记录错误消息
                sessionManager.addErrorMessage(sessionId, e.getMessage());
                // 继续循环，允许用户继续输入
                stateUpdate.put("err", e);
            }
        }
    }

}

