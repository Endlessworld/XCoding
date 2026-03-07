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

import com.agentclientprotocol.sdk.spec.AcpSchema;

import java.util.HashMap;
import java.util.Map;

import static com.agentclientprotocol.sdk.spec.AcpSchema.ToolKind.*;

/**
 *
 * @author Endless
 */
public class ToolKindFind {

    private static final Map<String, AcpSchema.ToolKind> NAME_TO_KIND;

    static {
        NAME_TO_KIND = new HashMap<>();
        // READ tools
        NAME_TO_KIND.put("read_file", READ);
        NAME_TO_KIND.put("ls", SEARCH);
        // EDIT tools
        NAME_TO_KIND.put("write_file", EDIT);
        NAME_TO_KIND.put("edit_file", EDIT);
        // SEARCH tools
        NAME_TO_KIND.put("glob", SEARCH);
        NAME_TO_KIND.put("grep", SEARCH);
        NAME_TO_KIND.put("webSearch", SEARCH);
        // EXECUTE tools
        NAME_TO_KIND.put("Bash", EXECUTE);
        NAME_TO_KIND.put("BashOutput", EXECUTE);
        NAME_TO_KIND.put("KillShell", EXECUTE);
        // THINK tools
        NAME_TO_KIND.put("think", THINK);
        NAME_TO_KIND.put("contextCacheTool", THINK);
        // FETCH tools
        NAME_TO_KIND.put("webFetch", FETCH);
        // OTHER tools
        NAME_TO_KIND.put("FeedBackTool", OTHER);
    }

    /**
     * 根据工具名称获取对应的 ToolKind 类型
     *
     * @param toolName 工具名称，例如 "read_file", "edit_file" 等
     * @return 对应的 ToolKind，如果没有匹配则返回 OTHER
     */
    public static AcpSchema.ToolKind find(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return OTHER;
        }
        return NAME_TO_KIND.getOrDefault(toolName, OTHER);
    }


}
