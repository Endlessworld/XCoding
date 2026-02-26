package com.xr21.ai.agent.tools;

import com.agentclientprotocol.sdk.spec.AcpSchema;
import com.agentclientprotocol.sdk.spec.AcpSchema.ToolCallContent;
import com.agentclientprotocol.sdk.spec.AcpSchema.ToolCallLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Standardized tool result wrapper that supports ACP schema features.
 * Provides structured output including content, locations, and specialized content types.
 */
public class ToolResult {

    public static final String KEY_CONTENT = "content";
    public static final String KEY_LOCATIONS = "locations";
    public static final String KEY_TOOL_CALL_CONTENTS = "toolCallContents";
    public static final String KEY_SUCCESS = "success";
    public static final String KEY_ERROR = "error";
    public static final String KEY_METADATA = "metadata";

    private final Map<String, Object> data = new HashMap<>();

    private ToolResult() {
    }

    public static ToolResult builder() {
        return new ToolResult();
    }

    public static ToolCallContent createDiffContent(String path, String oldText, String newText) {
        return new AcpSchema.ToolCallDiff("diff", path, oldText, newText);
    }

    public static ToolCallContent createTerminalContent(String terminalId) {
        return new AcpSchema.ToolCallTerminal("terminal", terminalId);
    }

    public static ToolCallContent createTextContent(String text) {
        return new AcpSchema.ToolCallContentBlock("content", new AcpSchema.TextContent(text));
    }

    @SuppressWarnings("unchecked")
    public static List<ToolCallLocation> getLocations(Map<String, Object> result) {
        Object locs = result.get(KEY_LOCATIONS);
        if (locs instanceof List) {
            return (List<ToolCallLocation>) locs;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static List<ToolCallContent> getToolCallContents(Map<String, Object> result) {
        Object contents = result.get(KEY_TOOL_CALL_CONTENTS);
        if (contents instanceof List) {
            return (List<ToolCallContent>) contents;
        }
        return null;
    }

    public static String getContent(Map<String, Object> result) {
        Object content = result.get(KEY_CONTENT);
        return content != null ? content.toString() : null;
    }

    public static boolean isSuccess(Map<String, Object> result) {
        Object success = result.get(KEY_SUCCESS);
        return Boolean.TRUE.equals(success);
    }

    public ToolResult success(boolean success) {
        data.put(KEY_SUCCESS, success);
        return this;
    }

    public ToolResult content(String content) {
        data.put(KEY_CONTENT, content);
        return this;
    }

    public ToolResult error(String error) {
        data.put(KEY_ERROR, error);
        data.put(KEY_SUCCESS, false);
        return this;
    }

    public ToolResult location(String path, Integer line) {
        return location(new ToolCallLocation(path, line));
    }

    // Helper methods for common content types

    public ToolResult location(ToolCallLocation location) {
        @SuppressWarnings("unchecked")
        List<ToolCallLocation> locations = (List<ToolCallLocation>) data.computeIfAbsent(KEY_LOCATIONS, k -> new ArrayList<ToolCallLocation>());
        locations.add(location);
        return this;
    }

    public ToolResult locations(List<ToolCallLocation> locations) {
        if (locations != null && !locations.isEmpty()) {
            data.put(KEY_LOCATIONS, new ArrayList<>(locations));
        }
        return this;
    }

    public ToolResult toolCallContent(ToolCallContent content) {
        @SuppressWarnings("unchecked")
        List<ToolCallContent> contents = (List<ToolCallContent>) data.computeIfAbsent(KEY_TOOL_CALL_CONTENTS, k -> new ArrayList<ToolCallContent>());
        contents.add(content);
        return this;
    }

    // Utility methods for extracting from result map

    public ToolResult toolCallContents(List<ToolCallContent> contents) {
        if (contents != null && !contents.isEmpty()) {
            data.put(KEY_TOOL_CALL_CONTENTS, new ArrayList<>(contents));
        }
        return this;
    }

    public ToolResult metadata(String key, Object value) {
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) data.computeIfAbsent(KEY_METADATA, k -> new HashMap<String, Object>());
        metadata.put(key, value);
        return this;
    }

    public ToolResult put(String key, Object value) {
        data.put(key, value);
        return this;
    }

    public Map<String, Object> build() {
        // Ensure success flag is set
        if (!data.containsKey(KEY_SUCCESS)) {
            data.put(KEY_SUCCESS, !data.containsKey(KEY_ERROR));
        }
        return new HashMap<>(data);
    }
}
