package com.xr21.ai.agent.tui;

import com.googlecode.lanterna.TerminalSize;

/**
 * 文本测量工具类 - 计算文本行数和尺寸
 */
public class TextMeasureUtils {

    private TextMeasureUtils() {
        // 工具类，禁止实例化
    }

    /**
     * 计算文本在指定宽度下需要的行数
     * 正确处理换行符、中文字符和特殊字符
     */
    public static int calculateTextLines(String text, int width) {
        if (text == null || text.isEmpty()) {
            return 1;
        }

        // 移除 "AI: " 前缀（4个字符）
        String content = text.startsWith("AI: ") ? text.substring(4) : text;

        if (content.isEmpty()) {
            return 1;
        }

        // 按换行符分割，计算每行的行数
        String[] lines = content.split("\n", -1);
        int totalLines = 0;

        for (String line : lines) {
            if (line.isEmpty()) {
                totalLines++;
                continue;
            }
            totalLines += calculateSingleLineWrappedLines(line, width);
        }

        return Math.max(1, totalLines);
    }

    /**
     * 计算单行文本在指定宽度下自动换行需要的行数
     */
    public static int calculateSingleLineWrappedLines(String line, int width) {
        if (line == null || line.isEmpty()) {
            return 1;
        }

        int wrappedLines = 1;
        int currentLineLength = 0;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            // 处理中文字符和其他非ASCII字符（占2个字符宽度）
            int charWidth = (c > 127) ? 2 : 1;
            // 特殊处理全角标点符号
            if (c >= 0xFF00 && c <= 0xFFEF) {
                charWidth = 2;
            }

            if (currentLineLength + charWidth > width) {
                wrappedLines++;
                currentLineLength = charWidth;
            } else {
                currentLineLength += charWidth;
            }
        }

        return wrappedLines;
    }

    /**
     * 根据文本内容动态计算TextBox的推荐尺寸
     */
    public static TerminalSize calculateTextBoxSize(String text, int width) {
        int calculatedLines = calculateTextLines(text, width);
        // 根据文本内容动态调整最小高度
        int minHeight = text.length() > 500 ? 5 : (text.length() > 100 ? 4 : 3);
        int height = Math.max(minHeight, calculatedLines);
        // 限制最大高度
        height = Math.min(height, 50);
        return new TerminalSize(width, height);
    }

    /**
     * 计算动态宽度（基于终端列数）
     * @param terminalColumns 终端列数
     * @param percentage 百分比（0-100）
     * @param margin 边距
     * @return 计算后的宽度
     */
    public static int calculateDynamicWidth(int terminalColumns, int percentage, int margin) {
        int width = terminalColumns * percentage / 100 - margin;
        return Math.max(50, width);
    }
}
