package com.xr21.ai.agent.tools;

import com.agentclientprotocol.sdk.spec.AcpSchema.ToolCallLocation;
import com.xr21.ai.agent.entity.ToolResult;
import com.xr21.ai.agent.utils.GitignoreUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;
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
    public Map<String, Object> listFiles(
        @ToolParam(description = "The directory path to list files from (default: current working directory)") String directory,
        @ToolParam(description = "Maximum depth to traverse (default: 3, max: 3)", required = false) Integer maxDepth
    ) { // @formatter:on
        var dir = directory;
        if ("/".equals(dir) || "\\".equals(dir)) {
            dir = WORKSPACE_ROOT;
        }
        Path basePath = Paths.get(dir).toAbsolutePath();
        if (!Files.exists(basePath) || !Files.isDirectory(basePath)) {
            return ToolResult.builder()
                    .error("Directory not found: " + basePath)
                    .build();
        }

        try {
            GitignoreUtil gitignoreUtil = getGitignoreUtil(basePath);
            List<String> filePaths = new ArrayList<>();
            List<ToolCallLocation> locations = new ArrayList<>();

            int depth = maxDepth != null ? Math.min(maxDepth, 3) : 3;
            Files.walk(basePath, depth)
                    .filter(Files::isRegularFile)
                    .filter(file -> !gitignoreUtil.isIgnored(file))
                    .map(Path::toAbsolutePath)
                    .forEach(path -> {
                        String pathStr = path.toString();
                        filePaths.add(pathStr);
                        // Add location for each file (line 1)
                        locations.add(new ToolCallLocation(pathStr, 1));
                    });

            ToolResult result = ToolResult.builder();

            if (filePaths.isEmpty()) {
                result.put("filePaths", "No files found in directory: " + basePath);
                result.content("No files found in directory: " + basePath);
            } else {
                result.put("filePaths", String.join("\r\n", filePaths));
                result.content(String.join("\r\n", filePaths));
            }

            // Add locations - limit to first 100 to avoid too many locations
            if (!locations.isEmpty()) {
                result.locations(locations.size() > 100 ? locations.subList(0, 100) : locations);
            }

            result.metadata("fileCount", filePaths.size());
            result.metadata("directory", basePath.toString());

            return result.build();
        } catch (IOException e) {
            return ToolResult.builder()
                    .error("Failed to traverse directory: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Gets a GitignoreUtil instance for the given base path.
     * If the path is within the workspace, uses the workspace root for consistent .gitignore handling.
     */
    private GitignoreUtil getGitignoreUtil(Path basePath) {
        return new GitignoreUtil(basePath);
    }
}
