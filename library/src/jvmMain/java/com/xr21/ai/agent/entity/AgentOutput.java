package com.xr21.ai.agent.entity;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.Usage;

import java.util.List;
import java.util.Map;

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

    public AgentOutput(String node, long timestamp, Map<String, Object> data, RunnableConfig config, String chunk, Message message, T originData) {
        this.node = node;
        this.timestamp = timestamp;
        this.data = data;
        this.config = config;
        this.chunk = chunk;
        this.message = message;
        this.originData = originData;
    }

    public AgentOutput(String node, long timestamp, Map<String, Object> data, RunnableConfig config, String chunk, Message message, T originData, Map<String, Object> metadata, String agent, Usage tokenUsage, boolean subGraph, List<AssistantMessage.ToolCall> toolsAutomaticallyApproved, List<InterruptionMetadata.ToolFeedback> toolFeedbacks) {
        this.node = node;
        this.timestamp = timestamp;
        this.data = data;
        this.config = config;
        this.chunk = chunk;
        this.message = message;
        this.originData = originData;
        this.metadata = metadata;
        this.agent = agent;
        this.tokenUsage = tokenUsage;
        this.subGraph = subGraph;
        this.toolsAutomaticallyApproved = toolsAutomaticallyApproved;
        this.toolFeedbacks = toolFeedbacks;
    }

    public static <T> AgentOutputBuilder<T> builder() {
        return new AgentOutputBuilder<T>();
    }

    public String getNode() {
        return this.node;
    }

    public long getTimestamp() {
        return this.timestamp;
    }

    public Map<String, Object> getData() {
        return this.data;
    }

    public RunnableConfig getConfig() {
        return this.config;
    }

    public String getChunk() {
        return this.chunk;
    }

    public Message getMessage() {
        return this.message;
    }

    public T getOriginData() {
        return this.originData;
    }

    public Map<String, Object> getMetadata() {
        return this.metadata;
    }

    public String getAgent() {
        return this.agent;
    }

    public Usage getTokenUsage() {
        return this.tokenUsage;
    }

    public boolean isSubGraph() {
        return this.subGraph;
    }

    public List<AssistantMessage.ToolCall> getToolsAutomaticallyApproved() {
        return this.toolsAutomaticallyApproved;
    }

    public List<InterruptionMetadata.ToolFeedback> getToolFeedbacks() {
        return this.toolFeedbacks;
    }

    public static class AgentOutputBuilder<T> {
        private String node;
        private long timestamp;
        private Map<String, Object> data;
        private RunnableConfig config;
        private String chunk;
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
            this.metadata = metadata;
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
            return new AgentOutput<T>(this.node, this.timestamp, this.data, this.config, this.chunk, this.message, this.originData, this.metadata, this.agent, this.tokenUsage, this.subGraph, this.toolsAutomaticallyApproved, this.toolFeedbacks);
        }

        public String toString() {
            return "AgentOutput.AgentOutputBuilder(node=" + this.node + ", timestamp=" + this.timestamp + ", data=" + this.data + ", config=" + this.config + ", chunk=" + this.chunk + ", message=" + this.message + ", originData=" + this.originData + ", metadata=" + this.metadata + ", agent=" + this.agent + ", tokenUsage=" + this.tokenUsage + ", subGraph=" + this.subGraph + ", toolsAutomaticallyApproved=" + this.toolsAutomaticallyApproved + ", toolFeedbacks=" + this.toolFeedbacks + ")";
        }
    }
}
