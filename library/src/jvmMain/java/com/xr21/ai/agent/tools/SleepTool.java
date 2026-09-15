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
package com.xr21.ai.agent.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.xr21.ai.agent.entity.ToolResult;
import com.xr21.ai.agent.utils.Prompts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;

import java.util.Map;

/**
 * 休眠工具 - 使智能体具备休眠/等待能力
 *
 * @author Endless
 */
@Slf4j
public class SleepTool {

    private static final int MAX_SLEEP_SECONDS = 600;

    // @formatter:off
    @Tool(name = "Sleep", description = Prompts.TOOL_SLEEP_DESCRIPTION)
    public Map<String, Object> sleep(
            @JsonProperty(value = "seconds", required = true)
            @JsonPropertyDescription("The number of seconds to sleep. Must be a positive integer, maximum 600 seconds (10 minutes) default 30s")
            Integer seconds) { // @formatter:on

        if (seconds == null || seconds <= 0) {
            return ToolResult.builder()
                    .error("Sleep seconds must be a positive integer")
                    .build();
        }

        long sleepSec = Math.min(seconds, MAX_SLEEP_SECONDS);
        log.info("Sleep tool called - sleeping {} seconds", sleepSec);

        try {
            Thread.sleep(sleepSec * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResult.builder()
                    .success(false)
                    .content("Sleep interrupted: " + e.getMessage())
                    .build();
        }

        return ToolResult.builder()
                .success(true)
                .content(String.format("Slept for %d seconds and woke up.", sleepSec))
                .put("requestedSeconds", seconds)
                .put("sleptSeconds", sleepSec)
                .build();
    }
}
