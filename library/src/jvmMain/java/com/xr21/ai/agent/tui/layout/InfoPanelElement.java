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
        return column(
                text("\uD83D\uDCA0 Token 用量").bold(),
                text("  Prompt: " + appState.tokenUsage.promptTokens),
                text("  生成:    " + appState.tokenUsage.completionTokens),
                text("  总计:    " + appState.tokenUsage.totalTokens).bold(),
                text("")
        );
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

    private Element buildConfigSection() {
        if (appState.configOptions.isEmpty()) return text("");
        Element[] items = new Element[appState.configOptions.size() + 1];
        items[0] = text("\u2699 配置").bold();
        int idx = 1;
        for (ConfigOption opt : appState.configOptions) {
            String valueText;
            if ("boolean".equals(opt.type)) {
                valueText = Boolean.parseBoolean(opt.currentValue) ? "\u2713 开" : "\u2717 关";
            } else {
                valueText = opt.currentValue;
            }
            items[idx++] = text("  " + opt.name + ": " + valueText);
        }
        return column(items);
    }

    public Element build() {
        return panel(" 信息 ", buildContent())
                .id("info-panel");
    }
}
