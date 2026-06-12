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

import com.xr21.ai.agent.acp.AgiAgent;
import com.xr21.ai.agent.acp.AcpAgentLauncher;

/**
 * XAgent 主入口，支持多种运行模式。
 *
 * 使用方式:
 *   java -jar XAgent-all.jar --acp         运行 ACP 模式（默认，标准 I/O 协议）
 *   java -jar XAgent-all.jar --acp --ws-server <port>  运行 ACP WebSocket 服务器模式
 *   java -jar XAgent-all.jar --tui         运行 TUI 模式（自动启动 WebSocket 服务器并连接）
 *   java -jar XAgent-all.jar --tui --ws-url <url>  运行 TUI 模式并连接到指定 WebSocket URL
 *   java -jar XAgent-all.jar --tui --command <cmd>  运行 TUI 模式并使用指定命令启动 Agent
 *   java -jar XAgent-all.jar --tui --ws-server-port <port>  运行 TUI 模式并指定自动启动的 WebSocket 服务器端口（默认：9988）
 *
 * @author Endless
 */
public class AgentApplication {

    public static void main(String[] args) {
        // 检查是否有 --tui 参数
        if (hasFlag(args, "--tui")) {
            // 检查是否提供了连接参数 (--ws-url 或 --command)
            boolean hasConnectionParams = hasFlag(args, "--ws-url") || hasFlag(args, "--command");
            
            if (!hasConnectionParams) {
                // 自动启动 WebSocket 服务器并连接
                int port = 9988;
                if (hasFlag(args, "--ws-server-port")) {
                    String portArg = getArgValue(args, "--ws-server-port");
                    if (portArg != null) {
                        try {
                            port = Integer.parseInt(portArg);
                        } catch (NumberFormatException e) {
                            System.err.println("Invalid port number: " + portArg + ", using default port 9988");
                        }
                    }
                }
                
                // 在后台线程启动 WebSocket 服务器
                int finalPort = port;
                Thread serverThread = new Thread(() -> {
                    try {
                        System.out.println("Starting WebSocket server on port " + finalPort + "...");
                        AcpAgentLauncher.launchWebSocketServer(new AgiAgent(), "127.0.0.1", finalPort);
                    } catch (Exception e) {
                        System.err.println("Failed to start WebSocket server: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
                serverThread.setDaemon(true);
                serverThread.start();
                
                // 等待服务器启动
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                // 构造 TUI 参数，添加自动生成的 WebSocket URL
                java.util.List<String> tuiArgsList = new java.util.ArrayList<>();
                boolean skipNext = false;
                for (int i = 0; i < args.length; i++) {
                    if (skipNext) {
                        skipNext = false;
                        continue;
                    }
                    if ("--tui".equals(args[i])) {
                        continue;
                    }
                    if ("--ws-server-port".equals(args[i])) {
                        skipNext = true;
                        continue;
                    }
                    tuiArgsList.add(args[i]);
                }
                tuiArgsList.add("--ws-url");
                tuiArgsList.add("ws://127.0.0.1:" + port + "/acp");
                
                // 使用 Kotlin Tamboui TUI
                com.xr21.ai.agent.tui.TambouiMainKt.main(tuiArgsList.toArray(new String[0]));
            } else {
                // 使用提供的连接参数 - 使用 Kotlin TUI
                com.xr21.ai.agent.tui.TambouiMainKt.main(filterArgs(args, "--tui"));
            }
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

    /**
     * 获取指定参数的值
     */
    private static String getArgValue(String[] args, String flag) {
        for (int i = 0; i < args.length - 1; i++) {
            if (flag.equals(args[i])) {
                return args[i + 1];
            }
        }
        return null;
    }
}