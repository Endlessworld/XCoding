package com.xr21.ai.agent;

import com.agentclientprotocol.sdk.agent.support.AcpAgentSupport;
import com.agentclientprotocol.sdk.agent.transport.StdioAcpAgentTransport;
import com.agentclientprotocol.sdk.agent.transport.WebSocketAcpAgentTransport;
import com.agentclientprotocol.sdk.spec.AcpAgentTransport;
import com.xr21.ai.agent.agent.AcpAgent;
import io.modelcontextprotocol.json.McpJsonMapper;

import java.time.Duration;
import java.util.List;

public class AgentApplication {

    public static void main(String[] args) {
        AcpAgentTransport acpAgentTransport = new StdioAcpAgentTransport();
        if (List.of(args).contains("--socket")) {
            acpAgentTransport = new WebSocketAcpAgentTransport(9315,"/acp", McpJsonMapper.createDefault());
        }
        AcpAgentSupport.create(new AcpAgent())
                .transport(acpAgentTransport)
                .requestTimeout(Duration.ofSeconds(300))
                .build()
                .run();
    }
}
