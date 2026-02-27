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
import java.util.Map;
import java.util.function.BiFunction;

import static com.xr21.ai.agent.agent.LocalAgent.WORKSPACE_ROOT;

public class WriteFileTool implements BiFunction<WriteFileTool.WriteFileRequest, ToolContext, Map<String, Object>> {

    private static final int MAX_CONTENT_LENGTH = 8000;
    private static final String WORKSPACE_ROOT_NORMALIZED = Paths.get(WORKSPACE_ROOT).normalize().toString();

    public static final String DESCRIPTION = """
            写入文件系统中的新文件。
            Usage:
                - file_path参数必须是绝对路径，且必须在workspace范围内
                - 内容参数必须是字符串
                - write_file工具会创建一个新文件
                - 写入文件时，内容将完全替代现有内容
                - 一次不得超过8000字符，剩余的部分使用edit_file工具继续添加
            """;

    public static ToolCallback createWriteFileToolCallback() {
        return FunctionToolCallback.builder("write_file", new WriteFileTool())
                .description(DESCRIPTION)
                .inputType(WriteFileRequest.class)
                .build();
    }

    @Override
    public Map<String, Object> apply(WriteFileRequest request, ToolContext toolContext) {
        // Validate request parameters
        if (request.filePath == null || request.filePath.isBlank()) {
            return ToolResult.builder()
                    .error("File path cannot be null or empty")
                    .build();
        }

        if (request.content == null) {
            return ToolResult.builder()
                    .error("Content cannot be null")
                    .build();
        }

        // Validate content length
        if (request.content.length() > MAX_CONTENT_LENGTH) {
            return ToolResult.builder()
                    .error(String.format(
                            "Content exceeds maximum length of %d characters (actual: %d). " +
                            "Please split the content and use edit_file tool for remaining parts.",
                            MAX_CONTENT_LENGTH,
                            request.content.length()
                    ))
                    .build();
        }

        try {
            Path path = Paths.get(request.filePath).normalize();
            Path workspacePath = Paths.get(WORKSPACE_ROOT_NORMALIZED);
            String absolutePath = path.toAbsolutePath().toString();

            // Security: Validate that the path is within workspace
            if (!path.startsWith(workspacePath)) {
                return ToolResult.builder()
                        .error(String.format(
                                "Security violation: Path '%s' is outside the workspace root '%s'",
                                path,
                                WORKSPACE_ROOT
                        ))
                        .build();
            }

            // Check if file already exists
            if (Files.exists(path)) {
                return ToolResult.builder()
                        .error(String.format(
                                "File already exists: %s. Use edit_file to modify existing files.",
                                request.filePath
                        ))
                        .build();
            }

            // Create parent directories if they don't exist
            Path parent = path.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            // Write the file
            Files.writeString(path, request.content);

            // Calculate line count for locations
            int lineCount = request.content.isEmpty() ? 1 : (int) request.content.lines().count();

            // Build result
            ToolResult result = ToolResult.builder()
                    .success(true)
                    .content(String.format("Successfully created file: %s (%d characters)", request.filePath, request.content.length()))
                    .put("message", String.format("Successfully created file: %s (%d characters)", request.filePath, request.content.length()))
                    .put("file_path", request.filePath)
                    .put("bytes_written", request.content.getBytes().length)
                    .put("line_count", lineCount);

            // Add locations - for write_file, we add the first line and optionally the last line
            result.location(absolutePath, 1);
            if (lineCount > 1) {
                result.location(absolutePath, lineCount);
            }

            return result.build();
        } catch (IOException e) {
            return ToolResult.builder()
                    .error("Error writing file: " + e.getMessage())
                    .build();
        } catch (SecurityException e) {
            return ToolResult.builder()
                    .error("Security error: " + e.getMessage())
                    .build();
        } catch (Exception e) {
            return ToolResult.builder()
                    .error("Unexpected error: " + e.getMessage())
                    .build();
        }
    }

    public static class WriteFileRequest {
        @JsonProperty(
                required = true,
                value = "file_path"
        )
        @JsonPropertyDescription("The absolute path of the file to create" )
        public String filePath;

        @JsonProperty(required = true)
        @JsonPropertyDescription("The content to write to the file, must be a string. Maximum 8000 characters.")
        public String content;

        public WriteFileRequest() {
        }

        public WriteFileRequest(String filePath, String content) {
            this.filePath = filePath;
            this.content = content;
        }
    }
}
