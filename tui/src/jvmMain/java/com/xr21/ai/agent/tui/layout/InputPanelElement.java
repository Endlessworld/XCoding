/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 */
package com.xr21.ai.agent.tui.layout;

import com.xr21.ai.agent.tui.AppState;
import com.xr21.ai.agent.tui.TuiTheme;
import com.xr21.ai.agent.tui.element.TextAreaInputElement;
import dev.tamboui.style.Style;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import lombok.extern.slf4j.Slf4j;

/**
 * DSL Element 版输入面板
 */
@Slf4j
public class InputPanelElement {
    private final AppState appState;
    private final TuiTheme theme;
    private Runnable onSubmit;
    private Runnable onCancel;
    private boolean ctrlEnterInserted;

    public InputPanelElement(AppState appState, TuiTheme theme) {
        this.appState = appState;
        this.theme = theme;
    }

    public InputPanelElement onSubmit(Runnable callback) {
        this.onSubmit = callback;
        return this;
    }

    public InputPanelElement onCancel(Runnable callback) {
        this.onCancel = callback;
        return this;
    }

    public Element build() {
        return new TextAreaInputElement(appState.inputState)
                .title(" Input ")
                .rounded()
                .borderColor(theme.borderNormal)
                .focusedBorderColor(theme.borderFocused)
                .placeholder("> 输入指令...  [Enter 发送, Ctrl+\\ 换行]")
                .placeholderStyle(Style.EMPTY.fg(theme.inputPrompt))
                .id("input-panel")
                .focusable()
                .showLineNumbers()
                .showCursor(true)
                .cursorStyle(Style.create().blue())
                .percent(15)
                .onKeyEvent(this::handleKeyEvent)
                .onMouseEvent(this::handleMouseEvent);
    }


    private EventResult handleKeyEvent(KeyEvent event) {
        log.info("InputPanelElement handleKeyEvent {}", event);
        // Up/Down 历史导航（覆盖 TextArea 内置的光标上下移动）
        if (event.isUp()) {
            appState.inputHistoryPrev();
            return EventResult.HANDLED;
        }
        if (event.isDown()) {
            appState.inputHistoryNext();
            return EventResult.HANDLED;
        }

        // Enter 发送
        boolean isEnter = event.isConfirm() || event.code() == KeyCode.ENTER;
        if (isEnter) {
            if (onSubmit != null) {
                onSubmit.run();
            }
            return EventResult.HANDLED;
        }

        // Ctrl+\ 换行
        if (event.hasCtrl() && event.isChar('\\')) {
            appState.inputState.insert("\n");
            return EventResult.HANDLED;
        }

        // 其他按键委托给 TextAreaElement 内置处理
        return EventResult.UNHANDLED;
    }

    private EventResult handleMouseEvent(MouseEvent event) {
        log.info("InputPanelElement handleMouseEvent {}", event);
        // 鼠标左键点击 → 聚焦输入面板
        if (event.kind() == MouseEventKind.PRESS && event.isLeftButton()) {
            appState.focusPanel = com.xr21.ai.agent.tui.PanelType.INPUT;
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }
}
