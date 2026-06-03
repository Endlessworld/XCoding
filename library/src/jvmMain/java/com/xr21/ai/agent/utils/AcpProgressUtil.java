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
package com.xr21.ai.agent.utils;

import com.agentclientprotocol.common.ClientSessionOperations;
import com.agentclientprotocol.model.ContentBlock;
import com.agentclientprotocol.model.SessionUpdate;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.xr21.ai.agent.bridge.BridgeKt;
import kotlin.coroutines.jvm.internal.RunSuspendKt;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;

import static com.agentclientprotocol.launcher.AgiAgentKt.CLIENT_SESSION_CONTEXT_KEY;
import static com.agentclientprotocol.launcher.AgiAgentKt.SESSION_ID_CONTEXT_KEY;

/**
 * Utility class for sending ACP real-time progress updates from tools.
 * Reference: AcpWriteTodosTool.sendAcpPlanUpdate()
 */
@Slf4j
public class AcpProgressUtil {

    /**
     * Send a real-time AgentThoughtChunk notification to the ACP client.
     * This allows tools to stream progress updates as they execute.
     *
     * @param toolContext the ToolContext from the tool method
     * @param message     the progress message to send
     */
    public static void sendProgress(ToolContext toolContext, String message) {
        try {
            if (toolContext.getContext().get("_AGENT_CONFIG_") instanceof RunnableConfig config) {
                sendProgress(config, message);
            }
        } catch (Exception e) {
            log.debug("Could not send ACP progress update: {}", e.getMessage());
        }
    }

    /**
     * Send a real-time AgentThoughtChunk notification to the ACP client.
     * This allows tools to stream progress updates as they execute.
     *
     * @param config  the RunnableConfig
     * @param message the progress message to send
     */
    public static void sendProgress(RunnableConfig config, String message) {
        try {
            if (config.context().get(CLIENT_SESSION_CONTEXT_KEY) instanceof ClientSessionOperations client) {
                RunSuspendKt.runSuspend((completion) -> {
                    SessionUpdate notification = BridgeKt.buildAgentThoughtChunk(new ContentBlock.Text(message, null, null));
                    client.notify(notification, null, completion);
                    if (config.context().get(SESSION_ID_CONTEXT_KEY) instanceof String sessionId) {
                        log.debug("ACP progress [{}]: {}", sessionId, message);
                    }
                    return null;
                });
            }
        } catch (Exception e) {
            log.debug("Could not send ACP progress update: {}", e.getMessage());
        }
    }
}
