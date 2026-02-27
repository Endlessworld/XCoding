package com.xr21.ai.agent;

import com.agentclientprotocol.sdk.client.AcpClient;
import com.agentclientprotocol.sdk.client.AcpSyncClient;
import com.agentclientprotocol.sdk.client.transport.AgentParameters;
import com.agentclientprotocol.sdk.client.transport.StdioAcpClientTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema;
import com.agentclientprotocol.sdk.spec.AcpSchema.AgentMessageChunk;
import com.agentclientprotocol.sdk.spec.AcpSchema.NewSessionRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.PromptRequest;
import com.agentclientprotocol.sdk.spec.AcpSchema.TextContent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

public class AsyncAgentTest {

    private static final String JAR_PATH = "D:\\local-github\\ai-agents\\library\\build\\libs\\library-1.0.0-all.jar";

    public static void main(String[] args) {
        // 设置控制台输出编码为 UTF-8
        setConsoleEncoding();

        // Launch the AsyncAgent as a subprocess
        var params = AgentParameters.builder("java")
                .arg("-Dfile.encoding=UTF-8")
                .arg("-jar")
                .arg(findAgentJar())
                .build();

        var transport = new StdioAcpClientTransport(params);

        try (AcpSyncClient client = AcpClient.sync(transport).sessionUpdateConsumer(notification -> {
            var update = notification.update();
            if (update instanceof AgentMessageChunk msg) {
                String text = ((TextContent) msg.content()).text();
                System.out.print(text);
            }
        }).requestTimeout(Duration.ofSeconds(500)).build()) {

            System.out.println("=== Module 22: Async Agent Demo ===\n");
            System.out.println("This agent uses AcpAgent.async() with reactive Mono patterns.\n");

            // Initialize
            System.out.println("Sending initialize...");
            client.initialize();
            System.out.println("Connected to AsyncAgent!\n");

            // Create session
            String cwd = System.getProperty("user.dir");
            var session = client.newSession(new NewSessionRequest(cwd, List.of()));
            System.out.println("Session: " + session.sessionId() + "\n");
            // Send prompts
            String[] messages = {"这张图片是什么"};
            for (String message : messages) {
                System.out.println("Sending: " + message);
                Path imagePath = Paths.get("C:\\Users\\Endless\\Desktop\\ScreenShot_2026-02-27_091706_421.png");
                byte[] imageBytes = Files.readAllBytes(imagePath);
                String base64Image = Base64.getEncoder().encodeToString(imageBytes);
                var response = client.prompt(new PromptRequest(session.sessionId(), List.of(new TextContent(message), new AcpSchema.ImageContent("image", base64Image, "image/png", null, null, null))));
                System.out.println("Stop reason: " + response.stopReason() + "\n");
            }

            System.out.println("Demo complete!");

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Find the agent JAR whether running from repo root or module directory.
     */
    private static String findAgentJar() {
        Path fromModule = Path.of(JAR_PATH);
        if (Files.exists(fromModule)) {
            return fromModule.toString();
        }
        throw new RuntimeException("Agent JAR not found.");
    }

    /**
     * 设置控制台输出编码为 UTF-8，解决 Windows 中文乱码问题。
     */
    private static void setConsoleEncoding() {
        try {
            // Windows 下切换控制台代码页为 UTF-8
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                new ProcessBuilder("cmd", "/c", "chcp", "65001").inheritIO().start().waitFor();
            }
            // 设置 System.out 使用 UTF-8 编码
            System.setOut(new java.io.PrintStream(System.out, true, "UTF-8"));
        } catch (Exception e) {
            System.err.println("Failed to set console encoding: " + e.getMessage());
        }
    }
}
