package com.xr21.ai.agent.tools;

import com.xr21.ai.agent.entity.ToolResult;
import com.xr21.ai.agent.utils.GitignoreUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static com.xr21.ai.agent.agent.LocalAgent.WORKSPACE_ROOT;

/**
 * 从文件系统读取文件或目录的工具
 */
public class ReadFileTool {

    // @formatter:off
    @Tool(name = "read_file", description = """
        从文件系统读取文件或目录。如果是目录，则会递归读取该目录及其所有子目录下的文件。
        你可以使用这个工具直接访问任何文件或目录、且一次性可以读取多个文件或目录。
        假设这个工具能够读取机器上的所有文件。如果用户提供了文件/目录路径，则假设该路径有效。
        读取不存在的文件/目录是可以的;将返回错误。

        Usage
            - 支持同时访问多个文件或目录，增加执行效率
            - file_paths 参数是个list可以同时传多个文件或目录路径，必须是绝对路径，而非相对路径
            - 你应该尽量在一次调用中批量读取多个可能有用的文件或目录。
            - 对于目录：
                - 会递归读取目录下所有子目录和文件
                - 每个文件的内容会单独显示，并包含完整路径
                - 空目录会显示为"Directory is empty"
            - 对于文件：
                - 默认从文件开头开始最多读取500行
                - 使用offset和limit参数进行分页读取
                - 任何超过2000字符的行将被截断
                - 结果采用cat -n格式，行号从1开始
            - 如果读取了存在但内容为空的文件，会收到"File is empty"提示
            - 建议在使用该工具前先使用list_files工具验证文件/目录路径
        """)
    public Map<String, Object> readFile(
        @ToolParam(description = "List of absolute paths of files or directory to read") List<String> filePaths,
        @ToolParam(description = "Line offset to start reading from (default: 0)", required = false) Integer offset,
        @ToolParam(description = "Maximum number of lines to read (default: 500)", required = false) Integer limit
    ) { // @formatter:on
        if (filePaths == null || filePaths.isEmpty()) {
            return ToolResult.builder()
                    .error("No file or directory paths provided")
                    .build();
        }

        StringBuilder content = new StringBuilder();
        ToolResult result = ToolResult.builder();
        int filesRead = 0;

        for (String pathStr : filePaths) {
            try {
                if (!StringUtils.hasText(pathStr)) {
                    continue;
                }
                if (pathStr.startsWith("/")) {
                    pathStr = WORKSPACE_ROOT + File.pathSeparator + pathStr.replaceFirst("/", "");
                }
                Path path = Paths.get(pathStr).normalize();
                if (!Files.exists(path)) {
                    content.append("Path not found - ").append(pathStr).append("\n\n");
                    continue;
                }

                if (Files.isDirectory(path)) {
                    processDirectory(path, content, offset, limit, result);
                } else {
                    processFile(path, content, offset, limit, result);
                    filesRead++;
                }
            } catch (IOException e) {
                content.append("reading path failed").append(pathStr).append(": ").append(e.getMessage()).append("\n\n");
            } catch (SecurityException e) {
                content.append("Permission denied when accessing path: ").append(pathStr).append("\n\n");
            } catch (Exception e) {
                content.append("Unexpected error processing path ")
                        .append(pathStr)
                        .append(": ")
                        .append(e.getMessage())
                        .append("\n\n");
            }
        }

        result.content(content.toString().trim());
        result.metadata("filesRead", filesRead);
        return result.build();
    }

    private void processDirectory(Path dir, StringBuilder result, Integer offset, Integer limit, ToolResult toolResult) throws IOException {
        boolean isEmpty = true;

        // Create gitignore utility for filtering files in this directory
        GitignoreUtil gitignoreUtil = new GitignoreUtil(dir);

        try (var paths = Files.walk(dir)) {
            for (Path path : paths.sorted().toList()) {
                if (Files.isRegularFile(path) && !gitignoreUtil.isIgnored(path)) {
                    processFile(path, result, offset, limit, toolResult);
                    isEmpty = false;
                }
            }
        }

        if (isEmpty) {
            result.append("Directory is empty: ").append(dir).append("\n\n");
        }
    }

    private void processFile(Path file, StringBuilder result, Integer offset, Integer limit, ToolResult toolResult) throws IOException {
        try {
            List<String> allLines = Files.readAllLines(file);
            String absolutePath = file.toAbsolutePath().toString();

            if (allLines.isEmpty()) {
                result.append("File is empty: ").append(file).append("\n\n");
                // 添加位置信息 - 空文件从第1行开始
                toolResult.location(absolutePath, 1);
                return;
            }

            int start = offset != null ? Math.max(0, offset) : 0;
            int maxLimit = limit != null ? limit : 500;
            int end = Math.min(start + maxLimit, allLines.size());

            result.append("=== ").append(absolutePath).append(" ===\n");
            if (start >= allLines.size()) {
                result.append("Error: Offset ")
                        .append(start)
                        .append(" is beyond file length ")
                        .append(allLines.size())
                        .append("\n");
                // 即使超出范围也添加位置信息
                toolResult.location(absolutePath, allLines.size());
            } else {
                // 添加起始行位置
                toolResult.location(absolutePath, start + 1);

                List<String> lines = allLines.subList(start, end);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    // 截断过长的行
                    if (line.length() > 2000) {
                        line = line.substring(0, 1997) + "...";
                    }
                    result.append(String.format("%6d\t%s\n", start + i + 1, line));
                }
                if (end < allLines.size()) {
                    result.append(String.format("\n... %d more lines not shown (total: %d lines, %d characters)\n", allLines.size() - end, allLines.size(), allLines.stream()
                            .mapToInt(String::length)
                            .sum()));
                } else {
                    result.append("\nTotal: ")
                            .append(allLines.size())
                            .append(" lines, ")
                            .append(allLines.stream().mapToInt(String::length).sum())
                            .append(" characters\n");
                }
            }
        } catch (IOException e) {
            result.append("Error reading file ").append(file).append(": ").append(e.getMessage()).append("\n\n");
            throw e;
        } catch (Exception e) {
            result.append("Unexpected error processing file ")
                    .append(file)
                    .append(": ")
                    .append(e.getMessage())
                    .append("\n\n");
            throw e;
        }
    }
}
