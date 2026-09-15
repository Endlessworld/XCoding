/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.xr21.ai.agent.tools;

import com.agentclientprotocol.common.ClientSessionOperations;
import com.agentclientprotocol.model.PlanEntry;
import com.agentclientprotocol.model.PlanEntryPriority;
import com.agentclientprotocol.model.PlanEntryStatus;
import com.agentclientprotocol.model.SessionUpdate;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.xr21.ai.agent.utils.Prompts;
import com.xr21.ai.agent.utils.SuspendKt;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.graph.agent.tools.ToolContextConstants.AGENT_STATE_FOR_UPDATE_CONTEXT_KEY;
import static com.xr21.ai.agent.acp.AgiAgentKt.CLIENT_SESSION_CONTEXT_KEY;
import static com.xr21.ai.agent.acp.AgiAgentKt.SESSION_ID_CONTEXT_KEY;

/**
 * ACP-compatible Tool for writing and managing todos in the agent workflow.
 * This tool allows agents to create, update, and track task lists using ACP protocol.
 *
 * @author Endless
 */
@Slf4j
public class AcpWriteTodosTool {

    public AcpWriteTodosTool() {
    }

    @Tool(name = "write_todos", description = Prompts.TOOL_WRITE_TODOS_DESCRIPTION)
    public Map<String, Object> writeTodos(
            @JsonProperty(value = "entries", required = true)
            @JsonPropertyDescription("List of todo entries with content, status and priority")
            List<RequestEntry> entries,
            ToolContext toolContext) {
        try {
            // Extract state from ToolContext
            Map<String, Object> contextData = toolContext.getContext();
            if (contextData == null) {
                return Map.of("error", "Tool context is not available");
            }

            Object extraStateObj = contextData.get(AGENT_STATE_FOR_UPDATE_CONTEXT_KEY);
            if (extraStateObj == null) {
                return Map.of("error", "Extra state is not initialized");
            }

            if (!(extraStateObj instanceof Map)) {
                return Map.of("error", "Extra state has invalid type");
            }
            List<PlanEntry> planEntries = convertToPlanEntries(entries);
            sendAcpPlanUpdate(toolContext, planEntries);
            return Map.of("success", true, "message", "Updated todo list with " + entries.size() + " entries using ACP Plan");

        } catch (ClassCastException e) {
            return Map.of("error", "Invalid state type - " + e.getMessage());
        } catch (Exception e) {
            return Map.of("error", "Failed to update todos - " + e.getMessage());
        }
    }

    /**
     * Convert tool request entries to ACP PlanEntries
     */
    private List<PlanEntry> convertToPlanEntries(List<RequestEntry> entries) {
        List<PlanEntry> planEntries = new ArrayList<>();

        for (int i = 0; i < entries.size(); i++) {
            RequestEntry entry = entries.get(i);

            PlanEntry planEntry = new PlanEntry(i + ". " + entry.content(), PlanEntryPriority.valueOf(entry.priority()), PlanEntryStatus.valueOf(entry.status()), null);
            planEntries.add(planEntry);
        }

        return planEntries;
    }

    /**
     * Send ACP Plan update ClientSessionOperations
     */
    private void sendAcpPlanUpdate(ToolContext toolContext, List<PlanEntry> planEntrys) {
        try {
            if (toolContext.getContext().get("_AGENT_CONFIG_") instanceof RunnableConfig config) {
                if (config.context().get(CLIENT_SESSION_CONTEXT_KEY) instanceof ClientSessionOperations clientSessionOperations) {
                    SuspendKt.runSuspend((completion) -> {
                        SessionUpdate notification = new SessionUpdate.PlanUpdate(planEntrys, null);
                        clientSessionOperations.notify(notification, null, completion);
                        if (config.context().get(SESSION_ID_CONTEXT_KEY) instanceof String sessionId) {
                            log.info("sendUpdate sessionId : {} plan: {}", sessionId, notification);
                        }
                        return null;
                    });
                }
            }
        } catch (Exception e) {
            log.info("Warning: Could not send ACP Plan update: {}", e.getMessage());
        }
    }

    /**
     * A single todo entry for ACP protocol
     */
    @JsonClassDescription("A single todo entry for ACP protocol")
    public record RequestEntry(
            @JsonProperty(required = true, value = "content") @JsonPropertyDescription("Content/description of the todo item") String content,

            @JsonProperty(value = "priority") @JsonPropertyDescription("Priority of the todo item (HIGH, MEDIUM, LOW)") String priority,

            @JsonProperty(required = true, value = "status") @JsonPropertyDescription("Status of the todo item (PENDING, IN_PROGRESS, COMPLETED)") String status


    ) {
        public RequestEntry(String content, String status) {
            this(content, "MEDIUM", status);
        }
    }
}