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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * OS 主题检测工具 - 自动感知当前控制台是夜间模式还是白天模式。
 *
 * <p>检测策略（按优先级）：
 * <ol>
 *   <li><b>终端转义序列</b>：发送 {@code OSC 11 ; ? ST} 查询终端实际背景色，
 *       解析返回的 RGB 值计算亮度。这是最准确的方式，直接反映终端当前背景色。</li>
 *   <li><b>OS 级检测（fallback）</b>：</li>
 *   <ul>
 *     <li>Windows: 读取注册表 AppsUseLightTheme</li>
 *     <li>macOS: defaults read AppleInterfaceStyle</li>
 *     <li>Linux: gsettings color-scheme</li>
 *   </ul>
 * </ol>
 */
public final class OsThemeDetector {

    private OsThemeDetector() {
    }

    /**
     * 检测当前终端/OS 是否为暗色模式。
     *
     * @return true = 暗色模式, false = 亮色模式, null = 无法检测
     */
    public static Boolean isDarkMode() {
        System.out.println("[OsThemeDetector] ====== 开始主题检测 ======");

        // 1. 优先通过终端转义序列查询实际背景色
        // 注意: 终端转义序列查询需要在 TUI raw mode 下才能正常工作。
        // 如果在 TUI 启动前调用（如 main() 入口处），终端尚未进入 raw mode，
        // Windows 终端可能不会响应 OSC 11 查询，导致超时。
        // 此时直接跳过终端查询，使用 OS 级检测。
        boolean isInTuiContext = detectTuiContext();
        System.out.println("[OsThemeDetector] 是否在TUI上下文中: " + isInTuiContext);

        if (isInTuiContext) {
            System.out.println("[OsThemeDetector] 步骤1: 尝试通过终端转义序列(OSC 11)查询背景色...");
            Boolean terminalResult = detectByTerminalQuery();
            System.out.println("[OsThemeDetector] 终端转义序列查询结果: " + terminalResult);
            if (terminalResult != null) {
                System.out.println("[OsThemeDetector] 使用终端转义序列结果, isDark=" + terminalResult);
                return terminalResult;
            }
        } else {
            System.out.println("[OsThemeDetector] 非TUI上下文, 跳过终端转义序列查询, 直接使用OS级检测");
        }

        // 2. fallback: OS 级检测
        String os = System.getProperty("os.name", "").toLowerCase();
        System.out.println("[OsThemeDetector] 执行OS级检测, 当前OS: " + os);
        try {
            if (os.contains("win")) {
                System.out.println("[OsThemeDetector] 检测到Windows系统, 执行注册表查询...");
                boolean winResult = detectWindows();
                System.out.println("[OsThemeDetector] Windows注册表检测结果: isDark=" + winResult);
                return winResult;
            } else if (os.contains("mac")) {
                System.out.println("[OsThemeDetector] 检测到macOS系统, 执行AppleInterfaceStyle查询...");
                boolean macResult = detectMacOs();
                System.out.println("[OsThemeDetector] macOS检测结果: isDark=" + macResult);
                return macResult;
            } else if (os.contains("nix") || os.contains("nux")) {
                System.out.println("[OsThemeDetector] 检测到Linux系统, 执行gsettings查询...");
                boolean linuxResult = detectLinux();
                System.out.println("[OsThemeDetector] Linux检测结果: isDark=" + linuxResult);
                return linuxResult;
            } else {
                System.out.println("[OsThemeDetector] 未知操作系统: " + os + ", 无法检测");
            }
        } catch (Exception e) {
            System.out.println("[OsThemeDetector] OS级检测异常: " + e.getMessage());
            e.printStackTrace(System.out);
        }
        System.out.println("[OsThemeDetector] ====== 主题检测结束, 返回null(无法检测) ======");
        return null;
    }

    /**
     * 检测当前是否在 TUI raw mode 上下文中。
     * 通过检查 System.in 是否支持 available() 非阻塞读取来判断。
     * 在 Windows 普通终端模式下，System.in.available() 在无输入时返回 0 且不会阻塞，
     * 但终端不会响应 OSC 查询，所以通过快速探测来判断。
     */
    private static boolean detectTuiContext() {
        try {
            // 快速检测: 检查是否在 Windows 上
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                // Windows 上终端转义序列查询在非 raw mode 下基本不可用
                // 通过检查 stderr 是否重定向到终端来判断
                return System.console() != null;
            }
            // macOS/Linux 终端通常支持 OSC 查询，即使在非 raw mode
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== 终端转义序列查询 ====================

    /**
     * 通过终端转义序列 {@code OSC 11 ; ? ST} 查询终端背景色。
     * <p>
     * 发送: {@code ESC ] 11 ; ? ESC \} (查询终端背景色)<br>
     * 响应: {@code ESC ] 11 ; rgb:RRRR/GGGG/BBBB ESC \}
     * <p>
     * 解析 RGB 值，计算相对亮度 (ITU-R BT.601):
     * {@code L = 0.299*R + 0.587*G + 0.114*B}，阈值 128。
     *
     * @return true=暗色, false=亮色, null=查询失败
     */
    private static Boolean detectByTerminalQuery() {
        System.out.println("[OsThemeDetector] detectByTerminalQuery() 开始执行");
        try {
            // OSC 11 查询序列: ESC ] 11 ; ? ST
            // ESC = \033 (0x1B), ST = \033\\ (0x1B 0x5C)
            byte[] querySeq = new byte[]{0x1B, ']', '1', '1', ';', '?', 0x1B, '\\'};
            System.out.println("[OsThemeDetector] 发送OSC 11查询序列到stderr");

            // 发送查询到 stderr（避免干扰 stdout 的 TUI 输出）
            System.err.write(querySeq);
            System.err.flush();
            System.out.println("[OsThemeDetector] 查询序列已发送, 等待终端响应(超时500ms)...");

            // 从 stdin 读取响应，超时 500ms
            // 响应格式: ESC ] 11 ; rgb:RRRR/GGGG/BBBB ESC \
            // 使用超时阻塞读取，因为终端在 raw mode 前也可能需要阻塞读取
            StringBuilder response = new StringBuilder();
            long deadline = System.currentTimeMillis() + 500;
            boolean started = false;
            int totalBytesRead = 0;

            while (System.currentTimeMillis() < deadline) {
                if (System.in.available() > 0) {
                    int b = System.in.read();
                    if (b < 0) break;
                    totalBytesRead++;

                    if (!started && b == 0x1B) {
                        started = true;
                        response.append((char) b);
                        System.out.println("[OsThemeDetector] 收到ESC(0x1B)起始字节, 开始收集响应");
                    } else if (started) {
                        response.append((char) b);
                        // ST 结束符: ESC \ (0x1B 0x5C)
                        if (b == 0x5C && response.length() >= 2
                                && response.charAt(response.length() - 2) == 0x1B) {
                            String respStr = response.toString();
                            System.out.println("[OsThemeDetector] 收到完整终端响应, 原始内容(hex): " + bytesToHex(respStr.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                            System.out.println("[OsThemeDetector] 收到完整终端响应, 原始内容: " + respStr);
                            return parseTerminalResponse(respStr);
                        }
                    }
                } else if (!started) {
                    // 尚未收到任何数据，短暂休眠避免 busy-wait
                    Thread.sleep(20);
                } else {
                    // 已经开始接收但暂时没有新数据
                    Thread.sleep(10);
                }
            }
            long elapsed = System.currentTimeMillis() - (deadline - 5000);
            System.out.println("[OsThemeDetector] 终端查询超时, 耗时=" + elapsed + "ms, 已接收字节数=" + totalBytesRead + ", 已收集响应=" + response.toString());
        } catch (Exception e) {
            System.out.println("[OsThemeDetector] detectByTerminalQuery() 异常: " + e.getMessage());
            e.printStackTrace(System.out);
        }
        System.out.println("[OsThemeDetector] detectByTerminalQuery() 返回null(查询失败)");
        return null;
    }

    /**
     * 将字节数组转为十六进制字符串, 用于调试日志。
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b & 0xFF));
        }
        return sb.toString().trim();
    }

    /**
     * 解析终端响应中的 RGB 值。
     * <p>
     * 响应格式示例: {@code ESC ] 11 ; rgb:1111/2222/3333 ESC \}
     * 或: {@code ESC ] 11 ; rgb:1a1a/2b2b/3c3c ESC \}
     */
    private static Boolean parseTerminalResponse(String response) {
        System.out.println("[OsThemeDetector] parseTerminalResponse() 解析响应: " + response);
        try {
            // 查找 "rgb:" 前缀
            int rgbIdx = response.indexOf("rgb:");
            if (rgbIdx < 0) {
                System.out.println("[OsThemeDetector] 响应中未找到 'rgb:' 前缀, 解析失败");
                return null;
            }

            String rgbPart = response.substring(rgbIdx + 4);
            System.out.println("[OsThemeDetector] RGB部分原始内容: " + rgbPart);
            // 格式: RRRR/GGGG/BBBB
            String[] parts = rgbPart.split("/");
            System.out.println("[OsThemeDetector] RGB分量数量: " + parts.length);
            if (parts.length < 3) {
                System.out.println("[OsThemeDetector] RGB分量不足3个, 解析失败");
                return null;
            }

            // 提取每个颜色分量（取前两个字符作为 8bit 值）
            int r = parseHexComponent(parts[0]);
            int g = parseHexComponent(parts[1]);
            int b = parseHexComponent(parts[2]);
            System.out.println("[OsThemeDetector] 解析RGB: r=" + r + "(" + parts[0] + "), g=" + g + "(" + parts[1] + "), b=" + b + "(" + parts[2] + ")");

            if (r < 0 || g < 0 || b < 0) {
                System.out.println("[OsThemeDetector] RGB分量解析异常(含负值), 解析失败");
                return null;
            }

            // 计算相对亮度 (ITU-R BT.601)
            double luminance = 0.299 * r + 0.587 * g + 0.114 * b;
            boolean isDark = luminance < 128;
            System.out.println("[OsThemeDetector] 亮度计算: luminance=" + String.format("%.2f", luminance) + ", 阈值=128, isDark=" + isDark);
            return isDark;
        } catch (Exception e) {
            System.out.println("[OsThemeDetector] parseTerminalResponse() 异常: " + e.getMessage());
            e.printStackTrace(System.out);
            return null;
        }
    }

    /**
     * 解析 16bit 十六进制颜色分量的高 8 位。
     * 例如 "1111" → 0x11 = 17, "ffff" → 0xff = 255
     */
    private static int parseHexComponent(String hex) {
        if (hex == null || hex.isEmpty()) {
            System.out.println("[OsThemeDetector] parseHexComponent: 输入为空");
            return -1;
        }
        // 取前两个字符
        String high = hex.length() >= 2 ? hex.substring(0, 2) : hex;
        try {
            int value = Integer.parseInt(high, 16);
            System.out.println("[OsThemeDetector] parseHexComponent: hex=" + hex + ", high=" + high + ", value=" + value);
            return value;
        } catch (NumberFormatException e) {
            System.out.println("[OsThemeDetector] parseHexComponent: 解析失败, hex=" + hex + ", high=" + high);
            return -1;
        }
    }

    // ==================== OS 级检测 (fallback) ====================

    private static boolean detectWindows() throws Exception {
        String cmd = "reg query "
                + "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize "
                + "/v AppsUseLightTheme";
        System.out.println("[OsThemeDetector] detectWindows() 执行命令: " + cmd);
        Process process = Runtime.getRuntime().exec(cmd);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            System.out.println("[OsThemeDetector] Windows注册表查询输出:");
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                System.out.println("[OsThemeDetector]   | " + line);
                if (line.contains("AppsUseLightTheme")) {
                    String[] parts = line.split("\\s+");
                    for (String part : parts) {
                        if (part.startsWith("0x")) {
                            int value = Integer.decode(part);
                            boolean isDark = (value == 0);
                            System.out.println("[OsThemeDetector] 解析到AppsUseLightTheme值: " + part + " = " + value + ", isDark=" + isDark);
                            return isDark;
                        }
                    }
                }
            }
        }
        // 读取错误流
        try (BufferedReader errorReader = new BufferedReader(
                new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            String errLine;
            while ((errLine = errorReader.readLine()) != null) {
                System.out.println("[OsThemeDetector] Windows注册表查询错误: " + errLine);
            }
        }
        System.out.println("[OsThemeDetector] Windows注册表查询未找到结果, 返回默认值 true(暗色)");
        return true; // 默认暗色
    }

    private static boolean detectMacOs() throws Exception {
        String cmd = "defaults read -g AppleInterfaceStyle";
        System.out.println("[OsThemeDetector] detectMacOs() 执行命令: " + cmd);
        Process process = Runtime.getRuntime().exec(cmd);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            System.out.println("[OsThemeDetector] macOS AppleInterfaceStyle 结果: " + line);
            boolean isDark = "Dark".equalsIgnoreCase(line != null ? line.trim() : "");
            System.out.println("[OsThemeDetector] macOS检测结果: isDark=" + isDark);
            return isDark;
        }
    }

    private static boolean detectLinux() throws Exception {
        String cmd = "gsettings get org.gnome.desktop.interface color-scheme";
        System.out.println("[OsThemeDetector] detectLinux() 执行命令: " + cmd);
        Process process = Runtime.getRuntime().exec(cmd);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            System.out.println("[OsThemeDetector] Linux gsettings 结果: " + line);
            if (line != null) {
                line = line.trim().toLowerCase();
                boolean isDark = line.contains("dark") || line.contains("prefer-dark");
                System.out.println("[OsThemeDetector] Linux检测结果: isDark=" + isDark);
                return isDark;
            }
        }
        System.out.println("[OsThemeDetector] Linux检测无输出, 返回false(亮色)");
        return false;
    }
}
