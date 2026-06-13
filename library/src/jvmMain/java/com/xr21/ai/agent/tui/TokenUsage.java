/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 */
package com.xr21.ai.agent.tui;

/**
 * Token 用量
 *
 * <p>对应 SDK 端 {@code com.agentclientprotocol.model.Usage} 与
 * {@code com.agentclientprotocol.model.SessionUpdate.UsageUpdate}：
 * <ul>
 *   <li>{@code inputTokens} → {@link #promptTokens}</li>
 *   <li>{@code outputTokens} → {@link #completionTokens}</li>
 *   <li>{@code totalTokens} → {@link #totalTokens}</li>
 *   <li>{@code thoughtTokens} → {@link #thoughtTokens}</li>
 *   <li>{@code cachedReadTokens} → {@link #cachedReadTokens}</li>
 *   <li>{@code cachedWriteTokens} → {@link #cachedWriteTokens}</li>
 *   <li>{@code UsageUpdate.size}（上下文窗口）→ {@link #contextWindowSize}</li>
 *   <li>{@code UsageUpdate.cost.amount}（Unstable）→ {@link #costUsd}</li>
 *   <li>{@code UsageUpdate.cost.currency}（Unstable）→ {@link #costCurrency}</li>
 * </ul>
 */
public class TokenUsage {
    public long promptTokens = 0;
    public long completionTokens = 0;
    public long totalTokens = 0;
    public long thoughtTokens = 0;
    public long cachedReadTokens = 0;
    public long cachedWriteTokens = 0;
    public long contextWindowSize = 0;
    public double costUsd = 0.0;
    public String costCurrency = "";
    /** 会话总 Token（来自 _meta.sessionTotal） */
    public long sessionTotal = 0;
    /** 耗时（秒，来自 _meta.duration） */
    public double duration = 0.0;
    /** 速度（tokens/s，来自 _meta.speed） */
    public String speed = "";
}
