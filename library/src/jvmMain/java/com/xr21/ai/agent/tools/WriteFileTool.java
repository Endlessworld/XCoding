//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.xr21.ai.agent.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

import static com.xr21.ai.agent.LocalAgent.WORKSPACE_ROOT;

public class WriteFileTool implements BiFunction<WriteFileTool.WriteFileRequest, ToolContext, Map<String, Object>> {
    public static final String DESCRIPTION = """
            写入文件系统中的新文件。
            Usage:
                - file_path参数必须是绝对路径，而非相对路径
                - 内容参数必须是字符串
                - write_file工具会创建一个新文件。
                - 写入文件时，内容将完全替代现有内容。
                - 一次不得超过8000字符，否则将添失败吗，剩余的部分使用edit_file工具继续添加
            """;

    public static ToolCallback createWriteFileToolCallback(String description) {
        return FunctionToolCallback.builder("write_file", new WriteFileTool())
                .description(DESCRIPTION)
                .inputType(WriteFileRequest.class)
                .build();
    }

    public Map<String, Object> apply(WriteFileRequest request, ToolContext toolContext) {
        Map<String, Object> result = new HashMap<>();
        try {
            Path path = Paths.get(request.filePath);
            if (Files.exists(path)) {
                result.put("error", "File already exists: " + request.filePath + ". Use edit_file to modify existing files.");
                return result;
            } else {
                Path parent = path.getParent();
                if (parent != null && !Files.exists(parent)) {
                    Files.createDirectories(parent);
                }

                Files.writeString(path, request.content);
                result.put("message", "Successfully created file: " + request.filePath);
                return result;
            }
        } catch (IOException e) {
            result.put("error", "Error writing file: " + e.getMessage());
            return result;
        }
    }

    public static class WriteFileRequest {
        @JsonProperty(
                required = true,
                value = "file_path"
        )
        @JsonPropertyDescription("The absolute path of the file to create. only parent path:" + WORKSPACE_ROOT)
        public String filePath;
        @JsonProperty(
                required = true
        )
        @JsonPropertyDescription("The content to write to the file, must be a string ")
        public String content;

        public WriteFileRequest() {
        }

        public WriteFileRequest(String filePath, String content) {
            this.filePath = filePath;
            this.content = content;
        }
    }
}
