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
import com.alibaba.cloud.ai.graph.agent.hook.Hook;
import com.alibaba.cloud.ai.graph.agent.interceptor.Interceptor;
import com.alibaba.cloud.ai.graph.checkpoint.savers.file.FileSystemSaver;
import com.alibaba.cloud.ai.graph.serializer.plain_text.jackson.SpringAIJacksonStateSerializer;
import com.xr21.ai.agent.acp.SessionConfigOptionsFactory;
import com.xr21.ai.agent.config.AiModels;
import com.xr21.ai.agent.plugins.GroovyPluginLoader;
import com.xr21.ai.agent.plugins.GroovyPluginRegistry;
import com.xr21.ai.agent.plugins.PluginContext;
import com.xr21.ai.agent.utils.AcpNotifyHelper;
import com.xr21.ai.agent.utils.AgentHelper;
import com.xr21.ai.agent.utils.Prompts;
import com.xr21.ai.agent.utils.SkillResourceReleaser;
import com.xr21.ai.agent.utils.ToolsUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
     * @param client         客户端会话操作
     * @return 配置完成的智能体实例
     * @throws RuntimeException         如果智能体创建失败
     * @throws IllegalArgumentException 如果参数无效
     */
    public static ReactAgent createAgent(String cwd, @Nullable List<McpServer> mcpServers, RunnableConfig runnableConfig, @NotNull ClientSessionOperations client) {
        if (!StringUtils.isNotBlank(cwd)) {
            String tempDir = System.getProperty("java.io.tmpdir");
            System.out.println("系统临时目录: " + tempDir);
            cwd = tempDir + File.separator + "cwd_" + System.currentTimeMillis();
            log.error("create agent with cwd tmpdir: {} ", cwd);
        }
        WORKSPACE_ROOT = cwd;
        // 释放 classpath 内置 skills 到工作目录 .agents/skills，使其由 FileSystemSkillRegistry 统一加载
        SkillResourceReleaser.release(Path.of(WORKSPACE_ROOT, ".agents", "skills"));
        return buildAgent(cwd, mcpServers, runnableConfig, client);
    }

    /**
     * 构建智能体的核心方法。
     * <p>
     * 配置智能体的所有组件，包括工具、拦截器、钩子和指令。具体组件由
     * {@link AiModels}、{@link AgentHelper}、{@link ToolsUtil}
     * 与 {@link Prompts} 分别负责装配。
     * </p>
     *
     * @param cwd            工作目录路径
     * @param mcpServers     MCP服务器列表
     * @param runnableConfig 运行配置
     * @param client         客户端会话操作
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

        ChatModel chatModel = AiModels.resolveChatModel(runnableConfig);
        AcpNotifyHelper.sendThoughtChunk(client, "Use model : " + chatModel.getOptions().getModel());
        List<Interceptor> interceptors = new ArrayList<>(AgentHelper.createInterceptors(runnableConfig, chatModel, client, WORKSPACE_ROOT));
        // 收集拦截器提供的文件系统工具与 write_todos 工具，供 Groovy 脚本绑定调用
        List<ToolCallback> interceptorTools = ToolsUtil.collectHostTools(interceptors);
        List<Hook> hooks = AgentHelper.createHooks(runnableConfig, WORKSPACE_ROOT);
        for (Hook hook : hooks) {
            AcpNotifyHelper.sendThoughtChunk(client, "Use Hook : " + hook.getName());
        }
        // 使用 PromptTemplate 渲染指令
        var instruction = getInstruction(WORKSPACE_ROOT);
        var chatOptions = ((OpenAiChatOptions) chatModel.getOptions()).mutate();
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
        var staticToolCallbackProvider = ToolsUtil.staticToolCallbackProvider(mcpServers, interceptorTools);
        var tools = List.of(staticToolCallbackProvider.getToolCallbacks());
        // 插件 hooks / interceptors 并入（默认追加到内置之后）
        hooks.addAll(GroovyPluginRegistry.get().hooks());
        interceptors.addAll(GroovyPluginRegistry.get().interceptors());
//        AcpNotifyHelper.sendThoughtChunk(client, "Use tools : " + tools.stream().map(ToolCallback::getToolDefinition).map(ToolDefinition::name).distinct().collect(Collectors.joining(",")));
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
                .wrapSyncToolsAsAsync(true)
                .maxParallelTools(8)
                .returnReasoningContents(true)
                .build();
        log.info("LocalAgent built successfully with {} tools and {} interceptors", tools.size(), interceptors.size());
        return agent;
    }

    /**
     * 渲染系统提示词。
     *
     * @param workspace 工作空间根目录
     * @return 渲染后的系统提示词
     */
    public static String getInstruction(String workspace) {
        return Prompts.renderAgentSystemPrompt(workspace);
    }

}
