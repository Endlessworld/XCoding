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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.xr21.ai.agent.agent.LocalAgent.WORKSPACE_ROOT;

/**
 * 文件系统读取工具类
 * <p>
 * 提供读取文件和目录的能力，支持批量读取、分页读取、Gitignore过滤等功能。
 * 被Spring AI框架识别为工具方法，供LLM Agent调用。
 * <p>
 * 增强功能：支持缩进可视化标记，自动检测每行使用的缩进类型（tab/空格），
 * 帮助 LLM 在生成 patch 时使用正确的缩进格式，避免因 tab/空格混用导致的 patch 应用失败。
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
        7. 缩进标记：每行末尾标注缩进类型（[→T]Tab / [·S]Space），帮助确认文件缩进风格 编辑或写入文件内容时务必忽略缩进标记
        8. 容错处理：路径不存在、权限不足、空文件等场景均有友好提示

        你可以使用这个工具直接访问任何文件或目录、且一次性可以读取多个文件或目录。
        假设这个工具能够读取机器上的所有文件。如果用户提供了文件/目录路径，则假设该路径有效。
        读取不存在的文件/目录是可以的;将返回错误。

        Usage
            - 支持同时访问多个文件或目录，增加执行效率
            - file_paths 参数是个list可以同时传多个文件或目录路径，必须是绝对路径，而非相对路径
            - 你应该尽量在一次调用中批量读取多个可能有用的文件或目录。
            - 对于目录：
                - 会递归读取目录下所有子目录和文件
                - 每个文件的内容会单独显示，包含完整路径和缩进统计信息
                - 空目录会显示为"Directory is empty"
            - 对于文件：
                - 默认从文件开头开始最多读取500行
                - 使用offset和limit参数进行分页读取
                - 任何超过2000字符的行将被截断
                - 缩进标记：每行末尾显示缩进类型
                  - [→T] 表示该行使用 Tab 缩进
                  - [·S] 表示该行使用 空格 缩进
                - 结果采用cat -n格式，行号从1开始
            - 如果读取了存在但内容为空的文件，会收到"File is empty"提示
            - 建议在使用该工具前先使用list_files工具验证文件/目录路径
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
            Integer limit
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
                    pathStr = WORKSPACE_ROOT + File.separator + pathStr.substring(1);
                }
                Path path = Paths.get(pathStr).normalize();
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
            // 一次性读取文件所有字节，避免换行符在读取阶段被转换
            byte[] rawBytes = Files.readAllBytes(file);
            // 优先 UTF-8 解码，若包含替换字符则尝试系统默认编码
            String fileContent = decodeFileContent(rawBytes);

            // 去除 UTF-8 BOM（\uFEFF），避免 BOM 作为不可见字符导致 patch 上下文不匹配
            if (fileContent.startsWith("\uFEFF")) {
                fileContent = fileContent.substring(1);
            }
            // 检测原始文件的换行符类型，用于输出提示
            String newlineType = detectNewlineType(fileContent);

            // 按行分割，保留所有行（包括空行）
            String[] allLines = fileContent.split("\n", -1);

            // 构建换行符信息
            String platformNewline = System.getProperty("os.name", "").toLowerCase().contains("win") ? "CRLF" : "LF";
            String newlineInfo = "Newline: " + newlineType + " (platform: " + platformNewline + ")\n";

            // 获取绝对路径用于输出和位置标记
            String absolutePath = file.toAbsolutePath().toString();

            // 空文件检查
            if (allLines.length == 0 || (allLines.length == 1 && allLines[0].isEmpty())) {
                result.append("File is empty: ").append(file).append("\n\n");
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
            int end = Math.min(start + maxLimit, allLines.length);

            result.append("=== ").append(absolutePath).append(" ===\n");
            // 添加缩进统计信息
            // 分析缩进统计信息
//            IndentStats indentStats = analyzeIndentation(allLines);
//            result.append(indentStats.toSummary());
            result.append(newlineInfo);

            if (start >= allLines.length) {
                result.append("Error: Offset ")
                        .append(start)
                        .append(" is beyond file length ")
                        .append(allLines.length)
                        .append("\n");
                toolResult.location(absolutePath, allLines.length);
            } else {
                toolResult.location(absolutePath, start + 1);

                for (int i = start; i < end; i++) {
                    String rawLine = allLines[i];
                    String displayLine = rawLine;
                    String indentMarker = getIndentMarker(rawLine);

                    // 截断过长的行
                    if (displayLine.length() > 2000) {
                        displayLine = displayLine.substring(0, 1997) + "...";
                    }

                    // 输出带缩进标记的行
                    if (indentMarker != null && !indentMarker.isEmpty()) {
                        result.append(String.format("%6d\t%s %s\n", i + 1, displayLine, indentMarker));
                    } else {
                        result.append(String.format("%6d\t%s\n", i + 1, displayLine));
                    }
                }

                if (end < allLines.length) {
                    // 未读完提示：显示剩余行数和总字符数，引导Agent继续分页读取
                    int remaining = allLines.length - end;
                    int totalChars = 0;
                    for (String l : allLines) totalChars += l.length();
                    result.append(String.format("\n... %d more lines not shown (total: %d lines, %d characters)\n",
                            remaining, allLines.length, totalChars));
                } else {
                    int totalChars = 0;
                    for (String l : allLines) totalChars += l.length();
                    result.append("\nTotal: ").append(allLines.length)
                            .append(" lines, ").append(totalChars).append(" characters\n");
                }
            }
        } catch (IOException e) {
            result.append("Error reading file ").append(file).append(": ").append(e.getMessage()).append("\n\n");
            throw e;
        } catch (Exception e) {
            result.append("Unexpected error processing file ")
                    .append(file).append(": ").append(e.getMessage()).append("\n\n");
            throw e;
        }
    }

    /**
     * 检测文件使用的换行符类型。
     */
    private String detectNewlineType(String content) {
        if (content == null || content.isEmpty()) return "Unknown";
        int crlfCount = 0;
        int lfCount = 0;
        int crCount = 0;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '\r') {
                if (i + 1 < content.length() && content.charAt(i + 1) == '\n') {
                    crlfCount++;
                    i++;
                } else {
                    crCount++;
                }
            } else if (c == '\n') {
                lfCount++;
            }
        }
        if (crlfCount > lfCount && crlfCount > crCount) return "CRLF";
        if (lfCount > crlfCount && lfCount > crCount) return "LF";
        if (crCount > 0) return "CR (old Mac)";
        if (crlfCount > 0) return "Mixed (CRLF+" + (lfCount > 0 ? "LF" : "") + ")";
        if (lfCount > 0) return "LF";
        return "Unknown";
    }

    /**
     * 解码文件字节内容。优先使用 UTF-8，如果解码结果包含替换字符（U+FFFD），
     * 说明文件可能不是 UTF-8 编码，尝试使用系统默认编码。
     * 这避免了非 UTF-8 文件（如 GBK 编码的中文文件）内容被错误解码，
     * 导致 LLM 生成的 patch 上下文与文件实际内容不匹配。
     */
    private String decodeFileContent(byte[] bytes) {
        String utf8Decoded = new String(bytes, StandardCharsets.UTF_8);
        if (!utf8Decoded.contains("\uFFFD")) {
            return utf8Decoded;
        }
        String fallback = new String(bytes, Charset.defaultCharset());
        if (!fallback.contains("\uFFFD")) {
            return fallback;
        }
        return utf8Decoded;
    }

    /**
     * 分析文件的缩进使用情况
     */
    private IndentStats analyzeIndentation(String[] lines) {
        IndentStats stats = new IndentStats();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.isEmpty() || line.isBlank()) continue;
            char firstChar = line.charAt(0);
            if (firstChar == '\t') {
                stats.tabLines++;
                String indent = extractIndent(line);
                if (indent.contains(" ") && indent.contains("\t")) {
                    stats.mixedLines++;
                    stats.mixedLineNumbers.add(i + 1);
                }
            } else if (firstChar == ' ') {
                stats.spaceLines++;
                String indent = extractIndent(line);
                if (indent.contains("\t") && indent.contains(" ")) {
                    stats.mixedLines++;
                    stats.mixedLineNumbers.add(i + 1);
                }
            }
        }
        return stats;
    }

    /** 提取行首缩进字符串 */
    private String extractIndent(String line) {
        int i = 0;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) {
            i++;
        }
        return line.substring(0, i);
    }

    /** 获取缩进标记字符串 */
    private String getIndentMarker(String line) {
        if (line == null || line.isEmpty() || line.isBlank()) return "";
        char firstChar = line.charAt(0);
        if (firstChar == '\t') return " [\u2192T]";
        if (firstChar == ' ') return " [\u00B7S]";
        return "";
    }

    /** 缩进统计内部类 */
    private static class IndentStats {
        int tabLines = 0;
        int spaceLines = 0;
        int mixedLines = 0;
        List<Integer> mixedLineNumbers = new ArrayList<>();

        String toSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("Indentation: ");
            if (tabLines > 0 && spaceLines > 0) {
                sb.append("MIXED (Tab:").append(tabLines).append(" lines, Space:").append(spaceLines).append(" lines)");
            } else if (tabLines > 0) {
                sb.append("Tab (all ").append(tabLines).append(" lines)");
            } else if (spaceLines > 0) {
                sb.append("Spaces (all ").append(spaceLines).append(" lines)");
            } else {
                sb.append("No indented lines");
            }
            if (mixedLines > 0) {
                sb.append(" [WARNING: ").append(mixedLines).append(" lines with mixed tab/space at lines: ");
                sb.append(mixedLineNumbers.stream().map(String::valueOf).collect(Collectors.joining(",")));
                sb.append("]");
            }
            sb.append("\n");
            return sb.toString();
        }
    }
}
