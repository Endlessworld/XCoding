package com.xr21.ai.agent.entity;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.Usage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
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

    @JsonProperty(value = "think")
    private final String think;

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

    public static <T> AgentOutputBuilder<T> builder() {
        return new AgentOutputBuilder<T>();
    }


    public static class AgentOutputBuilder<T> {
        private String node;
        private long timestamp;
        private Map<String, Object> data;
        private RunnableConfig config;
        private String chunk;
        private String think;
        private Message message;
        private T originData;
        private Map<String, Object> metadata;
        private String agent;
        private Usage tokenUsage;
        private boolean subGraph;
        private List<AssistantMessage.ToolCall> toolsAutomaticallyApproved;
        private List<InterruptionMetadata.ToolFeedback> toolFeedbacks;

        AgentOutputBuilder() {
        }

        @JsonProperty("node")
        public AgentOutputBuilder<T> node(String node) {
            this.node = node;
            return this;
        }

        @JsonProperty("node")
        public AgentOutputBuilder<T> think(String think) {
            this.think = think;
            return this;
        }

        @JsonProperty("timestamp")
        public AgentOutputBuilder<T> timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        @JsonProperty("data")
        public AgentOutputBuilder<T> data(Map<String, Object> data) {
            this.data = data;
            return this;
        }

        @JsonIgnore
        public AgentOutputBuilder<T> config(RunnableConfig config) {
            this.config = config;
            return this;
        }

        @JsonProperty("chunk")
        public AgentOutputBuilder<T> chunk(String chunk) {
            this.chunk = chunk;
            return this;
        }

        @JsonProperty("message")
        public AgentOutputBuilder<T> message(Message message) {
            this.message = message;
            return this;
        }

        @JsonIgnore
        public AgentOutputBuilder<T> originData(T originData) {
            this.originData = originData;
            return this;
        }

        @JsonProperty("metadata")
        public AgentOutputBuilder<T> metadata(Map<String, Object> metadata) {
            if ( this.metadata != null) {
                this.metadata.putAll(metadata);
            }else{
                this.metadata = new HashMap<>(metadata);
            }
            return this;
        }

        @JsonProperty("metadata")
        public AgentOutputBuilder<T> metadata(String key, Object value) {
            if (metadata == null) {
                metadata = new HashMap<>();
            }
            metadata.put(key, value);
            return this;
        }
        @JsonProperty("agent")
        public AgentOutputBuilder<T> agent(String agent) {
            this.agent = agent;
            return this;
        }

        @JsonProperty("tokenUsage")
        public AgentOutputBuilder<T> tokenUsage(Usage tokenUsage) {
            this.tokenUsage = tokenUsage;
            return this;
        }

        @JsonProperty("subGraph")
        public AgentOutputBuilder<T> subGraph(boolean subGraph) {
            this.subGraph = subGraph;
            return this;
        }

        @JsonProperty("toolsAutomaticallyApproved")
        public AgentOutputBuilder<T> toolsAutomaticallyApproved(List<AssistantMessage.ToolCall> toolsAutomaticallyApproved) {
            this.toolsAutomaticallyApproved = toolsAutomaticallyApproved;
            return this;
        }

        @JsonProperty("toolFeedbacks")
        public AgentOutputBuilder<T> toolFeedbacks(List<InterruptionMetadata.ToolFeedback> toolFeedbacks) {
            this.toolFeedbacks = toolFeedbacks;
            return this;
        }

        public AgentOutput<T> build() {
            return new AgentOutput<T>(this.node, this.timestamp, this.data, this.config, this.chunk, this.think, this.message, this.originData, this.metadata, this.agent, this.tokenUsage, this.subGraph, this.toolsAutomaticallyApproved, this.toolFeedbacks);
        }

        public String toString() {
            return "AgentOutput.AgentOutputBuilder(node=" + this.node + ", timestamp=" + this.timestamp + ", data=" + this.data + ", config=" + this.config + ", chunk=" + this.chunk + ",  think=" + this.think + ", message=" + this.message + ", originData=" + this.originData + ", metadata=" + this.metadata + ", agent=" + this.agent + ", tokenUsage=" + this.tokenUsage + ", subGraph=" + this.subGraph + ", toolsAutomaticallyApproved=" + this.toolsAutomaticallyApproved + ", toolFeedbacks=" + this.toolFeedbacks + ")";
        }
    }
}
