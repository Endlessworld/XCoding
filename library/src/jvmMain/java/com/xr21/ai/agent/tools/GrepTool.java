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

import com.agentclientprotocol.model.ToolCallLocation;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.xr21.ai.agent.bridge.BridgeKt;
import com.xr21.ai.agent.entity.ToolResult;
import com.xr21.ai.agent.utils.GitignoreUtil;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static com.xr21.ai.agent.agent.LocalAgent.WORKSPACE_ROOT;
import static com.xr21.ai.agent.utils.AcpProgressUtil.sendProgress;

/**
 * 在文件中搜索文本模式的工具
 *
 * @author Endless
 */
public class GrepTool {

    private static final int MAX_RESULTS = 25;

    // @formatter:off
    @Tool(name = "grep", description = """
        Search for a pattern in files.

        Usage:
        - The pattern parameter is the text to search for (literal string, not regex)
        - The path parameter filters which directory to search in
        - The glob parameter accepts a glob pattern to filter which files to search
        - Real-time progress and matches are pushed via ACP protocol during search

        Examples:
        - Search all files: `grep(pattern="TODO")`
        - The search is case-sensitive by default.
        """)
    public Map<String, Object> grep(
            @JsonProperty(value = "pattern", required = true)
            @JsonPropertyDescription("The text pattern to search for")
            String pattern,
            @JsonProperty(value = "path")
            @JsonPropertyDescription("The directory path to search in (default: base path)")
            String path,
            @JsonProperty(value = "glob")
            @JsonPropertyDescription("File pattern to filter which files to search (e.g., '*.java')")
            String glob,
            @JsonProperty(value = "outputMode")
            @JsonPropertyDescription("Output format: 'files_with_matches', 'content', or 'count' (default: 'files_with_matches')")
            String outputMode,
            ToolContext toolContext
    ) { // @formatter:on
        try {
            sendProgress(toolContext, "🔍 Searching for pattern: \"" + pattern + "\"...<br/>");

            Path searchPath = path != null ? Paths.get(path) : Paths.get(WORKSPACE_ROOT);
            List<String> matches = new ArrayList<>();
            List<ToolCallLocation> locations = new ArrayList<>();
            Map<String, Integer> fileMatchCounts = new ConcurrentHashMap<>();
            AtomicInteger matchCounter = new AtomicInteger(0);
            AtomicLong fileCounter = new AtomicLong(0);

            PathMatcher globMatcher = glob != null ? FileSystems.getDefault()
                    .getPathMatcher("glob:" + glob) : null;

            GitignoreUtil gitignoreUtil = GitignoreUtil.getInstance(searchPath);

            // First pass: count total files for progress reporting
            long totalFiles = Files.walk(searchPath)
                    .parallel()
                    .filter(Files::isRegularFile)
                    .filter(p -> !gitignoreUtil.isIgnored(p))
                    .filter(p -> globMatcher == null || globMatcher.matches(p.getFileName()))
                    .count();
            sendProgress(toolContext, "📁 Scanning " + totalFiles + " files for \"" + pattern + "\"...<br/>");

            // Second pass: search with progress reporting
            Files.walk(searchPath)
                    .filter(Files::isRegularFile)
                    .filter(p -> !gitignoreUtil.isIgnored(p))
                    .filter(p -> globMatcher == null || globMatcher.matches(p.getFileName()))
                    .forEach(p -> {
                        if (matchCounter.get() >= MAX_RESULTS) {
                            return;
                        }
                        long scanned = fileCounter.incrementAndGet();
                        // Report progress every 10 files or at 25%/50%/75%/100%
                        if (scanned % 10 == 0 || scanned == totalFiles) {
                            sendProgress(toolContext, "  Progress: " + scanned + "/" + totalFiles
                                    + " files, " + matchCounter.get() + " matches found<br/>");
                        }
                        try {
                            String absolutePath = p.toAbsolutePath().toString();
                            boolean[] fileAdded = {false};

                            // Read file lines with index to track actual line numbers
                            List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
                            IntStream.range(0, lines.size())
                                    .parallel()
                                    .filter(i -> lines.get(i).contains(pattern))
                                    .forEachOrdered(i -> {
                                        int currentTotal = matchCounter.incrementAndGet();
                                        if (currentTotal > MAX_RESULTS) {
                                            return;
                                        }

                                        int currentLine = i + 1; // line numbers are 1-based
                                        fileMatchCounts.merge(absolutePath, 1, Integer::sum);

                                        String matchEntry;
                                        switch (outputMode != null ? outputMode : "files_with_matches") {
                                            case "files_with_matches":
                                                synchronized (this) {
                                                    if (!fileAdded[0]) {
                                                        matchEntry = p.toString();
                                                        fileAdded[0] = true;
                                                        // Push each matched file in real-time
                                                        sendProgress(toolContext, "  ✅ Match in: "
                                                                + p.getFileName() + "<br/>");
                                                    } else {
                                                        return;
                                                    }
                                                }
                                                break;
                                            case "content":
                                                matchEntry = p + ":" + currentLine + ": " + lines.get(i);
                                                break;
                                            case "count":
                                                return;
                                            default:
                                                synchronized (this) {
                                                    if (!fileAdded[0]) {
                                                        matchEntry = p.toString();
                                                        fileAdded[0] = true;
                                                    } else {
                                                        return;
                                                    }
                                                }
                                                break;
                                        }

                                        matches.add(matchEntry);
                                        locations.add(BridgeKt.createToolCallLocation(absolutePath, currentLine));
                                    });
                        } catch (Exception e) {
                            // Ignore file read errors (including encoding errors)
                        }
                    });

            // Send final summary
            if (matchCounter.get() == 0) {
                sendProgress(toolContext, "❌ No matches found for pattern: \"" + pattern + "\"<br/>");
            } else {
                sendProgress(toolContext, "✅ Search complete: " + matchCounter.get()
                        + " matches in " + fileMatchCounts.size() + " files<br/>");
            }

            ToolResult result = ToolResult.builder();

            boolean truncated = matchCounter.get() > MAX_RESULTS;
            if (truncated) {
                result.metadata("truncated", true);
                result.metadata("totalMatches", matchCounter.get());
            }

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

            if (!locations.isEmpty()) {
                result.locations(locations.size() > MAX_RESULTS ? locations.subList(0, MAX_RESULTS) : locations);
            }
            result.metadata("matchCount", Math.min(matchCounter.get(), MAX_RESULTS));
            result.metadata("fileCount", fileMatchCounts.size());

            return result.build();

        } catch (IOException e) {
            return ToolResult.builder()
                    .error("Error searching files: " + e.getMessage())
                    .build();
        }
    }
}
