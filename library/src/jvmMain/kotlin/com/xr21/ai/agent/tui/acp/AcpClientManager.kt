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
package com.xr21.ai.agent.tui.acp

import com.xr21.ai.agent.tui.state.AppState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * ACP 客户端管理器
 *
 * 管理与 Agent 子进程的通信。
 * 通过 Stdio 或 WebSocket 与 Agent 建立 ACP 协议连接。
 *
 * TODO: 1.3 阶段实现完整的 ACP 客户端
 * TODO: 1.4 阶段实现 ACP 握手协议
 */
class AcpClientManager(private val appState: AppState) {

    private var process: Process? = null
    private var isConnected = false

    /** 启动 Agent 子进程并建立连接 */
    suspend fun connect(command: List<String>): Result<Unit> {
        return try {
            appState.connectionState = ConnectionState.CONNECTING

            val pb = ProcessBuilder(command)
                .redirectErrorStream(true)

            process = pb.start()
            isConnected = true
            appState.connectionState = ConnectionState.CONNECTED

            Result.success(Unit)
        } catch (e: Exception) {
            appState.connectionState = ConnectionState.DISCONNECTED_ERROR
            appState.errorMessage = "连接失败: ${e.message}"
            Result.failure(e)
        }
    }

    /** 发送 ACP 消息 */
    suspend fun sendMessage(message: String): Result<Unit> {
        return try {
            val writer = process?.outputStream ?: return Result.failure(Exception("进程未启动"))
            writer.write((message + "\n").toByteArray())
            writer.flush()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 接收 ACP 事件流 */
    fun receiveEvents(): Flow<String> = flow {
        val input = process?.inputStream ?: return@flow
        val reader = input.bufferedReader()
        while (isConnected) {
            val line = reader.readLine() ?: break
            emit(line)
        }
    }

    /** 断开连接 */
    fun disconnect() {
        isConnected = false
        process?.destroy()
        process = null
        appState.connectionState = ConnectionState.DISCONNECTED
    }

    /** 发送中断信号 */
    fun interrupt() {
        // 发送 Ctrl+C 信号到子进程
        process?.let {
            // TODO: 平台相关的信号处理
        }
    }
}