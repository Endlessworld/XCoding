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

import kotlinx.coroutines.channels.Channel;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;

/**
 * 可取消的请求信息。
 *
 * <p>在新架构中，prompt() 通过 {@code SinksUtil.fluxToChannel} 将 Flux 直接消费为
 * {@link Channel}，CancellableRequest 持有该 Channel 和 Flux 的 {@link Disposable}，
 * cancel() 时先关闭 Channel（终止 {@code receiveCatching()} 阻塞），再 dispose Flux 订阅，
 * 最后中断执行线程作为兜底。</p>
 *
 * @author Endless
 */
@Slf4j
public class CancellableRequest {
    public final String requestId;
    public final String sessionId;
    private final Thread executionThread;
    private final Disposable fluxDisposable;
    private final Channel<?> channel;
    private final Object lock = new Object();
    public volatile boolean cancelled;

    public CancellableRequest(String requestId, String sessionId, Thread executionThread,
                              Disposable fluxDisposable, Channel<?> channel) {
        this.requestId = requestId;
        this.sessionId = sessionId;
        this.executionThread = executionThread;
        this.fluxDisposable = fluxDisposable;
        this.channel = channel;
        this.cancelled = false;
    }

    /**
     * 取消请求：关闭 Channel → dispose Flux 订阅 → 中断执行线程。
     * 线程安全，可重复调用。
     */
    public void cancel() {
        synchronized (lock) {
            if (cancelled) {
                return;
            }

            this.cancelled = true;
            log.info("[CancellableRequest] Cancelling request: {}", requestId);

            // 1. 关闭 Channel，终止 prompt() 中的 receiveCatching() 阻塞
            log.info("[CancellableRequest] Closing channel for request: {}", requestId);
            channel.close(null);

            // 2. 取消 Flux 订阅，停止上游数据发射
            if (!fluxDisposable.isDisposed()) {
                log.info("[CancellableRequest] Disposing Flux subscription for request: {}", requestId);
                fluxDisposable.dispose();
            }

            // 3. 中断执行线程（兜底）
            if (executionThread != null && executionThread.isAlive()) {
                log.info("[CancellableRequest] Interrupting execution thread for request: {}", requestId);
                executionThread.interrupt();
            }

            log.info("[CancellableRequest] Request {} cancelled successfully", requestId);
        }
    }
}
