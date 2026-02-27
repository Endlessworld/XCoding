package com.xr21.ai.agent.tools;

import com.agentclientprotocol.sdk.spec.AcpSchema.ToolCallLocation;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static com.xr21.ai.agent.agent.LocalAgent.WORKSPACE_ROOT;

public class ListFilesTool implements BiFunction<ListFilesTool.ListFilesRequest, ToolContext, Map<String, Object>> {

    public static final String DESCRIPTION = "Lists all files in the filesystem, filtering by directory and .gitignore rules.\n\nUsage:\n- The path parameter must be an absolute path, not a relative path\n- The list_files tool will return a list of all files in the specified directory.\n- Files and directories listed in .gitignore will be excluded.\n- This is very useful for exploring the file system and finding the right file to read or edit.\n- You should almost ALWAYS use this tool before using the Read or Edit tools.\n";

    public static ToolCallback createListFilesToolCallback(String desc) {
        return FunctionToolCallback.builder("ls", new ListFilesTool())
                .description(DESCRIPTION)
                .inputType(ListFilesRequest.class)
                .build();
    }

    /**
     * Gets a GitignoreUtil instance for the given base path.
     * If the path is within the workspace, uses the workspace root for consistent .gitignore handling.
     */
    private GitignoreUtil getGitignoreUtil(Path basePath) {
        return new GitignoreUtil(basePath);
    }

    @Override
    public Map<String, Object> apply(ListFilesRequest request, ToolContext toolContext) {
        var directory = request.getDirectory();
        if ("/".equals(directory) || "\\".equals(directory)) {
            directory = WORKSPACE_ROOT;
        }
        Path basePath = Paths.get(directory).toAbsolutePath();
        if (!Files.exists(basePath) || !Files.isDirectory(basePath)) {
            return ToolResult.builder()
                    .error("Directory not found: " + basePath)
                    .build();
        }

        try {
            GitignoreUtil gitignoreUtil = getGitignoreUtil(basePath);
            List<String> filePaths = new ArrayList<>();
            List<ToolCallLocation> locations = new ArrayList<>();

            int maxDepth = request.getMaxDepth() != null ? Math.min(request.getMaxDepth(), 3) : 3;
            Files.walk(basePath, maxDepth)
                    .filter(Files::isRegularFile)
                    .filter(file -> !gitignoreUtil.isIgnored(file))
                    .map(Path::toAbsolutePath)
                    .forEach(path -> {
                        String pathStr = path.toString();
                        filePaths.add(pathStr);
                        // Add location for each file (line 1)
                        locations.add(new ToolCallLocation(pathStr, 1));
                    });

            ToolResult result = ToolResult.builder();

            if (filePaths.isEmpty()) {
                result.put("filePaths", "No files found in directory: " + basePath);
                result.content("No files found in directory: " + basePath);
            } else {
                result.put("filePaths", String.join("\r\n", filePaths));
                result.content(String.join("\r\n", filePaths));
            }

            // Add locations - limit to first 100 to avoid too many locations
            if (!locations.isEmpty()) {
                result.locations(locations.size() > 100 ? locations.subList(0, 100) : locations);
            }

            result.metadata("fileCount", filePaths.size());
            result.metadata("directory", basePath.toString());

            return result.build();
        } catch (IOException e) {
            return ToolResult.builder()
                    .error("Failed to traverse directory: " + e.getMessage())
                    .build();
        }
    }

    public static class ListFilesRequest {
        @JsonProperty(required = true, value = "directory")
        @JsonPropertyDescription("目录路径 默认为当前工作目录")
        private String directory;

        @JsonProperty(value = "maxDepth")
        private Integer maxDepth = 3; // 默认遍历3层目录

        public ListFilesRequest(String directory, Integer maxDepth) {
            this.directory = directory;
            this.maxDepth = maxDepth;
        }

        public ListFilesRequest() {
        }

        public String getDirectory() {
            return this.directory;
        }

        @JsonProperty(required = true, value = "directory")
        public void setDirectory(String directory) {
            this.directory = directory;
        }

        public Integer getMaxDepth() {
            return this.maxDepth;
        }

        @JsonProperty("maxDepth")
        public void setMaxDepth(Integer maxDepth) {
            this.maxDepth = maxDepth;
        }

        public boolean equals(final Object o) {
            if (o == this) return true;
            if (!(o instanceof ListFilesRequest)) return false;
            final ListFilesRequest other = (ListFilesRequest) o;
            if (!other.canEqual((Object) this)) return false;
            final Object this$directory = this.getDirectory();
            final Object other$directory = other.getDirectory();
            if (this$directory == null ? other$directory != null : !this$directory.equals(other$directory))
                return false;
            final Object this$maxDepth = this.getMaxDepth();
            final Object other$maxDepth = other.getMaxDepth();
            if (this$maxDepth == null ? other$maxDepth != null : !this$maxDepth.equals(other$maxDepth)) return false;
            return true;
        }

        protected boolean canEqual(final Object other) {
            return other instanceof ListFilesRequest;
        }

        public int hashCode() {
            final int PRIME = 59;
            int result = 1;
            final Object $directory = this.getDirectory();
            result = result * PRIME + ($directory == null ? 43 : $directory.hashCode());
            final Object $maxDepth = this.getMaxDepth();
            result = result * PRIME + ($maxDepth == null ? 43 : $maxDepth.hashCode());
            return result;
        }

        public String toString() {
            return "ListFilesTool.ListFilesRequest(directory=" + this.getDirectory() + ", maxDepth=" + this.getMaxDepth() + ")";
        }
    }
}
