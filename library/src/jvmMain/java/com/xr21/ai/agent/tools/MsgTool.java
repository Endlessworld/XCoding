package com.xr21.ai.agent.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.xr21.ai.agent.agent.LocalAgent;
import com.xr21.ai.agent.utils.Json;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * worker 专有的消息回传工具，用于将 worker 的执行成果回传给主智能体。
 * <p>
 * worker 在完成任务后调用本工具上报最终结果，由 worker 自行决定回传格式
 * （text/boolean/json/file）以及是否写入工作目录下的文件。如果结果内容过大，
 * 会自动写入文件并只返回文件路径，避免占用主智能体上下文。
 * <p>
 * 通过 {@link ThreadLocal} 与 {@link WorkerTool} 在同一个 worker 调用生命周期内传递结果：
 * worker 调用 msg 时写入，WorkerTool 在 worker 完成后消费并作为工具结果返回给主智能体。
 *
 * @author Endless
 */
@Slf4j
public class MsgTool implements BiFunction<MsgTool.MsgRequest, ToolContext, String> {

    /**
     * 结果内联返回的最大字符数，超过则写入工作目录文件并只返回文件路径。
     */
    private static final int DEFAULT_MAX_INLINE_LENGTH = 4000;

    private final int maxInlineLength;

    public MsgTool() {
        this(DEFAULT_MAX_INLINE_LENGTH);
    }

    public MsgTool(int maxInlineLength) {
        this.maxInlineLength = maxInlineLength;
    }

    /**
     * 创建 msg 工具的 ToolCallback，供注入到 worker 的工具列表中。
     */
    public static ToolCallback createMsgToolCallback() {
        return FunctionToolCallback.builder("msg", new MsgTool()).description("""
                将 worker 的执行成果回传给主智能体。当 worker 完成任务时，由你自行决定如何上报最终结果：
                - result_type 可选：text(默认)/boolean/json/file，决定回传结果的格式；
                - 若决定将成果写入文件，请指定 file_name（文件名或路径）或将 result_type 设为 file，工具会写入工作目录下文件并只返回文件路径；
                - 若内容过大（超过阈值），工具会自动写入文件并只返回文件路径。
                回传结果以 JSON 形式返回：{success, worker_type, result_type, content 或 filePath}，主智能体可据此进行分支或并行编排。
                """).inputType(MsgRequest.class).toolMetadata(ToolMetadata.builder().returnDirect(true).build()).build();
    }

    @Override
    public String apply(MsgRequest request, ToolContext context) {
        try {

            String content = request.content == null ? "" : request.content;
            // 由 worker 自行决定返回格式与文件名
            ResultType type = request.resultType != null ? ResultType.parse(request.resultType) : ResultType.TEXT;
            String fileName = request.filePath;

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("worker_type", request.workerType);
            result.put("result_type", type.value());

            if (type == ResultType.FILE || (fileName != null && !fileName.isBlank())) {
                // 始终写入文件，只返回文件路径
                String filePath = writeToFile(content, fileName, request.workerType);
                result.put("filePath", filePath);
                result.put("contentLength", content.length());
                result.put("content", "[成果物已写入文件: " + filePath + "]");
            } else if (type == ResultType.BOOLEAN) {
                // 校验并返回布尔值
                Boolean bool = parseBoolean(content);
                if (bool == null) {
                    result.put("success", false);
                    result.put("content", "[返回格式错误] 期望 boolean，实际为: " + content);
                } else {
                    result.put("content", bool);
                }
            } else if (type == ResultType.JSON) {
                // 校验为合法 JSON，并以结构化对象返回
                Object parsed;
                try {
                    parsed = Json.jsonMapper(mapper -> {
                        try {
                            return mapper.readValue(content, Object.class);
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                    });
                } catch (Exception e) {
                    parsed = null;
                }
                if (parsed == null) {
                    result.put("success", false);
                    result.put("content", "[返回格式错误] 期望合法 JSON，实际为: " + content);
                } else {
                    result.put("content", parsed);
                }
            } else if (content.length() > maxInlineLength) {
                // 默认 text：超长自动落盘
                String filePath = writeToFile(content, request.filePath, request.workerType);
                result.put("truncated", true);
                result.put("contentLength", content.length());
                result.put("filePath", filePath);
                result.put("content", "[结果过大，已写入文件: " + filePath + "]");
            } else {
                result.put("content", content);
            }
            return Json.toJson(result);
        } catch (Exception e) {
            log.error("Msg tool failed", e);
            return "Msg 回传失败: " + e.getMessage();
        }
    }

    /**
     * 将内容解析为布尔值；非合法布尔（true/false）时返回 null。
     */
    private static Boolean parseBoolean(String s) {
        String t = s.trim();
        if ("true".equalsIgnoreCase(t)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(t)) {
            return Boolean.FALSE;
        }
        return null;
    }

    /**
     * 将结果写入工作目录下 worker_output 子目录，返回文件绝对路径。
     * <p>
     * 支持多级相对目录（sub/dir/report.txt，自动创建父目录）；若传入绝对路径或
     * 越界（../）路径，则清洗非法字符后作为扁平文件名置于 worker_output 根下，
     * 保证最终文件始终位于 worker_output 目录内，避免路径穿越与怪异文件名。
     */
    private String writeToFile(String content, String fileName, String workerType) throws Exception {
        Path base = Path.of(LocalAgent.WORKSPACE_ROOT, "worker_output").toAbsolutePath();
        Files.createDirectories(base);

        // 未指定文件名时自动生成，避免重名覆盖
        if (fileName == null || fileName.isBlank()) {
            fileName = "worker_result_" + UUID.randomUUID().toString().substring(0, 8) + ".txt";
        }

        // 将 Windows/Linux 路径分隔符统一为 "/"，以支持多级相对目录（sub/dir/report.txt）
        String normalized = fileName.replace('\\', '/');
        Path target = base.resolve(normalized).normalize();

        // 防路径穿越：解析后必须仍位于 base 目录内，否则回退为扁平文件名置于 base 根下
        // （例如传入绝对路径或 "../" 越界路径时，清洗非法字符后作为普通文件名处理）
        if (!target.startsWith(base)) {
            String flat = fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
            target = base.resolve(flat).normalize();
        }

        // 确保目标父目录存在（支持多级子目录自动创建）
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(target, content, StandardCharsets.UTF_8);
        return target.toAbsolutePath().toString();
    }

    /**
     * worker 返回结果的期望格式。
     */
    public enum ResultType {
        /**
         * 普通文本，超长自动写入文件
         */
        TEXT("text"),
        /**
         * 布尔值，校验 true/false
         */
        BOOLEAN("boolean"),
        /**
         * JSON 结构，校验可解析并以结构化对象返回
         */
        JSON("json"),
        /**
         * 始终写入文件，只返回文件路径
         */
        FILE("file");

        private final String value;

        ResultType(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }

        public static ResultType parse(String s) {
            if (s == null) {
                return TEXT;
            }
            switch (s.trim().toLowerCase()) {
                case "boolean":
                case "bool":
                    return BOOLEAN;
                case "json":
                    return JSON;
                case "file":
                    return FILE;
                default:
                    return TEXT;
            }
        }
    }

    /**
     * msg 工具的请求结构。
     */
    public static class MsgRequest {

        @JsonProperty(required = true)
        @JsonPropertyDescription("worker执行是否完成期望目标")
        public Boolean success;

        @JsonProperty(required = true)
        @JsonPropertyDescription("worker 执行成果内容，需要回传给主智能体的结果")
        public String content;

        @JsonProperty(value = "worker_type")
        @JsonPropertyDescription("当前 worker 的类型名称")
        public String workerType;

        @JsonProperty(value = "file_path")
        @JsonPropertyDescription("(可选) 当决定将成果写入文件时使用的文件目录+文件名(相对于工作目录的多级目录)，默认自动生成。若不提供且未指定 result_type=file，则按内容大小决定是否自动落盘")
        public String filePath;

        @JsonProperty(value = "result_type")
        @JsonPropertyDescription("(可选) 本次回传结果的期望格式：text(默认)/boolean/json/file。boolean 校验 true/false；json 校验可解析的 JSON 并结构化返回；file 始终写入文件并只返回路径")
        public String resultType;
    }
}
