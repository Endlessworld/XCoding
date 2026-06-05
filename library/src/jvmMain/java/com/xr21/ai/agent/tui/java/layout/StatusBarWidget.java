/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 */
package com.xr21.ai.agent.tui.java.layout;

import com.xr21.ai.agent.tui.java.AppState;
import com.xr21.ai.agent.tui.java.TuiTheme;
import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.widget.Widget;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class StatusBarWidget implements Widget {
    private final AppState appState;
    private final TuiTheme theme;

    public StatusBarWidget(AppState appState, TuiTheme theme) {
        this.appState = appState;
        this.theme = theme;
    }

    @Override
    public void render(Rect area, Buffer buffer) {
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

        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        Style sepStyle = Style.EMPTY.fg(theme.statusBarText);
        Style primary = Style.EMPTY.fg(theme.textPrimary);

        Line line = Line.from(
                Span.styled(" " + appState.agentName + " " + appState.agentVersion, Style.EMPTY.fg(theme.textSecondary)),
                Span.styled(" | ", sepStyle),
                Span.styled(connSymbol, Style.EMPTY.fg(connColor)),
                Span.styled(" | ", sepStyle),
                Span.styled("模型: ", sepStyle),
                Span.styled(appState.modelName.isEmpty() ? "\u2014" : appState.modelName, primary),
                Span.styled(" | ", sepStyle),
                Span.styled("会话: ", sepStyle),
                Span.styled(appState.sessionCount() + "/" + appState.totalSessions, Style.EMPTY.fg(theme.info)),
                Span.styled(" | ", sepStyle),
                Span.styled(time, Style.EMPTY.fg(theme.accent))
        );
        buffer.setLine(area.left(), area.top(), line);
    }
}
