/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 */
package com.xr21.ai.agent.tui.layout;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.xr21.ai.agent.tui.AppState;
import com.xr21.ai.agent.tui.ChatMessage;
import com.xr21.ai.agent.tui.PanelType;
import com.xr21.ai.agent.tui.TuiTheme;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.common.ScrollBarPolicy;
import lombok.extern.slf4j.Slf4j;

/**
 * DSL 聊天面板。使用 {@link MarkdownListElement} 垂直滚动容纳多个
 * {@link ChatMessageItem}，每个 ChatMessageItem 内部使用 {@link dev.tamboui.toolkit.markdown.MarkdownElement}
 * 渲染 markdown 内容。
 *
 * <p>交互能力（以 AppState 为中心）：
 * <ul>
 *   <li>鼠标点击 -> 切焦点到 CENTER；点击消息本身 -> 复制到剪贴板</li>
 *   <li>Space -> 折叠/展开最后一条工具调用</li>
 *   <li>Ctrl+Y -> 复制当前消息（待实现）</li>
 *   <li>Up/Down/PgUp/PgDn/Home/End -> MarkdownListElement 自己处理</li>
 *   <li>streaming 时自动跳到底（followTail = appState.autoScroll）</li>
 * </ul>
 */
@Slf4j
public class ChatPanelElement {

    private final AppState appState;
    private final TuiTheme theme;
    private final MarkdownListElement list = new MarkdownListElement();
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public ChatPanelElement(AppState appState, TuiTheme theme) {
        this.appState = appState;
        this.theme = theme;
    }

    public Element build() {
        boolean chatFocused = appState.focusPanel == PanelType.CENTER;
        String title = " " + safeModelId() + " " + LocalTime.now().format(TIME_FMT) + " ";

        list.clear();
        list.followTail(appState.autoScroll);
        list.title(title);
        list.borderType(chatFocused ? BorderType.DOUBLE : BorderType.ROUNDED);
        list.borderColor(theme.borderNormal);
        list.focusedBorderColor(theme.borderFocused);
        list.id("chat-panel");
        list.focusable();
        list.percent(85);
        list.scrollbar(ScrollBarPolicy.AS_NEEDED);
        list.scrollbarThumbColor(theme.scrollHint);
        list.scrollbarTrackColor(theme.textMuted);
        list.padding(0);

        List<ChatMessage> messages = currentMessages();
        for (int i = 0; i < messages.size(); i++) {
            ChatMessageItem item = new ChatMessageItem(messages.get(i), theme, i);
            item.onCopyRequested(this::copyToSystemClipboard);
            list.addItem(item);
        }

        // 点击事件 -> 复制到剪贴板
        list.onItemClick((idx, ev) -> {
            if (idx >= 0 && idx < messages.size()) {
                ChatMessageItem item = new ChatMessageItem(messages.get(idx), theme, idx);
                copyToSystemClipboard(item.plainText());
            }
            if (appState.focusPanel != PanelType.CENTER) {
                appState.focusPanel = PanelType.CENTER;
            }
        });

        // 键盘：MarkdownListElement 自己处理滚动；我们只拦截 Space / Ctrl+Y
        list.onKeyEvent(this::handleKey);
        // 鼠标：MarkdownListElement 处理滚轮；点击走 onItemClick（已上面设置），同时切焦点
        list.onMouseEvent(this::handleMouse);

        return list;
    }

    private List<ChatMessage> currentMessages() {
        var session = appState.currentSession();
        return session == null ? List.of() : session.messages;
    }

    private String safeModelId() {
        String m = appState.currentModelId;
        return (m == null || m.isEmpty()) ? "chat" : m;
    }

    private EventResult handleKey(KeyEvent event) {
        if (event.isChar(' ')) {
            appState.toggleLastToolMessage();
            return EventResult.HANDLED;
        }
        if (event.isChar('y') && event.hasCtrl()) {
            // Ctrl+Y: 复制最后一条消息
            List<ChatMessage> messages = currentMessages();
            if (!messages.isEmpty()) {
                ChatMessage m = messages.get(messages.size() - 1);
                ChatMessageItem item = new ChatMessageItem(m, theme, messages.size() - 1);
                copyToSystemClipboard(item.plainText());
            }
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }

    private EventResult handleMouse(MouseEvent event) {
        if (event.kind() == MouseEventKind.PRESS && event.isLeftButton()) {
            if (appState.focusPanel != PanelType.CENTER) {
                appState.focusPanel = PanelType.CENTER;
            }
            return EventResult.HANDLED;
        }
        // 鼠标滚轮：转发给 MarkdownListElement 处理
        if (event.kind() == MouseEventKind.SCROLL_UP) {
            list.scrollUp(3);
            return EventResult.HANDLED;
        }
        if (event.kind() == MouseEventKind.SCROLL_DOWN) {
            list.scrollDown(3);
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }

    private void copyToSystemClipboard(String text) {
        if (text == null || text.isEmpty()) return;
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(new StringSelection(text), null);
            log.info("Copied {} chars to system clipboard", text.length());
        } catch (Throwable t) {
            log.warn("Failed to copy to system clipboard: {}", t.getMessage());
        }
    }
}
