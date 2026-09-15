package com.xr21.ai.agent.interceptors;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import com.xr21.ai.agent.tools.ShellTools;
import com.xr21.ai.agent.utils.DevEnvironmentDetector;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.util.List;

/**
 * 注入 Shell 相关工具（Bash/BashOutput/ShellInput/ShellSessions/KillShell）。
 * <p>
 * 工具经 {@link #getTools()} 交由框架自动挂载；仅动态运行环境（平台、shell、已安装开发工具）
 * 拼接为 systemPrompt 在每轮模型调用前注入。详细的 Bash / git / PR 使用说明已迁移到内置
 * `github` skill（{@code classpath:skills/github/SKILL.md}），由模型按需通过 {@code read_skill}
 * 渐进式加载，避免始终占用上下文。
 */
public class ShellInterceptor extends ModelInterceptor {

    private final List<ToolCallback> tools;
    private final String systemPrompt;

    private ShellInterceptor(Builder builder) {
        MethodToolCallbackProvider provider = MethodToolCallbackProvider.builder()
                .toolObjects(ShellTools.builder().build())
                .build();
        this.tools = List.of(provider.getToolCallbacks());
        this.systemPrompt = builder.systemPrompt != null ? builder.systemPrompt : "";
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
        // 仅注入动态运行环境（平台 / shell / 已安装开发工具）；详细用法说明由 github skill 按需加载
        String env = DevEnvironmentDetector.describe();
        String prompt = systemPrompt.isBlank() ? env : systemPrompt + "\n\n" + env;
        SystemMessage enhancedSystemMessage = request.getSystemMessage() == null
                ? new SystemMessage(prompt)
                : new SystemMessage(request.getSystemMessage().getText() + "\n\n" + prompt);
        return handler.call(ModelRequest.builder(request).systemMessage(enhancedSystemMessage).build());
    }

    @Override
    public List<ToolCallback> getTools() {
        return tools;
    }

    @Override
    public String getName() {
        return "Shell";
    }

    public static class Builder {
        private String systemPrompt;

        /** 自定义 Shell 使用说明（默认为空，详细说明见内置 github skill）。 */
        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public ShellInterceptor build() {
            return new ShellInterceptor(this);
        }
    }
}
