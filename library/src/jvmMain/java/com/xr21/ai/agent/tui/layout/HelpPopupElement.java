/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 */
package com.xr21.ai.agent.tui.layout;

import com.xr21.ai.agent.tui.AppState;
import com.xr21.ai.agent.tui.TuiTheme;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyEvent;

import static dev.tamboui.toolkit.Toolkit.*;

/**
 * DSL Element 版帮助弹框
 */
public class HelpPopupElement {
    private final AppState appState;
    private final TuiTheme theme;

    public HelpPopupElement(AppState appState, TuiTheme theme) {
        this.appState = appState;
        this.theme = theme;
    }

    public Element build() {
        return dialog("快捷键帮助",
                column(
                        text("对话").bold(),
                        text("  Enter         发送消息"),
                        text("  Alt+Enter     插入换行"),
                        text("  Ctrl+C        取消生成 / 退出"),
                        text(""),
                        text("会话").bold(),
                        text("  Ctrl+N        新建会话"),
                        text("  Ctrl+W        关闭当前会话"),
                        text("  Ctrl+P        打开会话列表"),
                        text("  Ctrl+K        清空当前对话"),
                        text(""),
                        text("导航").bold(),
                        text("  Tab           切换焦点面板"),
                        text("  ↑ / ↓         上下滚动"),
                        text("  PgUp / PgDn   翻页滚动"),
                        text("  Home / End    跳到顶部 / 底部"),
                        text("  Space         展开/折叠工具消息"),
                        text(""),
                        text("其他").bold(),
                        text("  Ctrl+H        打开帮助"),
                        text("  Ctrl+Q        退出应用"),
                        text("  Esc           关闭弹框"),
                        text(""),
                        text(" Esc 关闭弹框 ")
                )
                        .id("help-content")
                        .focusable()
                        .onKeyEvent(this::handleKeyEvent)
        )
                .id("help-popup")
                .focusable();
    }

    private EventResult handleKeyEvent(KeyEvent event) {
        if (event.isCancel()) {
            appState.closeHelpPopup();
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }
}
