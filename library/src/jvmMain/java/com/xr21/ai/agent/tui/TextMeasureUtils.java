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
     * 计算单行文本在指定宽度下自动换行需要的行数 - 优化版本
     */
    public static int calculateSingleLineWrappedLines(String line, int width) {
        if (line == null || line.isEmpty()) {
            return 1;
        }

        if (width <= 0) {
            return 1;
        }

        int wrappedLines = 1;
        int currentLineLength = 0;
        StringBuilder currentWord = new StringBuilder();

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            // 处理字符宽度
            int charWidth = getCharWidth(c);

            // 处理空格和换行符
            if (c == ' ' || c == '\t') {
                // 如果当前单词可以放入当前行
                if (currentLineLength + currentWord.length() + charWidth <= width) {
                    currentLineLength += currentWord.length() + charWidth;
                    currentWord.setLength(0);
                } else {
                    // 单词太长，需要换行
                    if (!currentWord.isEmpty()) {
                        wrappedLines++;
                        currentLineLength = currentWord.length();
                        currentWord.setLength(0);
                    }
                    currentLineLength += charWidth;
                }
            } else if (c == '\n') {
                // 处理换行符
                if (!currentWord.isEmpty()) {
                    if (currentLineLength + currentWord.length() > width) {
                        wrappedLines++;
                    }
                    currentWord.setLength(0);
                }
                wrappedLines++;
                currentLineLength = 0;
            } else {
                // 累积字符到当前单词
                currentWord.append(c);

                // 检查当前单词是否超过行宽
                if (currentLineLength + currentWord.length() > width) {
                    // 如果是单个字符就超过宽度，强制换行
                    if (currentWord.length() == 1) {
                        wrappedLines++;
                        currentLineLength = charWidth;
                        currentWord.setLength(0);
                    } else {
                        // 移除最后一个字符，换行后重新开始
                        String wordToProcess = currentWord.toString();
                        currentWord.setLength(0);
                        currentWord.append(c); // 保留当前字符

                        // 处理剩余的单词部分
                        for (int j = 0; j < wordToProcess.length() - 1; j++) {
                            char prevChar = wordToProcess.charAt(j);
                            int prevCharWidth = getCharWidth(prevChar);

                            if (currentLineLength + prevCharWidth > width) {
                                wrappedLines++;
                                currentLineLength = prevCharWidth;
                            } else {
                                currentLineLength += prevCharWidth;
                            }
                        }
                    }
                }
            }
        }

        // 处理最后一个单词
        if (!currentWord.isEmpty()) {
            if (currentLineLength + currentWord.length() > width) {
                wrappedLines++;
            }
        }

        return Math.max(1, wrappedLines);
    }

    /**
     * 获取字符的显示宽度
     */
    private static int getCharWidth(char c) {
        // 处理中文字符和其他非ASCII字符（占2个字符宽度）
        if (c > 127) {
            return 2;
        }
        // 特殊处理全角标点符号
        if (c >= 0xFF00 && c <= 0xFFEF) {
            return 2;
        }
        // 处理一些特殊字符
        switch (c) {
            case '…':
            case '—':
            case '–':
                return 2;
            default:
                return 1;
        }
    }

    /**
     * 根据文本内容动态计算TextBox的推荐尺寸 - 优化版本
     */
    public static TerminalSize calculateTextBoxSize(String text, int width) {
        if (text == null || text.isEmpty()) {
            return new TerminalSize(Math.max(width, 30), 3);
        }

        // 确保最小宽度
        int optimizedWidth = Math.max(width, 30);

        int calculatedLines = calculateTextLines(text, optimizedWidth);

        // 根据文本内容动态调整最小高度
        int minHeight = calculateMinHeight(text);
        int height = Math.max(minHeight, calculatedLines);

        // 限制最大高度，避免TextBox过高
        int maxHeight = Math.min(height, 20);

        return new TerminalSize(optimizedWidth, maxHeight);
    }

    /**
     * 根据文本内容计算最小高度
     */
    private static int calculateMinHeight(String text) {
        int textLength = text.length();

        if (textLength > 1000) {
            return 8;
        } else if (textLength > 500) {
            return 6;
        } else if (textLength > 200) {
            return 5;
        } else if (textLength > 100) {
            return 4;
        } else if (textLength > 50) {
            return 3;
        } else {
            return 2;
        }
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
