/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 */
package com.xr21.ai.agent.tui;

/**
 * ACP 模型信息的简化 Java 表示
 */
public class ModelInfo {
    public final String id;
    public final String name;
    /** 厂商分组名（可能为空） */
    public final String group;

    public ModelInfo(String id, String name) {
        this(id, name, "");
    }

    public ModelInfo(String id, String name, String group) {
        this.id = id;
        this.name = name;
        this.group = group != null ? group : "";
    }
}
