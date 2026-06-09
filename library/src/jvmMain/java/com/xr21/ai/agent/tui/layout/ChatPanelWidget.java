/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 */
package com.xr21.ai.agent.tui.layout;

import com.xr21.ai.agent.tui.AppState;
import com.xr21.ai.agent.tui.ChatMessage;
import com.xr21.ai.agent.tui.MessageRole;
import com.xr21.ai.agent.tui.TuiTheme;
import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.markdown.MarkdownView;
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
        int availableWidth = inner.width();
        int availableHeight = inner.height();

        // First pass: compute heights and collect renderable items
        List<MessageRenderItem> items = new ArrayList<>();
        int totalHeight = 0;

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
            Line headerLine = Line.from(
                    Span.styled(roleLabel + "  ", headerRoleStyle),
                    Span.styled("[" + timestamp + "]", headerTimeStyle)
            );
            items.add(new MessageRenderItem(headerLine, null, 1));
            totalHeight += 1;

            // Content
            String content = msg.content + streaming;
            if (msg.role == MessageRole.TOOL_CALL || msg.role == MessageRole.TOOL_RESULT) {
                List<Line> toolLines = renderToolMessage(msg);
                items.add(new MessageRenderItem(null, toolLines, toolLines.size()));
                totalHeight += toolLines.size();
            } else if (msg.role == MessageRole.ASSISTANT || msg.role == MessageRole.SYSTEM) {
                MarkdownView mdView = MarkdownView.builder()
                        .source(content)
                        .build();
                int mdHeight = mdView.computeHeight(availableWidth);
                items.add(new MessageRenderItem(null, null, mdHeight, mdView));
                totalHeight += mdHeight;
            } else {
                Style contentStyle = Style.EMPTY.fg(theme.textPrimary);
                List<Line> plainLines = new ArrayList<>();
                for (String line : content.split("\n")) {
                    plainLines.add(Line.styled(line, contentStyle));
                }
                int h = Math.max(1, plainLines.size());
                items.add(new MessageRenderItem(null, plainLines, h));
                totalHeight += h;
            }

            // Spacer
            items.add(new MessageRenderItem(Line.empty(), null, 1));
            totalHeight += 1;
        }

        // Scroll handling
        int maxOffset = Math.max(0, totalHeight - availableHeight);
        int offset;
        if (appState.scrollOffset == Integer.MAX_VALUE || appState.scrollOffset > maxOffset) {
            offset = maxOffset;
        } else {
            offset = Math.max(0, Math.min(appState.scrollOffset, maxOffset));
        }

        // Second pass: render visible items
        int currentY = inner.top();
        int skippedLines = 0;
        for (MessageRenderItem item : items) {
            if (currentY >= inner.bottom()) break;

            if (skippedLines + item.height <= offset) {
                skippedLines += item.height;
                continue;
            }

            int itemSkip = Math.max(0, offset - skippedLines);
            int visibleHeight = Math.min(item.height - itemSkip, inner.bottom() - currentY);
            if (visibleHeight <= 0) {
                skippedLines += item.height;
                continue;
            }

            if (item.headerLine != null) {
                buffer.setLine(inner.left(), currentY, item.headerLine);
                currentY++;
            } else if (item.plainLines != null) {
                int from = itemSkip;
                int to = Math.min(item.plainLines.size(), from + visibleHeight);
                for (int i = from; i < to; i++) {
                    buffer.setLine(inner.left(), currentY, item.plainLines.get(i));
                    currentY++;
                }
            } else if (item.markdownView != null) {
                // Render markdown into a scratch buffer, then copy visible slice
                Rect scratchRect = new Rect(0, 0, availableWidth, item.height);
                Buffer scratch = Buffer.empty(scratchRect);
                item.markdownView.render(scratchRect, scratch);
                for (int row = 0; row < visibleHeight; row++) {
                    for (int col = 0; col < availableWidth; col++) {
                        buffer.set(inner.left() + col, currentY + row,
                                scratch.get(col, itemSkip + row));
                    }
                }
                currentY += visibleHeight;
            } else {
                // empty spacer
                currentY += visibleHeight;
            }

            skippedLines += item.height;
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

    private List<Line> renderToolMessage(ChatMessage msg) {
        List<Line> lines = new ArrayList<>();

        String status = msg.toolStatus != null ? msg.toolStatus : "IN_PROGRESS";
        String statusIcon = switch (status) {
            case "IN_PROGRESS" -> "\u25CC";
            case "COMPLETED" -> "\u2713";
            case "FAILED" -> "\u2717";
            default -> "\u25CB";
        };
        Color statusColor = switch (status) {
            case "IN_PROGRESS" -> theme.warning;
            case "COMPLETED" -> theme.success;
            case "FAILED" -> theme.error;
            default -> theme.textMuted;
        };

        // Header: tool name + status
        String toolName = msg.toolName != null ? msg.toolName : "工具";
        Style nameStyle = Style.EMPTY.fg(theme.toolMessage).bold();
        lines.add(Line.from(
                Span.styled("\uD83D\uDD27 ", nameStyle),
                Span.styled(toolName, nameStyle),
                Span.styled("  " + statusIcon, Style.EMPTY.fg(statusColor))
        ));

        if (!msg.isExpanded) {
            String hint = isFocused ? " [Space 展开]" : " [折叠]";
            lines.add(Line.styled("  ..." + hint, Style.EMPTY.fg(theme.textMuted)));
            return lines;
        }

        // Input section
        if (msg.toolInput != null && !msg.toolInput.isEmpty()) {
            lines.add(Line.styled("  \u25B8 参数:", Style.EMPTY.fg(theme.textSecondary)));
            Style inputStyle = Style.EMPTY.fg(theme.textSecondary);
            for (String s : msg.toolInput.split("\n")) {
                lines.add(Line.styled("    " + s, inputStyle));
            }
        }

        // Output section
        if (msg.toolOutput != null && !msg.toolOutput.isEmpty()) {
            lines.add(Line.styled("  \u25B8 结果:", Style.EMPTY.fg(theme.textSecondary)));
            Style outputStyle = Style.EMPTY.fg(theme.textPrimary);
            for (String s : msg.toolOutput.split("\n")) {
                lines.add(Line.styled("    " + s, outputStyle));
            }
        } else if ("IN_PROGRESS".equals(status)) {
            lines.add(Line.styled("  \u25B8 执行中...", Style.EMPTY.fg(theme.warning)));
        }

        return lines;
    }

    private static class MessageRenderItem {
        final Line headerLine;
        final List<Line> plainLines;
        final int height;
        final MarkdownView markdownView;

        MessageRenderItem(Line headerLine, List<Line> plainLines, int height) {
            this(headerLine, plainLines, height, null);
        }

        MessageRenderItem(Line headerLine, List<Line> plainLines, int height, MarkdownView markdownView) {
            this.headerLine = headerLine;
            this.plainLines = plainLines;
            this.height = height;
            this.markdownView = markdownView;
        }
    }
}
