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

import com.agentclientprotocol.model.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xr21.ai.agent.bridge.BridgeKt;
import com.xr21.ai.agent.entity.ToolResult;
import io.agentscope.core.tool.mcp.McpSyncClientWrapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Endless
 */
@Slf4j
public class ToolsUtil {

    /**
     * 根据 MCP 服务器配置获取对应的工具列表
     *
     * @param mcpServers MCP 服务器列表
     * @return 工具回调列表
     */
    public static List<ToolCallback> getMcpTools(List<McpServer> mcpServers) {
        List<ToolCallback> mcpTools = new ArrayList<>();

        for (McpServer server : mcpServers) {
            try {
                if (server instanceof McpServer.Stdio stdio) {
                    List<ToolCallback> tools = getMcpToolsFromStdio(stdio);
                    mcpTools.addAll(tools);
                    log.info("Loaded {} tools from STDIO MCP server: {}", tools.size(), stdio.getName());
                } else if (server instanceof McpServer.Http http) {
                    List<ToolCallback> tools = getMcpToolsFromHttp(http);
                    mcpTools.addAll(tools);
                    log.info("Loaded {} tools from HTTP MCP server: {}", tools.size(), http.getName());
                } else if (server instanceof McpServer.Sse sse) {
                    List<ToolCallback> tools = getMcpToolsFromSse(sse);
                    mcpTools.addAll(tools);
                    log.info("Loaded {} tools from SSE MCP server: {}", tools.size(), sse.getName());
                } else {
                    log.warn("Unknown MCP server type: {}", server.getClass().getName());
                }
            } catch (Exception e) {
                log.error("Failed to load MCP server {}: {}", server, e.getMessage(), e);
            }
        }

        return mcpTools;
    }

    public static List<McpSyncClientWrapper> getMcpToolsWrapper(List<McpServer> mcpServers) {
        List<McpSyncClientWrapper> mcpTools = new ArrayList<>();

        for (McpServer server : mcpServers) {
            try {
                if (server instanceof McpServer.Stdio stdio) {
                    McpSyncClientWrapper tools = getMcpToolsWrapperFromStdio(stdio);
                    mcpTools.add(tools);
                    log.info("Loaded {} tools from STDIO MCP server: {}", tools.listTools().block().stream().count(), stdio.getName());
                } else if (server instanceof McpServer.Http http) {
                    McpSyncClientWrapper tools = getMcpToolsWrapperFromHttp(http);
                    mcpTools.add(tools);
                    log.info("Loaded {} tools from HTTP MCP server: {}", tools.listTools().block().stream().count(), http.getName());
                } else if (server instanceof McpServer.Sse sse) {
                    McpSyncClientWrapper tools = getMcpToolsWrapperFromSse(sse);
                    mcpTools.add(tools);
                    log.info("Loaded {} tools from SSE MCP server: {}", tools.listTools().block().stream().count(), sse.getName());
                } else {
                    log.warn("Unknown MCP server type: {}", server.getClass().getName());
                }
            } catch (Exception e) {
                log.error("Failed to load MCP server {}: {}", server, e.getMessage(), e);
            }
        }

        return mcpTools;
    }



    /**
     * 从 STDIO MCP 服务器获取工具
     */
    private static List<ToolCallback> getMcpToolsFromStdio(McpServer.Stdio stdio) {
        McpSyncClient mcpClient = getSyncClient(stdio);
        return McpToolUtils.getToolCallbacksFromSyncClients(mcpClient);
    }

    private static McpSyncClient getSyncClient(McpServer.Stdio stdio) {
        ServerParameters.Builder builder = ServerParameters.builder(stdio.getCommand());
        for (EnvVariable envVariable : stdio.getEnv()) {
            builder.addEnvVar(envVariable.getName(), envVariable.getValue());

        }
        // 添加命令行参数
        if (!CollectionUtils.isEmpty(stdio.getArgs())) {
            for (String arg : stdio.getArgs()) {
                builder.arg(arg);
            }
        }
        ServerParameters serverParameters = builder.build();
        StdioClientTransport transport = new StdioClientTransport(serverParameters, McpJsonMapper.getDefault());
        // Native Image 中进程启动和 stdio 通信可能较慢，增加初始化超时时间
        return McpClient.sync(transport)
                .initializationTimeout(Duration.ofSeconds(3))
                .requestTimeout(Duration.ofSeconds(3))
                .build();
    }

    /**
     * 从 HTTP MCP 服务器获取工具 (Streamable HTTP transport)
     */
    private static List<ToolCallback> getMcpToolsFromHttp(McpServer.Http http) {
        McpSyncClient mcpClient = getSyncClient(http);
        return McpToolUtils.getToolCallbacksFromSyncClients(mcpClient);
    }

    private static McpSyncClient getSyncClient(McpServer.Http http) {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(http.getUrl()).connectTimeout(Duration.ofSeconds(30)).build();
        return McpClient.sync(transport).build();
    }

    /**
     * 从 SSE MCP 服务器获取工具 (HTTP with SSE transport)
     */
    private static List<ToolCallback> getMcpToolsFromSse(McpServer.Sse sse) {
        McpSyncClient mcpClient = getSyncClient(sse);
        return McpToolUtils.getToolCallbacksFromSyncClients(mcpClient);
    }

    /**
     * 从 SSE MCP 服务器获取工具 (HTTP with SSE transport)
     */
    private static McpSyncClientWrapper getMcpToolsWrapperFromSse(McpServer.Sse sse) {
        McpSyncClient mcpClient = getSyncClient(sse);
        return new McpSyncClientWrapper("sse", mcpClient);
    }

    /**
     * 从 STDIO MCP 服务器获取工具
     */
    private static McpSyncClientWrapper getMcpToolsWrapperFromStdio(McpServer.Stdio stdio) {
        McpSyncClient mcpClient = getSyncClient(stdio);
        return new McpSyncClientWrapper("stdio", mcpClient);
    }

    /**
     * 从 HTTP MCP 服务器获取工具 (Streamable HTTP transport)
     */
    private static McpSyncClientWrapper getMcpToolsWrapperFromHttp(McpServer.Http http) {
        McpSyncClient mcpClient = getSyncClient(http);
        return new McpSyncClientWrapper("http", mcpClient);
    }

    private static McpSyncClient getSyncClient(McpServer.Sse sse) {
        HttpClientSseClientTransport transport = HttpClientSseClientTransport.builder(sse.getUrl()).connectTimeout(Duration.ofSeconds(30)).build();
        McpSyncClient mcpClient = McpClient.sync(transport).build();
        return mcpClient;
    }

    /**
     * 解析 ToolResult 格式的响应数据
     *
     * @param responseData 工具响应数据
     * @return 解析后的工具结果数据
     */
    public static ToolResultData parseToolResult(String responseData) {
        ToolResultData result = new ToolResultData();

        if (!StringUtils.hasText(responseData)) {
            result.success = true;
            return result;
        }

        try {
            // 尝试解析为 JSON Map
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> jsonMap = objectMapper.readValue(responseData, new TypeReference<Map<String, Object>>() {
            });

            // 提取 success
            if (jsonMap.containsKey(ToolResult.KEY_SUCCESS)) {
                result.success = Boolean.TRUE.equals(jsonMap.get(ToolResult.KEY_SUCCESS));
            } else {
                result.success = !jsonMap.containsKey(ToolResult.KEY_ERROR);
            }

            // 提取 content
            if (jsonMap.containsKey(ToolResult.KEY_CONTENT)) {
                result.content = String.valueOf(jsonMap.get(ToolResult.KEY_CONTENT));
            }

            // 提取 error
            if (jsonMap.containsKey(ToolResult.KEY_ERROR)) {
                result.error = String.valueOf(jsonMap.get(ToolResult.KEY_ERROR));
            }

            // 提取 locations
            if (jsonMap.containsKey(ToolResult.KEY_LOCATIONS)) {
                Object locs = jsonMap.get(ToolResult.KEY_LOCATIONS);
                if (locs instanceof List) {
                    List<Map<String, Object>> locList = (List<Map<String, Object>>) locs;
                    result.locations = new ArrayList<>();
                    for (Map<String, Object> loc : locList) {
                        String path = loc.get("path") != null ? String.valueOf(loc.get("path")) : "";
                        int line = loc.get("line") != null ? ((Number) loc.get("line")).intValue() : 0;
                        result.locations.add(BridgeKt.createToolCallLocation(path, line));
                    }
                }
            }

            // 提取 toolCallContents - 通过字段存在性推断类型（Jackson 不输出 Kotlin Serialization 的 type discriminator）
            if (jsonMap.containsKey(ToolResult.KEY_TOOL_CALL_CONTENTS)) {
                Object contents = jsonMap.get(ToolResult.KEY_TOOL_CALL_CONTENTS);
                if (contents instanceof List) {
                    List<Map<String, Object>> contentList = (List<Map<String, Object>>) contents;
                    result.toolCallContents = new ArrayList<>();
                    for (Map<String, Object> item : contentList) {
                        // Infer type by field presence (Jackson doesn't emit Kotlin Serialization's type discriminator)
                        if (item.containsKey("terminalId")) {
                            // Terminal type
                            String terminalId = item.get("terminalId") != null ? String.valueOf(item.get("terminalId")) : "";
                            result.toolCallContents.add(new ToolCallContent.Terminal(terminalId, null));
                        } else if (item.containsKey("path")) {
                            // Diff type
                            String path = item.get("path") != null ? String.valueOf(item.get("path")) : "";
                            String newText = item.get("newText") != null ? String.valueOf(item.get("newText")) : "";
                            String oldText = item.get("oldText") != null ? String.valueOf(item.get("oldText")) : null;
                            result.toolCallContents.add(new ToolCallContent.Diff(path, newText, oldText, null));
                        } else if (item.containsKey("content")) {
                            // Content type (nested { type: "text", text: "..." })
                            Object contentObj = item.get("content");
                            if (contentObj instanceof Map) {
                                Map<String, Object> contentMap = (Map<String, Object>) contentObj;
                                String text = contentMap.get("text") != null ? String.valueOf(contentMap.get("text")) : "";
                                result.toolCallContents.add(new ToolCallContent.Content(new ContentBlock.Text(text, null, null)));
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            // 解析失败，视为纯文本内容
            result.success = true;
            result.content = responseData;
        }

        return result;
    }

    /**
     * 工具结果数据结构
     */
    public static class ToolResultData {
        public boolean success = true;
        public String content;
        public String error;
        public List<ToolCallLocation> locations;
        public List<ToolCallContent> toolCallContents;
    }

}
