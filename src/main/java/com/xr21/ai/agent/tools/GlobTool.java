package com.xr21.ai.agent.tools;

import lombok.Data;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public class GlobTool implements BiFunction<GlobTool.GlobPattern, ToolContext, Map<String, String>> {
    public static final String DESCRIPTION = "Find files matching a glob pattern.\n\nUsage:\n- Supports standard glob patterns: `*` (any characters), `**` (any directories), `?` (single character)\n- Returns a list of absolute file paths that match the pattern\n\nExamples:\n- `**/*.java` - Find all Java files\n- `*.txt` - Find all text files in root\n- `/src/**/*.xml` - Find all XML files under /src\n";

    public static ToolCallback createGlobToolCallback(String description) {
        return FunctionToolCallback.builder("glob", new GlobTool())
                .description(DESCRIPTION)
                .inputType(GlobPattern.class)
                .build();
    }

    public Map<String, String> apply(@ToolParam(description = "The glob pattern to match files") GlobPattern globPattern, ToolContext toolContext) {
        try {
            String pattern = globPattern.getPattern();
            Path basePathObj = Paths.get(System.getProperty("user.dir"));
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
            List<String> matchedFiles = new ArrayList();
            Files.walk(basePathObj).filter((x$0) -> Files.isRegularFile(x$0)).filter((path) -> {
                Path relativePath = basePathObj.relativize(path);
                return matcher.matches(relativePath) || matcher.matches(path);
            }).forEach((path) -> matchedFiles.add(path.toString()));
            return Map.of("files", matchedFiles.isEmpty() ? "No files found matching pattern: " + pattern : String.join("\n", matchedFiles));
        } catch (IOException e) {
            return Map.of("Error", "searching for files: " + e.getMessage());
        }
    }

    @Data
    public static class GlobPattern {
        @ToolParam(description = "The glob pattern to match files")
        private String pattern;
    }
}
