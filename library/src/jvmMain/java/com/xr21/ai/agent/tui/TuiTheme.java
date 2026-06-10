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
package com.xr21.ai.agent.tui;

import dev.tamboui.markdown.MarkdownStyles;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TUI 主题系统 - 从 TCSS 变量文件构建的不可变主题快照。
 *
 * <p>TCSS 文件是单一事实源（Single Source of Truth），
 * {@link #fromTcss(String)} 工厂方法从中解析所有颜色变量，
 * 生成供 Widget 命令式渲染使用的 {@link TuiTheme} 对象。</p>
 */
public class TuiTheme {
    public final Color borderNormal;
    public final Color borderFocused;
    public final Color panelTitle;
    public final Color panelTitleFocused;
    public final Color textPrimary;
    public final Color textSecondary;
    public final Color textMuted;
    public final Color accent;
    public final Color success;
    public final Color warning;
    public final Color error;
    public final Color info;
    public final Color userMessage;
    public final Color assistantMessage;
    public final Color systemMessage;
    public final Color toolMessage;
    public final Color errorMessage;
    public final Color statusBarText;
    public final Color statusConnected;
    public final Color statusConnecting;
    public final Color statusDisconnected;
    public final Color statusError;
    public final Color inputPrompt;
    public final Color inputText;
    public final Color selectedText;
    public final Color currentIndicator;
    public final Color scrollHint;
    public final Color keyHint;
    public final MarkdownStyles markdownStyles;

    public TuiTheme(
            Color borderNormal, Color borderFocused,
            Color panelTitle, Color panelTitleFocused,
            Color textPrimary, Color textSecondary, Color textMuted,
            Color accent, Color success, Color warning, Color error, Color info,
            Color userMessage, Color assistantMessage, Color systemMessage, Color toolMessage, Color errorMessage,
            Color statusBarText, Color statusConnected, Color statusConnecting, Color statusDisconnected, Color statusError,
            Color inputPrompt, Color inputText,
            Color selectedText, Color currentIndicator,
            Color scrollHint, Color keyHint, MarkdownStyles markdownStyles) {
        this.borderNormal = borderNormal;
        this.borderFocused = borderFocused;
        this.panelTitle = panelTitle;
        this.panelTitleFocused = panelTitleFocused;
        this.textPrimary = textPrimary;
        this.textSecondary = textSecondary;
        this.textMuted = textMuted;
        this.accent = accent;
        this.success = success;
        this.warning = warning;
        this.error = error;
        this.info = info;
        this.userMessage = userMessage;
        this.assistantMessage = assistantMessage;
        this.systemMessage = systemMessage;
        this.toolMessage = toolMessage;
        this.errorMessage = errorMessage;
        this.statusBarText = statusBarText;
        this.statusConnected = statusConnected;
        this.statusConnecting = statusConnecting;
        this.statusDisconnected = statusDisconnected;
        this.statusError = statusError;
        this.inputPrompt = inputPrompt;
        this.inputText = inputText;
        this.selectedText = selectedText;
        this.currentIndicator = currentIndicator;
        this.scrollHint = scrollHint;
        this.keyHint = keyHint;
        this.markdownStyles = markdownStyles;
    }

    // ==================== TCSS 变量解析 ====================

    private static final Pattern VARIABLE_PATTERN = Pattern.compile(
            "^\\$([\\w-]+):\\s*(#[0-9a-fA-F]{6}|#[0-9a-fA-F]{3}|[a-zA-Z-]+)\\s*;\\s*$"
    );

    /**
     * 从 TCSS 文件内容构建 {@link TuiTheme} 实例。
     *
     * @param tcssContent TCSS 文件文本内容
     * @return 解析后的 TuiTheme
     */
    public static TuiTheme fromTcss(String tcssContent) {
        Map<String, String> vars = parseVariables(tcssContent);
        return new TuiTheme(
                color(vars, "border-normal"), color(vars, "border-focused"),
                color(vars, "panel-title"), color(vars, "panel-title-focused"),
                color(vars, "fg-primary"), color(vars, "fg-secondary"), color(vars, "fg-muted"),
                color(vars, "accent"), color(vars, "success"), color(vars, "warning"),
                color(vars, "error"), color(vars, "info"),
                color(vars, "user-msg"), color(vars, "assistant-msg"),
                color(vars, "system-msg"), color(vars, "tool-msg"), color(vars, "error-msg"),
                color(vars, "status-text"), color(vars, "status-connected"),
                color(vars, "status-connecting"), color(vars, "status-disconnected"), color(vars, "status-error"),
                color(vars, "input-prompt"), color(vars, "input-text"),
                color(vars, "selected-text"), color(vars, "current-indicator"),
                color(vars, "scroll-hint"), color(vars, "key-hint"),
                buildMarkdownStyles(vars)
        );
    }

    /**
     * 解析 TCSS 变量定义，返回变量名→值的映射。
     * 只解析 {@code $name: value;} 格式的行。
     */
    private static Map<String, String> parseVariables(String tcss) {
        Map<String, String> vars = new HashMap<>();
        for (String line : tcss.split("\\r?\\n")) {
            Matcher m = VARIABLE_PATTERN.matcher(line.trim());
            if (m.matches()) {
                vars.put(m.group(1), m.group(2));
            }
        }
        return vars;
    }

    /**
     * 从变量映射中获取颜色值，支持 hex (#xxx) 和命名颜色。
     */
    private static Color color(Map<String, String> vars, String key) {
        String val = vars.get(key);
        if (val == null || val.isEmpty()) {
            return Color.GRAY; // fallback
        }
        val = val.trim();
        if (val.startsWith("#")) {
            return Color.hex(val);
        }
        // 命名颜色映射
        return namedColor(val);
    }

    /**
     * 将 TCSS 命名颜色映射到 Tamboui Color 常量。
     */
    private static Color namedColor(String name) {
        return switch (name.toLowerCase()) {
            case "black" -> Color.BLACK;
            case "red" -> Color.RED;
            case "green" -> Color.GREEN;
            case "yellow" -> Color.YELLOW;
            case "blue" -> Color.BLUE;
            case "magenta" -> Color.MAGENTA;
            case "cyan" -> Color.CYAN;
            case "white" -> Color.WHITE;
            case "gray", "grey" -> Color.GRAY;
            case "dark-gray", "dark-grey" -> Color.DARK_GRAY;
            case "light-red" -> Color.LIGHT_RED;
            case "light-green" -> Color.LIGHT_GREEN;
            case "light-yellow" -> Color.LIGHT_YELLOW;
            case "light-blue" -> Color.LIGHT_BLUE;
            case "light-magenta" -> Color.LIGHT_MAGENTA;
            case "light-cyan" -> Color.LIGHT_CYAN;
            case "bright-white" -> Color.BRIGHT_WHITE;
            default -> Color.GRAY;
        };
    }

    // ==================== Markdown 样式构建 ====================

    /**
     * 从 TCSS 变量构建 MarkdownStyles。
     * Markdown 样式使用 TCSS 中定义的 accent、fg-primary 等变量，
     * 同时保留硬编码的排版风格（加粗、斜体等）。
     */
    private static MarkdownStyles buildMarkdownStyles(Map<String, String> vars) {
        Color accent = color(vars, "accent");
        Color fgPrimary = color(vars, "fg-primary");
        Color fgMuted = color(vars, "fg-muted");
        Color success = color(vars, "success");
        Color warning = color(vars, "warning");
        Color error = color(vars, "error");

        return MarkdownStyles.builder()
                // 标题：使用 accent 色系
                .heading(1, Style.EMPTY.bold().fg(accent))
                .heading(2, Style.EMPTY.bold().fg(accent))
                .heading(3, Style.EMPTY.bold().fg(accent))
                .heading(4, Style.EMPTY.bold().fg(fgMuted))
                .heading(5, Style.EMPTY.bold().fg(fgMuted))
                .heading(6, Style.EMPTY.bold().fg(fgMuted))
                // 加粗
                .strong(Style.EMPTY.bold().fg(fgPrimary))
                // 斜体
                .emphasis(Style.EMPTY.italic().fg(fgPrimary))
                // 删除线
                .strikethrough(Style.EMPTY.crossedOut().fg(fgMuted))
                // 行内代码
                .inlineCode(Style.EMPTY.fg(warning).bg(Color.hex("#fff0f0")))
                // 代码块
                .codeBlock(Style.EMPTY.fg(fgMuted).bg(Color.hex("#f5f5f5")))
                // 链接
                .link(Style.EMPTY.fg(accent).underlined())
                // 引用块
                .blockquote(Style.EMPTY.fg(success).dim())
                .blockquotePrefix("\u2502")
                // 列表标记
                .listMarker(Style.EMPTY.fg(success))
                // HTML
                .html(Style.EMPTY.dim().fg(fgMuted))
                // 水平分割线
                .horizontalRule(Style.EMPTY.fg(fgMuted))
                // 任务列表
                .taskChecked(Style.EMPTY.fg(success))
                .taskUnchecked(Style.EMPTY.fg(fgMuted))
                .taskCheckedSymbol("[x]")
                .taskUncheckedSymbol("[ ]")
                .build();
    }
}
