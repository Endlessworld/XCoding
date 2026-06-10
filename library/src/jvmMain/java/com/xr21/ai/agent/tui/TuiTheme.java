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

/**
 * TUI 主题系统 - Tamboui 版本
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

    public static TuiTheme modernDark() {
        return new TuiTheme(
                Color.GRAY, Color.LIGHT_BLUE,
                Color.BRIGHT_WHITE, Color.LIGHT_BLUE,
                Color.BRIGHT_WHITE, Color.WHITE, Color.GRAY,
                Color.LIGHT_BLUE, Color.LIGHT_GREEN, Color.LIGHT_YELLOW, Color.LIGHT_RED, Color.LIGHT_BLUE,
                Color.LIGHT_BLUE, Color.LIGHT_GREEN, Color.LIGHT_YELLOW, Color.LIGHT_MAGENTA, Color.LIGHT_RED,
                Color.GRAY, Color.LIGHT_GREEN, Color.LIGHT_YELLOW, Color.GRAY, Color.LIGHT_RED,
                Color.GRAY, Color.BRIGHT_WHITE,
                Color.LIGHT_BLUE, Color.LIGHT_GREEN,
                Color.LIGHT_BLUE, Color.GRAY, darkMarkdownStyles()
        );
    }

    /**
     * Markdown 样式配置。
     * <p>
     * - H1: 大号橙色标题
     * - H2: 橙色标题
     * - H3: 深橙色标题
     * - H4-H6: 灰色标题
     * - 行内代码: 橙红底色 + 深色文字
     * - 代码块: 灰底 + 等宽风格
     * - 引用块: 绿色左侧竖线 + 绿色调文字
     * - 链接: 蓝色 + 下划线
     * - 列表标记: 绿色
     * - 加粗: 亮白粗体
     * - 删除线: 灰色
     */
    private static MarkdownStyles darkMarkdownStyles() {
        return MarkdownStyles.builder()
                // 标题：CSDN 橙色调
                .heading(1, Style.EMPTY.bold().fg(Color.rgb(196, 86, 0)))
                .heading(2, Style.EMPTY.bold().fg(Color.rgb(210, 100, 0)))
                .heading(3, Style.EMPTY.bold().fg(Color.rgb(180, 90, 20)))
                .heading(4, Style.EMPTY.bold().fg(Color.GRAY))
                .heading(5, Style.EMPTY.bold().fg(Color.GRAY))
                .heading(6, Style.EMPTY.bold().fg(Color.GRAY))
                // 加粗：亮白色
                .strong(Style.EMPTY.bold().fg(Color.BRIGHT_WHITE))
                // 斜体：保持默认 italic
                .emphasis(Style.EMPTY.italic().fg(Color.WHITE))
                // 删除线：灰色
                .strikethrough(Style.EMPTY.crossedOut().fg(Color.GRAY))
                // 行内代码：CSDN 橙红底色风格
                .inlineCode(Style.EMPTY.fg(Color.LIGHT_YELLOW).bg(Color.rgb(255, 240, 240)))
                // 代码块：灰底
                .codeBlock(Style.EMPTY.fg(Color.rgb(80, 80, 80)).bg(Color.rgb(245, 245, 245)))
                // 链接：蓝色 + 下划线
                .link(Style.EMPTY.fg(Color.LIGHT_BLUE).underlined())
                // 引用块：CSDN 绿色调
                .blockquote(Style.EMPTY.fg(Color.rgb(70, 150, 70)).dim())
                .blockquotePrefix("\u2502")
                // 列表标记：绿色
                .listMarker(Style.EMPTY.fg(Color.rgb(70, 150, 70)))
                // HTML：灰色 dim
                .html(Style.EMPTY.dim().fg(Color.GRAY))
                // 水平分割线：灰色
                .horizontalRule(Style.EMPTY.fg(Color.DARK_GRAY))
                // 任务列表
                .taskChecked(Style.EMPTY.fg(Color.LIGHT_GREEN))
                .taskUnchecked(Style.EMPTY.fg(Color.GRAY))
                .taskCheckedSymbol("[x]")
                .taskUncheckedSymbol("[ ]")
                .build();
    }

    /**
     * Markdown 样式配置。
     * <p>
     * - H1: 大号橙色标题
     * - H2: 橙色标题
     * - H3: 深橙色标题
     * - H4-H6: 灰色标题
     * - 行内代码: 橙红底色 + 深色文字
     * - 代码块: 灰底 + 等宽风格
     * - 引用块: 绿色左侧竖线 + 绿色调文字
     * - 链接: 蓝色 + 下划线
     * - 列表标记: 绿色
     * - 加粗: 亮白粗体
     * - 删除线: 灰色
     */
    private static MarkdownStyles lightMarkdownStyles() {
        return MarkdownStyles.builder()
                // 标题：CSDN 橙色调
                .heading(1, Style.EMPTY.bold().fg(Color.rgb(196, 86, 0)))
                .heading(2, Style.EMPTY.bold().fg(Color.rgb(210, 100, 0)))
                .heading(3, Style.EMPTY.bold().fg(Color.rgb(180, 90, 20)))
                .heading(4, Style.EMPTY.bold().fg(Color.GRAY))
                .heading(5, Style.EMPTY.bold().fg(Color.GRAY))
                .heading(6, Style.EMPTY.bold().fg(Color.GRAY))
                // 加粗：亮白色
                .strong(Style.EMPTY.bold().fg(Color.BRIGHT_WHITE))
                // 斜体：保持默认 italic
                .emphasis(Style.EMPTY.italic().fg(Color.WHITE))
                // 删除线：灰色
                .strikethrough(Style.EMPTY.crossedOut().fg(Color.GRAY))
                // 行内代码：CSDN 橙红底色风格
                .inlineCode(Style.EMPTY.fg(Color.rgb(196, 58, 58)).bg(Color.rgb(255, 240, 240)))
                // 代码块：灰底
                .codeBlock(Style.EMPTY.fg(Color.rgb(80, 80, 80)).bg(Color.rgb(245, 245, 245)))
                // 链接：蓝色 + 下划线
                .link(Style.EMPTY.fg(Color.LIGHT_BLUE).underlined())
                // 引用块：CSDN 绿色调
                .blockquote(Style.EMPTY.fg(Color.rgb(70, 150, 70)).dim())
                .blockquotePrefix("\u2502")
                // 列表标记：绿色
                .listMarker(Style.EMPTY.fg(Color.rgb(70, 150, 70)))
                // HTML：灰色 dim
                .html(Style.EMPTY.dim().fg(Color.GRAY))
                // 水平分割线：灰色
                .horizontalRule(Style.EMPTY.fg(Color.DARK_GRAY))
                // 任务列表
                .taskChecked(Style.EMPTY.fg(Color.LIGHT_GREEN))
                .taskUnchecked(Style.EMPTY.fg(Color.GRAY))
                .taskCheckedSymbol("[x]")
                .taskUncheckedSymbol("[ ]")
                .build();
    }

    /**
     * 现代亮色（白天）主题
     * <p>浅色背景、深色文字，适合在亮色模式下使用。</p>
     */
    public static TuiTheme modernLight() {
        return new TuiTheme(
                Color.DARK_GRAY, Color.BLUE,
                Color.BLACK, Color.BLUE,
                Color.BLACK, Color.DARK_GRAY, Color.GRAY,
                Color.BLUE, Color.GREEN, Color.YELLOW, Color.RED, Color.BLUE,
                Color.BLUE, Color.GREEN, Color.YELLOW, Color.MAGENTA, Color.RED,
                Color.DARK_GRAY, Color.GREEN, Color.YELLOW, Color.GRAY, Color.RED,
                Color.GRAY, Color.BLACK,
                Color.BLUE, Color.GREEN,
                Color.BLUE, Color.DARK_GRAY, lightMarkdownStyles()
        );
    }
}