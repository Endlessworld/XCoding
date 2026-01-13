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

public class WriteFileTool implements BiFunction<WriteFileTool.WriteFileRequest, ToolContext, Map<String, String>> {
    public static final String DESCRIPTION = """
            Writes to a new file in the filesystem.
            
            Usage:
                - The file_path parameter must be an absolute path, not a relative path
                - The content parameter must be a string
                - The write_file tool will create a new file.
                - When writing to a file, the content will completely replace the existing content.""";

    public static ToolCallback createWriteFileToolCallback(String description) {
        return FunctionToolCallback.builder("write_file", new WriteFileTool())
                .description(description)
                .inputType(WriteFileRequest.class)
                .build();
    }

    public Map<String, String> apply(WriteFileRequest request, ToolContext toolContext) {
        try {
            Path path = Paths.get(request.filePath);
            if (Files.exists(path)) {
                return Map.of("Error created file: ", "File already exists: " + request.filePath + ". Use edit_file to modify existing files.");
            } else {
                Path parent = path.getParent();
                if (parent != null && !Files.exists(parent)) {
                    Files.createDirectories(parent);
                }

                Files.writeString(path, request.content);
                Map<String, String> result = new HashMap<>();
                return Map.of("Successfully created file: ", request.filePath);

            }
        } catch (IOException e) {
            return Map.of("Error writing file: ", e.getMessage());
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
