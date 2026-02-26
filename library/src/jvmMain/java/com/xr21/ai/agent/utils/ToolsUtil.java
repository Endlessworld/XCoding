package com.xr21.ai.agent.utils;

import com.agentclientprotocol.sdk.spec.AcpSchema;
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

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
            builder.addEnvVar(envVariable.name(),envVariable.value());

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

}
