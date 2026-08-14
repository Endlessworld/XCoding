package com.xr21.ai.agent.plugins;

import com.alibaba.cloud.ai.graph.agent.interceptor.Interceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;
import lombok.extern.slf4j.Slf4j;

/**
 * 把插件 interceptors[] 闭包适配为 {@link ModelInterceptor} / {@link ToolInterceptor}。
 * <p>
 * model 型闭包签名：apply(ModelRequest req, ModelCallHandler handler) 返回 ModelResponse；
 * tool 型闭包签名：apply(ToolCallRequest req, ToolCallHandler handler) 返回 ToolCallResponse。
 */
@Slf4j
public final class GroovyPluginInterceptor {

    private GroovyPluginInterceptor() {
    }

    public static Interceptor create(GroovyInterceptorSpec spec) {
        String type = spec.getType() == null ? "model" : spec.getType().toLowerCase();
        return switch (type) {
            case "model" -> new ModelInterceptorAdapter(spec);
            case "tool" -> new ToolInterceptorAdapter(spec);
            default -> throw new IllegalArgumentException("未知 interceptor type: " + type + "（支持 model/tool）");
        };
    }

    static class ModelInterceptorAdapter extends ModelInterceptor {
        private final GroovyInterceptorSpec spec;

        ModelInterceptorAdapter(GroovyInterceptorSpec spec) {
            this.spec = spec;
        }

        @Override
        public String getName() {
            return spec.getName();
        }

        @Override
        public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
            return (ModelResponse) spec.getApply().call(request, handler);
        }
    }

    static class ToolInterceptorAdapter extends ToolInterceptor {
        private final GroovyInterceptorSpec spec;

        ToolInterceptorAdapter(GroovyInterceptorSpec spec) {
            this.spec = spec;
        }

        @Override
        public String getName() {
            return spec.getName();
        }

        @Override
        public ToolCallResponse interceptToolCall(ToolCallRequest request, ToolCallHandler handler) {
            return (ToolCallResponse) spec.getApply().call(request, handler);
        }
    }
}
