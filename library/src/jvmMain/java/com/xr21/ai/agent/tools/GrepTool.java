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

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static com.xr21.ai.agent.agent.LocalAgent.WORKSPACE_ROOT;
import static com.xr21.ai.agent.utils.AcpProgressUtil.sendProgress;

/**
 * 在文件中搜索文本模式的工具
 *
 * @author Endless
 */
public class GrepTool {

    private static final int MAX_RESULTS = 25;

    /**
     * Progress reporting interval: report every N files.
     * Higher values reduce ACP progress message overhead.
     */
    private static final int PROGRESS_INTERVAL = 50;

    /**
     * Maximum file size to search (bytes). Files larger than this are skipped
     * to avoid excessive I/O on huge logs/binaries.
     * 10MB is sufficient for virtually all source code files.
     */
    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;

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
            List<String> matches = Collections.synchronizedList(new ArrayList<>());
            List<ToolCallLocation> locations = Collections.synchronizedList(new ArrayList<>());
            Map<String, Integer> fileMatchCounts = new ConcurrentHashMap<>();
            AtomicInteger matchCounter = new AtomicInteger(0);
            AtomicLong fileCounter = new AtomicLong(0);
            AtomicLong skippedFileCounter = new AtomicLong(0);

            PathMatcher globMatcher = glob != null ? FileSystems.getDefault()
                    .getPathMatcher("glob:" + glob) : null;

            GitignoreUtil gitignoreUtil = GitignoreUtil.getInstance(searchPath);

            // Single pass: walk and search simultaneously
            // Uses sequential stream to avoid I/O contention on directory walking
            try (Stream<Path> fileStream = Files.walk(searchPath)
                    .filter(Files::isRegularFile)
                    .filter(p -> !gitignoreUtil.isIgnored(p))
                    .filter(p -> globMatcher == null || globMatcher.matches(p.getFileName()))) {

                fileStream.forEach(p -> {
                    if (matchCounter.get() >= MAX_RESULTS) {
                        return;
                    }

                    long scanned = fileCounter.incrementAndGet();
                    // Report progress at intervals
                    if (scanned % PROGRESS_INTERVAL == 0) {
                        sendProgress(toolContext, "  Progress: " + scanned
                                + " files, " + matchCounter.get() + " matches found<br/>");
                    }

                    searchFile(p, pattern, outputMode, matches, locations,
                            fileMatchCounts, matchCounter, skippedFileCounter, toolContext);
                });
            }

            // Send final summary
            int totalMatches = matchCounter.get();
            long skippedFiles = skippedFileCounter.get();
            if (totalMatches == 0) {
                String suffix = skippedFiles > 0 ? " (" + skippedFiles + " large files skipped)" : "";
                sendProgress(toolContext, "❌ No matches found for pattern: \"" + pattern + "\"" + suffix + "<br/>");
            } else {
                String suffix = skippedFiles > 0 ? " (" + skippedFiles + " large files skipped)" : "";
                sendProgress(toolContext, "✅ Search complete: " + totalMatches
                        + " matches in " + fileMatchCounts.size() + " files" + suffix + "<br/>");
            }

            ToolResult result = ToolResult.builder();

            boolean truncated = totalMatches > MAX_RESULTS;
            if (truncated) {
                result.metadata("truncated", true);
                result.metadata("totalMatches", totalMatches);
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
            result.metadata("matchCount", Math.min(totalMatches, MAX_RESULTS));
            result.metadata("fileCount", fileMatchCounts.size());

            return result.build();

        } catch (IOException e) {
            return ToolResult.builder()
                    .error("Error searching files: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Search a single file for the pattern using line-by-line streaming.
     * Avoids loading the entire file into memory.
     */
    private void searchFile(
            Path file,
            String pattern,
            String outputMode,
            List<String> matches,
            List<ToolCallLocation> locations,
            Map<String, Integer> fileMatchCounts,
            AtomicInteger matchCounter,
            AtomicLong skippedFileCounter,
            ToolContext toolContext
    ) {
        String absolutePath = file.toAbsolutePath().toString();
        boolean isFilesMode = "files_with_matches".equals(outputMode) || outputMode == null;
        boolean isCountMode = "count".equals(outputMode);
        boolean fileAlreadyReported = fileMatchCounts.containsKey(absolutePath);

        // Skip oversized files to avoid excessive I/O on huge logs/binaries
        long fileSize;
        try {
            fileSize = Files.size(file);
            if (fileSize > MAX_FILE_SIZE_BYTES) {
                skippedFileCounter.incrementAndGet();
                return;
            }
        } catch (IOException e) {
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            int lineNum = 0;

            while ((line = reader.readLine()) != null) {
                lineNum++;

                if (!line.contains(pattern)) {
                    continue;
                }

                int currentTotal = matchCounter.incrementAndGet();
                if (currentTotal > MAX_RESULTS) {
                    return;
                }

                fileMatchCounts.merge(absolutePath, 1, Integer::sum);

                if (isFilesMode) {
                    if (!fileAlreadyReported) {
                        fileAlreadyReported = true;
                        matches.add(file.toString());
                        locations.add(BridgeKt.createToolCallLocation(absolutePath, lineNum));
                        sendProgress(toolContext, "  ✅ Match in: " + file.getFileName() + "<br/>");
                    }
                } else if (isCountMode) {
                    // count mode: just track counts, no output
                } else {
                    // content mode
                    matches.add(file + ":" + lineNum + ": " + line);
                    locations.add(BridgeKt.createToolCallLocation(absolutePath, lineNum));
                }
            }
        } catch (Exception e) {
            // Ignore file read errors (including encoding errors)
        }
    }
}
