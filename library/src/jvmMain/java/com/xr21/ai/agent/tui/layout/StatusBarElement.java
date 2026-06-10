/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 */
package com.xr21.ai.agent.tui.layout;

import com.xr21.ai.agent.tui.AppState;
import com.xr21.ai.agent.tui.TuiTheme;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.event.EventResult;

import dev.tamboui.tui.event.MouseEventKind;
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

        // 模型名称区域 — 独立可点击
        Element modelPart = text(" 模型: " + modelName + " ▾ ")
                .onMouseEvent(e -> {
                    if (e.kind() == MouseEventKind.PRESS && onModelClick != null) {
                        onModelClick.run();
                        return EventResult.HANDLED;
                    }
                    return EventResult.UNHANDLED;
                });

        // 主题切换区域 — 独立可点击
        Element themePart = text(themeIcon)
                .onMouseEvent(e -> {
                    if (e.kind() == MouseEventKind.PRESS && onThemeToggle != null) {
                        onThemeToggle.run();
                        return EventResult.HANDLED;
                    }
                    return EventResult.UNHANDLED;
                });

        return row(
                text(connSymbol + " |"),
                modelPart,
                text("| 会话: " + appState.sessionCount() + "/" + appState.totalSessions
                        + " | " + time),
                spacer(),
                text(" ctrl+l 帮助  ctrl+q 退出  "),
                themePart
        ).id("status-bar");
    }

}
