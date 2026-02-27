package com.xr21.ai.agent.tools;

import com.agentclientprotocol.sdk.spec.AcpSchema.ToolCallLocation;
import com.xr21.ai.agent.entity.ToolResult;
import com.xr21.ai.agent.utils.GitignoreUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.xr21.ai.agent.agent.LocalAgent.WORKSPACE_ROOT;

/**
 * 在文件中搜索文本模式的工具
 */
public class GrepTool {

    // @formatter:off
    @Tool(name = "grep", description = """
        Search for a pattern in files.

        Usage:
        - The pattern parameter is the text to search for (literal string, not regex)
        - The path parameter filters which directory to search in
        - The glob parameter accepts a glob pattern to filter which files to search

        Examples:
        - Search all files: `grep(pattern="TODO")`
        - The search is case-sensitive by default.
        """)
    public Map<String, Object> grep(
        @ToolParam(description = "The text pattern to search for") String pattern,
        @ToolParam(description = "The directory path to search in (default: base path)", required = false) String path,
        @ToolParam(description = "File pattern to filter which files to search (e.g., '*.java')", required = false) String glob,
        @ToolParam(description = "Output format: 'files_with_matches', 'content', or 'count' (default: 'files_with_matches')", required = false) String outputMode
    ) { // @formatter:on
        try {
            Path searchPath = path != null ? Paths.get(path) : Paths.get(WORKSPACE_ROOT);
            List<String> matches = new ArrayList<>();
            List<ToolCallLocation> locations = new ArrayList<>();
            Map<String, Integer> fileMatchCounts = new HashMap<>();

            PathMatcher globMatcher = glob != null ? FileSystems.getDefault()
                    .getPathMatcher("glob:" + glob) : null;

            // Create gitignore utility for filtering
            GitignoreUtil gitignoreUtil = GitignoreUtil.getInstance(searchPath);

            Files.walk(searchPath)
                    .filter(Files::isRegularFile)
                    .filter(p -> !gitignoreUtil.isIgnored(p))
                    .filter(p -> globMatcher == null || globMatcher.matches(p.getFileName()))
                    .forEach(p -> {
                        try {
                            List<String> lines = Files.readAllLines(p);
                            String absolutePath = p.toAbsolutePath().toString();
                            boolean fileAdded = false;

                            for (int i = 0; i < lines.size(); ++i) {
                                if (lines.get(i).contains(pattern)) {
                                    String matchEntry;
                                    switch (outputMode != null ? outputMode : "files_with_matches") {
                                        case "files_with_matches":
                                            if (!fileAdded) {
                                                matchEntry = p.toString();
                                                fileAdded = true;
                                            } else {
                                                continue;
                                            }
                                            break;
                                        case "content":
                                            matchEntry = p + ":" + (i + 1) + ": " + lines.get(i);
                                            break;
                                        case "count":
                                            fileMatchCounts.merge(p.toString(), 1, Integer::sum);
                                            continue;
                                        default:
                                            if (!fileAdded) {
                                                matchEntry = p.toString();
                                                fileAdded = true;
                                            } else {
                                                continue;
                                            }
                                            break;
                                    }
                                    matches.add(matchEntry);

                                    // Add location for this match
                                    locations.add(new ToolCallLocation(absolutePath, i + 1));

                                    if ("files_with_matches".equals(outputMode)) {
                                        break;
                                    }
                                }
                            }
                        } catch (IOException var8) {
                            // Ignore file read errors
                        }
                    });

            ToolResult result = ToolResult.builder();

            if ("count".equals(outputMode) && !fileMatchCounts.isEmpty()) {
                List<String> countEntries = new ArrayList<>();
                for (Map.Entry<String, Integer> entry : fileMatchCounts.entrySet()) {
                    countEntries.add(entry.getKey() + ": " + entry.getValue() + " matches");
                }
                result.put("matches", String.join("\n", countEntries));
                result.content(String.join("\n", countEntries));
            } else if (matches.isEmpty()) {
                result.put("matches", "No matches found for pattern: " + pattern);
                result.content("No matches found for pattern: " + pattern);
            } else {
                result.put("matches", String.join("\n", matches));
                result.content(String.join("\n", matches));
            }

            // Add locations
            result.locations(locations);
            result.metadata("matchCount", locations.size());
            result.metadata("fileCount", fileMatchCounts.isEmpty() ? -1 : fileMatchCounts.size());

            return result.build();

        } catch (IOException e) {
            return ToolResult.builder()
                    .error("Error searching files: " + e.getMessage())
                    .build();
        }
    }
}
