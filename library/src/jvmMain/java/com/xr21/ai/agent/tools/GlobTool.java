package com.xr21.ai.agent.tools;

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

import static com.xr21.ai.agent.LocalAgent.WORKSPACE_ROOT;

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
            Path basePathObj = Paths.get(WORKSPACE_ROOT);
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

    public static class GlobPattern {
        @ToolParam(description = "The glob pattern to match files")
        private String pattern;

        public GlobPattern() {
        }

        public String getPattern() {
            return this.pattern;
        }

        public void setPattern(String pattern) {
            this.pattern = pattern;
        }

        public boolean equals(final Object o) {
            if (o == this) return true;
            if (!(o instanceof GlobPattern)) return false;
            final GlobPattern other = (GlobPattern) o;
            if (!other.canEqual((Object) this)) return false;
            final Object this$pattern = this.getPattern();
            final Object other$pattern = other.getPattern();
            if (this$pattern == null ? other$pattern != null : !this$pattern.equals(other$pattern)) return false;
            return true;
        }

        protected boolean canEqual(final Object other) {
            return other instanceof GlobPattern;
        }

        public int hashCode() {
            final int PRIME = 59;
            int result = 1;
            final Object $pattern = this.getPattern();
            result = result * PRIME + ($pattern == null ? 43 : $pattern.hashCode());
            return result;
        }

        public String toString() {
            return "GlobTool.GlobPattern(pattern=" + this.getPattern() + ")";
        }
    }
}
