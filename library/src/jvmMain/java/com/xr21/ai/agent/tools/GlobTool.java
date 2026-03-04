package com.xr21.ai.agent.tools;

import com.agentclientprotocol.sdk.spec.AcpSchema.ToolCallLocation;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.xr21.ai.agent.entity.ToolResult;
import com.xr21.ai.agent.utils.GitignoreUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.xr21.ai.agent.agent.LocalAgent.WORKSPACE_ROOT;

/**
 * 查找匹配 glob 模式的文件的工具
 */
@Slf4j
public class GlobTool {

    // @formatter:off
    @Tool(name = "glob", description = """
        Find files matching a glob pattern.

        Usage:
        - Supports standard glob patterns: `*` (any characters), `**` (any directories), `?` (single character)
        - Returns a list of absolute file paths that match the pattern

        Examples:
        - `**/*.java` - Find all Java files
        - `*.txt` - Find all text files in root
        - `/src/**/*.xml` - Find all XML files under /src
        """)
    public Map<String, Object> glob(
            @JsonProperty(value = "pattern", required = true)
            @JsonPropertyDescription("The glob pattern to match files")
            String pattern
    ) { // @formatter:on
        try {
            Path basePathObj = Paths.get(WORKSPACE_ROOT);
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);

            // Create gitignore utility for filtering
            GitignoreUtil gitignoreUtil = GitignoreUtil.getInstance(basePathObj);

            // Use parallel stream for parallel processing
            List<Path> matchedPaths = Files.walk(basePathObj)
                    .parallel()
                    .filter(Files::isRegularFile)
                    .filter(path -> !gitignoreUtil.isIgnored(path))
                    .filter(path -> {
                        Path relativePath = basePathObj.relativize(path);
                        return matcher.matches(relativePath) || matcher.matches(path);
                    })
                    .collect(Collectors.toList());

            List<String> matchedFiles = new ArrayList<>();
            List<ToolCallLocation> locations = new ArrayList<>();

            for (Path path : matchedPaths) {
                String absolutePath = path.toAbsolutePath().toString();
                matchedFiles.add(absolutePath);
                locations.add(new ToolCallLocation(absolutePath, 1));
            }

            ToolResult result = ToolResult.builder();

            if (matchedFiles.isEmpty()) {
                result.put("files", "No files found matching pattern: " + pattern);
                result.content("No files found matching pattern: " + pattern);
            } else {
                result.put("files", String.join("\n", matchedFiles));
                result.content(String.join("\n", matchedFiles));
            }

            // Add locations - limit to first 100 to avoid too many locations
            if (!locations.isEmpty()) {
                result.locations(locations.size() > 100 ? locations.subList(0, 100) : locations);
            }

            result.metadata("fileCount", matchedFiles.size());
            result.metadata("pattern", pattern);

            return result.build();
        } catch (IOException e) {
            return ToolResult.builder()
                    .error("Error searching for files: " + e.getMessage())
                    .build();
        }
    }
}