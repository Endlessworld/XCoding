/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 */
package com.xr21.ai.agent.tui.layout;

import com.xr21.ai.agent.tui.AppState;
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

import java.util.ArrayList;
import java.util.List;

public class HelpPopupWidget implements Widget {
    private final AppState appState;
    private final TuiTheme theme;

    public HelpPopupWidget(AppState appState, TuiTheme theme) {
        this.appState = appState;
        this.theme = theme;
    }

    @Override
    public void render(Rect area, Buffer buffer) {
        Style borderStyle = Style.EMPTY.fg(theme.borderFocused);

        Block block = Block.builder()
                .title(" 快捷键帮助 ")
                .borders(Borders.ALL)
                .borderType(BorderType.DOUBLE)
                .borderStyle(borderStyle)
                .build();

        block.render(area, buffer);
        Rect inner = block.inner(area);
        if (inner.isEmpty()) return;

        Style accent = Style.EMPTY.fg(theme.accent).bold();
        Style secondary = Style.EMPTY.fg(theme.textSecondary);
        Style primary = Style.EMPTY.fg(theme.textPrimary);
        Style keyStyle = Style.EMPTY.fg(theme.info).bold();

        List<Line> lines = new ArrayList<>();
        lines.add(Line.styled("对话", accent));
        lines.add(Line.from(Span.styled("  Enter         ", keyStyle), Span.styled("发送消息", primary)));
        lines.add(Line.from(Span.styled("  Alt+Enter     ", keyStyle), Span.styled("插入换行", primary)));
        lines.add(Line.from(Span.styled("  Ctrl+C        ", keyStyle), Span.styled("取消生成 / 退出", primary)));
        lines.add(Line.empty());

        lines.add(Line.styled("会话", accent));
        lines.add(Line.from(Span.styled("  Ctrl+N        ", keyStyle), Span.styled("新建会话", primary)));
        lines.add(Line.from(Span.styled("  Ctrl+W        ", keyStyle), Span.styled("关闭当前会话", primary)));
        lines.add(Line.from(Span.styled("  Ctrl+P        ", keyStyle), Span.styled("打开会话列表", primary)));
        lines.add(Line.from(Span.styled("  Ctrl+K        ", keyStyle), Span.styled("清空当前对话", primary)));
        lines.add(Line.empty());

        lines.add(Line.styled("导航", accent));
        lines.add(Line.from(Span.styled("  Tab           ", keyStyle), Span.styled("切换焦点面板", primary)));
        lines.add(Line.from(Span.styled("  ↑ / ↓         ", keyStyle), Span.styled("上下滚动", primary)));
        lines.add(Line.from(Span.styled("  PgUp / PgDn   ", keyStyle), Span.styled("翻页滚动", primary)));
        lines.add(Line.from(Span.styled("  Home / End    ", keyStyle), Span.styled("跳到顶部 / 底部", primary)));
        lines.add(Line.from(Span.styled("  Space         ", keyStyle), Span.styled("展开/折叠工具消息", primary)));
        lines.add(Line.empty());

        lines.add(Line.styled("其他", accent));
        lines.add(Line.from(Span.styled("  Ctrl+H        ", keyStyle), Span.styled("打开帮助", primary)));
        lines.add(Line.from(Span.styled("  Ctrl+Q        ", keyStyle), Span.styled("退出应用", primary)));
        lines.add(Line.from(Span.styled("  Esc           ", keyStyle), Span.styled("关闭弹框", primary)));

        int y = inner.top();
        for (int i = 0; i < lines.size() && y < inner.bottom() - 1; i++, y++) {
            buffer.setLine(inner.left(), y, lines.get(i));
        }

        // ESC hint at bottom
        if (y < inner.bottom()) {
            buffer.setLine(inner.left(), inner.bottom() - 1,
                    Line.styled(" Esc 关闭弹框 ", Style.EMPTY.fg(theme.keyHint)));
        }
    }
}
