package com.xr21.ai.agent.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.BiFunction;

import static com.xr21.ai.agent.LocalAgent.WORKSPACE_ROOT;

public class ReadFileTool implements BiFunction<ReadFileTool.ReadFileRequest, ToolContext, String> {
    public static final String DESCRIPTION = """
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
            """;

    public static ToolCallback createReadFileToolCallback(String description) {
        return FunctionToolCallback.builder("read_file", new ReadFileTool())
                .description(StringUtils.hasText(description) ? description : DESCRIPTION)
                .inputType(ReadFileRequest.class)
                .build();
    }

    public String apply(ReadFileRequest request, ToolContext toolContext) {
        if (request.getFilePaths() == null || request.getFilePaths().isEmpty()) {
            return "Error: No file or directory paths provided";
        }

        StringBuilder result = new StringBuilder();
        for (String pathStr : request.getFilePaths()) {
            try {
                Path path = Paths.get(pathStr).normalize();
                if (!Files.exists(path)) {
                    result.append("Error: Path not found - ").append(pathStr).append("\n\n");
                    continue;
                }

                if (Files.isDirectory(path)) {
                    processDirectory(path, result, request.offset, request.limit);
                } else {
                    processFile(path, result, request.offset, request.limit);
                }
            } catch (IOException e) {
                result.append("Error reading path ").append(pathStr).append(": ").append(e.getMessage()).append("\n\n");
            } catch (SecurityException e) {
                result.append("Permission denied when accessing path: ").append(pathStr).append("\n\n");
            } catch (Exception e) {
                result.append("Unexpected error processing path ")
                        .append(pathStr)
                        .append(": ")
                        .append(e.getMessage())
                        .append("\n\n");
            }
        }

        return result.toString().trim();
    }

    private void processDirectory(Path dir, StringBuilder result, Integer offset, Integer limit) throws IOException {
        boolean isEmpty = true;
        try (var paths = Files.walk(dir)) {
            for (Path path : paths.sorted().toList()) {
                if (Files.isRegularFile(path)) {
                    processFile(path, result, offset, limit);
                    isEmpty = false;
                }
            }
        }

        if (isEmpty) {
            result.append("Directory is empty: ").append(dir).append("\n\n");
        }
    }

    private void processFile(Path file, StringBuilder result, Integer offset, Integer limit) throws IOException {
        try {
            List<String> allLines = Files.readAllLines(file);
            if (allLines.isEmpty()) {
                result.append("File is empty: ").append(file).append("\n\n");
                return;
            }

            int start = offset != null ? Math.max(0, offset) : 0;
            int maxLimit = limit != null ? limit : 500;
            int end = Math.min(start + maxLimit, allLines.size());

            result.append("=== ").append(file.toAbsolutePath()).append(" ===\n");
            if (start >= allLines.size()) {
                result.append("Error: Offset ")
                        .append(start)
                        .append(" is beyond file length ")
                        .append(allLines.size())
                        .append("\n");
            } else {
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

    @Data
    public static class ReadFileRequest {
        @JsonProperty("offset")
        @JsonPropertyDescription("Line offset to start reading from (default: 0)")
        public Integer offset;
        @JsonProperty("limit")
        @JsonPropertyDescription("Maximum number of lines to read (default: 500)")
        public Integer limit;
        @JsonProperty(required = true, value = "file_paths")
        @JsonPropertyDescription("List of absolute paths of files or directory to read. only parent path:" + WORKSPACE_ROOT)
        private List<String> filePaths;
    }
}
