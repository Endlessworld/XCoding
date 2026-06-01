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
package com.xr21.ai.agent.entity;

import com.agentclientprotocol.model.SessionId;
import com.xr21.ai.agent.tools.ShellTools;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 可取消的请求信息
 *
 * @author Endless
 */
@Slf4j
public class CancellableRequest {
    public final String requestId;
    public final SessionId sessionId;
    final Thread executionThread;
    final Flux<?> flux;
    final List<String> activeToolCallIds;
    final long startTime;
    private final Map<String, ShellTools.ShellSession> activeShellSessions;
    private final Object lock = new Object();
    public volatile boolean cancelled;
    private Disposable fluxDisposable;
    private Sinks.Many<?> agentSink;
    private final List<Sinks.Many<?>> recursiveSinks = new ArrayList<>();

    public CancellableRequest(String requestId, SessionId sessionId, Thread executionThread, Flux<?> flux) {
        this.requestId = requestId;
        this.sessionId = sessionId;
        this.executionThread = executionThread;
        this.flux = flux;
        this.activeToolCallIds = new ArrayList<>();
        this.activeShellSessions = new ConcurrentHashMap<>();
        this.startTime = System.currentTimeMillis();
        this.cancelled = false;
        this.fluxDisposable = null;
    }

    public void setFluxDisposable(Disposable disposable) {
        this.fluxDisposable = disposable;
    }

    @SuppressWarnings("rawtypes")
    public void setAgentSink(Sinks.Many sink) {
        synchronized (lock) {
            this.agentSink = sink;
            log.info("[CancellableRequest] AgentSink registered for request: {}", requestId);
        }
    }

    /**
     * 注册一个递归 Sink（来自 expand() 内部创建的 Flux）。
     * 取消时会向所有注册的递归 Sink 发送 complete 信号，
     * 以终止 expand() 链中正在等待的递归 Flux。
     */
    @SuppressWarnings("rawtypes")
    public void addRecursiveSink(Sinks.Many sink) {
        synchronized (lock) {
            if (!cancelled) {
                recursiveSinks.add(sink);
                log.info("[CancellableRequest] Recursive sink registered for request: {}, total: {}", requestId, recursiveSinks.size());
            } else {
                // 请求已经取消，立即终止这个新创建的 Sink
                log.info("[CancellableRequest] Request already cancelled, emitting complete to new recursive sink for request: {}", requestId);
                sink.tryEmitComplete();
            }
        }
    }

    public void addShellSession(String shellId, ShellTools.ShellSession session) {
        activeShellSessions.put(shellId, session);
    }

    public void removeShellSession(String shellId) {
        activeShellSessions.remove(shellId);
    }

    public void cancel() {
        synchronized (lock) {
            if (cancelled) {
                return; // 已经取消过
            }

            this.cancelled = true;
            log.info("[CancellableRequest] Cancelling request: {}", requestId);

            // 1. 取消所有活跃的工具调用（特别是 shell 进程）
            cancelActiveToolCalls();

            // 2. 发送 complete 信号给所有递归 Sink，终止 expand() 链中的递归 Flux
            for (Sinks.Many<?> recursiveSink : recursiveSinks) {
                log.info("[CancellableRequest] Emitting complete signal to recursive sink for request: {}", requestId);
                recursiveSink.tryEmitComplete();
            }
            recursiveSinks.clear();

            // 3. 发送 complete 信号给 agentSink（如果存在）
            if (agentSink != null) {
                log.info("[CancellableRequest] Emitting complete signal to agentSink for request: {}", requestId);
                agentSink.tryEmitComplete();
            }

            // 4. 取消 Flux 订阅
            if (fluxDisposable != null && !fluxDisposable.isDisposed()) {
                log.info("[CancellableRequest] Disposing Flux subscription for request: {}", requestId);
                fluxDisposable.dispose();
            }

            // 5. 中断执行线程
            if (executionThread != null && executionThread.isAlive()) {
                log.info("[CancellableRequest] Interrupting execution thread for request: {}", requestId);
                executionThread.interrupt();
            }

            // 6. 清理资源
            activeToolCallIds.clear();
            activeShellSessions.clear();
            recursiveSinks.clear();
            if (agentSink != null) {
                agentSink = null;
            }

            log.info("[CancellableRequest] Request {} cancelled successfully", requestId);
        }
    }

    private void cancelActiveToolCalls() {
        // 取消所有活跃的 shell 会话
        for (Map.Entry<String, ShellTools.ShellSession> entry : activeShellSessions.entrySet()) {
            String shellId = entry.getKey();
            ShellTools.ShellSession session = entry.getValue();
            try {
                log.info("[CancellableRequest] Killing shell session: {} for request: {}", shellId, requestId);
                ShellTools.builder().build().killShell(shellId);
            } catch (Exception e) {
                log.warn("[CancellableRequest] Failed to kill shell session {}: {}", shellId, e.getMessage());
            }
        }
        activeShellSessions.clear();
    }

    public void addToolCall(String toolCallId) {
        synchronized (activeToolCallIds) {
            activeToolCallIds.add(toolCallId);
        }
    }

    public void removeToolCall(String toolCallId) {
        synchronized (activeToolCallIds) {
            activeToolCallIds.remove(toolCallId);
        }
    }

    List<String> getActiveToolCalls() {
        synchronized (activeToolCallIds) {
            return new ArrayList<>(activeToolCallIds);
        }
    }
}