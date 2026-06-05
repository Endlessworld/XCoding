/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 */
package com.xr21.ai.agent.tui.java.layout;

import com.xr21.ai.agent.tui.java.AppState;
import com.xr21.ai.agent.tui.java.TuiTheme;
import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.widget.Widget;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;

public class InputPanelWidget implements Widget {
    private final AppState appState;
    private final TuiTheme theme;
    private final boolean isFocused;

    public InputPanelWidget(AppState appState, TuiTheme theme, boolean isFocused) {
        this.appState = appState;
        this.theme = theme;
        this.isFocused = isFocused;
    }

    @Override
    public void render(Rect area, Buffer buffer) {
        BorderType borderType = isFocused ? BorderType.DOUBLE : BorderType.ROUNDED;
        Style borderStyle = Style.EMPTY.fg(isFocused ? theme.borderFocused : theme.borderNormal);
        Style titleStyle = Style.EMPTY.fg(isFocused ? theme.panelTitleFocused : theme.panelTitle).bold();

        Block block = Block.builder()
                .title(" Input ")
                .borders(Borders.ALL)
                .borderType(borderType)
                .borderStyle(borderStyle)
                .build();

        block.render(area, buffer);
        Rect inner = block.inner(area);
        if (inner.isEmpty()) return;

        String[] inputLines;
        if (appState.inputBuffer.isEmpty()) {
            inputLines = new String[]{"> 输入指令...  [Enter 发送, Alt+Enter 换行]"};
        } else {
            inputLines = appState.inputBuffer.split("\n", -1);
        }

        // Scroll offset for input
        int maxOffset = Math.max(0, inputLines.length - inner.height());
        int offset;
        if (appState.inputScrollOffset == Integer.MAX_VALUE || appState.inputScrollOffset > maxOffset) {
            offset = maxOffset;
        } else {
            offset = Math.max(0, Math.min(appState.inputScrollOffset, maxOffset));
        }

        Style textStyle = Style.EMPTY.fg(theme.inputText);
        Style promptStyle = Style.EMPTY.fg(theme.inputPrompt);

        int y = inner.top();
        for (int i = offset; i < inputLines.length && y < inner.bottom(); i++, y++) {
            String prefix = appState.inputBuffer.isEmpty() ? "" : "> ";
            Style style = appState.inputBuffer.isEmpty() ? promptStyle : textStyle;
            buffer.setLine(inner.left(), y, Line.styled(prefix + inputLines[i], style));
        }

        // Scroll hint
        if (maxOffset > 0) {
            String hint = offset > 0 ? "\u2191 " + offset + "/" + maxOffset : "\u2193";
            buffer.setLine(inner.left(), inner.bottom() - 1, Line.styled(hint, Style.EMPTY.fg(theme.scrollHint)));
        }
    }
}
