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
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.xr21.ai.agent.bridge.BridgeKt;
import com.xr21.ai.agent.entity.ToolResult;
import com.xr21.ai.agent.utils.GitignoreUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.xr21.ai.agent.agent.LocalAgent.DEFAULT_WORKSPACE_ROOT;
import static com.xr21.ai.agent.agent.LocalAgent.WORKSPACE_ROOT;

/**
 * 列出文件系统中文件的工具
 *
 * @author Endless
 */
@Slf4j
public class ListFilesTool {

    // @formatter:off
    @Tool(name = "ls", description = """
            Lists all files in the filesystem, filtering by directory and .gitignore rules.
            
            Usage:
            - The path parameter must be an absolute path, not a relative path
            - The list_files tool will return a list of all files in the specified directory.
            - Files and directories listed in .gitignore will be excluded.
            - This is very useful for exploring the file system and finding the right file to read or edit.
            - You should almost ALWAYS use this tool before using the Read or Edit tools.
            """)
    public Map<String, Object> listDirectory(@JsonProperty(value = "directory",required = true)
                                         @JsonPropertyDescription("The directory path to list files from default: (current working directory absolute path)")
                                             String directory,
                                         @JsonProperty(value = "maxDepth",required = true)
                                         @JsonPropertyDescription("Maximum depth to traverse (default: 3, max: 5)")
                                         Integer maxDepth, ToolContext context) { // @formatter:on
        log.info("ls files context {}", context.getContext());
        if (context.getContext().get("_AGENT_CONFIG_") instanceof RunnableConfig config) {
            log.info("config context {}", config.context());
            log.info("config context PromptRequest {}", config.context().get("PromptRequest"));
            log.info("config context SyncPromptContext {}", config.context().get("SyncPromptContext"));

        }

        log.info("ls directory {}", directory);
        Path dir = Paths.get(WORKSPACE_ROOT);
        if (directory != null) {
            if (!"/".equals(directory) && !"\\".equals(directory)) {
                log.info("ls WORKSPACE_ROOT {}", directory);
                dir = Paths.get(directory);
            }
            if (!directory.contains(WORKSPACE_ROOT)) {
                if ("/".equals(directory) || ".".equals(directory)) {
                    dir = Paths.get(DEFAULT_WORKSPACE_ROOT);
                } else {
                    dir = Paths.get(DEFAULT_WORKSPACE_ROOT, directory.replaceFirst("/", ""));
                }

            }
        }
        log.info("ls dir {}", dir);
        Path basePath = dir.toAbsolutePath();
        if (!Files.exists(basePath) || !Files.isDirectory(basePath)) {
            return ToolResult.builder().error("Directory not found: " + basePath).build();
        }

        try {
            GitignoreUtil gitignoreUtil = getGitignoreUtil(basePath);
            List<ToolCallLocation> locations = new ArrayList<>();
            // 目录统计信息：路径 -> [子目录数, 文件数]
            Map<Path, long[]> dirStats = new java.util.HashMap<>();

            int depth = maxDepth != null ? Math.min(maxDepth, 10) : 5;
            Files.walk(basePath, depth)
                    .filter(path -> !gitignoreUtil.isIgnored(path))
                    .forEach(path -> {
                        Path absolutePath = path.toAbsolutePath();
                        String pathStr = absolutePath.toString();

                        if (Files.isRegularFile(path)) {
                            // 统计文件
                            long line;
                            try (var stream = Files.lines(path, StandardCharsets.UTF_8)) {
                                line = stream.count();
                            } catch (Throwable e) {
                                try (var stream = Files.lines(path, StandardCharsets.ISO_8859_1)) {
                                    line = stream.count();
                                } catch (Throwable e2) {
                                    line = 0;
                                }
                            }

                            locations.add(BridgeKt.createToolCallLocation(pathStr, (int) line));

                            // 更新父目录统计
                            Path parent = path.getParent();
                            while (parent != null && parent.startsWith(basePath)) {
                                dirStats.computeIfAbsent(parent, k -> new long[2])[1]++;
                                parent = parent.getParent();
                            }
                        } else if (Files.isDirectory(path) && !path.equals(basePath)) {
                            // 统计子目录
                            Path parent = path.getParent();
                            while (parent != null && parent.startsWith(basePath)) {
                                dirStats.computeIfAbsent(parent, k -> new long[2])[0]++;
                                parent = parent.getParent();
                            }
                        }
                    });

            ToolResult result = ToolResult.builder();
            StringBuilder contentBuilder = new StringBuilder();

            // 添加目录统计信息
            if (!dirStats.isEmpty()) {
                contentBuilder.append("## Directories:\n");
                dirStats.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> {
                            Path dirPath = entry.getKey();
                            long[] stats = entry.getValue();
                            String relativePath = basePath.relativize(dirPath).toString();
                            if (relativePath.isEmpty()) {
                                relativePath = ".";
                            }
                            contentBuilder.append(String.format("%s/ (%d dirs, %d files)%n",
                                    relativePath, stats[0], stats[1]));
                        });
                contentBuilder.append("\n");
            }

            // 添加文件列表
            if (!locations.isEmpty()) {
                contentBuilder.append("## Files:\n");
                locations.stream()
                        .map(location -> {
                            String path = location.getPath();
                            int lineCount = BridgeKt.getLine(location);
                            String relativePath = basePath.relativize(Paths.get(path)).toString();
                            return String.format("%s (%d lines)", relativePath, lineCount);
                        })
                        .forEach(line -> contentBuilder.append(line).append("\n"));

                // Add locations - limit to first 100 to avoid too many locations
                result.locations(locations.size() > 100 ? locations.subList(0, 100) : locations);
            }

            if (contentBuilder.length() == 0) {
                contentBuilder.append("No files or directories found in directory: ").append(basePath);
            }

            result.content(contentBuilder.toString());
            result.metadata("fileCount", locations.size());
            result.metadata("directoryCount", dirStats.size());
            result.metadata("directory", basePath.toString());

            return result.build();
        } catch (IOException e) {
            return ToolResult.builder().error("Failed to traverse directory: " + e.getMessage()).build();
        }
    }

    /**
     * Gets a GitignoreUtil instance for the given base path.
     * Uses the static factory method for better performance and caching.
     */
    private GitignoreUtil getGitignoreUtil(Path basePath) {
        return GitignoreUtil.getInstance(basePath);
    }
}
