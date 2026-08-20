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

import com.agentclientprotocol.common.ClientSessionOperations;
import com.agentclientprotocol.model.McpServer;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.extension.file.LocalFilesystemBackend;
import com.alibaba.cloud.ai.graph.agent.extension.interceptor.LargeResultEvictionInterceptor;
import com.alibaba.cloud.ai.graph.agent.hook.Hook;
import com.alibaba.cloud.ai.graph.agent.hook.hip.HumanInTheLoopHook;
import com.alibaba.cloud.ai.graph.agent.hook.hip.ToolConfig;
import com.alibaba.cloud.ai.graph.agent.hook.skills.SkillsAgentHook;
import com.alibaba.cloud.ai.graph.agent.interceptor.Interceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.toolerror.ToolErrorInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.toolretry.ToolRetryInterceptor;
import com.alibaba.cloud.ai.graph.checkpoint.savers.file.FileSystemSaver;
import com.alibaba.cloud.ai.graph.serializer.plain_text.jackson.SpringAIJacksonStateSerializer;
import com.alibaba.cloud.ai.graph.skills.registry.filesystem.FileSystemSkillRegistry;
import com.xr21.ai.agent.acp.SessionConfigOptionsFactory;
import com.xr21.ai.agent.config.AiModels;
import com.xr21.ai.agent.interceptors.*;
import com.xr21.ai.agent.plugins.GroovyPluginLoader;
import com.xr21.ai.agent.plugins.GroovyPluginRegistry;
import com.xr21.ai.agent.plugins.PluginContext;
import com.xr21.ai.agent.tools.*;
import com.xr21.ai.agent.utils.AcpNotifyHelper;
import com.xr21.ai.agent.utils.DefaultTokenCounter;
import com.xr21.ai.agent.utils.Json;
import com.xr21.ai.agent.utils.ToolsUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.StaticToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.lang.NonNull;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.File;
import java.io.IOException;
import java.net.SocketException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

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
    public static final Path FILE_SYSTEM_SAVER_FOLDER = Path.of(System.getProperty("user.home"), ".agi_working", "SystemSaver");
    /**
     * 文件系统保存器实例，用于持久化智能体状态
     */
    public static final FileSystemSaver FILE_SYSTEM_SAVER = FileSystemSaver.builder().targetFolder(FILE_SYSTEM_SAVER_FOLDER).stateSerializer(new SpringAIJacksonStateSerializer(OverAllState::new)).build();

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
             - 灵活利用run_groovy_script工具进行并发工具调用/多步骤工具编排/worker编排/执行现有工具无法实现的操作
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
                 - 遵循极致的高内聚 低耦合原则
                 问问自己：“高级工程师会说这太复杂了吗？”如果是，那就简化。
             ## 3.原子更改遵循最小改动
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
             2. [步骤] → 验证：[检查]
             3. [步骤] → 验证：[检查]
             ```
             **这些指南有效条件是：** 
                 差异中不必要的更改减少，因过度复杂而减少重写，澄清问题应在实施前而非错误之后。   
            </编码指南>
            <self>
            ## 上下文管理（主动维护，不等用户提示）
            在当前工作目录 .agents/context/ 目录维护结构化项目上下文，按需读写、控制 token 开销。
            必须**主动、及时、自动**更新：每当任务阶段完成、里程碑达成、关键决策产生、状态变化时，
            立即同步更新对应文件——不要等用户提示或 /context。

            ### 目录结构（对象三件套：info=是什么 / state=怎么样 / milestones=演进）
            .agents/context/
            ├── index.md                 # 入口导航 + 场景读取顺序
            ├── base/                    # 静态基线（低频，建立后几乎不变）
            │   ├── project.md           # 项目画像：定位/技术栈/关键路径
            │   ├── architecture.md      # 架构总览：模块划分/依赖关系
            │   ├── conventions.md       # 编码约定：风格/命名/禁忌
            │   └── commands.md          # 命令速查：构建/测试/运行/Git
            ├── modules/[模块名]/        # 核心模块各一个目录
            │   └── info.md / state.md / milestones.md
            ├── state/                   # 动态状态（高频，每轮增量更新）
            │   ├── session.md           # 当前目标/进行中任务/下一步
            │   ├── todo.md              # 待办队列 P0/P1/P2 + 阻塞项
            │   └── risks.md             # 风险与假设
            └── history/                 # 演进历史（只追加，永不重写）
                ├── adr.md               # 架构决策记录（为什么）
                ├── learnings.md         # 经验教训（踩过的坑）
                └── session-summary/     # 会话摘要
                    └── YYYY-MM-DD.md    # 每次会话结束写一篇
            ### 读写策略
            - 会话启动：index.md → base/ 按需 → state/session.md 恢复上下文；任务未完成则续接
            - 操作中：只写 state/ 与对应模块 state.md；里程碑完成 → 更新 modules/*/milestones.md
            - 决策 → 追加 adr.md；会话结束 → 写 session-summary/[日期].md
            - base/ 变更须显式更新，禁止静默漂移；history/ 只追加不重写
            - 模块为最小粒度，不做类级拆分；代码文件不入上下文；只维护可复用知识

            ### 主动维护时机（自动触发，不等用户）
            - 每次产生持久结论后（新增/修改文件、架构变更、git 提交、里程碑达成）：顺手更新
              state/ 与对应模块 state.md/milestones.md，延迟不超过下一个回复
            - 关键决策 → 立即追加 adr.md；踩坑/经验 → 立即追加 learnings.md
            - 分支/提交/版本变化 → 更新 base/project.md「版本状态」
            - 会话结束或切换任务前 → 写 session-summary/[日期].md
            - 新会话且 .agents/context/ 不存在：探索 AGENTS.md/README.md/SKILL.md + ls 后初始化
            - 已存在且项目未变化：直接引用，跳过重建
            - 用户输入 /context：强制全量校验/更新/重建（补全部遗漏）
            - 保持轻量：单次只更新受影响的最小文件集，控制 token 开销
            </self>
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
     * @param client
     * @return 配置完成的智能体实例
     * @throws RuntimeException         如果智能体创建失败
     * @throws IllegalArgumentException 如果参数无效
     */
    public static ReactAgent createAgent(String cwd, @Nullable List<McpServer> mcpServers, RunnableConfig runnableConfig, @NotNull ClientSessionOperations client) {
        try {
            if (!StringUtils.isNotBlank(cwd)) {
                String tempDir = System.getProperty("java.io.tmpdir");
                System.out.println("系统临时目录: " + tempDir);
                cwd = tempDir + File.separator + "cwd_" + System.currentTimeMillis();
                log.error("create agent with cwd tmpdir: {} ", cwd);
            }
            WORKSPACE_ROOT = cwd;
            return buildAgent(cwd, mcpServers, runnableConfig, client);
        } catch (Exception e) {
            log.error("Failed to create agent with cwd: {}, mcpServers: {}", cwd, mcpServers != null ? mcpServers.size() : 0, e);
            throw new RuntimeException("Failed to create LocalAgent", e);
        }
    }

    private static StaticToolCallbackProvider staticToolCallbackProvider(List<McpServer> mcpServers, List<ToolCallback> interceptorTools) {
        var toolCallbackProvider = MethodToolCallbackProvider.builder().toolObjects(ShellTools.builder().build(), new WebTool(), new SleepTool(), new ConversationCompactionTool()).build();
        List<ToolCallback> tools = new ArrayList<>(List.of(toolCallbackProvider.getToolCallbacks()));
        log.debug("Loaded {} base tools", tools.size());
        // 添加 MCP 工具
        if (!CollectionUtils.isEmpty(mcpServers)) {
            List<ToolCallback> mcpTools = ToolsUtil.getMcpTools(mcpServers);
            tools.addAll(mcpTools);
            log.info("Added {} MCP tools from {} servers", mcpTools.size(), mcpServers.size());
        }
        // 将拦截器提供的文件系统工具（ls/read_file/write_file 等）与 write_todos 工具一并暴露给 Groovy 脚本绑定
        if (interceptorTools != null && !interceptorTools.isEmpty()) {
            tools.addAll(interceptorTools);
            log.info("Added {} interceptor tools to Groovy script bindings", interceptorTools.size());
        }
        // 插件工具并入（loader 已在 buildAgent 中以完整 PluginContext 触发；此处幂等并入已注册插件工具）
        List<ToolCallback> pluginTools = GroovyPluginRegistry.get().toolCallbacks();
        tools.addAll(pluginTools);
        log.info("Loaded {} plugin tools", pluginTools.size());
        // Groovy 脚本工具：脚本内绑定 tools 对象，可调用以上全部工具实现 MCP 工具编排
        GroovyScriptTool groovyScriptTool = new GroovyScriptTool(tools);
        ToolCallback groovyCallback = MethodToolCallbackProvider.builder().toolObjects(groovyScriptTool).build().getToolCallbacks()[0];
        tools.add(groovyCallback);
        return new StaticToolCallbackProvider(tools);
    }

    private static @NonNull List<Interceptor> getInterceptors(RunnableConfig runnableConfig, ChatModel chatModel,@NotNull ClientSessionOperations client) {
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
        var toolCallbackProvider = MethodToolCallbackProvider.builder().toolObjects(ShellTools.builder().build(), new WebTool(), new SleepTool(), new ConversationCompactionTool()).build();
        List<ToolCallback> tools = new ArrayList<>(List.of(toolCallbackProvider.getToolCallbacks()));
        // Groovy 脚本工具：脚本内绑定 tools 对象，可调用以上全部工具实现 MCP 工具编排
        GroovyScriptTool groovyScriptTool = new GroovyScriptTool(tools);
        ToolCallback groovyCallback = MethodToolCallbackProvider.builder().toolObjects(groovyScriptTool).build().getToolCallbacks()[0];
        tools.add(groovyCallback);
        tools.addAll(filesystemInterceptor.getTools());
        log.debug("Loaded {} base tools", tools.size());
        WorkerInterceptor workerInterceptor = WorkerInterceptor.builder().defaultModel(chatModel).defaultTools(tools)
                .includeGeneralPurpose(true)  // 同时包含通用Worker
                .build();
        ModelRetryInterceptor retryInterceptor = ModelRetryInterceptor.builder()
                .maxAttempts(3)              // 总尝试次数 3（即最多重试 2··· 次）
                .initialDelay(200)           // 首次重试延迟 200ms
                .maxDelay(4000)              // 最大延迟 4s
                .retryableExceptionPredicate((e) -> {
                    // 5xx + 网络/连接异常 + 限流（429）才值得重试
                    if (e instanceof RestClientResponseException restClientException) {
                        var status = restClientException.getStatusCode();
                        return status.is5xxServerError() || status.value() == 429;
                    }
                    if (e instanceof WebClientResponseException webClientException) {
                        var status = webClientException.getStatusCode();
                        return status.is5xxServerError() || status.value() == 429;
                    }
                    // 连接超时、IO 异常等暂时性网络错误也应重试
                    return e instanceof IOException || e.getCause() instanceof SocketException;
                })
                .backoffMultiplier(2.0)      // 指数退避倍数
                .build();
        List<Interceptor> interceptors = new ArrayList<>();
//        interceptors.add(contextEditingInterceptor);
        interceptors.add(largeResultEvictionInterceptor);
        interceptors.add(toolRetryInterceptor);
        interceptors.add(filesystemInterceptor);
        interceptors.add(workerInterceptor);
        interceptors.add(retryInterceptor);
        interceptors.add(new ToolErrorInterceptor());
        interceptors.add(AcpTodoListInterceptor.builder().build());
        // 路线 B：运行时热挂载 —— 每轮模型调用前注入 registry 当前插件工具（dynamicToolCallbacks）
        interceptors.add(new PluginDynamicToolsInterceptor());
        log.info("Agent mode: {}, filesystem readOnly: {}", currentMode, readOnly);
        AcpNotifyHelper.sendThoughtChunk(client, "Use Mode : " + currentMode);
        for (Interceptor interceptor : interceptors) {
            AcpNotifyHelper.sendThoughtChunk(client, "Use Interceptor : " + interceptor.getName());
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
    public static ReactAgent buildAgent(String cwd, List<McpServer> mcpServers, RunnableConfig runnableConfig, @NotNull ClientSessionOperations client) {
        if (cwd == null || cwd.trim().isEmpty()) {
            throw new IllegalArgumentException("Workspace directory (cwd) cannot be null or empty");
        }
        AcpNotifyHelper.sendThoughtChunk(client, "当前工作目录 :" + cwd);
        log.info("Building LocalAgent for workspace: {}", cwd);
        log.info("Building LocalAgent for context: {}", runnableConfig.context());

        ChatModel chatModel = getChatModel(runnableConfig);
        AcpNotifyHelper.sendThoughtChunk(client, "Use model : " + chatModel.getDefaultOptions().getModel());
        List<Interceptor> interceptors = new ArrayList<>(getInterceptors(runnableConfig, chatModel,client));
        // 收集拦截器提供的文件系统工具与 write_todos 工具，供 Groovy 脚本绑定调用
        List<ToolCallback> interceptorTools = new ArrayList<>();
        for (Interceptor interceptor : interceptors) {
            if (interceptor instanceof FilesystemInterceptor fs) {
                interceptorTools.addAll(fs.getTools());
            } else if (interceptor instanceof AcpTodoListInterceptor todo) {
                interceptorTools.addAll(todo.getTools());
            } else if (interceptor instanceof WorkerInterceptor worker) {
                interceptorTools.addAll(worker.getTools());
            }
        }
        List<Hook> hooks = getHooks(runnableConfig, chatModel);
        for (Hook hook : hooks) {
            AcpNotifyHelper.sendThoughtChunk(client, "Use Hook : " +hook.getName());
        }
        // 使用 PromptTemplate 渲染指令
        var instruction = getInstruction(WORKSPACE_ROOT);
        var chatOptions = OpenAiChatOptions.builder().streamUsage(true);
        String thoughtLevel = SessionConfigOptionsFactory.ThoughtLevel.LOW.getValueId();
        if (runnableConfig.context().get("thought_level") instanceof String level) {
            log.info("thought_level: {}", level);
            thoughtLevel = level;
        }
        AcpNotifyHelper.sendThoughtChunk(client, "Use thought_level : " + thoughtLevel);
        if (SessionConfigOptionsFactory.ThoughtLevel.DISABLED.getValueId().equals(thoughtLevel)) {
            chatOptions.extraBody(Map.of("thinking", Map.of("type", "disabled")));
        } else {
            chatOptions.extraBody(Map.of("thinking", Map.of("type", "enabled")));
            chatOptions.reasoningEffort(thoughtLevel);
        }
        // Groovy 插件加载（阶段二）：以完整 PluginContext（client/chatModel）触发，随后并入插件工具
        PluginContext pluginCtx = PluginContext.builder()
                .toolContext(null)
                .client(client)
                .chatModel(chatModel)
                .hostTools(interceptorTools)
                .build();
        GroovyPluginLoader.loadAll(interceptorTools, WORKSPACE_ROOT, pluginCtx);
        var staticToolCallbackProvider = staticToolCallbackProvider(mcpServers, interceptorTools);
        var tools = List.of(staticToolCallbackProvider.getToolCallbacks());
        // 插件 hooks / interceptors 并入（默认追加到内置之后）
        hooks.addAll(GroovyPluginRegistry.get().hooks());
        interceptors.addAll(GroovyPluginRegistry.get().interceptors());
        AcpNotifyHelper.sendThoughtChunk(client, "Use tools : " + tools.stream().map(ToolCallback::getToolDefinition).map(ToolDefinition::name).distinct().collect(Collectors.joining(",")));
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

    public static String getInstruction(String workspace) {
        Locale locale = Locale.getDefault();
        String displayName = locale.getDisplayLanguage();
        return PromptTemplate.builder().template(SYSTEM_PROMPT_TEMPLATE).variables(Map.of("cwd", workspace, "osName", System.getProperty("os.name").toLowerCase(),
                "language", displayName,
                "lineSeparator", System.lineSeparator().replace("\r", "\\r").replace("\n", "\\n"))).build().render();
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
            log.info("{} mode: Bash commands require human approval", currentMode);
        } else {
            log.info("YOLO mode: all operations auto-approved");
        }
        hooks.add(SkillsAgentHook.builder()
                .skillRegistry(FileSystemSkillRegistry.builder()
                        .userSkillsDirectory(Path.of(System.getProperty("user.home"), ".agents", "skills").toAbsolutePath().toString())
                        .projectSkillsDirectory(Path.of(WORKSPACE_ROOT, ".agents", "skills").toAbsolutePath().toString())
                        .autoLoad(true)
                        .build())
                .autoReload(true)
                .build());
        // 上下文 token 达到阈值时，注入引导消息促使模型主动调用 compact_conversation 工具压缩会话
        hooks.add(CompactionPromptHook.builder()
                .maxTokensBeforePrompt(256 * 1024)
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


