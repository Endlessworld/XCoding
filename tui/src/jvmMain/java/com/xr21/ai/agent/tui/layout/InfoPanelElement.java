/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 */
package com.xr21.ai.agent.tui.layout;

import com.xr21.ai.agent.tui.*;
import dev.tamboui.toolkit.element.Element;

import static dev.tamboui.toolkit.Toolkit.*;

/**
 * DSL Element 版信息面板
 */
public class InfoPanelElement {
    private final AppState appState;
    private final TuiTheme theme;

    public InfoPanelElement(AppState appState, TuiTheme theme) {
        this.appState = appState;
        this.theme = theme;
    }

    private Element buildContent() {
        return column(
                buildTokenSection(),
                buildTodoSection()
        );
    }

    private Element buildTokenSection() {
        TokenUsage u = appState.tokenUsage;
        return column(text("\uD83D\uDCA0 Token 用量").bold(), text("  输入:     " + u.promptTokens),
                text("  输出:     " + u.completionTokens), text("  总计:     " + u.totalTokens).bold(),
                u.thoughtTokens > 0 ? text("  思考:     " + u.thoughtTokens) : text(""),
                u.cachedReadTokens > 0 ? text("  缓存读:   " + u.cachedReadTokens) : text(""),
                u.cachedWriteTokens > 0 ? text("  缓存写:   " + u.cachedWriteTokens) : text(""),
                u.sessionTotal > 0 ? text("  会话总计: " + u.sessionTotal) : text(""),
                u.duration > 0 ? text("  耗时:     " + String.format("%.1fs", u.duration)) : text(""),
                !u.speed.isEmpty() ? text("  速度:     " + u.speed + " tok/s") : text(""),
                u.costUsd > 0 ? text("  费用:     $" + String.format("%.6f", u.costUsd)) : text(""), text(""));
    }

    private Element buildTodoSection() {
        if (appState.todos.isEmpty()) return text("");
        int completed = 0;
        for (TodoItem t : appState.todos) if (t.status == TodoStatus.COMPLETED) completed++;
        Element[] items = new Element[appState.todos.size() + 2];
        items[0] = text("\uD83D\uDCCB Todo (" + completed + "/" + appState.todos.size() + ")").bold();
        int idx = 1;
        for (TodoItem todo : appState.todos) {
            String statusIcon = switch (todo.status) {
                case PENDING -> "\u25CB";
                case IN_PROGRESS -> "\u25CC";
                case COMPLETED -> "\u2713";
                case FAILED -> "\u2717";
                case SKIPPED -> "\u2014";
            };
            items[idx++] = text("  " + statusIcon + " " + todo.content);
        }
        items[idx] = text("");
        return column(items);
    }

    public Element build() {
        return panel(" 信息 ", buildContent()).id("info-panel").percent(15);
    }
}
