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
package com.xr21.ai.agent;

import com.agentclientprotocol.launcher.AcpAgentLauncher;
import com.agentclientprotocol.launcher.AgiAgent;

/**
 * XAgent 主入口，支持多种运行模式。
 *
 * 使用方式:
 *   java -jar XAgent-all.jar --acp         运行 ACP 模式（默认，标准 I/O 协议）
 *   java -jar XAgent-all.jar --acp --ws-server <port>  运行 ACP WebSocket 服务器模式
 *   java -jar XAgent-all.jar --tui         运行 TUI 模式
 *
 * @author Endless
 */
public class AgentApplication {

    public static void main(String[] args) {
        // 检查是否有 --tui 参数
        if (hasFlag(args, "--tui")) {
            com.xr21.ai.agent.tui.MainKt.main(filterArgs(args, "--tui"));
            return;
        }

        // ACP 模式（默认）：处理 --ws-server 子命令
        String[] acpArgs = filterArgs(args, "--acp", "--tui");

        if (acpArgs.length >= 2 && "--ws-server".equals(acpArgs[0])) {
            int port = 8080;
            try {
                port = Integer.parseInt(acpArgs[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number: " + acpArgs[1] + ", using default port 8080");
            }
            AcpAgentLauncher.launchWebSocketServer(new AgiAgent(), "0.0.0.0", port);
        } else {
            // 默认：ACP 标准 I/O 模式
            AcpAgentLauncher.launchStdioAgent(new AgiAgent());
        }
    }

    /**
     * 检查参数列表中是否包含指定标志。
     */
    private static boolean hasFlag(String[] args, String flag) {
        for (String arg : args) {
            if (flag.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 过滤掉参数列表中指定的标志，返回剩余的参数数组。
     */
    private static String[] filterArgs(String[] args, String... flagsToRemove) {
        java.util.List<String> flagsList = java.util.Arrays.asList(flagsToRemove);
        return java.util.Arrays.stream(args)
                .filter(arg -> !flagsList.contains(arg))
                .toArray(String[]::new);
    }
}