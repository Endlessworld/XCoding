/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.xr21.ai.agent.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.xr21.ai.agent.entity.ToolResult;
import org.springframework.ai.tool.annotation.Tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static com.xr21.ai.agent.agent.LocalAgent.WORKSPACE_ROOT;

/**
 * 写入文件系统中的新文件
 *
 * @author Endless
 */
public class WriteFileTool {

    private static final int MAX_CONTENT_LENGTH = 8000;
    private static final String WORKSPACE_ROOT_NORMALIZED = Paths.get(WORKSPACE_ROOT).normalize().toString();

    // @formatter:off
    @Tool(name = "write_file", description = """
        写入文件系统中的文件。
        Usage:
            - file_path参数必须是绝对路径，且必须在workspace范围内
            - 内容参数必须是字符串
            - write_file工具会创建新文件或覆写已存在的文件
            - 写入文件时，内容将完全替代现有内容
        """)
    public Map<String, Object> writeFile(
            @JsonProperty(value = "filePath", required = true)
            @JsonPropertyDescription("The absolute path of the file to create")
            String filePath,
            @JsonProperty(value = "content", required = true)
            @JsonPropertyDescription("The content to write to the file, must be a string. Maximum 8000 characters.")
            String content
    ) { // @formatter:on
        // Validate request parameters
        if (filePath == null || filePath.isBlank()) {
            return ToolResult.builder()
                    .error("File path cannot be null or empty")
                    .build();
        }

        if (content == null) {
            return ToolResult.builder()
                    .error("Content cannot be null")
                    .build();
        }
        try {
            Path path = Paths.get(filePath).normalize();
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

            // Check if file already exists (we'll overwrite it)
            boolean fileExists = Files.exists(path);

            // Create parent directories if they don't exist
            Path parent = path.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            // Write the file
            Files.writeString(path, content);

            // Calculate line count for locations
            int lineCount = content.isEmpty() ? 1 : (int) content.lines().count();

            // Build result
            String action = fileExists ? "overwrote" : "created";
            ToolResult result = ToolResult.builder()
                    .success(true)
                    .content(String.format("Successfully %s file: %s (%d characters)", action, filePath, content.length()))
                    .put("message", String.format("Successfully %s file: %s (%d characters)", action, filePath, content.length()))
                    .put("file_path", filePath)
                    .put("bytes_written", content.getBytes().length)
                    .put("line_count", lineCount)
                    .put("action", action);

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
}
