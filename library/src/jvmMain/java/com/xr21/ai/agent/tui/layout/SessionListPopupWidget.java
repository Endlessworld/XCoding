/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 */
package com.xr21.ai.agent.tui.layout;

import com.xr21.ai.agent.tui.AppState;
import com.xr21.ai.agent.tui.Session;
import com.xr21.ai.agent.tui.TuiTheme;
import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.widget.Widget;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;

public class SessionListPopupWidget implements Widget {
    private final AppState appState;
    private final TuiTheme theme;

    public SessionListPopupWidget(AppState appState, TuiTheme theme) {
        this.appState = appState;
        this.theme = theme;
    }

    @Override
    public void render(Rect area, Buffer buffer) {
        Style borderStyle = Style.EMPTY.fg(theme.borderFocused);
        Style titleStyle = Style.EMPTY.fg(theme.accent).bold();

        Block block = Block.builder()
                .title(" 会话列表 (" + appState.sessionCount() + ") ")
                .borders(Borders.ALL)
                .borderType(BorderType.DOUBLE)
                .borderStyle(borderStyle)
                .build();

        block.render(area, buffer);
        Rect inner = block.inner(area);
        if (inner.isEmpty()) return;

        int y = inner.top();
        for (int i = 0; i < appState.sessions.size() && y < inner.bottom(); i++, y++) {
            Session session = appState.sessions.get(i);
            boolean isSelected = i == appState.sidebarSelectedIndex;
            boolean isCurrent = i == appState.currentSessionIndex;

            String prefix = isSelected ? "\u25B8 " : (isCurrent ? "\u25CF " : "  ");
            Style prefixStyle = isSelected ? Style.EMPTY.fg(theme.selectedText).bold()
                    : (isCurrent ? Style.EMPTY.fg(theme.currentIndicator) : Style.EMPTY.fg(theme.textMuted));
            Style nameStyle = isSelected ? Style.EMPTY.fg(theme.selectedText).bold()
                    : (isCurrent ? Style.EMPTY.fg(theme.textSecondary) : Style.EMPTY.fg(theme.textMuted));

            buffer.setLine(inner.left(), y, Line.from(
                    Span.styled("  ", Style.EMPTY),
                    Span.styled(prefix, prefixStyle),
                    Span.styled(session.name, nameStyle)
            ));
        }

        // Key hint
        if (y < inner.bottom()) {
            buffer.setLine(inner.left(), y, Line.styled("  \u2191\u2193 选择  Enter 切换  Esc 关闭", Style.EMPTY.fg(theme.keyHint)));
        }
    }
}
