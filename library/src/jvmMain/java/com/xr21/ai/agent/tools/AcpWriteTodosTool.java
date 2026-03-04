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

import com.agentclientprotocol.sdk.agent.SyncPromptContext;
import com.agentclientprotocol.sdk.spec.AcpSchema;
import com.agentclientprotocol.sdk.spec.AcpSchema.Plan;
import com.agentclientprotocol.sdk.spec.AcpSchema.PlanEntry;
import com.agentclientprotocol.sdk.spec.AcpSchema.PlanEntryPriority;
import com.agentclientprotocol.sdk.spec.AcpSchema.PlanEntryStatus;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.graph.agent.tools.ToolContextConstants.AGENT_STATE_FOR_UPDATE_CONTEXT_KEY;

/**
 * ACP-compatible Tool for writing and managing todos in the agent workflow.
 * This tool allows agents to create, update, and track task lists using ACP protocol.
 */
@Slf4j
public class AcpWriteTodosTool {

    public AcpWriteTodosTool() {
    }

    @Tool(name = "write_todos", description = """
            Use this tool to create and manage a structured task list using ACP protocol.
            This sends real-time Plan updates to the client showing your progress.
            
            When to use:
            1. Complex multi-step tasks (3+ steps)
            2. Non-trivial tasks requiring planning
            3. User explicitly requests todo list
            4. User provides multiple tasks
            5. Plan may need revisions based on results
            
            How to use:
            1. Mark tasks as IN_PROGRESS before starting
            2. Mark as COMPLETED immediately after finishing
            3. Update tasks as needed (add/remove/change)
            4. Each update sends ACP Plan update
            
            Task States (Must be uppercase):
            - PENDING: Not started
            - IN_PROGRESS: Currently working
            - COMPLETED: Finished
            
            Task Priorities (Must be uppercase):
            - HIGH: Critical
            - MEDIUM: Important
            - LOW: Nice-to-have
            
            Important: Don't use for simple tasks (<3 steps). Update status immediately.
            """)
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

            // Convert request entries to ACP PlanEntries
            List<PlanEntry> planEntries = convertToPlanEntries(entries);

            // Create ACP Plan
            Plan plan = new Plan("plan", planEntries);

            sendAcpPlanUpdate(toolContext, plan);

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

            PlanEntry planEntry = new PlanEntry(i + ". " + entry.content(), PlanEntryPriority.valueOf(entry.priority()), PlanEntryStatus.valueOf(entry.status()));
            planEntries.add(planEntry);
        }

        return planEntries;
    }

    /**
     * Send ACP Plan update through SyncPromptContext if available
     */
    private void sendAcpPlanUpdate(ToolContext toolContext, Plan plan) {
        try {
            log.info("config context {}", toolContext);
            if (toolContext.getContext().get("_AGENT_CONFIG_") instanceof RunnableConfig config) {
                log.info("config context {}", config.context());
                log.info("config context PromptRequest {}", config.context().get("PromptRequest"));
                log.info("config context SyncPromptContext {}", config.context().get("SyncPromptContext"));
                if (config.context().get("SyncPromptContext") instanceof SyncPromptContext syncPromptContext) {
                    if (config.context().get("PromptRequest") instanceof AcpSchema.PromptRequest promptRequest) {
                        var sessionId = promptRequest.sessionId();
                        syncPromptContext.sendUpdate(sessionId, plan);
                        log.info("sendUpdate sessionId : {} plan: {}", sessionId, plan);
                    }
                }
            }

        } catch (Exception e) {
            // Log but don't fail if we can't send the update
            System.err.println("Warning: Could not send ACP Plan update: " + e.getMessage());
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