/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 */
package com.xr21.ai.agent.tui.layout;

import com.xr21.ai.agent.tui.AppState;
import com.xr21.ai.agent.tui.TuiTheme;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.event.EventResult;

import dev.tamboui.tui.event.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import static dev.tamboui.toolkit.Toolkit.*;

/**
 * DSL Element 版状态栏
 */
public class StatusBarElement {
    private final AppState appState;
    private final TuiTheme theme;
    private Runnable onModelClick;
    private Runnable onThemeToggle;

    public StatusBarElement(AppState appState, TuiTheme theme) {
        this.appState = appState;
        this.theme = theme;
    }

    public StatusBarElement onModelClick(Runnable callback) {
        this.onModelClick = callback;
        return this;
    }

    public StatusBarElement onThemeToggle(Runnable callback) {
        this.onThemeToggle = callback;
        return this;
    }

    public Element build() {
        String connSymbol;
        switch (appState.connectionState) {
            case CONNECTED -> connSymbol = "● 已连接";
            case CONNECTING, RECONNECTING -> connSymbol = "◌ 连接中";
            case DISCONNECTED_ERROR -> connSymbol = "✕ 错误";
            default -> connSymbol = "○ 断开";
        }

        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String modelName = appState.modelName.isEmpty() ? "—" : appState.modelName;
        String themeIcon = appState.isDarkMode ? "🌙 dark" : "☀️ light";

        return text(connSymbol + " | 模型: " + modelName + " ▾ | 会话: "
                + appState.sessionCount() + "/" + appState.totalSessions
                + " | " + time
                + "  ctrl+l 帮助  ctrl+q 退出  " + themeIcon)
                .id("status-bar")
                .onMouseEvent(this::handleMouseEvent);
    }

    private EventResult handleMouseEvent(MouseEvent event) {
        if (event.kind() != MouseEventKind.PRESS) return EventResult.UNHANDLED;
        // 模型名称区域点击（第 2 段）
        String connText = switch (appState.connectionState) {
            case CONNECTED -> "● 已连接";
            case CONNECTING, RECONNECTING -> "◌ 连接中";
            case DISCONNECTED_ERROR -> "✕ 错误";
            default -> "○ 断开";
        } + " | ";
        String modelLabel = "模型: ";
        String modelName = appState.modelName.isEmpty() ? "—" : appState.modelName;
        int modelStart = connText.length();
        int modelEnd = modelStart + modelLabel.length() + modelName.length() + 2;
        if (event.x() >= modelStart && event.x() < modelEnd) {
            if (onModelClick != null) onModelClick.run();
            return EventResult.HANDLED;
        }
        // 主题图标区域（右侧）
        if (event.x() >= 60) {
            if (onThemeToggle != null) onThemeToggle.run();
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }
}
