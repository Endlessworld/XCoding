package com.xr21.ai.agent.utils;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.lang.NonNull;

import java.util.List;

public class StdioMcpUtil {

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

}
