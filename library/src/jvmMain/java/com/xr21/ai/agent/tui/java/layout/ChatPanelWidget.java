/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 */
package com.xr21.ai.agent.tui.java.layout;

import com.xr21.ai.agent.tui.java.AppState;
import com.xr21.ai.agent.tui.java.ChatMessage;
import com.xr21.ai.agent.tui.java.MessageRole;
import com.xr21.ai.agent.tui.java.TuiTheme;
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

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ChatPanelWidget implements Widget {
    private final AppState appState;
    private final TuiTheme theme;
    private final boolean isFocused;

    public ChatPanelWidget(AppState appState, TuiTheme theme, boolean isFocused) {
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
                .title(" 对话 ")
                .borders(Borders.ALL)
                .borderType(borderType)
                .borderStyle(borderStyle)
                .build();

        block.render(area, buffer);
        Rect inner = block.inner(area);
        if (inner.isEmpty()) return;

        List<ChatMessage> messages = appState.currentSession().messages;
        if (messages.isEmpty()) {
            Style hintStyle = Style.EMPTY.fg(theme.textMuted);
            Line line = Line.styled("开始新的对话", hintStyle);
            buffer.setLine(inner.left(), inner.top(), line);
            Line line2 = Line.styled("输入消息后按 Enter 发送", hintStyle);
            buffer.setLine(inner.left(), inner.top() + 2, line2);
            return;
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm");
        List<Line> allLines = new ArrayList<>();

        for (ChatMessage msg : messages) {
            String roleLabel = switch (msg.role) {
                case USER -> "\uD83D\uDC64 你";
                case ASSISTANT -> "\uD83E\uDD16 AI";
                case SYSTEM -> "\u2699 系统";
                case TOOL_CALL -> "\uD83D\uDD27 工具";
                case TOOL_RESULT -> "\uD83D\uDCCE 结果";
                case ERROR -> "\u274C 错误";
            };
            Color roleColor = switch (msg.role) {
                case USER -> theme.userMessage;
                case ASSISTANT -> theme.assistantMessage;
                case SYSTEM -> theme.systemMessage;
                case TOOL_CALL, TOOL_RESULT -> theme.toolMessage;
                case ERROR -> theme.errorMessage;
            };

            String timestamp = msg.timestamp.format(fmt);
            String streaming = msg.isStreaming ? " \u258C" : "";

            // Header line
            Style headerRoleStyle = Style.EMPTY.fg(roleColor).bold();
            Style headerTimeStyle = Style.EMPTY.fg(theme.textMuted);
            allLines.add(Line.from(
                    Span.styled(roleLabel + "  ", headerRoleStyle),
                    Span.styled("[" + timestamp + "]", headerTimeStyle)
            ));

            // Content lines
            String content = msg.content + streaming;
            if ((msg.role == MessageRole.TOOL_CALL || msg.role == MessageRole.TOOL_RESULT) && !msg.isExpanded) {
                int nl = content.indexOf('\n');
                String first = nl >= 0 ? content.substring(0, nl) : content;
                String hint = isFocused ? " [Space 展开]" : " [折叠]";
                content = first + "\u2026" + hint;
            }

            Style contentStyle = Style.EMPTY.fg(theme.textPrimary);
            for (String line : content.split("\n")) {
                allLines.add(Line.styled(line, contentStyle));
            }
            allLines.add(Line.empty());
        }

        // Scroll handling
        int availableHeight = inner.height();
        int maxOffset = Math.max(0, allLines.size() - availableHeight);
        int offset;
        if (appState.scrollOffset == Integer.MAX_VALUE || appState.scrollOffset > maxOffset) {
            offset = maxOffset;
        } else {
            offset = Math.max(0, Math.min(appState.scrollOffset, maxOffset));
        }

        // Render visible lines
        int y = inner.top();
        for (int i = offset; i < allLines.size() && y < inner.bottom(); i++, y++) {
            buffer.setLine(inner.left(), y, allLines.get(i));
        }

        // Scroll hint
        if (maxOffset > 0) {
            String hint;
            if (offset > 0 && offset < maxOffset) hint = "\u2191 " + offset + "/" + maxOffset + " \u2193";
            else if (offset > 0) hint = "\u2191 " + offset + "/" + maxOffset + " 底部";
            else hint = "\u2193 更多消息";
            Style hintStyle = Style.EMPTY.fg(theme.scrollHint);
            buffer.setLine(inner.left(), inner.bottom() - 1, Line.styled(hint, hintStyle));
        }
    }
}
