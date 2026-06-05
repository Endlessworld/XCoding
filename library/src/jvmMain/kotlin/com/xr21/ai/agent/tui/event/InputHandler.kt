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

import com.github.ajalt.mordant.terminal.Terminal

/**
 * 键盘输入处理器
 *
 * 将原始终端输入转换为 KeyEvent。
 * 使用 mordant 的 RawMode 读取按键。
 *
 * TODO: 1.8 阶段实现完整的键盘输入解析
 */
class InputHandler(private val terminal: Terminal) {

    /**
     * 读取一个按键事件
     *
     * 从 stdin 读取原始字节序列，映射为 KeyEvent。
     */
    fun readKey(): KeyEvent {
        // 使用 terminal.input() 读取原始输入
        // mordant 的 Terminal 没有直接的 raw input API
        // 需要使用 System.in.read() 在 RawMode 下读取

        val byte = System.`in`.read()
        if (byte == -1) return KeyEvent.Unknown

        return when (byte.toChar()) {
            '\n' -> KeyEvent.Enter
            '\r' -> KeyEvent.Enter
            '\b' -> KeyEvent.Backspace
            0x7f.toChar() -> KeyEvent.Backspace
            '\t' -> KeyEvent.Tab
            0x1b.toChar() -> {
                // ESC 序列
                val next1 = System.`in`.read()
                if (next1 == -1) return KeyEvent.Escape
                when (next1.toChar()) {
                    '[' -> {
                        val next2 = System.`in`.read()
                        if (next2 == -1) return KeyEvent.Unknown
                        when (next2.toChar()) {
                            'A' -> KeyEvent.Up
                            'B' -> KeyEvent.Down
                            'C' -> KeyEvent.Right
                            'D' -> KeyEvent.Left
                            'H' -> KeyEvent.Home
                            'F' -> KeyEvent.End
                            '5' -> { // PageUp
                                val next3 = System.`in`.read()
                                if (next3 == '~'.code) KeyEvent.PageUp else KeyEvent.Unknown
                            }
                            '6' -> { // PageDown
                                val next3 = System.`in`.read()
                                if (next3 == '~'.code) KeyEvent.PageDown else KeyEvent.Unknown
                            }
                            'Z' -> KeyEvent.ShiftTab
                            else -> KeyEvent.Unknown
                        }
                    }
                    'O' -> {
                        val next2 = System.`in`.read()
                        when (next2.toChar()) {
                            'H' -> KeyEvent.Home
                            'F' -> KeyEvent.End
                            else -> KeyEvent.Unknown
                        }
                    }
                    '\r', '\n' -> KeyEvent.AltEnter
                    else -> KeyEvent.Unknown
                }
            }
            else -> {
                // Ctrl+字母组合
                val c = byte.toChar()
                if (c in '\u0001'..'\u001a') {
                    val code = c.code + 'a'.code - 1
                    val letter = code.toChar()
                    when (letter) {
                        'c' -> KeyEvent.CtrlC
                        'n' -> KeyEvent.CtrlN
                        'p' -> KeyEvent.CtrlP
                        'q' -> KeyEvent.CtrlQ
                        'w' -> KeyEvent.CtrlW
                        'k' -> KeyEvent.CtrlK
                        'd' -> KeyEvent.CtrlD
                        'r' -> KeyEvent.CtrlR
                        's' -> KeyEvent.CtrlS
                        else -> KeyEvent.CharInput(letter)
                    }
                } else {
                    KeyEvent.CharInput(c)
                }
            }
        }
    }
}