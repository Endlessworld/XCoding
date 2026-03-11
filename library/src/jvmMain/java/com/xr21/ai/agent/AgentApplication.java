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
package com.xr21.ai.agent;

import com.agentclientprotocol.sdk.agent.support.AcpAgentSupport;
import com.agentclientprotocol.sdk.agent.transport.StdioAcpAgentTransport;
import com.agentclientprotocol.sdk.agent.transport.WebSocketAcpAgentTransport;
import com.agentclientprotocol.sdk.spec.AcpAgentTransport;
import com.xr21.ai.agent.agent.AcpAgent;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;

import java.time.Duration;
import java.util.List;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

/**
 *
 * @author Endless
 */
public class AgentApplication {

    public static void main(String[] args) {
        JacksonMcpJsonMapper mapper = (JacksonMcpJsonMapper) McpJsonMapper.createDefault();
        mapper.getObjectMapper().configure(FAIL_ON_UNKNOWN_PROPERTIES,true);
        AcpAgentTransport acpAgentTransport = new StdioAcpAgentTransport();
        if (List.of(args).contains("--socket")) {
            acpAgentTransport = new WebSocketAcpAgentTransport(9315, "/acp", mapper);
        }
        AcpAgentSupport.create(new AcpAgent())
                .transport(acpAgentTransport)
                .requestTimeout(Duration.ofSeconds(300))
                .build()
                .run();
    }
}
