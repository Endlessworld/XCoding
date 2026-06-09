/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 */
package com.xr21.ai.agent.tui.layout;

import com.xr21.ai.agent.tui.*;
import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.widget.Widget;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;

import java.util.ArrayList;
import java.util.List;

public class InfoPanelWidget implements Widget {
    private final AppState appState;
    private final TuiTheme theme;

    public InfoPanelWidget(AppState appState, TuiTheme theme) {
        this.appState = appState;
        this.theme = theme;
    }

    @Override
    public void render(Rect area, Buffer buffer) {
        Style borderStyle = Style.EMPTY.fg(theme.borderNormal);
        Style titleStyle = Style.EMPTY.fg(theme.panelTitle).bold();

        Block block = Block.builder()
                .title(" 信息 ")
                .borders(Borders.ALL)
                .borderType(BorderType.ROUNDED)
                .borderStyle(borderStyle)
                .build();

        block.render(area, buffer);
        Rect inner = block.inner(area);
        if (inner.isEmpty()) return;

        List<Line> lines = new ArrayList<>();
        Style accent = Style.EMPTY.fg(theme.accent).bold();
        Style secondary = Style.EMPTY.fg(theme.textSecondary);
        Style primary = Style.EMPTY.fg(theme.textPrimary);
        Style info = Style.EMPTY.fg(theme.info);
        Style warn = Style.EMPTY.fg(theme.warning);

        // Token usage
        lines.add(Line.styled("\uD83D\uDCA0 Token 用量", accent));
        lines.add(Line.from(Span.styled("  Prompt: ", secondary), Span.styled(String.valueOf(appState.tokenUsage.promptTokens), info)));
        lines.add(Line.from(Span.styled("  生成:    ", secondary), Span.styled(String.valueOf(appState.tokenUsage.completionTokens), info)));
        lines.add(Line.from(Span.styled("  总计:    ", secondary), Span.styled(String.valueOf(appState.tokenUsage.totalTokens), accent)));
        lines.add(Line.empty());

        // Todo list
        if (!appState.todos.isEmpty()) {
            int completed = 0;
            for (TodoItem t : appState.todos) if (t.status == TodoStatus.COMPLETED) completed++;
            lines.add(Line.from(Span.styled("\uD83D\uDCCB Todo", accent), Span.styled(" (" + completed + "/" + appState.todos.size() + ")", secondary)));
            for (TodoItem todo : appState.todos) {
                String statusIcon = switch (todo.status) {
                    case PENDING -> "\u25CB";
                    case IN_PROGRESS -> "\u25CC";
                    case COMPLETED -> "\u2713";
                    case FAILED -> "\u2717";
                    case SKIPPED -> "\u2014";
                };
                Color priColor = switch (todo.priority) {
                    case HIGH -> theme.error;
                    case MEDIUM -> theme.warning;
                    case LOW -> theme.info;
                };
                lines.add(Line.from(
                        Span.styled("  ", secondary),
                        Span.styled("\u25CF", Style.EMPTY.fg(priColor)),
                        Span.styled(" " + statusIcon + " ", secondary),
                        Span.styled(todo.content, primary)
                ));
            }
            lines.add(Line.empty());
        }

        // Config options
        if (!appState.configOptions.isEmpty()) {
            lines.add(Line.styled("\u2699 配置", accent));
            for (ConfigOption opt : appState.configOptions) {
                String valueText;
                if ("boolean".equals(opt.type)) {
                    valueText = Boolean.parseBoolean(opt.currentValue) ? "\u2713 开" : "\u2717 关";
                } else {
                    valueText = opt.currentValue;
                }
                lines.add(Line.from(
                        Span.styled("  " + opt.name + ": ", secondary),
                        Span.styled(valueText, primary)
                ));
            }
            lines.add(Line.empty());
        }

        // Render lines
        int y = inner.top();
        for (int i = 0; i < lines.size() && y < inner.bottom(); i++, y++) {
            buffer.setLine(inner.left(), y, lines.get(i));
        }
    }
}
