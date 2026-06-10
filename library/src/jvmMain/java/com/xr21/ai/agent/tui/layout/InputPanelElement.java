/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 */
package com.xr21.ai.agent.tui.layout;

import com.xr21.ai.agent.tui.AppState;
import com.xr21.ai.agent.tui.TuiTheme;
import dev.tamboui.style.Style;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;

import static dev.tamboui.toolkit.Toolkit.*;

/**
 * DSL Element 版输入面板
 */
public class InputPanelElement {
    private final AppState appState;
    private final TuiTheme theme;
    private Runnable onSubmit;
    private Runnable onCancel;
    private boolean altEnterInserted;

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
        return textArea(appState.inputState)
                .title(" Input ")
                .rounded()
                .borderColor(theme.borderNormal)
                .focusedBorderColor(theme.borderFocused)
                .placeholder("> 输入指令...  [Enter 发送, Alt+Enter 换行]")
                .placeholderStyle(Style.EMPTY.fg(theme.inputPrompt))
                .id("input-panel")
                .focusable()
                .percent(15)
                .onTextChange(this::handleTextChange)
                .onKeyEvent(this::handleKeyEvent);
    }

    /**
     * 文本变化监听器：检测 ENTER 提交
     * <p>
     * TextAreaElement 内置 handleKeyEvent 会先消费 ENTER 并插入 \n，
     * 不会调用 onKeyEvent 回调，所以通过 onTextChange 来检测提交。
     */
    private void handleTextChange(String newText) {
        // Alt+Enter 插入的换行，跳过提交检测
        if (altEnterInserted) {
            altEnterInserted = false;
            return;
        }
        // ENTER 提交：文本末尾有 \n（由 TextAreaElement 内置处理插入）
        if (newText.endsWith("\n")) {
            // 移除末尾的 \n
            appState.inputState.setText(newText.substring(0, newText.length() - 1));
            String text = appState.inputState.text().trim();
            if (!text.isEmpty() && onSubmit != null) {
                onSubmit.run();
            }
        }
    }

    private EventResult handleKeyEvent(KeyEvent event) {
        // Alt+Enter 插入换行（用 code 判断而非 isConfirm，因为 isConfirm 只匹配无修饰符的 Enter）
        if (event.code() == KeyCode.ENTER && event.modifiers().alt()) {
            altEnterInserted = true;
            appState.inputState.insert('\n');
            return EventResult.HANDLED;
        }

        // Up/Down 历史导航（覆盖 TextArea 内置的光标上下移动）
        if (event.isUp()) {
            appState.inputHistoryPrev();
            return EventResult.HANDLED;
        }
        if (event.isDown()) {
            appState.inputHistoryNext();
            return EventResult.HANDLED;
        }

        // 其他按键委托给 TextAreaElement 内置处理
        return EventResult.UNHANDLED;
    }
}
