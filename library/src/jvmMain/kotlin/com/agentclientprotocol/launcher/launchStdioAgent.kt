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
@file:JvmName("AcpAgentLauncher")
package com.agentclientprotocol.launcher

import com.agentclientprotocol.agent.Agent
import com.agentclientprotocol.agent.AgentSupport
import com.agentclientprotocol.protocol.Protocol
import com.agentclientprotocol.transport.StdioTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered

@JvmOverloads
fun launchStdioAgent(
    agentSupport: AgentSupport,
    transportName: String = "stdio-agent"
) {
    runBlocking(Dispatchers.IO) {
        val transport = StdioTransport(
            this, Dispatchers.IO,
            System.`in`.asSource().buffered(),
            System.out.asSink().buffered(),
            transportName
        )
        val protocol = Protocol(this, transport)
        Agent(protocol, agentSupport)
        protocol.start()
    }
}