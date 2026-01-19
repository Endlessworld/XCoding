package com.xr21.ai.agent.entity;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.Usage;

import java.util.List;
import java.util.Map;

@Builder
@AllArgsConstructor
@RequiredArgsConstructor

@Getter
public class AgentOutput<T> {

    @JsonProperty(value = "node")
    private final String node;

    @JsonProperty(value = "timestamp")
    private final long timestamp;

    @JsonProperty(value = "data")
    private final Map<String, Object> data;

    //    @JsonProperty(value = "config")
    @JsonIgnore
    private final RunnableConfig config;

    /**
     * 大模型流式输出内容-界面渲染
     */
//    @Deprecated
    @JsonProperty(value = "chunk")
    private final String chunk;

    /**
     * 生成的消息
     */
    @JsonProperty(value = "message")
    private final Message message;

    @JsonIgnore
    private final T originData;

    @JsonProperty(value = "metadata")
    private Map<String, Object> metadata;

    /**
     * 当前执行的智能体
     */
    @JsonProperty(value = "agent")
    private String agent;

    @JsonProperty(value = "tokenUsage")
    private Usage tokenUsage;

    @JsonProperty(value = "subGraph")
    private boolean subGraph = false;

    @JsonProperty(value = "toolsAutomaticallyApproved")
    private List<AssistantMessage.ToolCall> toolsAutomaticallyApproved;

    @JsonProperty(value = "toolFeedbacks")
    private List<InterruptionMetadata.ToolFeedback> toolFeedbacks;

}
