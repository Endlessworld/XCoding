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
import org.springframework.ai.tool.annotation.Tool;
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
    @Tool(name = "read_file", description = """
        【文件读取工具】
        功能：从文件系统读取文件内容或递归读取目录下所有文件。

        核心能力：
        1. 批量读取：支持一次传入多个文件/目录路径，提升执行效率
        2. 路径处理：以"/"开头的路径会自动拼接WORKSPACE_ROOT前缀
        3. 目录递归：自动遍历目录及其所有子目录，跳过.gitignore匹配的文件
        4. 分页读取：通过offset和limit参数控制读取范围，默认读取前500行
        5. 行号显示：输出格式类似"cat -n"，每行带6位行号
        6. 超长截断：单行超过2000字符自动截断，避免输出爆炸
        7. 容错处理：路径不存在、权限不足、空文件等场景均有友好提示

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
            - workspaceOnly参数控制是否仅允许读取工作目录内的文件，默认为true
        """)
    public Map<String, Object> readFile(
            @JsonProperty(value = "filePaths", required = true)
            @JsonPropertyDescription("要读取的文件或目录的绝对路径列表，支持批量传入多个路径")
            List<String> filePaths,
            @JsonProperty(value = "offset")
            @JsonPropertyDescription("起始行偏移量（从0开始计数），默认从文件开头读取")
            Integer offset,
            @JsonProperty(value = "limit")
            @JsonPropertyDescription("最大读取行数，默认500行，防止一次性读取过大文件")
            Integer limit,
            @JsonProperty(value = "workspaceOnly")
            @JsonPropertyDescription("是否仅允许读取工作目录内的文件，默认为true。设为false可读取工作目录之外的文件")
            Boolean workspaceOnly
    ) { // @formatter:on
        // 参数校验：路径列表不能为空
        if (filePaths == null || filePaths.isEmpty()) {
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

        // workspaceOnly 默认 true：仅允许读取工作目录内的文件
        boolean restrictToWorkspace = workspaceOnly == null || workspaceOnly;

        // 遍历每个传入的路径，逐个处理
        for (String pathStr : filePaths) {
            try {
                // 跳过空字符串路径
                if (!StringUtils.hasText(pathStr)) {
                    continue;
                }
                // 路径转换：以"/"开头的绝对路径，拼接WORKSPACE_ROOT作为工作空间相对路径
                // 例如："/src/main" -> WORKSPACE_ROOT + "/src/main"
                if (pathStr.startsWith("/")) {
                    pathStr = WORKSPACE_ROOT + File.pathSeparator + pathStr.replaceFirst("/", "");
                }
                Path path = Paths.get(pathStr).normalize();

                // 当 workspaceOnly=true 时，校验路径必须在工作目录内
                if (restrictToWorkspace) {
                    String pathAbs = path.toAbsolutePath().toString().replace("\\", "/");
                    String workspaceRoot = WORKSPACE_ROOT.replace("\\", "/");
                    if (!pathAbs.startsWith(workspaceRoot)) {
                        content.append("Path is outside workspace directory: ").append(pathStr).append("\n\n");
                        continue;
                    }
                }

                if (!Files.exists(path)) {
                    content.append("Path not found - ").append(pathStr).append("\n\n");
                    continue;
                }

                // 根据路径类型分发处理：目录递归读取，文件直接读取
                if (Files.isDirectory(path)) {
                    processDirectory(path, content, offset, limit, result);
                } else {
                    processFile(path, content, offset, limit, result);
                    filesRead++;
                }
            } catch (IOException e) {
                // IO异常：文件不存在、读取失败等
                content.append("reading path failed").append(pathStr).append(": ").append(e.getMessage()).append("\n\n");
            } catch (SecurityException e) {
                // 安全异常：权限不足（如尝试读取/root目录）
                content.append("Permission denied when accessing path: ").append(pathStr).append("\n\n");
            } catch (Exception e) {
                // 兜底异常：捕获所有未预期的错误，防止单个路径失败影响其他路径
                content.append("Unexpected error processing path ")
                        .append(pathStr)
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
             * - maxLimit: 最大行数，默认500，防止输出过长
             * - end: 实际结束索引，不超过文件总行数
             */
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
}
