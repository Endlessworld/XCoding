/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 */
package com.xr21.ai.agent.tui;

import java.util.List;

/**
 * ACP 配置选项的简化 Java 表示
 */
public class ConfigOption {
    public final String id;
    public final String name;
    public final String type; // "boolean" or "select"
    public final String currentValue;
    public final List<String> options; // for select type

    public ConfigOption(String id, String name, String type, String currentValue, List<String> options) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.currentValue = currentValue;
        this.options = options;
    }
}
