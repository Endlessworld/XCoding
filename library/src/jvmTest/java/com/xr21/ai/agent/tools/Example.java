package com.xr21.ai.agent.tools;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.common.McpTransportContext;
import reactor.util.annotation.Nullable;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Map;

public class Example {
    /**
     * 从配置Map中创建带有自定义头部的传输层
     */
    public static HttpClientStreamableHttpTransport createTransportWithHeaders(
            String url, String endpoint, Map<String, String> headers) {
        return HttpClientStreamableHttpTransport
                .builder(url)
                .endpoint(endpoint)
                .connectTimeout(Duration.ofSeconds(30))
                .httpRequestCustomizer((HttpRequest.Builder builder, String method, URI uri, @Nullable String body,
                                        McpTransportContext context) -> {
                    headers.forEach(builder::header);

                })
                .build();
    }
    public static void main(String[] args) {
        // 配置参数
        String url = "http://127.0.0.1:64342";
        String endpoint = "/stream";
        Map<String, String> headers = Map.of(
                "IJ_MCP_SERVER_PROJECT_PATH", "E:/local-github/ai-agents"
        );

        // 1. 创建传输层（使用静态方法）
        HttpClientStreamableHttpTransport transport = createTransportWithHeaders(url, endpoint, headers);

        // 2. 创建 MCP 客户端
        McpSyncClient mcpClient = McpClient.sync(transport)
                .initializationTimeout(Duration.ofSeconds(30))
                .requestTimeout(Duration.ofSeconds(30))
                .build();

        try {
            // 3. 初始化连接
            mcpClient.initialize();
            System.out.println("✅ 连接成功！");

            // 4. 获取可用工具列表
            var tools = mcpClient.listTools();
            System.out.println("可用工具数量: " + tools.tools().size());
            tools.tools().forEach(tool ->
                System.out.println("工具: " + tool.name() + " - " + tool.description()));

            // 5. 这里可以执行工具调用等操作
            // ...

        } catch (Exception e) {
            System.err.println("❌ 连接失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // 6. 清理资源
            mcpClient.closeGracefully();
        }
    }
}