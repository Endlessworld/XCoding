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
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.xr21.ai.agent.config.ModelConfigLoader;
import com.xr21.ai.agent.model.Config.ModelConfig;
import com.xr21.ai.agent.tools.ShellTools;
import com.xr21.ai.agent.tools.WebTool;
import com.xr21.ai.agent.utils.ToolsUtil;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.state.JsonFileAgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpSyncClientWrapper;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.filesystem.spec.LocalFilesystemSpec;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.agentscope.harness.agent.workspace.LocalFsMode;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.tool.StaticToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * HarnessCodingAgent 类负责创建和配置本地文件操作编码智能体。
 * <p>
 * 该类提供了创建智能体的工厂方法，配置了文件操作工具、Shell执行、MCP工具等组件。
 * 基于 AgentScope HarnessAgent 实现，提供本地文件系统访问和命令执行能力。
 * </p>
 *
 * @author Endless
 * @version 2.0
 */
@SuppressWarnings("unused")
@Slf4j
public class HarnessCodingAgent {

    /**
     * 默认工作空间根目录
     */
    public static final String DEFAULT_WORKSPACE_ROOT = Path.of(System.getProperty("user.home"), ".agi_working", "workspace", System.currentTimeMillis() + "").toAbsolutePath().toString();

    /**
     * 当前工作空间根目录，可在运行时更新
     */
    public static String WORKSPACE_ROOT = DEFAULT_WORKSPACE_ROOT;

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
            - "添加验证"→"为无效输入写测试，然后使其通过"
            - "修复漏洞"→"编写一个复现该漏洞的测试，然后使其通过"
            - "重构X"→"确保测试在之前和之后通过"
            对于多步骤任务，请提出简要计划：
            ```
            1. [步骤] → 验证：[检查]
            2. [步骤] → 验证：[检查]
            3. [步骤] → 验证：[检查]
            ```
            **这些指南有效条件是：**
                差异中不必要的更改减少，因过度复杂而减少重写，澄清问题应在实施前而非错误之后。
           </编码指南>
           """;

    /**
     * 默认状态存储目录
     */
    private static final Path STATE_STORE_DIR = Path.of(System.getProperty("user.home"), ".agi_working", "agent_state");

    /**
     * Agent 状态存储（JSON文件后端）
     */
    public static final JsonFileAgentStateStore AGENT_STATE_STORE =
            new JsonFileAgentStateStore(STATE_STORE_DIR);

    /**
     * 创建本地编码智能体。
     * <p>
     * 使用 AgentScope HarnessAgent 构建，配置本地文件系统、Shell执行、MCP工具等。
     * </p>
     *
     * @param cwd            工作目录路径
     * @param mcpServers     MCP服务器列表
     * @param runnableConfig 运行配置（包含模型选择和模式）
     * @return 配置完成的 HarnessAgent 实例
     * @throws RuntimeException 如果智能体创建失败
     */
    public static HarnessAgent createAgent(String cwd, List<McpServer> mcpServers, RunnableConfig runnableConfig) {
        try {
            if (!StringUtils.isNotBlank(cwd)) {
                String tempDir = System.getProperty("java.io.tmpdir");
                cwd = tempDir + File.separator + "cwd_" + System.currentTimeMillis();
                log.error("create agent with cwd tmpdir: {} ", cwd);
            }
            WORKSPACE_ROOT = cwd;
            return buildAgent(cwd, mcpServers, runnableConfig);
        } catch (Exception e) {
            log.error("Failed to create agent with cwd: {}, mcpServers: {}", cwd, mcpServers != null ? mcpServers.size() : 0, e);
            throw new RuntimeException("Failed to create HarnessCodingAgent", e);
        }
    }

    /**
     * 构建智能体的核心方法。
     * <p>
     * 使用 AgentScope HarnessAgent.Builder 配置所有组件。
     * HarnessAgent 自动注册：文件系统工具(local/read_file/glob/grep/write_file/smart_edit)、
     * Shell执行工具、技能加载、上下文压缩、工具结果清理等。
     * </p>
     *
     * @param cwd            工作目录路径（作为 project root）
     * @param mcpServers     MCP服务器列表
     * @param runnableConfig 运行配置
     * @return 构建完成的 HarnessAgent
     */
    public static HarnessAgent buildAgent(String cwd, List<McpServer> mcpServers, RunnableConfig runnableConfig) {
        if (cwd == null || cwd.trim().isEmpty()) {
            throw new IllegalArgumentException("Workspace directory (cwd) cannot be null or empty");
        }
        log.info("Building HarnessAgent for workspace: {}", cwd);
        log.info("Building HarnessAgent for context: {}", runnableConfig.context());

        // 1. 创建 Model（使用 AgentScope OpenAI 扩展）
        Model model = createModel(runnableConfig);

        // 2. 配置 Toolkit（工具包）
        Toolkit toolkit = new Toolkit();
        // 3. 配置 LocalFilesystemSpec（本地文件系统 + Shell）
        boolean readOnly = isReadOnly(runnableConfig);
        LocalFsMode fsMode = readOnly ? LocalFsMode.ROOTED : LocalFsMode.ROOTED;
        LocalFilesystemSpec fsSpec = new LocalFilesystemSpec()
                .project(Path.of(cwd))
                .mode(fsMode)
                .executeTimeoutSeconds(300)
                .inheritEnv(true);

        // 4. 渲染系统提示词
        String instruction = getInstruction(WORKSPACE_ROOT);

        // 5. 配置 MCP 工具
        // 将 ACP 协议的 MCP 服务器转换为 AgentScope MCP 客户端并注册到 Toolkit
        configureMcpTools(toolkit, mcpServers);

        // 6. 构建 HarnessAgent
        HarnessAgent agent = HarnessAgent.builder()
                .name("agent")
                .description("本地文件操作智能体，主要负责文件创建，编辑,命令执行")
                .model(model)
                .toolkit(toolkit)
                .workspace(Path.of(cwd))
                .filesystem(fsSpec)
                .sysPrompt(instruction)
                .agentId("harness-coding-agent")
                .stateStore(AGENT_STATE_STORE)
                .compaction(CompactionConfig.builder()
                        .triggerMessages(50)
                        .model(model)
                        .build())
                .enableTaskList()
                .enablePendingToolRecovery(true)
                .maxIters(50)
                .build();

        log.info("HarnessAgent built successfully");
        return agent;
    }

    /**
     * 渲染系统提示词模板。
     */
    public static String getInstruction(String workspace) {
        Locale locale = Locale.getDefault();
        String displayName = locale.getDisplayLanguage();
        return PromptTemplate.builder()
                .template(SYSTEM_PROMPT_TEMPLATE)
                .variables(Map.of(
                        "cwd", workspace,
                        "osName", System.getProperty("os.name").toLowerCase(),
                        "language", displayName,
                        "lineSeparator", System.lineSeparator()
                                .replace("\r", "\\r")
                                .replace("\n", "\\n")))
                .build()
                .render();
    }

    /**
     * 从 RunnableConfig 创建 AgentScope Model 实例。
     */
    private static Model createModel(RunnableConfig runnableConfig) {
        String modelId = null;
        if (runnableConfig.context().get("model") instanceof String mId) {
            modelId = mId;
        }
        List<ModelConfig> configs = ModelConfigLoader.loadConfigs();
        ModelConfig config = null;
        if (modelId != null) {
            config = ModelConfigLoader.findConfigByModelName(modelId, configs);
        }
        if (config == null) {
            config = ModelConfigLoader.getDefaultConfig(configs);
        }
        if (config == null) {
            throw new RuntimeException("No model configuration found. modelId=" + modelId);
        }
        log.info("Creating AgentScope model: {} (provider: {})", config.getModelId(), config.getModelName());
        return OpenAIChatModel.builder()
                .baseUrl(config.getBaseUrl())
                .apiKey(config.getApiKey())
                .generateOptions(GenerateOptions.builder().temperature(config.getTemperature()).build())
                .modelName(config.getModelName())
                .build();
    }

    /**
     * 判断当前是否为只读模式（plan 模式）。
     */
    private static boolean isReadOnly(RunnableConfig runnableConfig) {
        String currentMode = runnableConfig.context().get("mode") instanceof String mode
                ? mode : "accept_edits";
        return "plan".equalsIgnoreCase(currentMode);
    }

    /**
     * 配置 MCP 工具到 Toolkit。
     * 将 ACP 协议的 McpServer 转换为 AgentScope 的 MCP 客户端并注册。
     * 注意：此方法需要根据 agentscope MCP 客户端 API 调整。
     */
    private static void configureMcpTools(Toolkit toolkit, List<McpServer> mcpServers) {
        if (mcpServers == null || mcpServers.isEmpty()) {
            return;
        }
        // MCP Server 配置的自动加载: HarnessAgent 在 build() 时会从
        // workspace/tools.json 加载 MCP 服务器配置并自动注册到 Toolkit。
        // 对于通过参数传入的 McpServer (ACP协议)，可将其写入 tools.json
        // 或直接使用 agentscope 的 McpClientManager 进行注册。
        // TODO: 将 ACP McpServer 转换为 agentscope MCP 客户端并注册
        var mcpToolsWrapper = ToolsUtil.getMcpToolsWrapper(mcpServers);
        for (McpSyncClientWrapper server : mcpToolsWrapper) {
            toolkit.registerMcpClient(server);
        }
        var staticToolCallbackProvider = staticToolCallbackProvider(mcpServers);
        var tools = List.of(staticToolCallbackProvider.getToolCallbacks());

    }

    /**
     * 获取 HarnessAgent 内部的 ReActAgent，用于流式调用。
     */
    public static ReActAgent getDelegate(HarnessAgent agent) {
        return agent.getDelegate();
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

}