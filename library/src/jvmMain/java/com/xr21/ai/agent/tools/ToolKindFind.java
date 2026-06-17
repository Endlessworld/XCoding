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

import com.agentclientprotocol.model.ToolKind;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author Endless
 */
public class ToolKindFind {

    private static final Map<String, ToolKind> NAME_TO_KIND;

    static {
        NAME_TO_KIND = new HashMap<>();
        // READ tools
        NAME_TO_KIND.put("read_file", ToolKind.READ);
        NAME_TO_KIND.put("ls", ToolKind.SEARCH);
        // EDIT tools
        NAME_TO_KIND.put("write_file", ToolKind.EDIT);
        NAME_TO_KIND.put("edit_file", ToolKind.EDIT);
        NAME_TO_KIND.put("smart_edit", ToolKind.EDIT);
        // SEARCH tools
        NAME_TO_KIND.put("glob", ToolKind.SEARCH);
        NAME_TO_KIND.put("grep", ToolKind.SEARCH);
        NAME_TO_KIND.put("web_search", ToolKind.SEARCH);
        NAME_TO_KIND.put("web_fetch", ToolKind.FETCH);
        // EXECUTE tools
        NAME_TO_KIND.put("Bash", ToolKind.EXECUTE);
        NAME_TO_KIND.put("BashOutput", ToolKind.EXECUTE);
        NAME_TO_KIND.put("KillShell", ToolKind.EXECUTE);
        // THINK tools
        NAME_TO_KIND.put("think", ToolKind.THINK);
        NAME_TO_KIND.put("contextCacheTool", ToolKind.THINK);
        // FETCH tools
        NAME_TO_KIND.put("webFetch", ToolKind.FETCH);
        // OTHER tools
        NAME_TO_KIND.put("write_todos", ToolKind.EDIT);
    }

    /**
     * 根据工具名称获取对应的 ToolKind 类型
     *
     * @param toolName 工具名称，例如 "read_file", "edit_file" 等
     * @return 对应的 ToolKind，如果没有匹配则返回 OTHER
     */
    public static ToolKind find(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return ToolKind.OTHER;
        }
        return NAME_TO_KIND.getOrDefault(toolName, ToolKind.OTHER);
    }


}
