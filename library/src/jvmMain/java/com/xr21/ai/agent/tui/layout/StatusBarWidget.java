/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 */
package com.xr21.ai.agent.tui.layout;

import com.xr21.ai.agent.tui.AppState;
import com.xr21.ai.agent.tui.TuiTheme;
import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.widget.Widget;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import java.util.function.Consumer;

/**
 * 状态栏组件
 * <p>
 * 职责：渲染底部状态信息 + 处理状态栏相关事件。
 * 基于 ToolkitRunner 调研最佳实践：
 * - 渲染逻辑：连接状态、模型名称、会话数、时间、快捷键提示、主题切换
 * - 鼠标事件：模型名称点击、主题切换点击 → 回调注册
 * - 按键事件：全局快捷键（Ctrl+L 帮助、Ctrl+Q 退出）→ 回调注册
 */
public class StatusBarWidget implements Widget {
    private final AppState appState;
    private final TuiTheme theme;

    // ========== 鼠标点击区域回调 ==========
    private Runnable onModelClick;
    private Runnable onThemeToggle;
    /** 全局快捷键回调：key = 快捷键标识, callback = 执行动作 */
    private Consumer<String> onGlobalShortcut;

    // ========== 缓存的状态栏布局信息（供鼠标命中检测） ==========
    private int cachedWidth;
    private int modelStartCol;
    private int modelEndCol;

    public StatusBarWidget(AppState appState, TuiTheme theme) {
        this.appState = appState;
        this.theme = theme;
    }

    // ========== 回调注册（fluent API） ==========

    /** 注册模型名称点击回调 */
    public StatusBarWidget onModelClick(Runnable callback) {
        this.onModelClick = callback;
        return this;
    }

    /** 注册主题切换点击回调 */
    public StatusBarWidget onThemeToggle(Runnable callback) {
        this.onThemeToggle = callback;
        return this;
    }

    /** 注册全局快捷键回调 */
    public StatusBarWidget onGlobalShortcut(Consumer<String> callback) {
        this.onGlobalShortcut = callback;
        return this;
    }

    // ========== 鼠标命中检测 ==========

    /**
     * 检测鼠标点击是否命中状态栏的可交互区域。
     *
     * @param mx 鼠标列坐标
     * @param my 鼠标行坐标
     * @return true 表示事件已处理
     */
    public boolean handleMouseClick(int mx, int my) {
        // 模型名称区域点击
        if (mx >= modelStartCol && mx < modelEndCol) {
            if (onModelClick != null) onModelClick.run();
            return true;
        }

        // 主题图标区域（右下角）
        if (mx >= cachedWidth - 10) {
            if (onThemeToggle != null) onThemeToggle.run();
            return true;
        }

        return false;
    }

    // ========== 按键事件处理 ==========

    /**
     * 处理状态栏相关的全局快捷键。
     *
     * @param key 按键事件
     * @return true 表示事件已处理
     */
    public boolean handleKeyEvent(KeyEvent key) {
        if (key.code() == KeyCode.CHAR && key.modifiers().ctrl()) {
            char c = Character.toLowerCase(key.string().charAt(0));
            switch (c) {
                case 'l': // Ctrl+L: 帮助
                    if (onGlobalShortcut != null) onGlobalShortcut.accept("help");
                    return true;
                case 'q': // Ctrl+Q: 退出
                    if (onGlobalShortcut != null) onGlobalShortcut.accept("quit");
                    return true;
                case 'p': // Ctrl+P: 会话列表
                    if (onGlobalShortcut != null) onGlobalShortcut.accept("session-list");
                    return true;
                case 'n': // Ctrl+N: 新会话
                    if (onGlobalShortcut != null) onGlobalShortcut.accept("new-session");
                    return true;
                case 'w': // Ctrl+W: 关闭会话
                    if (onGlobalShortcut != null) onGlobalShortcut.accept("close-session");
                    return true;
                case 'k': // Ctrl+K: 清空对话
                    if (onGlobalShortcut != null) onGlobalShortcut.accept("clear-conversation");
                    return true;
                case 'c': // Ctrl+C: 取消/退出
                    if (onGlobalShortcut != null) onGlobalShortcut.accept("cancel-or-quit");
                    return true;
            }
        }
        return false;
    }

    @Override
    public void render(Rect area, Buffer buffer) {
        this.cachedWidth = area.width();

        String connSymbol;
        Color connColor;
        switch (appState.connectionState) {
            case CONNECTED -> {
                connSymbol = "\u25CF 已连接";
                connColor = theme.statusConnected;
            }
            case CONNECTING -> {
                connSymbol = "\u25CC 连接中";
                connColor = theme.statusConnecting;
            }
            case RECONNECTING -> {
                connSymbol = "\u25CC 重连中";
                connColor = theme.statusConnecting;
            }
            case DISCONNECTED_ERROR -> {
                connSymbol = "\u2715 错误";
                connColor = theme.statusError;
            }
            default -> {
                connSymbol = "\u25CB 断开";
                connColor = theme.statusDisconnected;
            }
        }

        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        Style sepStyle = Style.EMPTY.fg(theme.statusBarText);
        Style primary = Style.EMPTY.fg(theme.textPrimary);

        // 计算模型名称区域的列范围（供鼠标命中检测）
        String connText = connSymbol + " | ";
        String modelLabel = "模型: ";
        String modelName = appState.modelName.isEmpty() ? "\u2014" : appState.modelName;
        String modelSuffix = " \u25BE";
        modelStartCol = connText.length();
        modelEndCol = modelStartCol + modelLabel.length() + modelName.length() + modelSuffix.length();

        Line line = Line.from(
                Span.styled(connSymbol, Style.EMPTY.fg(connColor)),
                Span.styled(" | ", sepStyle),
                Span.styled("模型: ", sepStyle),
                Span.styled(modelName, Style.EMPTY.fg(theme.accent)),
                Span.styled(" \u25BE", Style.EMPTY.fg(theme.keyHint)),
                Span.styled(" | ", sepStyle),
                Span.styled("会话: ", sepStyle),
                Span.styled(appState.sessionCount() + "/" + appState.totalSessions, Style.EMPTY.fg(theme.info)),
                Span.styled(" | ", sepStyle),
                Span.styled(time, Style.EMPTY.fg(theme.accent)),
                Span.styled("  ", sepStyle),
                Span.styled("ctrl+l 帮助", Style.EMPTY.fg(theme.keyHint)),
                Span.styled("  ", sepStyle),
                Span.styled("ctrl+q 退出", Style.EMPTY.fg(theme.keyHint)),
                Span.styled("  ", sepStyle),
                Span.styled(appState.isDarkMode ? "\uD83C\uDF19 dark" : "\u2600\uFE0F light", Style.EMPTY.fg(theme.accent))
        );
        buffer.setLine(area.left(), area.top(), line);
    }
}
