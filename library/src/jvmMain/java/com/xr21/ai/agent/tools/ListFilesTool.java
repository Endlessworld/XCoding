package com.xr21.ai.agent.tools;

import com.agentclientprotocol.sdk.spec.AcpSchema.ToolCallLocation;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
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

import static com.xr21.ai.agent.agent.LocalAgent.WORKSPACE_ROOT;

/**
 * 列出文件系统中文件的工具
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
    public Map<String, Object> listFiles(@JsonProperty(value = "directory",required = true)
                                         @JsonPropertyDescription("The directory path to list files from default: / (current working directory: ${cwd}/)")
                                             String directory,
                                         @JsonProperty(value = "maxDepth",required = true)
                                         @JsonPropertyDescription("Maximum depth to traverse (default: 5, max: 10)")
                                         Integer maxDepth, ToolContext context) { // @formatter:on
        log.info("ls files context {}", context.getContext());
        if (context.getContext().get("_AGENT_CONFIG_") instanceof RunnableConfig config) {
            log.info("config context {}", config.context());
            log.info("config context PromptRequest {}", config.context().get("PromptRequest"));
            log.info("config context SyncPromptContext {}", config.context().get("SyncPromptContext"));

        }

        log.info("ls directory {}", directory);
        var dir = directory;
        if (dir == null || "/".equals(dir) || "\\".equals(dir)) {
            log.info("ls WORKSPACE_ROOT {}", WORKSPACE_ROOT);
            dir = WORKSPACE_ROOT;
        }
        log.info("ls dir {}", dir);
        Path basePath = Paths.get(dir).toAbsolutePath();
        if (!Files.exists(basePath) || !Files.isDirectory(basePath)) {
            return ToolResult.builder().error("Directory not found: " + basePath).build();
        }

        try {
            GitignoreUtil gitignoreUtil = getGitignoreUtil(basePath);
            List<String> filePaths = new ArrayList<>();
            List<ToolCallLocation> locations = new ArrayList<>();

            int depth = maxDepth != null ? Math.max(maxDepth, 6) : 6;
            Files.walk(basePath, depth)
                    .filter(Files::isRegularFile)
                    .filter(file -> !gitignoreUtil.isIgnored(file))
                    .map(Path::toAbsolutePath)
                    .forEach(path -> {
                        String pathStr = path.toString();
                        long line;
                        try (var stream = Files.lines(path, StandardCharsets.UTF_8)) {
                            line = stream.count();
                        } catch (Throwable e) {
                            // 如果UTF-8读取失败，尝试使用ISO-8859-1编码
                            try (var stream = Files.lines(path, StandardCharsets.ISO_8859_1)) {
                                line = stream.count();
                            } catch (Throwable e2) {
                                // 如果还是失败，可能是二进制文件，设置为0行
                                line = 0;
                            }
                        }
                        locations.add(new ToolCallLocation(pathStr, (int) line));
                    });

            ToolResult result = ToolResult.builder();

            if (locations.isEmpty()) {
                result.put("locations", "No files found in directory: " + basePath);
                result.content("No files found in directory: " + basePath);
            } else {
//                result.put("filePaths", String.join("\r\n", filePaths));
//                result.content(String.join("\r\n", filePaths));
            }

            // Add locations - limit to first 100 to avoid too many locations
            if (!locations.isEmpty()) {
                result.locations(locations.size() > 100 ? locations.subList(0, 100) : locations);
            }

            result.metadata("fileCount", filePaths.size());
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
