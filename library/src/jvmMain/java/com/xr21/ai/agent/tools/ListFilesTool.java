package com.xr21.ai.agent.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.xr21.ai.agent.LocalAgent.WORKSPACE_ROOT;

public class ListFilesTool implements BiFunction<ListFilesTool.ListFilesRequest, ToolContext, Map<String, Object>> {

    public static final String GITIGNORE_FILE = WORKSPACE_ROOT + File.pathSeparator + ".gitignore";
    public static final String DESCRIPTION = "Lists all files in the filesystem, filtering by directory and .gitignore rules.\n\nUsage:\n- The path parameter must be an absolute path, not a relative path\n- The list_files tool will return a list of all files in the specified directory.\n- Files and directories listed in .gitignore will be excluded.\n- This is very useful for exploring the file system and finding the right file to read or edit.\n- You should almost ALWAYS use this tool before using the Read or Edit tools.\n";

    public static ToolCallback createListFilesToolCallback(String desc) {
        return FunctionToolCallback.builder("ls", new ListFilesTool())
                .description(DESCRIPTION)
                .inputType(ListFilesRequest.class)
                .build();
    }

    private List<Pattern> loadGitignorePatterns(Path basePath) {
        Path gitignorePath = basePath.resolve(GITIGNORE_FILE);
        if (!Files.exists(gitignorePath)) {
            return List.of();
        }

        try (Stream<String> lines = Files.lines(gitignorePath)) {
            return lines.map(String::trim).filter(line -> !line.isEmpty() && !line.startsWith("#")).map(pattern -> {
                // Convert .gitignore patterns to regex
                String regex = pattern.replace(".", "\\Q.\\E")
                        .replace("*", "[^/]*")
                        .replace("?", "[^/]")
                        .replace("**/", ".*");
                if (pattern.endsWith("/")) {
                    regex = ".*" + regex + ".*";
                }
                return Pattern.compile(regex);
            }).toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private boolean isIgnored(Path file, Path basePath, List<Pattern> ignorePatterns) {
        if (ignorePatterns.isEmpty()) {
            return false;
        }

        // 获取相对于basePath的相对路径
        Path relativePath = basePath.relativize(file);
        String pathStr = relativePath.toString().replace('\\', '/');

        // 检查每一级目录是否被忽略
        Path currentPath = basePath;
        for (Path path : relativePath) {
            currentPath = currentPath.resolve(path);
            String currentPathStr = basePath.relativize(currentPath).toString().replace('\\', '/');

            // 检查当前路径是否匹配任何忽略模式
            for (Pattern pattern : ignorePatterns) {
                if (pattern.matcher(currentPathStr).matches() || pattern.matcher(currentPathStr + "/").matches()) {
                    return true;
                }
            }
        }

        // 检查完整路径
        return ignorePatterns.stream()
                .anyMatch(pattern -> pattern.matcher(pathStr).matches() || pattern.matcher(pathStr + "/")
                        .matches() || pattern.matcher("/" + pathStr).matches() || pattern.matcher("/" + pathStr + "/")
                        .matches());
    }

    @Override
    public Map<String, Object> apply(ListFilesRequest request, ToolContext toolContext) {
        Map<String, Object> result = new HashMap<>();
        Path basePath = Paths.get(request.getDirectory()).toAbsolutePath();
        if (!Files.exists(basePath) || !Files.isDirectory(basePath)) {
            result.put("error", "Directory not found: " + basePath);
            return result;
        }

        try {
            List<Pattern> ignorePatterns = loadGitignorePatterns(basePath);
            List<String> filePaths = new ArrayList<>();
            int maxDepth = request.getMaxDepth() != null ? request.getMaxDepth() : 3;
            Files.walk(basePath, maxDepth)
                    .filter(Files::isRegularFile)
                    .filter(file -> !isIgnored(file, basePath, ignorePatterns))
                    .map(Path::toAbsolutePath)
                    .map(Path::toString)
                    .forEach(filePaths::add);

            if (filePaths.isEmpty()) {
                result.put("filePaths", "No files found in directory: " + basePath);
            } else {
                result.put("filePaths", String.join("\r\n", filePaths));
            }
            return result;
        } catch (IOException e) {
            result.put("error", "Failed to traverse directory: " + e.getMessage());
            return result;
        }
    }

    public static class ListFilesRequest {
        @JsonProperty(required = true, value = "directory")
        @JsonPropertyDescription("目录路径. only parent directory:" + WORKSPACE_ROOT)
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
