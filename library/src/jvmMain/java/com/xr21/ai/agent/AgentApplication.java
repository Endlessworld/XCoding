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

import java.util.Arrays;

/**
 *
 * @author Endless
 */
public class AgentApplication {

    public static void main(String[] args) {
        if (args.length >= 2) {
//            if ("--ws".equals(args[0])) {
//                String wsUrl = args[1];
//                HttpClient client = AcpAgentLauncher.createWebSocketClient();
//                AcpAgentLauncher.launchWebSocketAgent(new AgiAgent(), wsUrl, client);
//            }
            if ("--ws-server".equals(args[0])) {
                int port = 8080;
                try {
                    port = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    System.err.println("Invalid port number: " + args[1] + ", using default port 9988");
                }
                AcpAgentLauncher.launchWebSocketServer(new AgiAgent(), "0.0.0.0", port);
            } else {
                System.err.println("Invalid args" + Arrays.asList(args));
            }
        } else {
            AcpAgentLauncher.launchStdioAgent(new AgiAgent());
        }
    }
}