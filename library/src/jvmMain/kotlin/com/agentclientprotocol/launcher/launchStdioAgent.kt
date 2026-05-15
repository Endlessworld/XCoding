// AcpAgentLauncher.kt
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