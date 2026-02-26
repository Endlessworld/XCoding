package com.xr21.ai.agent.tools;

import com.agentclientprotocol.sdk.spec.AcpSchema;

import java.util.HashMap;
import java.util.Map;

import static com.agentclientprotocol.sdk.spec.AcpSchema.ToolKind.*;

public class ToolKindFind {

    private static final Map<String, AcpSchema.ToolKind> NAME_TO_KIND;

    static {
        NAME_TO_KIND = new HashMap<>();
        // READ tools
        NAME_TO_KIND.put("read_file", READ);
        NAME_TO_KIND.put("ls", READ);
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
