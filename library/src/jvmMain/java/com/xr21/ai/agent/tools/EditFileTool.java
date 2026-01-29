package com.xr21.ai.agent.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.xr21.ai.agent.LocalAgent.WORKSPACE_ROOT;

public class EditFileTool implements BiFunction<EditFileTool.EditFileRequest, ToolContext, Map<String, Object>> {

    public static ToolCallback createEditFileToolCallback(String description) {
        return FunctionToolCallback.builder("edit_file", new EditFileTool())
                .description(description)
                .inputType(EditFileRequest.class)
                .build();
    }

    @Override
    public Map<String, Object> apply(EditFileRequest request, ToolContext toolContext) {
        Map<String, Object> result = new HashMap<>();
        Path path = Paths.get(request.filePath);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            result.put("error", "File not found: " + request.filePath);
            return result;
        }

        String content;
        try {
            content = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            result.put("error", "Error reading file: " + e.getMessage());
            return result;
        }

        // 归一换行符，减少因 CRLF/LF 差异导致的匹配失败
        String normalizedContent = normalizeLineEndings(content);
        String normalizedOld = normalizeLineEndings(request.oldString);

        // 如果归一化后仍找不到，尝试直接匹配原始内容（兼容未归一化写法）
        boolean found = normalizedContent.contains(normalizedOld);
        if (!found && !content.contains(request.oldString)) {
            result.put("error", "String not found in file: " + request.oldString);
            result.put("stringSearched", request.oldString);
            return result;
        }

        // 使用正则进行字面量匹配（跨行 DOTALL），确保对特殊字符安全
        Pattern pattern = Pattern.compile(Pattern.quote(request.oldString), Pattern.DOTALL | Pattern.UNICODE_CHARACTER_CLASS);
        Matcher matcher = pattern.matcher(content);
        if (!matcher.find()) {
            // 兼容“归一换行”的匹配：如果 original 匹配失败，再对归一化后的字符串匹配
            Pattern normalizedPattern = Pattern.compile(Pattern.quote(normalizedOld), Pattern.DOTALL | Pattern.UNICODE_CHARACTER_CLASS);
            Matcher normalizedMatcher = normalizedPattern.matcher(normalizedContent);
            if (!normalizedMatcher.find()) {
                result.put("error", "String not found in file (even after normalization): " + request.oldString);
                result.put("stringSearched", request.oldString);
                return result;
            } else {
                // 如果在归一化后才找到，提示用户可能存在换行/空白差异
                // 继续使用原始 content 做替换，但采用归一化后定位的文本做替换
                // 为简单起见，此处直接将 oldString 以归一换行形式替换，效果等价
                content = normalizedContent;
                matcher = normalizedMatcher;
            }
        }

        // 统计出现次数
        int count = 0;
        matcher.reset();
        while (matcher.find()) {
            count++;
        }

        // 如果不允许全局替换且出现多次，返回提示
        if (!request.replaceAll && count > 1) {
            result.put("error", "String appears multiple times in file. Use replace_all=true or provide more context. Occurrences: " + count);
            return result;
        }

        // 执行替换
        String newContent;
        if (request.replaceAll) {
            newContent = content.replaceAll(Pattern.quote(request.oldString),
                    Matcher.quoteReplacement(request.newString));
        } else {
            newContent = content.replaceFirst(Pattern.quote(request.oldString),
                    Matcher.quoteReplacement(request.newString));
        }

        // 保存文件时保留原有权限
        Set<PosixFilePermission> perms = null;
        try {
            perms = Files.getPosixFilePermissions(path);
        } catch (UnsupportedOperationException | IOException ignore) {
            // 非 POSIX 文件系统，可忽略
        }

        try {
            Files.writeString(path, newContent, StandardOpenOption.TRUNCATE_EXISTING);
            if (perms != null) {
                try {
                    Files.setPosixFilePermissions(path, perms);
                } catch (IOException e) {
                    System.err.println("Warning: Failed to restore file permissions: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            result.put("error", "Error writing file: " + e.getMessage());
            return result;
        }

        int replacements = request.replaceAll ? count : 1;
        result.put("message", "File edited successfully");
        result.put("filePath", request.filePath);
        result.put("replacements", replacements);
        result.put("totalOccurrences", count);
        result.put("replacedAll", request.replaceAll);
        return result;
    }

    /**
     * 将字符串中的 CRLF 统一转换为 LF，减少因换行符差异导致的匹配失败。
     * 如需更强的“空白归一”，可以在此扩展对制表符、尾随空格的处理。
     */
    private String normalizeLineEndings(String s) {
        if (s == null) {
            return "";
        }
        // 将 \r\n 或 \r 统统替换为 \n
        return s.replace("\r\n", "\n").replace("\r", "\n");
    }

    public static class EditFileRequest {
        @JsonProperty(value = "file_path", required = true)
        @JsonPropertyDescription("The absolute path of the file to edit only path:" + WORKSPACE_ROOT)
        public String filePath;

        @JsonProperty(value = "old_string", required = true)
        @JsonPropertyDescription("The exact string to find and replace")
        public String oldString;

        @JsonProperty(value = "new_string", required = true)
        @JsonPropertyDescription("The new string to replace with")
        public String newString;

        @JsonProperty("replace_all")
        @JsonPropertyDescription("If true, replace all occurrences; if false, only replace if unique (default: false)")
        public boolean replaceAll = false;

        public EditFileRequest() {
        }

        public EditFileRequest(String filePath, String oldString, String newString, boolean replaceAll) {
            this.filePath = filePath;
            this.oldString = oldString;
            this.newString = newString;
            this.replaceAll = replaceAll;
        }
    }
}
