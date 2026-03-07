package com.xr21.ai.agent;

import com.agentclientprotocol.sdk.client.AcpClient;
import com.agentclientprotocol.sdk.client.AcpSyncClient;
import com.agentclientprotocol.sdk.client.transport.AgentParameters;
import com.agentclientprotocol.sdk.client.transport.StdioAcpClientTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class FileRequestingAgentTest {

    private static final String JAR_PATH = "D:\\local-github\\ai-agents\\library\\build\\libs\\library-1.0.0-all.jar";

    public static void main(String[] args) {
        var params = AgentParameters.builder("java")
                .arg("-jar")
                .arg(findAgentJar())
                .build();

        var transport = new StdioAcpClientTransport(params);

        try (AcpSyncClient client = AcpClient.sync(transport)
                // Handle agent's file read requests
                // Error handling: throw exceptions - SDK converts to JSON-RPC errors
                .readTextFileHandler((ReadTextFileRequest req) -> {
                    System.out.println("[READ] " + req.path());
                    Path path = Path.of(req.path());
                    if (!Files.exists(path)) {
                        throw new RuntimeException("File not found: " + req.path());
                    }
                    try {
                        String content = Files.readString(path);
                        // Apply line limit if specified
                        if (req.limit() != null && req.limit() > 0) {
                            content = content.lines().limit(req.limit())
                                    .collect(java.util.stream.Collectors.joining("\n"));
                        }
                        return new ReadTextFileResponse(content);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to read file: " + e.getMessage(), e);
                    }
                })
                // Handle agent's file write requests
                // Error handling: throw exceptions - SDK converts to JSON-RPC errors
                .writeTextFileHandler((WriteTextFileRequest req) -> {
                    System.out.println("[WRITE] " + req.path());
                    try {
                        Files.writeString(Path.of(req.path()), req.content());
                        return new WriteTextFileResponse();
                    } catch (IOException e) {
                        System.err.println("[WRITE] Error: " + e.getMessage());
                        throw new RuntimeException("Failed to write file: " + e.getMessage(), e);
                    }
                })
                // Handle agent's permission requests (auto-approve for demo)
                .requestPermissionHandler((RequestPermissionRequest req) -> {
                    System.out.println("[PERMISSION] " + req.toolCall().title() + " - auto-approved");
                    return new RequestPermissionResponse(
                            new PermissionSelected(req.options().get(0).optionId()));
                })
                // Handle session updates
                .sessionUpdateConsumer(notification -> {
                    var update = notification.update();
                    if (update instanceof AgentMessageChunk msg) {
                        String text = ((TextContent) msg.content()).text();
                        System.out.print(text);
                    }
                })
                .build()) {

            System.out.println("=== Module 15: Agent File Requests Demo ===\n");

            // Initialize with file system capabilities
            var caps = new ClientCapabilities(
                    new FileSystemCapability(true, true), false);
            client.initialize(new InitializeRequest(1, caps));
            System.out.println("Connected with file capabilities!\n");

            String cwd = System.getProperty("user.dir");
            var session = client.newSession(new NewSessionRequest(cwd, List.of()));
            System.out.println("Session: " + session.sessionId() + "\n");

            System.out.println("--- Agent Output ---");
            var response = client.prompt(new PromptRequest(
                    session.sessionId(),
                    List.of(new TextContent("Demonstrate file operations"))));

            System.out.println("--- End ---");
            System.out.println("Stop reason: " + response.stopReason());

            // Show created file if exists
            Path summaryPath = Path.of("summary.txt");
            if (Files.exists(summaryPath)) {
                System.out.println("\nCreated summary.txt:");
                System.out.println(Files.readString(summaryPath));
                Files.delete(summaryPath);
            }

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
}
