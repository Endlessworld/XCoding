package com.xr21.ai.agent.tools;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.xr21.ai.agent.agent.LocalAgent;
import com.xr21.ai.agent.config.AiModels;
import com.xr21.ai.agent.interceptors.FilesystemInterceptor;
import com.xr21.ai.agent.interceptors.SummarizationHook;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;

public class AgentsTest {
    /**
     * 示例2：消息压缩 Hook
     */
//    @Test
    public void messageSummarization() {
        LocalAgent.WORKSPACE_ROOT = "E:\\local-github\\ai-agents";
        ChatModel chatModel = AiModels.createChatModelFromJson("volcengine/GLM-5.1");
        // 创建消息压缩 Hook
        SummarizationHook summarizationHook = SummarizationHook.builder()
                .model(chatModel)
                .maxTokensBeforeSummary(64 * 1024)
                .messagesToKeep(20)
                .build();
        var filesystemInterceptor = FilesystemInterceptor.builder().withWorkspaceRoot("E:\\local-github\\ai-agents").readOnly(false).withDefaultSecurity().build();

        // 使用
        ReactAgent agent = ReactAgent.builder()
                .name("my_agent")
                .model(chatModel)
                .interceptors(filesystemInterceptor)
                .hooks(summarizationHook)
                .build();
        try {
//            Flux<NodeOutput> stream = agent.stream("根据CONTEXT_EDITING_INTERCEPTOR_ANALYSIS.md分析结果 优化ContextEditingInterceptor");
            Flux<NodeOutput> stream = agent.stream("[·S] 是什么意思 你为何要在代码中输出[·S]");
            stream.subscribe(e -> {
                if (e instanceof StreamingOutput<?> output) {
                    if (output.message() == null) {
                        return;
                    }
                    if (output.message().getMetadata().get("reasoningContent") instanceof String reasoningContent) {
                        System.out.print(reasoningContent);
                    }
                    if (StringUtils.isNotBlank(output.message().getText())) {
                        System.err.print(output.message().getText());
                    }
                    if (output.message() instanceof AssistantMessage assistant && assistant.hasToolCalls()) {
                        for (AssistantMessage.ToolCall toolCall : assistant.getToolCalls()) {
                            System.err.println(toolCall.name());
                            System.err.println(toolCall.arguments());
                        }

                    }
                    if (output.message() instanceof ToolResponseMessage toolResponseMessage && !CollectionUtils.isEmpty(toolResponseMessage.getResponses())) {
                        for (ToolResponseMessage.ToolResponse toolResponse : toolResponseMessage.getResponses()) {
                            System.err.println(toolResponse.responseData());
                        }

                    }
                    System.err.flush();
                }
            });
            stream.blockLast();
        } catch (GraphRunnerException e) {
            throw new RuntimeException(e);
        }

    }
}
