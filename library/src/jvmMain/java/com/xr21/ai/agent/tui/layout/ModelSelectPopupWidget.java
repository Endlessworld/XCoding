/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 */
package com.xr21.ai.agent.tui.layout;

import com.xr21.ai.agent.tui.AppState;
import com.xr21.ai.agent.tui.ModelInfo;
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

public class ModelSelectPopupWidget implements Widget {
    private final AppState appState;
    private final TuiTheme theme;

    public ModelSelectPopupWidget(AppState appState, TuiTheme theme) {
        this.appState = appState;
        this.theme = theme;
    }

    @Override
    public void render(Rect area, Buffer buffer) {
        Style borderStyle = Style.EMPTY.fg(theme.borderFocused);
        Style titleStyle = Style.EMPTY.fg(theme.accent).bold();

        Block block = Block.builder()
                .title(" 选择模型 ")
                .borders(Borders.ALL)
                .borderType(BorderType.DOUBLE)
                .borderStyle(borderStyle)
                .build();

        block.render(area, buffer);
        Rect inner = block.inner(area);
        if (inner.isEmpty()) return;

        int y = inner.top();
        for (int i = 0; i < appState.availableModels.size() && y < inner.bottom(); i++, y++) {
            ModelInfo model = appState.availableModels.get(i);
            boolean isSelected = i == appState.modelSelectIndex;
            boolean isCurrent = model.id.equals(appState.currentModelId);

            String prefix = isSelected ? "\u25B8 " : "  ";
            Style prefixStyle = isSelected
                    ? Style.EMPTY.fg(theme.selectedText).bold()
                    : Style.EMPTY.fg(theme.textMuted);
            Style nameStyle = isSelected
                    ? Style.EMPTY.fg(theme.selectedText).bold()
                    : (isCurrent ? Style.EMPTY.fg(theme.info) : Style.EMPTY.fg(theme.textSecondary));

            String suffix = isCurrent ? " \u2713" : "";
            Style suffixStyle = Style.EMPTY.fg(theme.accent);

            buffer.setLine(inner.left(), y, Line.from(
                    Span.styled("  ", Style.EMPTY),
                    Span.styled(prefix, prefixStyle),
                    Span.styled(model.name.isEmpty() ? model.id : model.name, nameStyle),
                    Span.styled(suffix, suffixStyle)
            ));
        }

        // Key hint
        if (y < inner.bottom()) {
            buffer.setLine(inner.left(), y, Line.styled("  \u2191\u2193 选择  Enter 确认  Esc 关闭", Style.EMPTY.fg(theme.keyHint)));
        }
    }
}
