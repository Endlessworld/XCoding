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

import com.xr21.ai.agent.tools.ShellTools;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

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
    public final String sessionId;
    final Thread executionThread;
    final Flux<?> flux;
    final List<String> activeToolCallIds;
    final long startTime;
    private final Map<String, ShellTools.BackgroundProcess> activeShellProcesses;
    private final Object lock = new Object();
    public volatile boolean cancelled;
    private Disposable fluxDisposable;

    public CancellableRequest(String requestId, String sessionId, Thread executionThread, Flux<?> flux) {
        this.requestId = requestId;
        this.sessionId = sessionId;
        this.executionThread = executionThread;
        this.flux = flux;
        this.activeToolCallIds = new ArrayList<>();
        this.activeShellProcesses = new ConcurrentHashMap<>();
        this.startTime = System.currentTimeMillis();
        this.cancelled = false;
        this.fluxDisposable = null;
    }

    public void setFluxDisposable(Disposable disposable) {
        this.fluxDisposable = disposable;
    }

    public void addShellProcess(String shellId, ShellTools.BackgroundProcess process) {
        activeShellProcesses.put(shellId, process);
    }

    public void removeShellProcess(String shellId) {
        activeShellProcesses.remove(shellId);
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

            // 2. 取消 Flux 订阅
            if (fluxDisposable != null && !fluxDisposable.isDisposed()) {
                log.info("[CancellableRequest] Disposing Flux subscription for request: {}", requestId);
                fluxDisposable.dispose();
            }

            // 3. 中断执行线程
            if (executionThread != null && executionThread.isAlive()) {
                log.info("[CancellableRequest] Interrupting execution thread for request: {}", requestId);
                executionThread.interrupt();
            }

            // 4. 清理资源
            activeToolCallIds.clear();
            activeShellProcesses.clear();

            log.info("[CancellableRequest] Request {} cancelled successfully", requestId);
        }
    }

    private void cancelActiveToolCalls() {
        // 取消所有活跃的 shell 进程
        for (Map.Entry<String, ShellTools.BackgroundProcess> entry : activeShellProcesses.entrySet()) {
            String shellId = entry.getKey();
            ShellTools.BackgroundProcess process = entry.getValue();
            try {
                log.info("[CancellableRequest] Killing shell process: {} for request: {}", shellId, requestId);
                ShellTools.builder().build().killShell(shellId);
            } catch (Exception e) {
                log.warn("[CancellableRequest] Failed to kill shell process {}: {}", shellId, e.getMessage());
            }
        }
        activeShellProcesses.clear();
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