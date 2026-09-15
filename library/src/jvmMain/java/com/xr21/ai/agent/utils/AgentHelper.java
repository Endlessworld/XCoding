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
package com.xr21.ai.agent.utils;

import com.agentclientprotocol.common.ClientSessionOperations;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.extension.file.LocalFilesystemBackend;
import com.alibaba.cloud.ai.graph.agent.extension.interceptor.LargeResultEvictionInterceptor;
import com.alibaba.cloud.ai.graph.agent.hook.Hook;
import com.alibaba.cloud.ai.graph.agent.hook.hip.HumanInTheLoopHook;
import com.alibaba.cloud.ai.graph.agent.hook.hip.ToolConfig;
import com.alibaba.cloud.ai.graph.agent.hook.skills.SkillsAgentHook;
import com.alibaba.cloud.ai.graph.agent.interceptor.Interceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.toolerror.ToolErrorInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.toolretry.ToolRetryInterceptor;
import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.alibaba.cloud.ai.graph.skills.registry.filesystem.FileSystemSkillRegistry;
import com.xr21.ai.agent.interceptors.AcpTodoListInterceptor;
import com.xr21.ai.agent.interceptors.CompactionPromptHook;
import com.xr21.ai.agent.interceptors.FilesystemInterceptor;
import com.xr21.ai.agent.interceptors.ModelRetryInterceptor;
import com.xr21.ai.agent.interceptors.PersistedStateHook;
import com.xr21.ai.agent.interceptors.PluginDynamicToolsInterceptor;
import com.xr21.ai.agent.interceptors.ShellInterceptor;
import com.xr21.ai.agent.interceptors.WorkerInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.net.SocketException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Agent 组件装配辅助类：集中构建智能体所需的拦截器（Interceptor）与钩子（Hook）。
 *
 * @author Endless
 */
@Slf4j
public final class AgentHelper {

    private AgentHelper() {
    }

    /**
     * 构建拦截器列表。
     * <p>
     * 包含大结果驱逐、工具重试、文件系统、Shell、Worker、模型重试、错误处理、
     * ACP Todo 以及插件动态工具等拦截器。
     *
     * @param runnableConfig 运行配置
     * @param chatModel      对话模型（用于 Worker 默认模型）
     * @param client         客户端会话操作（用于通知）
     * @param workspaceRoot  工作空间根目录
     * @return 拦截器列表
     */
    public static @NotNull List<Interceptor> createInterceptors(RunnableConfig runnableConfig, ChatModel chatModel,
                                                                @NotNull ClientSessionOperations client, String workspaceRoot) {
        var largeResultEvictionInterceptor = LargeResultEvictionInterceptor.builder()
                .toolTokenLimitBeforeEvict(30000)
                .backend(new LocalFilesystemBackend(workspaceRoot))
                .build();

        var toolRetryInterceptor = ToolRetryInterceptor.builder()
                .maxRetries(2)
                .initialDelay(1)
                .backoffFactor(1.5)
                .maxDelay(5000)
                .onFailure(ToolRetryInterceptor.OnFailureBehavior.RETURN_MESSAGE)
                .errorFormatter(e -> Json.toJson(Map.of("error", "工具调用失败，请输出完整、严谨的JSON结构: " + e.getMessage())))
                .jitter(true)
                .build();

        // 根据当前模式决定文件系统是否只读
        log.info(" runnableConfig.context() {}", runnableConfig.context().get("mode"));
        String currentMode = runnableConfig.context().get("mode") instanceof String mode ? mode : "accept_edits";
        boolean readOnly = "plan".equalsIgnoreCase(currentMode);
        var filesystemInterceptor = FilesystemInterceptor.builder()
                .withWorkspaceRoot(workspaceRoot)
                .readOnly(readOnly)
                .withDefaultSecurity()
                .build();
        var shellInterceptor = ShellInterceptor.builder().build();
        List<ToolCallback> workerDefaultTools = buildWorkerDefaultTools(filesystemInterceptor, shellInterceptor);
        log.debug("Loaded {} base tools", workerDefaultTools.size());
        WorkerInterceptor workerInterceptor = WorkerInterceptor.builder()
                .defaultModel(chatModel)
                .defaultTools(workerDefaultTools)
                .includeGeneralPurpose(true)
                .build();
        ModelRetryInterceptor retryInterceptor = ModelRetryInterceptor.builder()
                .maxAttempts(30)
                .initialDelay(1000)
                .maxDelay(3 * 60 * 1000)
                .retryableExceptionPredicate(e -> {
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
                .runnableConfig(runnableConfig)
                .backoffMultiplier(2.0)
                .build();

        List<Interceptor> interceptors = new ArrayList<>();
        interceptors.add(largeResultEvictionInterceptor);
        interceptors.add(toolRetryInterceptor);
        interceptors.add(filesystemInterceptor);
        interceptors.add(shellInterceptor);
        interceptors.add(workerInterceptor);
        interceptors.add(retryInterceptor);
        interceptors.add(new ToolErrorInterceptor());
        interceptors.add(AcpTodoListInterceptor.builder().build());
        // 路线 B：运行时热挂载 —— 每轮模型调用前注入 registry 当前插件工具（dynamicToolCallbacks）
        interceptors.add(new PluginDynamicToolsInterceptor());
        log.info("Agent mode: {}, filesystem readOnly: {}", currentMode, readOnly);
        AcpNotifyHelper.sendThoughtChunk(client, "Use Mode : " + currentMode);
        return interceptors;
    }

    /**
     * 构建 Hook 列表。
     * <p>
     * 包含状态持久化、命令审批（Human-in-the-loop）、技能加载与会话压缩提示等 Hook。
     *
     * @param runnableConfig 运行配置
     * @param workspaceRoot  工作空间根目录
     * @return Hook 列表
     */
    public static @NotNull List<Hook> createHooks(RunnableConfig runnableConfig, String workspaceRoot) {
        List<Hook> hooks = new ArrayList<>(4);
        // 每轮模型请求结束后，把 context 中可持久化的条目合入 OverAllState（随 FileSystemSaver 快照持久化）
        hooks.add(new PersistedStateHook());
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
                        .projectSkillsDirectory(Path.of(workspaceRoot, ".agents", "skills").toAbsolutePath().toString())
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

    /**
     * 构建 Worker 默认工具集合：基础工具 + Shell 工具 + Groovy 脚本工具 + 文件系统工具。
     *
     * @param filesystemInterceptor 文件系统拦截器
     * @param shellInterceptor      Shell 拦截器
     * @return Worker 默认工具列表
     */
    private static List<ToolCallback> buildWorkerDefaultTools(FilesystemInterceptor filesystemInterceptor,
                                                              ShellInterceptor shellInterceptor) {
        List<ToolCallback> tools = ToolsUtil.baseTools();
        // Shell 工具（Bash/BashOutput/ShellInput/ShellSessions/KillShell）经 ShellInterceptor 注入，此处并入以作为 Worker 默认工具
        tools.addAll(shellInterceptor.getTools());
        // Groovy 脚本工具：脚本内绑定 tools 对象，可调用以上全部工具实现 MCP 工具编排
        tools.add(ToolsUtil.groovyScriptTool(tools));
        tools.addAll(filesystemInterceptor.getTools());
        return tools;
    }
}
