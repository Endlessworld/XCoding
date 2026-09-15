package com.xr21.ai.agent.interceptors;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import com.xr21.ai.agent.acp.SessionConfigOptionsFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.Map;

/**
 * 动态模型和思考等级
 *
 */
@Slf4j
public class DynamicOptionsInterceptor extends ModelInterceptor {

    @Override
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
        if (request.getOptions() instanceof OpenAiChatOptions options) {
            String thoughtLevel = request.getContext().getOrDefault("thought_level", SessionConfigOptionsFactory.ThoughtLevel.LOW.getValueId()).toString();
           var  mutate =  options.mutate();
//            AcpNotifyHelper.sendThoughtChunk(client, "Use thought_level : " + thoughtLevel);
            if (SessionConfigOptionsFactory.ThoughtLevel.DISABLED.getValueId().equals(thoughtLevel)) {
                mutate.extraBody(Map.of("thinking", Map.of("type", "disabled")));
            } else {
//                mutate.extraBody(Map.of("thinking", Map.of("type", "enabled")));
                mutate.reasoningEffort(thoughtLevel);
            }
            return handler.call(ModelRequest.builder(request).options(options).build());
        }
        return handler.call(request);
    }

    @Override
    public String getName() {
        return "dynamic_options";
    }
}
