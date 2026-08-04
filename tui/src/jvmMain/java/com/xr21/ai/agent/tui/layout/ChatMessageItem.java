/*
 * Copyright \u00a9 2026 XR21 Team. All rights reserved.
 */
package com.xr21.ai.agent.tui.layout;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import com.agentclientprotocol.model.SessionUpdate;
import com.xr21.ai.agent.bridge.BridgeKt;
import com.xr21.ai.agent.model.ChatMessage;
import com.xr21.ai.agent.model.MessageRole;
import com.xr21.ai.agent.tui.TuiTheme;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.toolkit.element.Size;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.markdown.MarkdownElement;

import static dev.tamboui.toolkit.Toolkit.column;
import static dev.tamboui.toolkit.Toolkit.row;
import static dev.tamboui.toolkit.Toolkit.spacer;
import static dev.tamboui.toolkit.Toolkit.text;

/**
 * 一条聊天消息的 DSL 显示单元：header line + body（body 可能是 markdown / 思考 / 工具调用）。
 *
 * <p>继承 {@link StyledElement}以便 MarkdownListElement 能够将其作为 Element 管理（
 * preferredSize + renderContent ）。
 *
 * <p>事件交给父容器（MarkdownListElement + ChatPanelElement）处理。
 */
public final class ChatMessageItem extends StyledElement<ChatMessageItem> {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ChatMessage message;
    private final TuiTheme theme;
    private final int index;
    private final boolean isStreamingThought;
    private Consumer<String> onCopyRequested;
    /** Cached element tree so measure/render see the same instance (avoids re-building on every frame). */
    private Element cachedElement;
    /** Snapshot key used to invalidate the cache when the message content mutates (e.g. streaming). */
    private int cacheKey = -1;

    public ChatMessageItem(ChatMessage message, TuiTheme theme, int index) {
        this.message = message;
        this.theme = theme;
        this.index = index;
        this.isStreamingThought = message.isStreaming;
    }

    public ChatMessageItem onCopyRequested(Consumer<String> handler) {
        this.onCopyRequested = handler;
        return this;
    }

    public ChatMessage message() { return message; }
    public int index() { return index; }

    /** 快捷文本：可以复制到剪贴板。 */
    public String plainText() {
        if (message.role == MessageRole.ASSISTANT) {
            StringBuilder sb = new StringBuilder();
            if (message.events != null) {
                for (SessionUpdate e : message.events) {
                    String t = BridgeKt.getAgentMessageText(Collections.singletonList(e));
                    if (!t.isEmpty()) sb.append(t);
                }
            }
            if (sb.length() == 0) sb.append(message.content);
            return sb.toString();
        }
        return message.content;
    }

    public Element buildElement() {
        int key = computeCacheKey();
        if (cachedElement == null || key != cacheKey) {
            List<Element> rows = new ArrayList<>();
            rows.add(buildHeader());
            rows.addAll(buildBody());
            // Ensure the column has a visible default text color so that
            // content is always readable regardless of terminal defaults.
            cachedElement = column(rows.toArray(new Element[0])).fg(theme.textPrimary);
            cacheKey = key;
        }
        return cachedElement;
    }

    /**
     * Snapshot key that captures any input affecting the rendered tree. When the
     * key changes (e.g. a new streaming chunk arrived), the cached element is
     * rebuilt. Using a small fingerprint keeps the cost negligible compared to
     * re-traversing the full event list.
     */
    private int computeCacheKey() {
        int h = message.content == null ? 0 : message.content.hashCode();
        h = 31 * h + (message.events == null ? 0 : message.events.size());
        h = 31 * h + (message.isStreaming ? 1 : 0);
        h = 31 * h + (message.isExpanded ? 1 : 0);
        return h;
    }

    @Override
    protected void renderContent(Frame frame, Rect area, RenderContext context) {
        if (area.isEmpty() || area.height() <= 0 || area.width() <= 0) return;
        Element content = buildElement();
        context.renderChild(content, frame, area);
    }

    @Override
    public Size preferredSize(int availableWidth, int availableHeight, RenderContext context) {
        // Build the element tree and measure its preferred size
        // This ensures MarkdownListElement can correctly calculate item heights
        Element content = buildElement();
        return content.preferredSize(availableWidth, availableHeight, context);
    }

    // ==================== 头部 ====================

    private Element buildHeader() {
        String roleLabel;
        Color roleColor;
        switch (message.role) {
            case USER -> { roleLabel = "\uD83D\uDC64 \u4f60"; roleColor = theme.userMessage; }
            case ASSISTANT -> { roleLabel = "\uD83E\uDD16 AI"; roleColor = theme.assistantMessage; }
            case ERROR -> { roleLabel = "\u274C \u9519\u8bef"; roleColor = theme.errorMessage; }
            default -> { roleLabel = "?"; roleColor = theme.textPrimary; }
        }
        String timestamp = message.timestamp.format(TIME_FMT);
        String streaming = message.isStreaming ? " \u258C" : "";

        Style headerRoleStyle = Style.EMPTY.fg(roleColor).bold();
        Style headerTimeStyle = Style.EMPTY.fg(theme.textMuted);
        return row(
                text(roleLabel + "  ").style(headerRoleStyle),
                text("[" + timestamp + "]" + streaming).style(headerTimeStyle),
                spacer(),
                text("#" + index).dim()
        );
    }

    // ==================== 正文 ====================

    private List<Element> buildBody() {
        List<Element> out = new ArrayList<>();
        switch (message.role) {
            case USER -> {
                String text = BridgeKt.getUserMessageText(message.events);
                if (text.isEmpty()) text = message.content;
                if (!text.isEmpty()) out.add(text(text).fg(theme.textPrimary));
            }
            case ERROR -> {
                String text = BridgeKt.getAgentMessageText(message.events);
                if (text.isEmpty()) text = message.content;
                if (!text.isEmpty()) {
                    Style s = Style.EMPTY.fg(theme.errorMessage);
                    for (String line : text.split("\n", -1)) {
                        out.add(text(line).style(s));
                    }
                }
            }
            case ASSISTANT -> out.addAll(buildAssistantBody());
        }
        if (out.isEmpty()) out.add(spacer());
        return out;
    }

    private List<Element> buildAssistantBody() {
        List<Element> out = new ArrayList<>();
        List<SessionUpdate> events = message.events;
        if (events == null || events.isEmpty()) {
            if (!message.content.isEmpty()) {
                out.add(MarkdownElement.of(message.content).styles(theme.markdownStyles));
            }
            return out;
        }
        StringBuilder textBuf = new StringBuilder();
        StringBuilder thoughtBuf = new StringBuilder();

        for (int i = 0; i < events.size(); i++) {
            SessionUpdate event = events.get(i);
            if (event instanceof SessionUpdate.AgentMessageChunk) {
                String t = BridgeKt.getAgentMessageText(Collections.singletonList(event));
                textBuf.append(t);
                boolean nextDifferent = i + 1 >= events.size()
                        || !(events.get(i + 1) instanceof SessionUpdate.AgentMessageChunk);
                if (nextDifferent) {
                    String merged = textBuf.toString();
                    textBuf.setLength(0);
                    if (!merged.isEmpty()) {
                        out.add(MarkdownElement.of(merged + (message.isStreaming ? " \u258C" : ""))
                                .styles(theme.markdownStyles));
                    }
                }
            } else if (event instanceof SessionUpdate.AgentThoughtChunk) {
                String t = BridgeKt.getAgentThoughtText(Collections.singletonList(event));
                thoughtBuf.append(t);
                boolean nextDifferent = i + 1 >= events.size()
                        || !(events.get(i + 1) instanceof SessionUpdate.AgentThoughtChunk);
                if (nextDifferent) {
                    String merged = thoughtBuf.toString();
                    thoughtBuf.setLength(0);
                    if (!merged.isEmpty()) out.add(buildThoughtBlock(merged));
                }
            } else if (event instanceof SessionUpdate.ToolCall) {
                SessionUpdate.ToolCall tc = (SessionUpdate.ToolCall) event;
                List<SessionUpdate.ToolCallUpdate> updates = BridgeKt.getToolCallUpdates(
                        events.subList(i, events.size()), BridgeKt.getToolCallIdValue(tc));
                out.add(buildToolCallBlock(BridgeKt.getToolCallTitle(tc), updates));
            } else if (event instanceof SessionUpdate.ToolCallUpdate) {
                SessionUpdate.ToolCallUpdate tcu = (SessionUpdate.ToolCallUpdate) event;
                String tcuId = BridgeKt.getToolCallUpdateIdValue(tcu);
                boolean hasParent = false;
                for (int j = 0; j < i; j++) {
                    if (events.get(j) instanceof SessionUpdate.ToolCall
                            && BridgeKt.getToolCallIdValue((SessionUpdate.ToolCall) events.get(j)).equals(tcuId)) {
                        hasParent = true; break;
                    }
                }
                if (!hasParent) {
                    boolean already = false;
                    for (int j = 0; j < i; j++) {
                        if (events.get(j) instanceof SessionUpdate.ToolCallUpdate
                                && BridgeKt.getToolCallUpdateIdValue((SessionUpdate.ToolCallUpdate) events.get(j)).equals(tcuId)) {
                            already = true; break;
                        }
                    }
                    if (!already) {
                        List<SessionUpdate.ToolCallUpdate> updates = BridgeKt.getToolCallUpdates(
                                events.subList(i, events.size()), tcuId);
                        String name = BridgeKt.getToolCallUpdateTitle(tcu);
                        if (name == null || name.isEmpty()) name = tcuId;
                        out.add(buildToolCallBlock(name, updates));
                    }
                }
            }
        }
        if (textBuf.length() > 0) {
            out.add(MarkdownElement.of(textBuf.toString()).styles(theme.markdownStyles));
        }
        return out;
    }

    // ==================== 思考块 ====================

    private Element buildThoughtBlock(String thought) {
        boolean expanded = message.isExpanded || isStreamingThought;
        Style titleStyle = Style.EMPTY.fg(theme.systemMessage).bold().italic();
        Style thoughtStyle = Style.EMPTY.fg(theme.systemMessage).italic();
        if (expanded) {
            List<Element> lines = new ArrayList<>();
            lines.add(text("\uD83D\uDCAD \u601d\u8003:").style(titleStyle));
            for (String line : thought.split("\n", -1)) {
                lines.add(text("  " + line).style(thoughtStyle));
            }
            return column(lines.toArray(new Element[0]));
        } else {
            return text("\uD83D\uDCAD \u601d\u8003: [\u6298\u53e0]").style(titleStyle.dim());
        }
    }

    // ==================== 工具调用块 ====================

    private Element buildToolCallBlock(String toolName, List<SessionUpdate.ToolCallUpdate> updates) {
        String status = "IN_PROGRESS";
        StringBuilder input = new StringBuilder();
        StringBuilder output = new StringBuilder();
        if (updates != null) {
            for (SessionUpdate.ToolCallUpdate u : updates) {
                com.agentclientprotocol.model.ToolCallStatus s = BridgeKt.getToolCallUpdateStatus(u);
                if (s != null) status = BridgeKt.getToolCallStatusString(s);
                String txt = BridgeKt.extractToolCallUpdateText(u);
                com.agentclientprotocol.model.ToolCallStatus us = BridgeKt.getToolCallUpdateStatus(u);
                if (us == com.agentclientprotocol.model.ToolCallStatus.IN_PROGRESS
                        || us == com.agentclientprotocol.model.ToolCallStatus.PENDING) {
                    input.append(txt);
                } else {
                    output.append(txt);
                }
            }
        }
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

        boolean expanded = message.isExpanded;
        Style nameStyle = Style.EMPTY.fg(theme.toolMessage).bold();
        List<Element> lines = new ArrayList<>();
        lines.add(row(
                text("\uD83D\uDD27 ").style(nameStyle),
                text(toolName).style(nameStyle),
                spacer(),
                text(statusIcon).style(Style.EMPTY.fg(statusColor))
        ));

        if (expanded) {
            if (input.length() > 0) {
                lines.add(text("  \u25B8 \u53c2\u6570:").style(Style.EMPTY.fg(theme.textSecondary)));
                for (String s : input.toString().split("\n", -1)) {
                    lines.add(text("    " + s).style(Style.EMPTY.fg(theme.textSecondary)));
                }
            }
            if (output.length() > 0) {
                lines.add(text("  \u25B8 \u7ed3\u679c:").style(Style.EMPTY.fg(theme.textSecondary)));
                String outText = output.toString();
                if (looksLikeMarkdown(outText)) {
                    lines.add(MarkdownElement.of(outText).styles(theme.markdownStyles));
                } else {
                    for (String s : outText.split("\n", -1)) {
                        lines.add(text("    " + s).style(Style.EMPTY.fg(theme.textPrimary)));
                    }
                }
            } else if ("IN_PROGRESS".equals(status)) {
                lines.add(text("  \u25B8 \u6267\u884c\u4e2d...").style(Style.EMPTY.fg(theme.warning)));
            }
        } else {
            lines.add(text("  ... [\u6298\u53e0]").style(Style.EMPTY.fg(theme.textMuted).dim()));
        }
        return column(lines.toArray(new Element[0]));
    }

    private static boolean looksLikeMarkdown(String s) {
        if (s == null || s.isEmpty()) return false;
        return s.contains("```") || s.contains("`") || s.contains("## ")
                || s.contains("](") || (s.indexOf('\n') >= 0 && (s.contains("|") || s.contains("- ")));
    }
}
