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

import dev.tamboui.terminal.Backend;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * OS 主题检测工具 - 自动感知当前控制台是夜间模式还是白天模式。
 *
 * <p>检测策略（按优先级）：
 * <ol>
 *   <li><b>终端转义序列</b>：通过 {@link Backend} 发送 {@code OSC 11 ; ? ST} 查询终端实际背景色，
 *       使用状态机解析返回的 RGB 值计算亮度。这是最准确的方式，直接反映终端当前背景色。</li>
 *   <li><b>OS 级检测（fallback）</b>：</li>
 *   <ul>
 *     <li>Windows: 读取注册表 AppsUseLightTheme</li>
 *     <li>macOS: defaults read AppleInterfaceStyle</li>
 *     <li>Linux: gsettings color-scheme</li>
 *   </ul>
 * </ol>
 *
 * <p>终端查询逻辑参考 {@code Mode2027Support} 的状态机解析模式。
 */
public final class OsThemeDetector {

    // OSC 11 query: ESC ] 11 ; ? ST
    private static final String OSC = "\\033]";
    private static final String BEL = "\\007";
    private static final String ST = "\\\\";  // ESC \

    // 查询背景色: OSC 11 ; ? BEL
    private static final String QUERY_BG = OSC + "11;?" + BEL;


    // Response parsing states for OSC 11 color query
    private static final int STATE_INITIAL = 0;
    private static final int STATE_OSC = 1;
    private static final int STATE_OSC_NUM = 2;
    private static final int STATE_SEMICOLON = 3;
    private static final int STATE_RGB_PREFIX = 4;
    private static final int STATE_RGB_R = 5;
    private static final int STATE_RGB_G = 6;
    private static final int STATE_RGB_B = 7;
    private static final int STATE_ST_ESC = 8;

    private OsThemeDetector() {
    }

    /**
     * 通过 Backend 查询终端背景色并检测是否为暗色模式。
     * <p>
     * 发送 OSC 11 查询序列，使用状态机解析终端返回的 RGB 值，
     * 计算相对亮度 (ITU-R BT.601) 判断暗/亮色模式。
     *
     * @param backend  终端 Backend（需已进入 raw mode）
     * @param timeoutMs 超时时间（毫秒）
     * @return true=暗色, false=亮色, null=查询失败
     * @throws IOException 如果 I/O 操作失败
     */

    public static Boolean queryByBackend(Backend backend, int timeoutMs) throws IOException {
        // 发送 OSC 11 查询
        backend.writeRaw(QUERY_BG);
        backend.flush();

        // 使用状态机逐字符解析终端响应
        return parseOsc11Response(backend, timeoutMs);
    }

    /**
     * 检测当前终端/OS 是否为暗色模式（无 Backend 时使用 OS 级检测）。
     *
     * @return true = 暗色模式, false = 亮色模式, null = 无法检测
     */
    public static Boolean isDarkMode() {
        return detectByOs();
    }

    /**
     * 检测当前终端/OS 是否为暗色模式（使用 Backend 优先查询终端）。
     *
     * @param backend   终端 Backend
     * @param timeoutMs 超时时间（毫秒）
     * @return true = 暗色模式, false = 亮色模式, null = 无法检测
     * @throws IOException 如果 I/O 操作失败
     */
    public static Boolean isDarkMode(Backend backend, int timeoutMs) throws IOException {
        // 1. 优先通过 Backend 查询终端背景色
        Boolean terminalResult = queryByBackend(backend, timeoutMs);
        System.err.println("queryByBackend" + terminalResult);
        if (terminalResult != null) {
            return terminalResult;
        }

        // 2. fallback: OS 级检测
        return detectByOs();
    }

    /**
     * 使用状态机解析 OSC 11 终端响应。
     * <p>
     * 响应格式: {@code ESC ] 11 ; rgb:RRRR/GGGG/BBBB ST}
     * 其中 ST = ESC \ (0x1B 0x5C)
     */
    private static Boolean parseOsc11Response(Backend backend, int timeoutMs) throws IOException {
        int state = STATE_INITIAL;
        int oscNumber = 0;
        int r = 0, g = 0, b = 0;
        int componentIndex = 0;
        StringBuilder hexBuffer = new StringBuilder(4);
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);

        while (System.nanoTime() < deadlineNanos) {
            int remainingTime = (int) TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime());
            if (remainingTime <= 0) {
                break;
            }

            int ch = backend.read(remainingTime);
            if (ch == -1 || ch == -2) {
                // EOF or timeout
                break;
            }

            switch (state) {
                case STATE_INITIAL:
                    if (ch == 0x1B) {
                        state = STATE_OSC;
                    }
                    break;

                case STATE_OSC:
                    if (ch == ']') {
                        state = STATE_OSC_NUM;
                        oscNumber = 0;
                    } else {
                        state = STATE_INITIAL;
                    }
                    break;

                case STATE_OSC_NUM:
                    if (ch >= '0' && ch <= '9') {
                        oscNumber = oscNumber * 10 + (ch - '0');
                    } else if (ch == ';') {
                        if (oscNumber == 11) {
                            state = STATE_SEMICOLON;
                        } else {
                            state = STATE_INITIAL;
                        }
                    } else {
                        state = STATE_INITIAL;
                    }
                    break;

                case STATE_SEMICOLON:
                    if (ch == 'r') {
                        state = STATE_RGB_PREFIX;
                    } else {
                        state = STATE_INITIAL;
                    }
                    break;

                case STATE_RGB_PREFIX:
                    // 'r' has already been consumed in STATE_SEMICOLON
                    // Now expecting "gb:"
                    if (ch == 'g') {
                        // continue matching "gb:", stay in this state
                    } else if (ch == 'b') {
                        // continue matching "gb:", stay in this state
                    } else if (ch == ':') {
                        state = STATE_RGB_R;
                        componentIndex = 0;
                        hexBuffer.setLength(0);
                    } else {
                        state = STATE_INITIAL;
                    }
                    break;

                case STATE_RGB_R:
                    if ((ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f') || (ch >= 'A' && ch <= 'F')) {
                        hexBuffer.append((char) ch);
                    } else if (ch == '/') {
                        r = parseHexComponent(hexBuffer.toString());
                        state = STATE_RGB_G;
                        hexBuffer.setLength(0);
                    } else {
                        state = STATE_INITIAL;
                    }
                    break;

                case STATE_RGB_G:
                    if ((ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f') || (ch >= 'A' && ch <= 'F')) {
                        hexBuffer.append((char) ch);
                    } else if (ch == '/') {
                        g = parseHexComponent(hexBuffer.toString());
                        state = STATE_RGB_B;
                        hexBuffer.setLength(0);
                    } else {
                        state = STATE_INITIAL;
                    }
                    break;

                case STATE_RGB_B:
                    if ((ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f') || (ch >= 'A' && ch <= 'F')) {
                        hexBuffer.append((char) ch);
                    } else if (ch == 0x1B) {
                        // ST (string terminator) starts with ESC (0x1B)
                        b = parseHexComponent(hexBuffer.toString());
                        state = STATE_ST_ESC;
                    } else if (ch == 0x07) {
                        // Some terminals use BEL (0x07) as string terminator
                        b = parseHexComponent(hexBuffer.toString());
                        return computeLuminance(r, g, b);
                    } else {
                        state = STATE_INITIAL;
                    }
                    break;

                case STATE_ST_ESC:
                    if (ch == '\\') {
                        // ST = ESC \ (0x1B 0x5C), successfully parsed complete response
                        return computeLuminance(r, g, b);
                    }
                    state = STATE_INITIAL;
                    break;

                default:
                    state = STATE_INITIAL;
            }
        }

        // No valid response received
        return null;
    }

    /**
     * 计算 RGB 相对亮度并判断是否为暗色模式。
     * <p>
     * 使用 ITU-R BT.601 亮度公式: L = 0.299*R + 0.587*G + 0.114*B
     *
     * @param r 红色分量 (0-255)
     * @param g 绿色分量 (0-255)
     * @param b 蓝色分量 (0-255)
     * @return true=暗色 (luminance < 128), false=亮色
     */
    private static Boolean computeLuminance(int r, int g, int b) {
        if (r < 0 || g < 0 || b < 0) {
            return null;
        }
        double luminance = 0.299 * r + 0.587 * g + 0.114 * b;
        return luminance < 128;
    }

    /**
     * 解析 16bit 十六进制颜色分量的高 8 位。
     * 例如 "1111" → 0x11 = 17, "ffff" → 0xff = 255
     */
    private static int parseHexComponent(String hex) {
        if (hex == null || hex.isEmpty()) {
            return -1;
        }
        String high = hex.length() >= 2 ? hex.substring(0, 2) : hex;
        try {
            return Integer.parseInt(high, 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ==================== OS 级检测 (fallback) ====================

    private static Boolean detectByOs() {
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.contains("win")) {
                return detectWindows();
            } else if (os.contains("mac")) {
                return detectMacOs();
            } else if (os.contains("nix") || os.contains("nux")) {
                return detectLinux();
            }
        } catch (Exception e) {
            // Fall through to return null
        }
        return null;
    }

    private static boolean detectWindows() throws Exception {
        String cmd = "reg query "
                + "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize "
                + "/v AppsUseLightTheme";
        Process process = Runtime.getRuntime().exec(cmd);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.contains("AppsUseLightTheme")) {
                    String[] parts = line.split("\\s+");
                    for (String part : parts) {
                        if (part.startsWith("0x")) {
                            int value = Integer.decode(part);
                            return value == 0;
                        }
                    }
                }
            }
        }
        return true; // 默认暗色
    }

    private static boolean detectMacOs() throws Exception {
        String cmd = "defaults read -g AppleInterfaceStyle";
        Process process = Runtime.getRuntime().exec(cmd);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            return "Dark".equalsIgnoreCase(line != null ? line.trim() : "");
        }
    }

    private static boolean detectLinux() throws Exception {
        String cmd = "gsettings get org.gnome.desktop.interface color-scheme";
        Process process = Runtime.getRuntime().exec(cmd);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            if (line != null) {
                line = line.trim().toLowerCase();
                return line.contains("dark") || line.contains("prefer-dark");
            }
        }
        return false;
    }
}
