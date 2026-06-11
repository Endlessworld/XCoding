/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 */
package com.xr21.ai.agent.tui.layout;

import com.xr21.ai.agent.tui.AppState;
import com.xr21.ai.agent.tui.Session;
import com.xr21.ai.agent.tui.TuiTheme;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;

import java.util.ArrayList;
import java.util.List;

import static dev.tamboui.toolkit.Toolkit.*;

/**
 * DSL Element 版会话列表弹框
 */
public class SessionListPopupElement {
    private final AppState appState;
    private final TuiTheme theme;

    public SessionListPopupElement(AppState appState, TuiTheme theme) {
        this.appState = appState;
        this.theme = theme;
    }

    public Element build() {
        List<Element> items = new ArrayList<>();
        for (int i = 0; i < appState.sessions.size(); i++) {
            Session session = appState.sessions.get(i);
            boolean isSelected = i == appState.sidebarSelectedIndex;
            boolean isCurrent = i == appState.currentSessionIndex;
            String prefix = isSelected ? "▸ " : (isCurrent ? "● " : "  ");
            items.add(text(prefix + session.name));
        }


        items.add(text("  ↑↓ 选择  Enter 切换  Esc 关闭"));

        return dialog("会话列表 (" + appState.sessionCount() + ")",
                column(items.toArray(new Element[0]))
                        .id("session-list-content")
                        .focusable()
                        .onKeyEvent(this::handleKeyEvent)
        )
                .id("session-list-popup")
                .focusable();
    }

    private EventResult handleKeyEvent(KeyEvent event) {
        if (event.isUp()) {
            appState.selectUp();
            return EventResult.HANDLED;
        }
        if (event.isDown()) {
            appState.selectDown();
            return EventResult.HANDLED;
        }
        boolean isEnter = event.isConfirm() || event.code() == KeyCode.ENTER;
        if (isEnter) {
            appState.popupConfirmSelection();
            return EventResult.HANDLED;
        }
        if (event.isCancel()) {
            appState.closeSessionListPopup();
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }
}
