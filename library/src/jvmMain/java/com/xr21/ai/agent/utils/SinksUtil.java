/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.state.StateSnapshot;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.xr21.ai.agent.entity.AgentOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.HashMap;
import java.util.Map;

public class SinksUtil {

    private static final Logger logger = LoggerFactory.getLogger(SinksUtil.class);

    public static Flux<ServerSentEvent<String>> sinks(Flux<NodeOutput> outputFlux) {
        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().unicast().onBackpressureBuffer();
        processJsonStream(outputFlux, sink);
        return sink.asFlux()
                .doOnCancel(() -> logger.info("Client disconnected from stream"))
                .doOnError(e -> logger.error("Error occurred during streaming", e))
                .doOnComplete(() -> logger.info("Streaming output completed"));
    }

    public static void processJsonStream(Flux<NodeOutput> outputFlux, Sinks.Many<ServerSentEvent<String>> sink) {
        outputFlux.doOnNext(output -> {
//            logger.info("output = {}", output);
            try {
                sink.tryEmitNext(ServerSentEvent.builder(buildJsonContent(output)).build());
            } catch (JsonProcessingException e) {
                logger.error("Error processing JSON for NodeOutput", e);
//                        sink.t(e);
            }
        }).doOnComplete(() -> {
            // 正常完成
            sink.tryEmitComplete();
        }).doOnError(e -> {
            logger.error("Error occurred during streaming", e);
            sink.tryEmitError(e);
        }).subscribe();
    }

    public static Flux<AgentOutput<Object>> sinksOutput(Flux<NodeOutput> outputFlux) {
        Sinks.Many<AgentOutput<Object>> sink = Sinks.many().unicast().onBackpressureBuffer();
        processStream(outputFlux, sink);
        return sink.asFlux()
                .doOnCancel(() -> logger.info("Client disconnected from stream"))
                .doOnError(e -> logger.error("Error occurred during streaming", e))
                .doOnComplete(() -> logger.info("Streaming output completed"));
    }

    public static void processStream(Flux<NodeOutput> outputFlux, Sinks.Many<AgentOutput<Object>> sink) {
        outputFlux.doOnNext(output -> {
//            logger.info("output = {}", output);
            sink.tryEmitNext(buildContent(output));
        }).doOnComplete(() -> {
            // 正常完成
            sink.tryEmitComplete();
        }).doOnError(e -> {
            logger.error("Error occurred during streaming", e);
            sink.tryEmitError(e);
        }).subscribe();
    }

    private static String buildJsonContent(NodeOutput output) throws JsonProcessingException {
        return JsonMapper.builder().build().writeValueAsString(buildContent(output));
    }

    private static AgentOutput<Object> buildContent(NodeOutput output) {
        logger.debug("NodeOutput: {}", output);
        var builder = AgentOutput.builder()
                .agent(output.agent())
                .data(output.state().data())
                .node(output.node())
                .timestamp(System.currentTimeMillis());
        Map<String, Object> jsonMap = new HashMap<>();
        jsonMap.put("timestamp", System.currentTimeMillis());
        if (output instanceof StreamingOutput<?> streamingOutput) {
            builder.message(streamingOutput.message());
            if (streamingOutput.message() != null) {
                builder.metadata(streamingOutput.message().getMetadata());
                builder.metadata("timestamp",System.currentTimeMillis());
                String reasoningContent = streamingOutput.message()
                        .getMetadata()
                        .getOrDefault("reasoningContent", "")
                        .toString();
                if (StringUtils.hasText(reasoningContent)) {
                    builder.think(reasoningContent);
                }
            }
            builder.originData(streamingOutput.getOriginData());
            builder.chunk(streamingOutput.chunk());
            builder.tokenUsage(streamingOutput.tokenUsage());
            builder.subGraph(streamingOutput.isSubGraph());
            if(streamingOutput.message() instanceof AssistantMessage message){
                if(message.hasToolCalls()){
                    logger.debug("Tool calls: {}", message.getToolCalls());
                }
            }
        }
        if (output instanceof InterruptionMetadata interruptionMetadata) {
            builder.metadata(interruptionMetadata.metadata().orElse(new HashMap<>()));
            builder.toolsAutomaticallyApproved(interruptionMetadata.getToolsAutomaticallyApproved());
            builder.toolFeedbacks(interruptionMetadata.toolFeedbacks());
            builder.tokenUsage(interruptionMetadata.tokenUsage());
            builder.subGraph(interruptionMetadata.isSubGraph());
        }
        if (output instanceof StateSnapshot stateSnapshot) {
            builder.config(stateSnapshot.config());
        }
        if (!output.isEND()) {
            var data = output.state().data();
            if (data.containsKey("chunk")) {
                builder.chunk(String.valueOf(data.getOrDefault("chunk", "")));
            }
        }
        return builder.build();
    }


    public static Flux<AgentOutput<Object>> toFlux(Agent agent, String input, String threadId, InterruptionMetadata feedbackMetadata, Map<String, Object> stateUpdate) {
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
}
