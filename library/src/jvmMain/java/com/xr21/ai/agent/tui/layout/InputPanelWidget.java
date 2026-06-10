/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 */
package com.xr21.ai.agent.tui.layout;

import com.xr21.ai.agent.tui.AppState;
import com.xr21.ai.agent.tui.TuiTheme;
import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.widget.Widget;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.input.TextArea;
import dev.tamboui.widgets.input.TextAreaState;

import java.util.function.Consumer;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;

/**
 * 输入面板组件
 * <p>
 * 职责：渲染输入框 + 处理输入相关按键事件。
 * 基于 ToolkitRunner 调研最佳实践：
 * - 元素固定逻辑（字符输入、光标移动）→ 委托 TextAreaState 内置方法
 * - 应用层业务逻辑（发送消息）→ 回调注册
 * </p>
 */
public class InputPanelWidget implements Widget {
    private final AppState appState;
    private final TuiTheme theme;
    private boolean isFocused;

    // ========== 事件回调 ==========
    private Runnable onSubmit;
    private Consumer<String> onInputChange;
    private Runnable onCancel;

    public InputPanelWidget(AppState appState, TuiTheme theme) {
        this.appState = appState;
        this.theme = theme;
        this.isFocused = false;
    }

    // ========== 回调注册（fluent API） ==========

    /** 注册提交回调（Enter 发送消息） */
    public InputPanelWidget onSubmit(Runnable callback) {
        this.onSubmit = callback;
        return this;
    }

    /** 注册输入变更回调 */
    public InputPanelWidget onInputChange(Consumer<String> callback) {
        this.onInputChange = callback;
        return this;
    }

    /** 注册取消回调（Ctrl+C 取消流式输出） */
    public InputPanelWidget onCancel(Runnable callback) {
        this.onCancel = callback;
        return this;
    }

    /** 设置焦点状态 */
    public void setFocused(boolean focused) {
        this.isFocused = focused;
    }

    /** 当前是否聚焦 */
    public boolean isFocused() {
        return isFocused;
    }

    // ========== 按键事件处理 ==========

    /**
     * 处理输入面板的按键事件。
     * <p>
     * 基于 ToolkitRunner 调研最佳实践：
     * - 元素固定逻辑（字符输入、光标移动、Backspace/Delete）→ 委托 TextAreaState
     * - 应用层业务逻辑（发送消息、取消）→ 回调
     *
     * @param key 按键事件
     * @return true 表示事件已处理
     */
    public boolean handleKeyEvent(KeyEvent key) {
        if (!isFocused) return false;

        TextAreaState state = appState.inputState;

        // Enter 发送（仅在非 Alt 组合时）
        if (key.code() == KeyCode.ENTER && !key.modifiers().alt()) {
            String text = state.text().trim();
            if (!text.isEmpty() && onSubmit != null) {
                onSubmit.run();
            }
            return true;
        }

        // Alt+Enter 插入换行
        if (key.code() == KeyCode.ENTER && key.modifiers().alt()) {
            state.insert('\n');
            notifyInputChange();
            return true;
        }

        // Up/Down 历史导航（仅在单行模式或光标在第一行/最后一行时）
        if (key.code() == KeyCode.UP) {
            appState.inputHistoryPrev();
            return true;
        }
        if (key.code() == KeyCode.DOWN) {
            appState.inputHistoryNext();
            return true;
        }

        // 委托 TextAreaState 处理标准编辑按键
        if (handleTextAreaKey(state, key)) {
            notifyInputChange();
            return true;
        }

        return false;
    }

    /**
     * 处理 TextArea 标准编辑按键。
     * 与 TextAreaElement.handleTextAreaKey 逻辑一致。
     */
    private static boolean handleTextAreaKey(TextAreaState state, KeyEvent key) {
        switch (key.code()) {
            case BACKSPACE:
                state.deleteBackward();
                return true;
            case DELETE:
                state.deleteForward();
                return true;
            case LEFT:
                state.moveCursorLeft();
                return true;
            case RIGHT:
                state.moveCursorRight();
                return true;
            case HOME:
                state.moveCursorToLineStart();
                return true;
            case END:
                state.moveCursorToLineEnd();
                return true;
            case TAB:
                state.insert("    ");
                return true;
            case CHAR:
                // 非 Ctrl/Alt 的可打印字符
                if (!key.modifiers().ctrl() && !key.modifiers().alt()) {
                    int cp = key.codePoint();
                    if (cp >= 32 && cp != 127) {
                        state.insert(key.string());
                        return true;
                    }
                }
                return false;
            default:
                return false;
        }
    }

    private void notifyInputChange() {
        if (onInputChange != null) {
            onInputChange.accept(appState.inputState.text());
        }
    }

    @Override
    public void render(Rect area, Buffer buffer) {
        BorderType borderType = isFocused ? BorderType.DOUBLE : BorderType.ROUNDED;
        Style borderStyle = Style.EMPTY.fg(isFocused ? theme.borderFocused : theme.borderNormal);

        Block block = Block.builder()
                .title(" Input ")
                .borders(Borders.ALL)
                .borderType(borderType)
                .borderStyle(borderStyle)
                .build();

        // 使用 TextArea widget 渲染
        TextArea textArea = TextArea.builder()
                .block(block)
                .style(Style.EMPTY.fg(theme.inputText))
                .cursorStyle(Style.EMPTY.reversed())
                .placeholder("> 输入指令...  [Enter 发送, Alt+Enter 换行]")
                .placeholderStyle(Style.EMPTY.fg(theme.inputPrompt))
                .build();

        textArea.render(area, buffer, appState.inputState);
    }
}
