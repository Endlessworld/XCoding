/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 */
package com.xr21.ai.agent.tui.layout;

import com.xr21.ai.agent.tui.AppState;
import com.xr21.ai.agent.tui.TuiTheme;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;

import static dev.tamboui.toolkit.Toolkit.widget;

/**
 * DSL Element 版聊天面板
 */
public class ChatPanelElement {
    private final AppState appState;
    private final TuiTheme theme;
    private final boolean isFocused;

    public ChatPanelElement(AppState appState, TuiTheme theme, boolean isFocused) {
        this.appState = appState;
        this.theme = theme;
        this.isFocused = isFocused;
    }

    public Element build() {
        return widget(new ChatPanelWidget(appState, theme, isFocused))
                .id("chat-panel")
                .focusable()
                .percent(85)
                .onKeyEvent(this::handleKeyEvent)
                .onMouseEvent(this::handleMouseEvent);
    }

    private EventResult handleKeyEvent(KeyEvent event) {
        if (event.isUp()) {
            appState.scrollUp();
            return EventResult.HANDLED;
        }
        if (event.isDown()) {
            appState.scrollDown();
            return EventResult.HANDLED;
        }
        if (event.isPageUp()) {
            appState.scrollPageUp();
            return EventResult.HANDLED;
        }
        if (event.isPageDown()) {
            appState.scrollPageDown();
            return EventResult.HANDLED;
        }
        if (event.isHome()) {
            appState.scrollOffset = 0;
            return EventResult.HANDLED;
        }
        if (event.isEnd()) {
            appState.scrollOffset = Integer.MAX_VALUE;
            appState.autoScroll = true;
            return EventResult.HANDLED;
        }
        if (event.isChar(' ')) {
            appState.toggleLastToolMessage();
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }

    private EventResult handleMouseEvent(MouseEvent event) {
        if (event.kind() == MouseEventKind.SCROLL_UP) {
            appState.scrollUp();
            return EventResult.HANDLED;
        }
        if (event.kind() == MouseEventKind.SCROLL_DOWN) {
            appState.scrollDown();
            return EventResult.HANDLED;
        }
        // 鼠标左键点击 → 聚焦聊天面板
        if (event.kind() == MouseEventKind.PRESS && event.isLeftButton()) {
            appState.focusPanel = com.xr21.ai.agent.tui.PanelType.CENTER;
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }
}
