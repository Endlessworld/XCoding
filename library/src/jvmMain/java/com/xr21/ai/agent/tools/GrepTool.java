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
import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static com.xr21.ai.agent.LocalAgent.WORKSPACE_ROOT;

public class GrepTool implements BiFunction<GrepTool.GrepRequest, ToolContext, Map<String, Object>> {
    public static final String DESCRIPTION = "Search for a pattern in files.\n\nUsage:\n- The pattern parameter is the text to search for (literal string, not regex)\n- The path parameter filters which directory to search in\n- The glob parameter accepts a glob pattern to filter which files to search\n\nExamples:\n- Search all files: `grep(pattern=\"TODO\")`\n- The search is case-sensitive by default.\n";

    public static ToolCallback createGrepToolCallback(String description) {
        return FunctionToolCallback.builder("grep", new GrepTool())
                .description(description)
                .inputType(GrepRequest.class)
                .build();
    }

    public Map<String, Object> apply(GrepRequest request, ToolContext toolContext) {
        Map<String, Object> result = new HashMap<>();
        try {
            Path searchPath = request.path != null ? Paths.get(request.path) : Paths.get(WORKSPACE_ROOT);
            List<String> matches = new ArrayList();
            PathMatcher globMatcher = request.glob != null ? FileSystems.getDefault()
                    .getPathMatcher("glob:" + request.glob) : null;
            Files.walk(searchPath)
                    .filter((x$0) -> Files.isRegularFile(x$0))
                    .filter((path) -> globMatcher == null || globMatcher.matches(path.getFileName()))
                    .forEach((path) -> {
                        try {
                            List<String> lines = Files.readAllLines(path);

                            for (int i = 0; i < lines.size(); ++i) {
                                if (lines.get(i).contains(request.pattern)) {
                                    String var10000;
                                    switch (request.outputMode) {
                                        case "files_with_matches" -> var10000 = path.toString();
                                        case "content" -> var10000 = path + ":" + (i + 1) + ": " + lines.get(i);
                                        case "count" -> var10000 = path + ": matched";
                                        default -> var10000 = path.toString();
                                    }
                                    matches.add(var10000);
                                    if ("files_with_matches".equals(request.outputMode)) {
                                        break;
                                    }
                                }
                            }
                        } catch (IOException var8) {
                        }

                    });
            if (matches.isEmpty()) {
                result.put("matches", "No matches found for pattern: " + request.pattern);
            } else {
                result.put("matches", String.join("\n", matches));
            }
            return result;

        } catch (IOException e) {
            result.put("error", "Error searching files: " + e.getMessage());
            return result;
        }
    }

    public static class GrepRequest {
        @JsonProperty(
                required = true
        )
        @JsonPropertyDescription("The text pattern to search for")
        public String pattern;
        @JsonProperty("path")
        @JsonPropertyDescription("The directory path to search in (default: base path)")
        public String path;
        @JsonProperty("glob")
        @JsonPropertyDescription("File pattern to filter which files to search (e.g., '*.java')")
        public String glob;
        @JsonProperty("output_mode")
        @JsonPropertyDescription("Output format: 'files_with_matches', 'content', or 'count' (default: 'files_with_matches')")
        public String outputMode = "files_with_matches";

        public GrepRequest() {
        }

        public GrepRequest(String pattern, String path, String glob, String outputMode) {
            this.pattern = pattern;
            this.path = path;
            this.glob = glob;
            this.outputMode = outputMode;
        }
    }
}
