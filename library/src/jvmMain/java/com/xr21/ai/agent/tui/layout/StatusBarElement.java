/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 */
package com.xr21.ai.agent.tui.layout;

import com.xr21.ai.agent.tui.AppState;
import com.xr21.ai.agent.tui.ConfigOption;
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
    private java.util.function.BiConsumer<String, String> onConfigChange;
    private java.util.function.Consumer<ConfigOption> onConfigClick;

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

    public StatusBarElement onConfigChange(java.util.function.BiConsumer<String, String> callback) {
        this.onConfigChange = callback;
        return this;
    }

    public StatusBarElement onConfigClick(java.util.function.Consumer<ConfigOption> callback) {
        this.onConfigClick = callback;
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

        // 配置选项区域 — 独立可点击弹出选项列表
        java.util.List<Element> configPartList = new java.util.ArrayList<>();
        for (ConfigOption opt : appState.configOptions) {
            if ("model".equals(opt.name)) continue;
            String valueText;
            if ("boolean".equals(opt.type)) {
                valueText = Boolean.parseBoolean(opt.currentValue) ? "开" : "关";
            } else {
                valueText = opt.currentValue;
            }
            String label = opt.name + ":" + valueText;
            ConfigOption capturedOpt = opt;
            configPartList.add(text(" " + label + " ▾")
                    .onMouseEvent(e -> {
                        if (e.kind() == MouseEventKind.PRESS) {
                            if (onConfigClick != null) {
                                onConfigClick.accept(capturedOpt);
                            }
                            return EventResult.HANDLED;
                        }
                        return EventResult.UNHANDLED;
                    }));
            configPartList.add(text(" |"));
        }

        // 模型名称区域 — 独立可点击弹出模型选择
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
                row(configPartList.toArray(new Element[0])),
                modelPart,
                text("| 会话: " + appState.sessionCount() + "/" + appState.totalSessions
                        + " | " + time),
                spacer(),
                text(" ctrl+l 帮助  ctrl+q 退出  "),
                themePart
        ).id("status-bar");
    }

}
