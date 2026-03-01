package com.xr21.ai.agent.tools;

import com.xr21.ai.agent.entity.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 编辑文件的工具
 */
public class EditFileTool {

    private static final Logger logger = LoggerFactory.getLogger(EditFileTool.class);
    
    // 缓存文件权限，避免重复获取
    private static final Map<Path, Set<PosixFilePermission>> permissionCache = new WeakHashMap<>();

    // @formatter:off
    @Tool(name = "edit_file", description = """
        优化版编辑文件工具，提供高效的文本替换功能。
        
        主要优化：
        1. 智能换行符处理，自动适配不同系统
        2. 详细的错误提示，帮助快速定位问题
        3. 性能优化，减少内存使用和IO操作
        4. 支持大文件处理

        Usage:
            - file_path: 文件的绝对路径
            - old_string: 要查找和替换的文本
            - new_string: 用于替换的新文本
            - replace_all: 是否替换所有出现（默认false，仅替换第一个）
            - normalize_line_endings: 是否自动归一化换行符（默认true）
        """)
    public Map<String, Object> editFile(
        @ToolParam(description = "The absolute path of the file to edit") String filePath,
        @ToolParam(description = "The exact string to find and replace") String oldString,
        @ToolParam(description = "The new string to replace with") String newString,
        @ToolParam(description = "If true, replace all occurrences; if false, only replace if unique (default: false)", required = false) Boolean replaceAll,
        @ToolParam(description = "If true, automatically normalize line endings before matching (default: true)", required = false) Boolean normalizeLineEndings
    ) { // @formatter:on
        long startTime = System.currentTimeMillis();
        Path path = Paths.get(filePath);
        
        // 验证文件存在
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return ToolResult.builder()
                    .error("文件不存在: " + filePath)
                    .metadata("filePath", filePath)
                    .metadata("absolutePath", path.toAbsolutePath().toString())
                    .build();
        }

        // 检查文件是否可写
        if (!Files.isWritable(path)) {
            return ToolResult.builder()
                    .error("文件不可写，请检查文件权限: " + filePath)
                    .metadata("filePath", filePath)
                    .build();
        }

        // 读取文件内容
        String originalContent;
        try {
            // 检查文件大小，避免读取过大文件
            long fileSize = Files.size(path);
            if (fileSize > 10 * 1024 * 1024) { // 10MB
                return ToolResult.builder()
                        .error("文件过大（超过10MB），建议使用其他工具处理")
                        .metadata("filePath", filePath)
                        .metadata("fileSizeBytes", fileSize)
                        .metadata("fileSizeMB", String.format("%.2f MB", fileSize / (1024.0 * 1024.0)))
                        .build();
            }
            
            originalContent = Files.readString(path, StandardCharsets.UTF_8);
            logger.debug("读取文件成功，大小: {} 字符 ({} 字节)", originalContent.length(), fileSize);
            setReadComplete();
        } catch (IOException e) {
            return ToolResult.builder()
                    .error("读取文件失败: " + e.getMessage())
                    .metadata("filePath", filePath)
                    .metadata("errorType", "IO_ERROR")
                    .build();
        }

        // 处理参数
        boolean replaceAllFlag = replaceAll != null && replaceAll;
        boolean normalizeFlag = normalizeLineEndings == null || normalizeLineEndings;
        
        // 准备匹配内容
        String contentToMatch = originalContent;
        String oldStringToMatch = oldString;
        
        if (normalizeFlag) {
            contentToMatch = normalizeLineEndings(originalContent);
            oldStringToMatch = normalizeLineEndings(oldString);
            logger.debug("已归一化换行符，原内容长度: {}, 归一化后: {}", 
                originalContent.length(), contentToMatch.length());
        }

        // 查找匹配
        setMatchStart();
        MatchResult matchResult = findMatches(contentToMatch, oldStringToMatch, originalContent);
        setReplaceStart();
        
        if (matchResult.count == 0) {
            // 提供更详细的错误信息
            return buildNotFoundError(oldString, originalContent, contentToMatch, normalizeFlag);
        }

        // 检查是否需要全局替换
        if (!replaceAllFlag && matchResult.count > 1) {
            return buildMultipleMatchesError(matchResult, oldString);
        }

        // 执行替换
        String newContent = performReplacement(
            originalContent, 
            contentToMatch, 
            oldString, 
            oldStringToMatch, 
            newString, 
            replaceAllFlag, 
            normalizeFlag
        );
        setWriteStart();

        // 保存文件
        boolean success = saveFileWithPermissions(path, newContent, originalContent);
        
        if (!success) {
            return ToolResult.builder()
                    .error("保存文件失败，可能是权限问题或磁盘空间不足")
                    .metadata("filePath", filePath)
                    .build();
        }

        // 构建成功结果
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        logger.info("文件编辑完成 - 文件: {}, 大小: {} 字符, 处理时间: {}ms, 替换次数: {}", 
            filePath, originalContent.length(), totalTime, 
            replaceAllFlag ? matchResult.count : 1);
        
        return buildSuccessResult(
            filePath, path, oldString, newString, 
            replaceAllFlag, matchResult, 
            startTime, endTime
        );
    }

    /**
     * Find the line number (1-based) of a given character position in the content.
     */
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

    /**
     * 将字符串中的 CRLF 统一转换为 LF，减少因换行符差异导致的匹配失败。
     * 如需更强的"空白归一"，可以在此扩展对制表符、尾随空格的处理。
     */
    private String normalizeLineEndings(String s) {
        if (s == null) {
            return "";
        }
        // 将 \r\n 或 \r 统统替换为 \n
        return s.replace("\r\n", "\n").replace("\r", "\n");
    }

    /**
     * 查找匹配结果
     */
    private MatchResult findMatches(String contentToMatch, String oldStringToMatch, String originalContent) {
        MatchResult result = new MatchResult();
        
        // 使用高效的字符串搜索
        List<Integer> positions = new ArrayList<>();
        int index = 0;
        while ((index = contentToMatch.indexOf(oldStringToMatch, index)) != -1) {
            positions.add(index);
            index += oldStringToMatch.length();
        }
        result.count = positions.size();
        
        if (result.count > 0) {
            result.positions = positions;
            // 计算行号
            result.lineNumbers = new ArrayList<>();
            for (int pos : positions) {
                result.lineNumbers.add(findLineNumber(originalContent, pos));
            }
        }
        
        return result;
    }

    /**
     * 执行替换操作
     */
    private String performReplacement(
        String originalContent,
        String contentToMatch,
        String oldString,
        String oldStringToMatch,
        String newString,
        boolean replaceAllFlag,
        boolean normalizeFlag
    ) {
        if (normalizeFlag && !contentToMatch.equals(originalContent)) {
            // 如果归一化了内容，需要在归一化后的内容上进行替换
            String normalizedNewString = normalizeLineEndings(newString);
            String normalizedResult;
            
            if (replaceAllFlag) {
                normalizedResult = contentToMatch.replace(oldStringToMatch, normalizedNewString);
            } else {
                normalizedResult = contentToMatch.replaceFirst(
                    Pattern.quote(oldStringToMatch), 
                    Matcher.quoteReplacement(normalizedNewString)
                );
            }
            
            // 将结果转换回原始换行符格式
            return restoreLineEndings(normalizedResult, originalContent);
        } else {
            // 直接替换原始内容
            if (replaceAllFlag) {
                return originalContent.replace(oldString, newString);
            } else {
                return originalContent.replaceFirst(
                    Pattern.quote(oldString), 
                    Matcher.quoteReplacement(newString)
                );
            }
        }
    }

    /**
     * 保存文件并保留权限
     */
    private boolean saveFileWithPermissions(Path path, String newContent, String originalContent) {
        try {
            // 检查内容是否真的改变了
            if (newContent.equals(originalContent)) {
                logger.warn("替换后内容未改变，跳过保存");
                return true;
            }
            
            // 获取并缓存文件权限
            Set<PosixFilePermission> perms = getFilePermissions(path);
            
            // 写入文件
            Files.writeString(path, newContent, 
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
            
            // 恢复权限
            if (perms != null) {
                try {
                    Files.setPosixFilePermissions(path, perms);
                } catch (IOException e) {
                    logger.warn("恢复文件权限失败: {}", e.getMessage());
                }
            }
            
            logger.debug("文件保存成功，大小: {} 字符", newContent.length());
            return true;
        } catch (IOException e) {
            logger.error("保存文件失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 获取文件权限（带缓存）
     */
    private Set<PosixFilePermission> getFilePermissions(Path path) {
        return permissionCache.computeIfAbsent(path, p -> {
            try {
                return Files.getPosixFilePermissions(p);
            } catch (UnsupportedOperationException | IOException e) {
                return null;
            }
        });
    }

    /**
     * 构建未找到错误
     */
    private Map<String, Object> buildNotFoundError(
        String oldString, 
        String originalContent, 
        String normalizedContent,
        boolean normalized
    ) {
        ToolResult result = ToolResult.builder()
                .error("未在文件中找到指定的文本")
                .metadata("searchedText", oldString)
                .metadata("searchedLength", oldString.length())
                .metadata("fileContentLength", originalContent.length())
                .metadata("normalized", normalized);
        
        // 提供调试信息
        if (oldString.length() > 0) {
            // 显示前几个字符
            String preview = oldString.length() > 50 ? 
                oldString.substring(0, 50) + "..." : oldString;
            result.metadata("textPreview", preview);
        }
        
        // 检查换行符差异
        if (normalized && !originalContent.equals(normalizedContent)) {
            result.metadata("lineEndingNormalized", true);
            result.metadata("originalLineEndings", detectLineEndings(originalContent));
            result.put("suggestion", "尝试关闭 normalize_line_endings 参数");
        }
        
        return result.build();
    }

    /**
     * 构建多个匹配错误
     */
    private Map<String, Object> buildMultipleMatchesError(MatchResult matchResult, String oldString) {
        ToolResult result = ToolResult.builder()
                .error("文本在文件中出现多次，请使用 replace_all=true 或提供更具体的上下文")
                .metadata("occurrences", matchResult.count)
                .metadata("searchedText", oldString)
                .metadata("matchPositions", matchResult.positions)
                .metadata("matchLines", matchResult.lineNumbers);
        
        // 显示所有匹配位置
        if (matchResult.lineNumbers != null && !matchResult.lineNumbers.isEmpty()) {
            result.put("lineNumbers", matchResult.lineNumbers);
            result.put("suggestion", "文本出现在以下行: " + matchResult.lineNumbers);
        }
        
        return result.build();
    }

    /**
     * 构建成功结果
     */
    private Map<String, Object> buildSuccessResult(
        String filePath, Path path, String oldString, String newString,
        boolean replaceAllFlag, MatchResult matchResult,
        long startTime, long endTime
    ) {
        int replacements = replaceAllFlag ? matchResult.count : 1;
        String absolutePath = path.toAbsolutePath().toString();
        
        ToolResult result = ToolResult.builder()
                .success(true)
                .content("文件编辑成功")
                .put("message", String.format("成功替换 %d 处文本", replacements))
                .put("filePath", filePath)
                .put("absolutePath", absolutePath)
                .put("replacements", replacements)
                .put("totalOccurrences", matchResult.count)
                .put("replacedAll", replaceAllFlag)
                .metadata("processingTimeMs", endTime - startTime)
                .metadata("readTimeMs", calculateReadTime(startTime))
                .metadata("matchTimeMs", calculateMatchTime(startTime))
                .metadata("replaceTimeMs", calculateReplaceTime(startTime))
                .metadata("writeTimeMs", calculateWriteTime(startTime))
                .metadata("oldTextLength", oldString.length())
                .metadata("newTextLength", newString.length());
        
        // 添加差异内容
        result.toolCallContent(ToolResult.createDiffContent(absolutePath, oldString, newString));
        
        // 添加位置信息
        if (replaceAllFlag && matchResult.lineNumbers != null) {
            for (Integer line : matchResult.lineNumbers) {
                result.location(absolutePath, line);
            }
        } else if (matchResult.lineNumbers != null && !matchResult.lineNumbers.isEmpty()) {
            result.location(absolutePath, matchResult.lineNumbers.get(0));
        }
        
        return result.build();
    }

    /**
     * 恢复原始换行符
     */
    private String restoreLineEndings(String normalizedContent, String originalContent) {
        if (originalContent.contains("\r\n")) {
            // 原始是 Windows 换行符
            return normalizedContent.replace("\n", "\r\n");
        }
        return normalizedContent;
    }

    /**
     * 检测换行符类型
     */
    private String detectLineEndings(String content) {
        if (content.contains("\r\n")) {
            return "CRLF (Windows)";
        } else if (content.contains("\r")) {
            return "CR (Mac)";
        } else if (content.contains("\n")) {
            return "LF (Unix)";
        }
        return "Unknown";
    }

    /**
     * 匹配结果内部类
     */
    private static class MatchResult {
        int count;
        List<Integer> positions;
        List<Integer> lineNumbers;
    }

    // 性能监控相关字段
    private long readStartTime;
    private long matchStartTime;
    private long replaceStartTime;
    private long writeStartTime;

    /**
     * 计算读取时间
     */
    private long calculateReadTime(long startTime) {
        return matchStartTime - startTime;
    }

    /**
     * 计算匹配时间
     */
    private long calculateMatchTime(long startTime) {
        return replaceStartTime - matchStartTime;
    }

    /**
     * 计算替换时间
     */
    private long calculateReplaceTime(long startTime) {
        return writeStartTime - replaceStartTime;
    }

    /**
     * 计算写入时间
     */
    private long calculateWriteTime(long startTime) {
        return System.currentTimeMillis() - writeStartTime;
    }

    // 在关键位置设置计时点
    private void setReadComplete() {
        readStartTime = System.currentTimeMillis();
    }
    
    private void setMatchStart() {
        matchStartTime = System.currentTimeMillis();
    }
    
    private void setReplaceStart() {
        replaceStartTime = System.currentTimeMillis();
    }
    
    private void setWriteStart() {
        writeStartTime = System.currentTimeMillis();
    }
}
