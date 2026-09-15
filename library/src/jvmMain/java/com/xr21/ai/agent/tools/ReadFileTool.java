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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.xr21.ai.agent.entity.ToolResult;
import com.xr21.ai.agent.utils.GitignoreUtil;
import com.xr21.ai.agent.utils.Prompts;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.util.CollectionUtils;
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
 * 文件系统读取工具类
 * <p>
 * 提供读取文件和目录的能力，支持批量读取、分页读取、Gitignore过滤等功能。
 * 被Spring AI框架识别为工具方法，供LLM Agent调用。
 *
 * @author Endless
 */
public class ReadFileTool {

    // @formatter:off
    @Tool(name = "read_file", description = Prompts.TOOL_READ_FILE_DESCRIPTION)
    public Map<String, Object> readFile(List<FilesReader> filesReaders) {
        // @formatter:on
        // 参数校验：路径列表不能为空
        if (CollectionUtils.isEmpty(filesReaders)) {
            return ToolResult.builder()
                    .error("No file or directory paths provided")
                    .build();
        }

        // 使用StringBuilder累积所有读取结果，最后统一写入ToolResult
        StringBuilder content = new StringBuilder();
        // 构建结果对象，支持链式调用设置content、metadata、location等
        ToolResult result = ToolResult.builder();
        // 统计成功读取的文件数量（目录递归的不计入）
        int filesRead = 0;

        // 遍历每个传入的路径，逐个处理
        for (FilesReader files : filesReaders) {
            // workspaceOnly 默认 true：仅允许读取工作目录内的文件
            boolean restrictToWorkspace = files.workspaceOnly == null || files.workspaceOnly;
            try {
                // 跳过空字符串路径
                if (!StringUtils.hasText(files.filePath)) {
                    continue;
                }
                // 路径转换：以"/"开头的绝对路径，拼接WORKSPACE_ROOT作为工作空间相对路径
                // 例如："/src/main" -> WORKSPACE_ROOT + "/src/main"
                if (files.filePath.startsWith("/")) {
                    files.filePath = WORKSPACE_ROOT + File.pathSeparator + files.filePath.replaceFirst("/", "");
                }
                Path path = Paths.get(files.filePath).normalize();

                // 当 workspaceOnly=true 时，校验路径必须在工作目录内
                if (restrictToWorkspace) {
                    String pathAbs = path.toAbsolutePath().toString().replace("\\", "/");
                    String workspaceRoot = WORKSPACE_ROOT.replace("\\", "/");
                    if (!pathAbs.startsWith(workspaceRoot)) {
                        content.append("Path is outside workspace directory: ").append(files.filePath).append("\n\n");
                        continue;
                    }
                }

                if (!Files.exists(path)) {
                    content.append("Path not found - ").append(files.filePath).append("\n\n");
                    continue;
                }

                // 根据路径类型分发处理：目录递归读取，文件直接读取
                if (Files.isDirectory(path)) {
                    processDirectory(path, content, files.offset, files.limit, result);
                } else {
                    processFile(path, content, files.offset, files.limit, result);
                    filesRead++;
                }
            } catch (IOException e) {
                // IO异常：文件不存在、读取失败等
                content.append("reading path failed").append(files.filePath).append(": ").append(e.getMessage()).append("\n\n");
            } catch (SecurityException e) {
                // 安全异常：权限不足（如尝试读取/root目录）
                content.append("Permission denied when accessing path: ").append(files.filePath).append("\n\n");
            } catch (Exception e) {
                // 兜底异常：捕获所有未预期的错误，防止单个路径失败影响其他路径
                content.append("Unexpected error processing path ")
                        .append(files.filePath)
                        .append(": ")
                        .append(e.getMessage())
                        .append("\n\n");
            }
        }

        // 设置最终结果：去除尾部空白，附加元数据
        // metadata供调用方统计，如Agent判断是否需要继续读取
        result.content(content.toString().trim());
        result.metadata("filesRead", filesRead);
        return result.build();
    }

    private void processDirectory(Path dir, StringBuilder result, Integer offset, Integer limit, ToolResult toolResult) throws IOException {
        // 标记目录是否为空（无文件或全被gitignore过滤）
        boolean isEmpty = true;

        /*
         * Gitignore过滤机制：
         * 1. 在每个目录下查找.gitignore文件（支持多层级）
         * 2. 使用单例模式缓存解析结果，避免重复读取
         * 3. 被忽略的文件（如node_modules、.git）自动跳过
         */
        GitignoreUtil gitignoreUtil = GitignoreUtil.getInstance(dir);

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
            // 一次性读取文件所有行到内存，适用于中小文件（大文件需配合limit控制）
            List<String> allLines = Files.readAllLines(file);
            // 获取绝对路径用于输出和位置标记
            String absolutePath = file.toAbsolutePath().toString();

            if (allLines.isEmpty()) {
                result.append("File is empty: ").append(file).append("\n\n");
                /*
                 * 位置标记机制：
                 * 即使文件为空，也记录位置信息（行号=1），
                 * 方便Agent后续写入操作知道目标文件位置。
                 */
                toolResult.location(absolutePath, 1);
                return;
            }

            /*
             * 分页参数计算：
             * - start: 起始索引，默认0（第1行），负数保护
             * - maxLimit: 最大行数，默认100，防止输出过长
             * - end: 实际结束索引，不超过文件总行数
             */
            int start = offset != null ? Math.max(0, offset) : 0;
            int maxLimit = limit != null ? limit : 100;
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
                    // 未读完提示：显示剩余行数和总字符数，引导Agent继续分页读取
                    result.append(String.format("\n... %d more lines not shown (total: %d lines, %d characters)\n", allLines.size() - end, allLines.size(), allLines.stream()
                            .mapToInt(String::length)
                            .sum()));
                } else {
                    result.append("\nTotal: ")
                            .append(allLines.size())
                            .append(" lines, ")
                            // 计算总字符数，帮助Agent评估文件规模
                            .append(allLines.stream().mapToInt(String::length).sum())
                            .append(" characters\n");
                }
            }
        } catch (IOException e) {
            result.append("Error reading file ").append(file).append(": ").append(e.getMessage()).append("\n\n");
            // 向上抛出IO异常，由外层统一处理
            throw e;
        } catch (Exception e) {
            result.append("Unexpected error processing file ")
                    .append(file)
                    .append(": ")
                    .append(e.getMessage())
                    .append("\n\n");
            // 向上抛出其他异常，确保错误不被静默吞掉
            throw e;
        }
    }

    public static class FilesReader {

        @JsonProperty(value = "filePath", required = true)
        @JsonPropertyDescription("要读取的文件或目录的绝对路径列表，支持批量传入多个路径")
        String filePath;

        @JsonProperty(value = "offset")
        @JsonPropertyDescription("起始行偏移行（从0开始计数），默认从文件开头读取")
        Integer offset;

        @JsonProperty(value = "limit")
        @JsonPropertyDescription("最大读取行数，默认100行，防止一次性读取过大文件")
        Integer limit;

        @JsonProperty(value = "workspaceOnly")
        @JsonPropertyDescription("是否仅允许读取工作目录内的文件，默认为true。设为false可读取工作目录之外的文件")
        Boolean workspaceOnly;
    }
}
