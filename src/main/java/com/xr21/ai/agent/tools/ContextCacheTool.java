package com.xr21.ai.agent.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

@Slf4j
public class ContextCacheTool implements BiFunction<ContextCacheTool.RefRequest, ToolContext, Map<String, Object>> {

    public static final Map<String, String> argumentsRef = new HashMap<>();

    public static final Map<String, String> responsesRef = new HashMap<>();
    public static final String DESCRIPTION = "指针数据读取器，上下文编辑器会将你超长的工具调用参数或工具调用执行结果转换成指针,指针地址格式：$ref+工具调用id，你可以在需要的时候 重新根据指针地址 重新获取具体内容";

    public static ToolCallback contextCacheTool(String description) {
        return FunctionToolCallback.builder("contextCacheTool", new ContextCacheTool())
                .description(StringUtils.hasText(description) ? description : DESCRIPTION)
                .inputType(RefRequest.class)
                .build();

    }

    public static void addArgumentsRef(String refId, String content) {
        argumentsRef.put(refId, content);
        log.info("add argumentsRef {} > {}", refId, content);
    }

    public static void addResponsesRef(String refId, String content) {
        responsesRef.put(refId, content);
        log.info("add responsesRef {} > {}", refId, content);
    }


    @Override
    public Map<String, Object> apply(RefRequest request, ToolContext toolContext) {
        System.out.println("RefTool tool called : " + request.refs);
        log.info("ContextCacheTool call  {}", request);
        if (request.refs == null || request.refs.isEmpty()) {
            return Map.of("error", "错误：未提供有效的指针地址");
        }

        Map<String, Object> result = new HashMap<>();
        for (String ref : request.refs) {
            // 首先尝试从参数引用中获取
            String content = argumentsRef.get(ref);
            if (content == null) {
                // 如果参数引用中不存在，则从响应引用中获取
                content = responsesRef.get(ref);
            }

            if (content != null) {
                result.put(ref, content);
            } else {
                result.put(ref, "错误：未找到对应的引用内容");
            }
        }

        return result;
    }

    /**
     * Request object for ticket booking containing name and date.
     */
    @Data
    @AllArgsConstructor
    public static class RefRequest {

        @JsonProperty(required = true, value = "指针地址列表，指针格式：$ref+工具调用id，根据指针地址重新获取具体内容")
        private List<String> refs;
    }
}
