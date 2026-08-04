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
package com.xr21.ai.agent.tui;

import com.sun.jna.LastErrorException;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import dev.tamboui.backend.jline3.JLineBackend;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;


/**
 * 修复 Windows Terminal 中组合键（Ctrl+Space、Alt+Enter、Ctrl+Enter 等）
 * 被拦截或修饰符丢失的问题。
 *
 * <p>通过 JNA 调用 Windows Kernel32 API，在启用 Raw Mode 后额外设置
 * {@code ENABLE_VIRTUAL_TERMINAL_INPUT} (0x0200) 标志，
 * 让 Windows Terminal 将组合键转换为 ANSI 转义序列，
 * 从而被 tamboui 的 EventParser 正确解析。</p>
 */
@Slf4j
public class FixedJLineBackend extends JLineBackend {

    // Windows 控制台模式常量
    private static final int STD_INPUT_HANDLE = -10;
    private static final int ENABLE_VIRTUAL_TERMINAL_INPUT = 0x0200;
    private static final int ENABLE_PROCESSED_INPUT = 0x0001;
    private static final int ENABLE_LINE_INPUT = 0x0002;
    private static final int ENABLE_ECHO_INPUT = 0x0004;
    private static final int ENABLE_WINDOW_INPUT = 0x0008;
    private static final int ENABLE_MOUSE_INPUT = 0x0010;

    private boolean vtInputEnabled = false;
    private int originalConsoleMode = -1;

    public FixedJLineBackend() throws IOException {
        super();
    }

    @Override
    public void enableRawMode() throws IOException {
        super.enableRawMode();
        // 在 Windows 上通过 JNA 启用虚拟终端输入模式
        // 让 Windows Terminal 将组合键转换为 ANSI 转义序列
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            enableWindowsVtInput();
        }
    }

    @Override
    public void disableRawMode() throws IOException {
        restoreWindowsConsoleMode();
        super.disableRawMode();
    }

    private void enableWindowsVtInput() {
        try {
            com.sun.jna.Pointer consoleHandle = Kernel32.GetStdHandle(STD_INPUT_HANDLE);
            if (consoleHandle == null || Pointer.nativeValue(consoleHandle) == -1) {
                log.warn("Failed to get console input handle");
                return;
            }
            IntByReference modeRef = new IntByReference();
            if (!Kernel32.GetConsoleMode(consoleHandle, modeRef)) {
                log.warn("Failed to get console mode");
                return;
            }
            originalConsoleMode = modeRef.getValue();
            int newMode = originalConsoleMode
                    | ENABLE_VIRTUAL_TERMINAL_INPUT
                    | ENABLE_WINDOW_INPUT;
            // 关闭行输入和回显（raw mode 要求）
            newMode &= ~(ENABLE_LINE_INPUT | ENABLE_ECHO_INPUT);
            if (!Kernel32.SetConsoleMode(consoleHandle, newMode)) {
                log.warn("Failed to set console mode with ENABLE_VIRTUAL_TERMINAL_INPUT");
                return;
            }
            vtInputEnabled = true;
            log.info("Enabled ENABLE_VIRTUAL_TERMINAL_INPUT on Windows console");
        } catch (Throwable t) {
            log.warn("Failed to enable Windows VT input mode: {}", t.getMessage());
        }
    }

    private void restoreWindowsConsoleMode() {
        if (!vtInputEnabled || originalConsoleMode < 0) return;
        try {
            Pointer consoleHandle = Kernel32.GetStdHandle(STD_INPUT_HANDLE);
            if (consoleHandle != null && Pointer.nativeValue(consoleHandle) != -1) {
                Kernel32.SetConsoleMode(consoleHandle, originalConsoleMode);
            }
        } catch (Throwable t) {
            log.warn("Failed to restore console mode: {}", t.getMessage());
        } finally {
            vtInputEnabled = false;
            originalConsoleMode = -1;
        }
    }

    /**
     * JNA 映射到 Windows Kernel32 API
     */
    private static class Kernel32 {
        static {
            Native.register("kernel32");
        }

        static native Pointer GetStdHandle(int nStdHandle) throws LastErrorException;

        static native boolean GetConsoleMode(Pointer hConsoleHandle, IntByReference lpMode) throws LastErrorException;

        static native boolean SetConsoleMode(Pointer hConsoleHandle, int dwMode) throws LastErrorException;
    }
}
