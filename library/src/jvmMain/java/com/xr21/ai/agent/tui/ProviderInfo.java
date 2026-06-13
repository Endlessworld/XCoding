/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 */
package com.xr21.ai.agent.tui;

/**
 * ACP Provider 信息的简化 Java 表示
 */
public class ProviderInfo {
    public final String id;
    public final String baseUrl;
    public final String apiType;
    public volatile boolean enabled;

    public ProviderInfo(String id, String baseUrl, String apiType, boolean enabled) {
        this.id = id;
        this.baseUrl = baseUrl;
        this.apiType = apiType;
        this.enabled = enabled;
    }

    /**
     * 切换启用/禁用状态
     */
    public void toggleEnabled() {
        this.enabled = !this.enabled;
    }

    /**
     * 设置启用状态
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
