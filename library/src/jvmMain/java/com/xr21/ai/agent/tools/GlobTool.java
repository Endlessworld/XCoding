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
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.xr21.ai.agent.agent.LocalAgent.WORKSPACE_ROOT;
import static com.xr21.ai.agent.utils.AcpNotifyHelper.sendProgress;

/**
 * 查找匹配 glob 模式的文件的工具
 *
 * @author Endless
 */
@Slf4j
public class GlobTool {

    private static final int MAX_RESULTS = 25;
    private static final int MAX_DEPTH = 20;

    // Directories that are almost always ignored — skip them without the overhead
    // of a full GitignoreUtil check.
    private static final Set<String> FAST_SKIP_DIRS = Set.of(
            ".git", "node_modules", ".gradle", "build", "target", ".idea", ".vs",
            "__pycache__", ".next", ".nuxt", "dist", ".cache"
    );

    // @formatter:off
    @Tool(name = "glob", description = """
        Find files matching glob patterns.

        Usage:
        - Supports standard glob patterns: `*` (any characters), `**` (any directories), `?` (single character)
        - Returns a list of absolute file paths that match the pattern (maximum 25 results)
        - Real-time progress is pushed via ACP protocol during search
        - Supports multiple patterns — files matching any pattern are included

        Examples:
        - `**/*.java` - Find all Java files
        - `*.txt` - Find all text files in root
        - `/src/**/*.xml` - Find all XML files under /src
        """)
    public Map<String, Object> glob(
            @JsonProperty(value = "patterns", required = true)
            @JsonPropertyDescription("The glob patterns to match files")
            List<String> patterns,
            ToolContext toolContext
    ) { // @formatter:on
        try {
            String patternsDesc = String.join(", ", patterns);
            sendProgress(toolContext, "🔍 Searching for patterns: " + patternsDesc + "...\r\n");

            Path basePathObj = Paths.get(WORKSPACE_ROOT);
            List<PathMatcher> matchers = new ArrayList<>();
            for (String p : patterns) {
                // Strip leading "/" so that patterns like "/src/**/*.xml" match relative paths
                String normalized = p.startsWith("/") ? p.substring(1) : p;
                matchers.add(FileSystems.getDefault().getPathMatcher("glob:" + normalized));
            }

            // Create gitignore utility for filtering
            GitignoreUtil gitignoreUtil = GitignoreUtil.getInstance(basePathObj);

            // Walk files with depth limit, match on-the-fly, stop early when limit reached
            List<Path> matchedPaths = new ArrayList<>();
            int[] scannedCount = {0};

            Files.walkFileTree(basePathObj, java.util.Collections.emptySet(), MAX_DEPTH, new SimpleFileVisitor<>() {
                @Override
                public @NotNull FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) {
                    // Stop early if we already have enough results
                    if (matchedPaths.size() >= MAX_RESULTS) {
                        return FileVisitResult.TERMINATE;
                    }

                    scannedCount[0]++;

                    // Compute relative path once and reuse for both glob matching and gitignore check
                    Path relativePath = basePathObj.relativize(file);

                    // Glob match first (cheap) — skip gitignore check entirely for non-matches
                    boolean matched = false;
                    for (PathMatcher m : matchers) {
                        if (m.matches(relativePath)) {
                            matched = true;
                            break;
                        }
                    }
                    if (!matched) {
                        return FileVisitResult.CONTINUE;
                    }

                    // Only run the expensive gitignore check on files that match the glob pattern
                    if (gitignoreUtil.isIgnoredRelative(relativePath.toString(), false)) {
                        return FileVisitResult.CONTINUE;
                    }

                    matchedPaths.add(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    // Fast-path: skip well-known heavy directories without gitignore overhead
                    Path dirName = dir.getFileName();
                    if (dirName != null && FAST_SKIP_DIRS.contains(dirName.toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    // Skip gitignored directories entirely (prune the tree)
                    if (!dir.equals(basePathObj)) {
                        Path relDir = basePathObj.relativize(dir);
                        if (gitignoreUtil.isIgnoredRelative(relDir.toString(), true)) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });

            sendProgress(toolContext, "📁 Scanned " + scannedCount[0] + " files for patterns: " + patternsDesc + "\r\n");

            // Sort results for deterministic output
            matchedPaths.sort(Path::compareTo);

            // Report matching results via ACP in real-time
            if (matchedPaths.isEmpty()) {
                sendProgress(toolContext, "❌ No files found matching patterns: " + patternsDesc + "\r\n");
            } else {
                sendProgress(toolContext, "✅ Found " + matchedPaths.size() + " file(s) matching: " + patternsDesc + "\r\n");
                int previewCount = Math.min(matchedPaths.size(), 5);
                StringBuilder preview = new StringBuilder("📄 Matches:\r\n");
                for (int i = 0; i < previewCount; i++) {
                    String relPath = basePathObj.relativize(matchedPaths.get(i)).toString();
                    preview.append("  ").append(relPath).append("\r\n");
                }
                if (matchedPaths.size() > previewCount) {
                    preview.append("  ... and ").append(matchedPaths.size() - previewCount).append(" more\r\n");
                }
                sendProgress(toolContext, preview.toString());
            }

            List<String> matchedFiles = new ArrayList<>();
            List<ToolCallLocation> locations = new ArrayList<>();

            for (Path path : matchedPaths) {
                String absolutePath = path.toAbsolutePath().toString();
                matchedFiles.add(absolutePath);
                locations.add(BridgeKt.createToolCallLocation(absolutePath, 1));
            }

            ToolResult result = ToolResult.builder();

            if (matchedFiles.isEmpty()) {
                result.put("files", "No files found matching patterns: " + patternsDesc);
                result.content("No files found matching patterns: " + patternsDesc);
            } else {
                result.put("files", String.join("\n", matchedFiles));
                result.content(String.join("\n", matchedFiles));
            }

            // Add locations (already limited to MAX_RESULTS by early termination)
            if (!locations.isEmpty()) {
                result.locations(locations);
            }

            result.metadata("fileCount", matchedFiles.size());
            result.metadata("patterns", patterns);

            return result.build();
        } catch (IOException e) {
            return ToolResult.builder()
                    .error("Error searching for files: " + e.getMessage())
                    .build();
        }
    }
}
