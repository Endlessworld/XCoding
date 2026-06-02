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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;

/**
 * 智能文件编辑工具 - 多策略合一的高效文件编辑方案。
 *
 * <p>相比 {@link EditFileTool}（需要完整输出旧文本）和 {@link EditFileWithGitPatchTool}（对格式要求过高），
 * 本工具提供三种更高效的编辑模式，让模型根据场景选择最合适的策略：
 *
 * <ul>
 *   <li><b>replace_lines</b>：按行号范围替换，token 消耗最少，适合整段替换/删除</li>
 *   <li><b>search_replace</b>：按唯一搜索文本替换，最稳定可靠，适合局部修改</li>
 *   <li><b>insert_at_line</b>：在指定行插入，适合添加 import、方法等</li>
 * </ul>
 *
 * <p>支持一次调用批量执行多个编辑操作，自动处理行号偏移。
 *
 * @author Endless
 */
public class SmartEditTool {

    private static final Logger logger = LoggerFactory.getLogger(SmartEditTool.class);
    private static final int MAX_FILE_SIZE_MB = 10;
    private static final int MAX_FILE_SIZE_BYTES = MAX_FILE_SIZE_MB * 1024 * 1024;

    // @formatter:off
    @Tool(name = "smart_edit", description = """
        高效智能文件编辑工具。支持三种编辑策略，一次调用可执行多个编辑操作。

        【三种编辑模式】
        =================

        1. replace_lines — 按行号范围替换（推荐用于整段替换/删除）
            - filePath: 绝对路径
            - startLine: 起始行号（1-based，包含）
            - endLine: 结束行号（1-based，包含），可等于 startLine 表示替换单行
            - newContent: 替换后的新内容
            - 特点：不需要输出任何旧内容，token 消耗最少
            - 适合：重写函数/方法、删除代码块、替换类定义等

        2. search_replace — 按唯一搜索文本替换（推荐用于局部精确修改）
            - filePath: 绝对路径
            - searchText: 要查找的文本（必须在文件中唯一出现，否则会报错并返回所有匹配位置）
            - replaceText: 替换后的新文本
            - 特点：searchText 只需足够具体确保唯一性，不需要 surrounding context
            - 适合：修改变量名、方法调用、单行修改等

        3. insert_at_line — 在指定行插入（推荐用于新增代码）
            - filePath: 绝对路径
            - line: 目标行号（1-based）
            - newContent: 要插入的内容
            - position: "before" 或 "after"（默认 before，即在指定行前插入）
            - 适合：添加 import、新增方法、在方法内添加语句等

        【批量编辑】
        ============
        - 可传入 edits 数组，一次执行多个编辑操作
        - 编辑按顺序执行，自动处理行号偏移
        - 如果某个编辑失败，后续编辑不会执行，返回已成功的编辑结果

        【最佳实践】
        ============
        - 优先使用 replace_lines 进行大范围修改（整函数/方法级别）
        - 小范围精确修改使用 search_replace
        - 新增内容使用 insert_at_line
        - 编辑前先使用 read_file 查看文件内容（带行号）
        - 批量编辑同一文件时，按从后到前的顺序排列可避免行号偏移问题
        """)
    public Map<String, Object> smartEdit(
            @JsonProperty(value = "edits", required = true)
            @JsonPropertyDescription("List of edit operations to perform in order. Each edit must specify a mode.")
            List<EditOperation> edits
    ) { // @formatter:on
        long startTime = System.currentTimeMillis();

        if (edits == null || edits.isEmpty()) {
            return ToolResult.builder()
                    .error("No edits provided. Please specify at least one edit operation.")
                    .build();
        }

        if (edits.size() > 50) {
            return ToolResult.builder()
                    .error("Too many edits in one call. Maximum is 20, got: " + edits.size())
                    .build();
        }

        // Group edits by file path for efficient processing
        Map<String, List<IndexedEdit>> editsByFile = new LinkedHashMap<>();
        for (int i = 0; i < edits.size(); i++) {
            EditOperation edit = edits.get(i);
            String validationError = validateEditOperation(edit, i);
            if (validationError != null) {
                return ToolResult.builder()
                        .error(validationError)
                        .metadata("failedEditIndex", i)
                        .build();
            }
            editsByFile.computeIfAbsent(edit.filePath, k -> new ArrayList<>())
                    .add(new IndexedEdit(i, edit));
        }

        List<EditResult> results = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;

        // Process each file
        for (Map.Entry<String, List<IndexedEdit>> entry : editsByFile.entrySet()) {
            String filePath = entry.getKey();
            List<IndexedEdit> fileEdits = entry.getValue();

            // Sort edits by line number in reverse order (to avoid offset issues)
            fileEdits.sort((a, b) -> {
                int lineA = getEffectiveLine(a.edit);
                int lineB = getEffectiveLine(b.edit);
                return Integer.compare(lineB, lineA);
            });

            // Read file
            Path path = Paths.get(filePath);
            FileContent fileContent = readFileContent(path);
            if (fileContent.error != null) {
                for (IndexedEdit ie : fileEdits) {
                    results.add(new EditResult(ie.index, false, fileContent.error, null));
                    failCount++;
                }
                continue;
            }

            // Track line offset changes
            int lineOffset = 0;
            boolean fileFailed = false;

            for (IndexedEdit ie : fileEdits) {
                if (fileFailed) {
                    results.add(new EditResult(ie.index, false,
                            "Skipped due to previous edit failure in the same file", null));
                    failCount++;
                    continue;
                }

                EditResult result = executeEdit(ie.edit, path, fileContent, lineOffset);
                results.add(result);

                if (result.success) {
                    successCount++;
                    // Update line offset based on the edit
                    lineOffset += result.lineDelta;
                } else {
                    failCount++;
                    fileFailed = true;
                }
            }
        }

        // Build final result
        long duration = System.currentTimeMillis() - startTime;
        return buildFinalResult(results, successCount, failCount, duration, edits.size());
    }

    private String validateEditOperation(EditOperation edit, int index) {
        if (edit == null) {
            return "Edit at index " + index + " is null";
        }
        if (edit.filePath == null || edit.filePath.isBlank()) {
            return "Edit at index " + index + ": filePath is required";
        }
        if (edit.mode == null || edit.mode.isBlank()) {
            return "Edit at index " + index + ": mode is required (replace_lines, search_replace, or insert_at_line)";
        }

        switch (edit.mode) {
            case "replace_lines" -> {
                if (edit.startLine == null || edit.startLine < 1) {
                    return "Edit at index " + index + ": replace_lines requires startLine >= 1";
                }
                if (edit.endLine == null || edit.endLine < edit.startLine) {
                    return "Edit at index " + index + ": replace_lines requires endLine >= startLine";
                }
                if (edit.newContent == null) {
                    return "Edit at index " + index + ": replace_lines requires newContent";
                }
            }
            case "search_replace" -> {
                if (edit.searchText == null || edit.searchText.isEmpty()) {
                    return "Edit at index " + index + ": search_replace requires non-empty searchText";
                }
                if (edit.replaceText == null) {
                    return "Edit at index " + index + ": search_replace requires replaceText";
                }
            }
            case "insert_at_line" -> {
                if (edit.line == null || edit.line < 1) {
                    return "Edit at index " + index + ": insert_at_line requires line >= 1";
                }
                if (edit.newContent == null) {
                    return "Edit at index " + index + ": insert_at_line requires newContent";
                }
            }
            default -> {
                return "Edit at index " + index + ": unknown mode '" + edit.mode + "'. Use replace_lines, search_replace, or insert_at_line";
            }
        }
        return null;
    }

    private int getEffectiveLine(EditOperation edit) {
        return switch (edit.mode) {
            case "replace_lines" -> edit.startLine != null ? edit.startLine : 0;
            case "insert_at_line" -> edit.line != null ? edit.line : 0;
            case "search_replace" -> {
                // For search_replace, we don't know the line until execution
                // Return 0 to keep original order
                yield 0;
            }
            default -> 0;
        };
    }

    private FileContent readFileContent(Path path) {
        if (!Files.exists(path)) {
            return new FileContent(null, "File does not exist: " + path);
        }
        if (!Files.isRegularFile(path)) {
            return new FileContent(null, "Path is not a file: " + path);
        }
        if (!Files.isWritable(path)) {
            return new FileContent(null, "File is not writable: " + path);
        }

        try {
            long fileSize = Files.size(path);
            if (fileSize > MAX_FILE_SIZE_BYTES) {
                return new FileContent(null, "File too large (" + (fileSize / (1024 * 1024)) + "MB > " + MAX_FILE_SIZE_MB + "MB limit): " + path);
            }

            String content = Files.readString(path, StandardCharsets.UTF_8);
            List<String> lines = new ArrayList<>();
            // Split while preserving all lines including empty trailing ones
            String[] split = content.split("\n", -1);
            for (String line : split) {
                // Remove trailing \r for CRLF files
                if (line.endsWith("\r")) {
                    line = line.substring(0, line.length() - 1);
                }
                lines.add(line);
            }
            // Handle the case where content ends with newline
            if (content.endsWith("\n") && !lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
                // The split already handles this correctly with -1 limit
            }

            boolean isWindowsLineEnding = content.contains("\r\n");
            return new FileContent(lines, null, isWindowsLineEnding, content);
        } catch (IOException e) {
            return new FileContent(null, "Failed to read file: " + e.getMessage());
        }
    }

    private EditResult executeEdit(EditOperation edit, Path path, FileContent fileContent, int lineOffset) {
        return switch (edit.mode) {
            case "replace_lines" -> executeReplaceLines(edit, path, fileContent, lineOffset);
            case "search_replace" -> executeSearchReplace(edit, path, fileContent);
            case "insert_at_line" -> executeInsertAtLine(edit, path, fileContent, lineOffset);
            default -> new EditResult(-1, false, "Unknown mode: " + edit.mode, null);
        };
    }

    private EditResult executeReplaceLines(EditOperation edit, Path path, FileContent fileContent, int lineOffset) {
        int startLine = edit.startLine + lineOffset;
        int endLine = edit.endLine + lineOffset;

        if (startLine > fileContent.lines.size()) {
            return new EditResult(-1, false,
                    "startLine " + startLine + " exceeds file length " + fileContent.lines.size(),
                    null);
        }

        // Convert to 0-based index
        int startIndex = startLine - 1;
        int endIndex = Math.min(endLine, fileContent.lines.size());

        // Calculate line delta
        int oldLineCount = endIndex - startIndex;
        int newLineCount = countLines(edit.newContent);
        int lineDelta = newLineCount - oldLineCount;

        // Build new content
        StringBuilder newContent = new StringBuilder();
        for (int i = 0; i < startIndex; i++) {
            newContent.append(fileContent.lines.get(i)).append("\n");
        }
        newContent.append(edit.newContent);
        // Ensure newline before remaining content if there is remaining content
        if (endIndex < fileContent.lines.size()) {
            if (!edit.newContent.isEmpty() && !edit.newContent.endsWith("\n")) {
                newContent.append("\n");
            }
            for (int i = endIndex; i < fileContent.lines.size(); i++) {
                newContent.append(fileContent.lines.get(i));
                if (i < fileContent.lines.size() - 1) {
                    newContent.append("\n");
                }
            }
        }

        // Preserve trailing newline if original had it
        if (fileContent.originalContent.endsWith("\n") && !newContent.toString().endsWith("\n")) {
            newContent.append("\n");
        }

        String result = saveFile(path, newContent.toString(), fileContent.isWindowsLineEnding);
        if (result != null) {
            return new EditResult(-1, false, result, null);
        }

        // Update fileContent for subsequent edits
        fileContent.updateFromString(newContent.toString());

        String message = String.format("Replaced lines %d-%d with %d lines (delta: %+d)",
                startLine, endLine, newLineCount, lineDelta);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("type", "replace_lines");
        detail.put("startLine", startLine);
        detail.put("endLine", endLine);
        detail.put("newLineCount", newLineCount);
        detail.put("lineDelta", lineDelta);
        detail.put("filePath", edit.filePath);

        return new EditResult(-1, true, message, detail, lineDelta);
    }

    private EditResult executeSearchReplace(EditOperation edit, Path path, FileContent fileContent) {
        String original = fileContent.originalContent;
        String searchText = edit.searchText;
        String replaceText = edit.replaceText;

        // Check for uniqueness
        int firstIndex = original.indexOf(searchText);
        if (firstIndex == -1) {
            // Provide helpful context: show if it's a whitespace/line ending issue
            String normalizedOriginal = normalizeLineEndings(original);
            String normalizedSearch = normalizeLineEndings(searchText);
            if (normalizedOriginal.contains(normalizedSearch)) {
                return new EditResult(-1, false,
                        "Text not found (possible line ending mismatch). Try normalize_line_endings or ensure exact match.",
                        null);
            }
            // Show a preview of what we're looking for
            String preview = searchText.length() > 60 ? searchText.substring(0, 60) + "..." : searchText;
            return new EditResult(-1, false,
                    "Search text not found in file. Preview: [" + preview + "]",
                    null);
        }

        // Check if there's more than one match
        int secondIndex = original.indexOf(searchText, firstIndex + searchText.length());
        if (secondIndex != -1) {
            // Find all match positions and line numbers
            List<Integer> matchLines = new ArrayList<>();
            int index = 0;
            while ((index = original.indexOf(searchText, index)) != -1) {
                matchLines.add(findLineNumber(original, index));
                index += searchText.length();
            }
            return new EditResult(-1, false,
                    "Search text appears " + matchLines.size() + " times in the file. Must be unique for search_replace. "
                            + "Consider using replace_lines mode, or make searchText more specific. "
                            + "Match lines: " + matchLines,
                    Map.of("matchCount", matchLines.size(), "matchLines", matchLines));
        }

        // Perform replacement
        String newContent = original.replace(searchText, replaceText);

        // Check if anything changed
        if (newContent.equals(original)) {
            return new EditResult(-1, false, "Replacement resulted in no change", null);
        }

        String result = saveFile(path, newContent, fileContent.isWindowsLineEnding);
        if (result != null) {
            return new EditResult(-1, false, result, null);
        }

        // Update fileContent
        fileContent.updateFromString(newContent);

        int lineNumber = findLineNumber(original, firstIndex);
        String message = String.format("Replaced unique match at line %d (chars %d..%d)",
                lineNumber, firstIndex, firstIndex + searchText.length());
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("type", "search_replace");
        detail.put("line", lineNumber);
        detail.put("charStart", firstIndex);
        detail.put("charEnd", firstIndex + searchText.length());
        detail.put("filePath", edit.filePath);

        // Line delta calculation is approximate for search_replace
        int oldLines = countLines(searchText);
        int newLines = countLines(replaceText);
        int lineDelta = newLines - oldLines;

        return new EditResult(-1, true, message, detail, lineDelta);
    }

    private EditResult executeInsertAtLine(EditOperation edit, Path path, FileContent fileContent, int lineOffset) {
        int line = edit.line + lineOffset;
        String position = edit.position != null ? edit.position : "before";

        if (line > fileContent.lines.size() + 1) {
            return new EditResult(-1, false,
                    "Line " + line + " exceeds file length " + fileContent.lines.size(),
                    null);
        }

        // Convert to insertion index (0-based)
        int insertIndex;
        if ("after".equalsIgnoreCase(position)) {
            insertIndex = line;
        } else {
            insertIndex = line - 1;
        }

        // Ensure insertIndex is within valid range
        insertIndex = Math.max(0, Math.min(insertIndex, fileContent.lines.size()));

        int newLineCount = countLines(edit.newContent);

        // Build new content
        StringBuilder newContent = new StringBuilder();
        for (int i = 0; i < insertIndex; i++) {
            newContent.append(fileContent.lines.get(i)).append("\n");
        }
        newContent.append(edit.newContent);
        if (!edit.newContent.endsWith("\n")) {
            newContent.append("\n");
        }
        for (int i = insertIndex; i < fileContent.lines.size(); i++) {
            newContent.append(fileContent.lines.get(i));
            if (i < fileContent.lines.size() - 1) {
                newContent.append("\n");
            }
        }

        // Preserve trailing newline if original had it
        if (fileContent.originalContent.endsWith("\n") && !newContent.toString().endsWith("\n")) {
            newContent.append("\n");
        }

        String result = saveFile(path, newContent.toString(), fileContent.isWindowsLineEnding);
        if (result != null) {
            return new EditResult(-1, false, result, null);
        }

        // Update fileContent
        fileContent.updateFromString(newContent.toString());

        String message = String.format("Inserted %d lines %s line %d", newLineCount, position, line);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("type", "insert_at_line");
        detail.put("line", line);
        detail.put("position", position);
        detail.put("insertedLines", newLineCount);
        detail.put("filePath", edit.filePath);

        return new EditResult(-1, true, message, detail, newLineCount);
    }

    private String saveFile(Path path, String content, boolean useWindowsLineEnding) {
        try {
            // Store original permissions
            Set<PosixFilePermission> perms = null;
            try {
                perms = Files.getPosixFilePermissions(path);
            } catch (UnsupportedOperationException | IOException e) {
                // Non-POSIX system or permission error, ignore
            }

            // Apply line endings if needed
            String contentToWrite = content;
            if (useWindowsLineEnding) {
                contentToWrite = content.replace("\n", "\r\n").replace("\r\r\n", "\r\n");
            }

            Files.writeString(path, contentToWrite, StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

            // Restore permissions
            if (perms != null) {
                try {
                    Files.setPosixFilePermissions(path, perms);
                } catch (IOException e) {
                    logger.warn("Failed to restore file permissions: {}", e.getMessage());
                }
            }

            return null; // success
        } catch (IOException e) {
            return "Failed to write file: " + e.getMessage();
        }
    }

    private Map<String, Object> buildFinalResult(List<EditResult> results, int successCount, int failCount,
                                                  long duration, int totalEdits) {
        StringBuilder content = new StringBuilder();
        content.append("Smart Edit Results: ").append(successCount).append("/").append(totalEdits).append(" succeeded");
        if (failCount > 0) {
            content.append(", ").append(failCount).append(" failed");
        }
        content.append(" (").append(duration).append("ms)\n");
        content.append("==================================================\n");

        ToolResult result = ToolResult.builder()
                .success(failCount == 0)
                .put("totalEdits", totalEdits)
                .put("successCount", successCount)
                .put("failCount", failCount)
                .put("processingTimeMs", duration);

        for (int i = 0; i < results.size(); i++) {
            EditResult er = results.get(i);
            content.append("\n[").append(i + 1).append("] ");
            content.append(er.success ? "OK" : "FAIL").append(" | ");
            content.append(er.message).append("\n");

            if (er.detail != null) {
                result.metadata("edit_" + i, er.detail);
                String filePath = (String) er.detail.get("filePath");
                Integer line = (Integer) er.detail.get("startLine");
                if (line == null) {
                    line = (Integer) er.detail.get("line");
                }
                if (filePath != null && line != null) {
                    result.location(filePath, line);
                }
            }
        }

        result.content(content.toString());
        return result.build();
    }

    private int countLines(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int count = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                count++;
            }
        }
        // If text ends with newline, the last empty line doesn't count as a new line
        if (text.endsWith("\n")) {
            count--;
        }
        return Math.max(1, count);
    }

    private String normalizeLineEndings(String s) {
        if (s == null) return "";
        return s.replace("\r\n", "\n").replace("\r", "\n");
    }

    private int findLineNumber(String content, int charPosition) {
        if (charPosition <= 0) return 1;
        int line = 1;
        for (int i = 0; i < Math.min(charPosition, content.length()); i++) {
            if (content.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    // ==================== Data Classes ====================

    /**
     * 单个编辑操作的参数结构
     */
    public static class EditOperation {
        @JsonProperty(value = "mode", required = true)
        @JsonPropertyDescription("Edit mode: 'replace_lines', 'search_replace', or 'insert_at_line'")
        public String mode;

        @JsonProperty(value = "filePath", required = true)
        @JsonPropertyDescription("Absolute path of the file to edit")
        public String filePath;

        // For replace_lines
        @JsonProperty("startLine")
        @JsonPropertyDescription("Start line number (1-based, inclusive). Required for replace_lines.")
        public Integer startLine;

        @JsonProperty("endLine")
        @JsonPropertyDescription("End line number (1-based, inclusive). Required for replace_lines. Can equal startLine.")
        public Integer endLine;

        // For search_replace
        @JsonProperty("searchText")
        @JsonPropertyDescription("Text to search for. Must be unique in the file. Required for search_replace.")
        public String searchText;

        @JsonProperty("replaceText")
        @JsonPropertyDescription("Text to replace with. Required for search_replace.")
        public String replaceText;

        // For insert_at_line
        @JsonProperty("line")
        @JsonPropertyDescription("Line number (1-based). Required for insert_at_line.")
        public Integer line;

        @JsonProperty("position")
        @JsonPropertyDescription("Insertion position: 'before' (default) or 'after'. For insert_at_line.")
        public String position;

        // Shared
        @JsonProperty("newContent")
        @JsonPropertyDescription("New content to insert or replace with. Required for replace_lines and insert_at_line.")
        public String newContent;
    }

    private static class FileContent {
        List<String> lines;
        String error;
        boolean isWindowsLineEnding;
        String originalContent;

        FileContent(List<String> lines, String error) {
            this.lines = lines;
            this.error = error;
        }

        FileContent(List<String> lines, String error, boolean isWindowsLineEnding, String originalContent) {
            this.lines = lines;
            this.error = error;
            this.isWindowsLineEnding = isWindowsLineEnding;
            this.originalContent = originalContent;
        }

        void updateFromString(String content) {
            this.originalContent = content;
            this.lines = new ArrayList<>();
            String[] split = content.split("\n", -1);
            for (String line : split) {
                if (line.endsWith("\r")) {
                    line = line.substring(0, line.length() - 1);
                }
                this.lines.add(line);
            }
        }
    }

    private static class IndexedEdit {
        final int index;
        final EditOperation edit;

        IndexedEdit(int index, EditOperation edit) {
            this.index = index;
            this.edit = edit;
        }
    }

    private static class EditResult {
        final int index;
        final boolean success;
        final String message;
        final Map<String, Object> detail;
        final int lineDelta;

        EditResult(int index, boolean success, String message, Map<String, Object> detail) {
            this(index, success, message, detail, 0);
        }

        EditResult(int index, boolean success, String message, Map<String, Object> detail, int lineDelta) {
            this.index = index;
            this.success = success;
            this.message = message;
            this.detail = detail;
            this.lineDelta = lineDelta;
        }
    }
}
