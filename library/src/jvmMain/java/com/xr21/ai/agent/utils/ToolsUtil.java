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

import com.agentclientprotocol.sdk.spec.AcpSchema;
import com.agentclientprotocol.sdk.spec.AcpSchema.ToolCallContent;
import com.agentclientprotocol.sdk.spec.AcpSchema.ToolCallLocation;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xr21.ai.agent.entity.ToolResult;
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
import org.springframework.lang.NonNull;
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

    private static @NonNull List<ToolCallback> getMcpTools() {
        ServerParameters serverParameters = createMcpServerParameters();
        StdioClientTransport stdioClientTransport = new StdioClientTransport(serverParameters, McpJsonMapper.getDefault());
        McpSyncClient mcpSyncClient = McpClient.sync(stdioClientTransport).build();
        return McpToolUtils.getToolCallbacksFromSyncClients(mcpSyncClient);
    }

    /**
     * 创建MCP服务器参数
     */
    private static ServerParameters createMcpServerParameters() {
        String javaPath = "D:\\JetBrains\\IntelliJ IDEA 2025.1.3\\jbr\\bin\\java";
        String classpath = """
                C:\\Users\\Endless\\AppData\\Roaming\\JetBrains\\IntelliJIdea2025.3\\plugins\\mcpserver\\lib\\mcpserver.jar;\
                C:\\Users\\Endless\\AppData\\Roaming\\JetBrains\\IntelliJIdea2025.3\\plugins\\mcpserver\\lib\\io.modelcontextprotocol.kotlin.sdk.jar;\
                C:\\Users\\Endless\\AppData\\Roaming\\JetBrains\\IntelliJIdea2025.3\\plugins\\mcpserver\\lib\\io.github.oshai.kotlin.logging.jvm.jar;\
                D:\\JetBrains\\IntelliJ IDEA 2025.1.3\\lib\\util-8.jar;\
                D:\\JetBrains\\IntelliJ IDEA 2025.1.3\\lib\\module-intellij.libraries.ktor.client.cio.jar;\
                D:\\JetBrains\\IntelliJ IDEA 2025.1.3\\lib\\module-intellij.libraries.ktor.client.jar;\
                D:\\JetBrains\\IntelliJ IDEA 2025.1.3\\lib\\module-intellij.libraries.ktor.network.tls.jar;\
                D:\\JetBrains\\IntelliJ IDEA 2025.1.3\\lib\\module-intellij.libraries.ktor.io.jar;\
                D:\\JetBrains\\IntelliJ IDEA 2025.1.3\\lib\\module-intellij.libraries.ktor.utils.jar;\
                D:\\JetBrains\\IntelliJ IDEA 2025.1.3\\lib\\module-intellij.libraries.kotlinx.io.jar;\
                D:\\JetBrains\\IntelliJ IDEA 2025.1.3\\lib\\module-intellij.libraries.kotlinx.serialization.core.jar;\
                D:\\JetBrains\\IntelliJ IDEA 2025.1.3\\lib\\module-intellij.libraries.kotlinx.serialization.json.jar\
                """;

        return ServerParameters.builder(javaPath)
                .addEnvVar("IJ_MCP_SERVER_PORT", "64342")
                .arg("-classpath")
                .arg(classpath)
                .arg("com.intellij.mcpserver.stdio.McpStdioRunnerKt")
                .build();
    }

    /**
     * 根据 MCP 服务器配置获取对应的工具列表
     *
     * @param mcpServers MCP 服务器列表
     * @return 工具回调列表
     */
    public static List<ToolCallback> getMcpTools(List<AcpSchema.McpServer> mcpServers) {
        List<ToolCallback> mcpTools = new ArrayList<>();

        for (AcpSchema.McpServer server : mcpServers) {
            try {
                if (server instanceof AcpSchema.McpServerStdio stdio) {
                    List<ToolCallback> tools = getMcpToolsFromStdio(stdio);
                    mcpTools.addAll(tools);
                    log.info("Loaded {} tools from STDIO MCP server: {}", tools.size(), stdio.name());
                } else if (server instanceof AcpSchema.McpServerHttp http) {
                    List<ToolCallback> tools = getMcpToolsFromHttp(http);
                    mcpTools.addAll(tools);
                    log.info("Loaded {} tools from HTTP MCP server: {}", tools.size(), http.name());
                } else if (server instanceof AcpSchema.McpServerSse sse) {
                    List<ToolCallback> tools = getMcpToolsFromSse(sse);
                    mcpTools.addAll(tools);
                    log.info("Loaded {} tools from SSE MCP server: {}", tools.size(), sse.name());
                } else {
                    log.warn("Unknown MCP server type: {}", server.getClass().getName());
                }
            } catch (Exception e) {
                log.error("Failed to load MCP server {}: {}", server, e.getMessage(), e);
            }
        }

        return mcpTools;
    }

    public static String describeMcpServer(AcpSchema.McpServer server) {
        if (server instanceof AcpSchema.McpServerStdio stdio) {
            return String.format("STDIO[name=%s, command=%s, args=%s]",
                    stdio.name(), stdio.command(), stdio.args());
        } else if (server instanceof AcpSchema.McpServerHttp http) {
            return String.format("HTTP[name=%s, url=%s]",
                    http.name(), http.url());
        } else if (server instanceof AcpSchema.McpServerSse sse) {
            return String.format("SSE[name=%s, url=%s]",
                    sse.name(), sse.url());
        } else {
            return server.toString();
        }
    }

    /**
     * 从 STDIO MCP 服务器获取工具
     */
    private static List<ToolCallback> getMcpToolsFromStdio(AcpSchema.McpServerStdio stdio) {
        ServerParameters.Builder builder = ServerParameters.builder(stdio.command());
        for (AcpSchema.EnvVariable envVariable : stdio.env()) {
            builder.addEnvVar(envVariable.name(), envVariable.value());

        }
        // 添加命令行参数
        if (stdio.args() != null) {
            for (String arg : stdio.args()) {
                builder.arg(arg);
            }
        }
        ServerParameters serverParameters = builder.build();
        StdioClientTransport transport = new StdioClientTransport(serverParameters, McpJsonMapper.getDefault());
        McpSyncClient mcpClient = McpClient.sync(transport).build();
        return McpToolUtils.getToolCallbacksFromSyncClients(mcpClient);
    }

    /**
     * 从 HTTP MCP 服务器获取工具 (Streamable HTTP transport)
     */
    private static List<ToolCallback> getMcpToolsFromHttp(AcpSchema.McpServerHttp http) {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(http.url())
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        McpSyncClient mcpClient = McpClient.sync(transport).build();
        return McpToolUtils.getToolCallbacksFromSyncClients(mcpClient);
    }

    /**
     * 从 SSE MCP 服务器获取工具 (HTTP with SSE transport)
     */
    private static List<ToolCallback> getMcpToolsFromSse(AcpSchema.McpServerSse sse) {
        HttpClientSseClientTransport transport = HttpClientSseClientTransport.builder(sse.url())
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        McpSyncClient mcpClient = McpClient.sync(transport).build();
        return McpToolUtils.getToolCallbacksFromSyncClients(mcpClient);
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
                        String path = loc.get("path") != null ? String.valueOf(loc.get("path")) : null;
                        Integer line = loc.get("line") != null ? ((Number) loc.get("line")).intValue() : null;
                        result.locations.add(new ToolCallLocation(path, line));
                    }
                }
            }

            // 提取 toolCallContents (简化处理，只提取原始数据)
            if (jsonMap.containsKey(ToolResult.KEY_TOOL_CALL_CONTENTS)) {
                // 这里可以添加更复杂的解析逻辑
                // 目前简单地将原始数据保存
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
