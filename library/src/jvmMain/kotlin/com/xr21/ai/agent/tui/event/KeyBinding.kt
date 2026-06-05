/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.xr21.ai.agent.tui.event

/** 按键事件 */
sealed class KeyEvent {
    data class CharInput(val char: Char) : KeyEvent()
    data object Enter : KeyEvent()
    data object Backspace : KeyEvent()
    data object Tab : KeyEvent()
    data object ShiftTab : KeyEvent()
    data object Escape : KeyEvent()
    data object Up : KeyEvent()
    data object Down : KeyEvent()
    data object Left : KeyEvent()
    data object Right : KeyEvent()
    data object PageUp : KeyEvent()
    data object PageDown : KeyEvent()
    data object Home : KeyEvent()
    data object End : KeyEvent()
    data object CtrlC : KeyEvent()
    data object CtrlN : KeyEvent()
    data object CtrlP : KeyEvent()
    data object CtrlQ : KeyEvent()
    data object CtrlW : KeyEvent()
    data object CtrlK : KeyEvent()
    data object CtrlD : KeyEvent()
    data object CtrlR : KeyEvent()
    data object CtrlS : KeyEvent()
    data object CtrlEnter : KeyEvent()
    data object AltEnter : KeyEvent()
    data object Space : KeyEvent()
    data object Unknown : KeyEvent()
}

/** TUI 操作 */
enum class Action {
    SEND_MESSAGE,
    CANCEL_OR_INTERRUPT,
    NEW_SESSION,
    CLOSE_SESSION,
    QUIT_APP,
    COMMAND_PALETTE,
    CLEAR_CONVERSATION,
    TOGGLE_THEME,
    FOCUS_NEXT,
    FOCUS_PREVIOUS,
    SCROLL_UP,
    SCROLL_DOWN,
    SCROLL_PAGE_UP,
    SCROLL_PAGE_DOWN,
    SCROLL_TOP,
    SCROLL_BOTTOM,
    INPUT_HISTORY_PREV,
    INPUT_HISTORY_NEXT,
    SELECT_UP,
    SELECT_DOWN,
    SELECT_CONFIRM,
    TOGGLE_EXPAND,
    NOOP,
}

/**
 * 快捷键映射表
 *
 * TODO: 3.4 阶段支持自定义快捷键
 */
val DEFAULT_KEY_BINDINGS: Map<KeyEvent, Action> = mapOf(
    // Ctrl+Enter 在终端中与 Enter 无法区分，都会产生 \n
    // 实际发送逻辑在 EventLoop.handleInputChar() 中处理
    KeyEvent.CtrlC to Action.CANCEL_OR_INTERRUPT,
    KeyEvent.CtrlN to Action.NEW_SESSION,
    KeyEvent.CtrlW to Action.CLOSE_SESSION,
    KeyEvent.CtrlQ to Action.QUIT_APP,
    KeyEvent.CtrlP to Action.COMMAND_PALETTE,
    KeyEvent.CtrlK to Action.CLEAR_CONVERSATION,
    KeyEvent.CtrlD to Action.TOGGLE_THEME,
    KeyEvent.Tab to Action.FOCUS_NEXT,
    KeyEvent.ShiftTab to Action.FOCUS_PREVIOUS,
    KeyEvent.Up to Action.INPUT_HISTORY_PREV,
    KeyEvent.Down to Action.INPUT_HISTORY_NEXT,
    KeyEvent.PageUp to Action.SCROLL_PAGE_UP,
    KeyEvent.PageDown to Action.SCROLL_PAGE_DOWN,
    KeyEvent.Home to Action.SCROLL_TOP,
    KeyEvent.End to Action.SCROLL_BOTTOM,
)