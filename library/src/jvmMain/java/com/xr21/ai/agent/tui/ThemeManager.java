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
package com.xr21.ai.agent.tui;

import dev.tamboui.css.engine.StyleEngine;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Consumer;

/**
 * 主题管理器 - 基于 TamboUI StyleEngine 的命名样式表机制。
 *
 * <p>职责：
 * <ol>
 *   <li>封装 StyleEngine，加载 dark/light 两套 TCSS 主题</li>
 *   <li>TUI 启动时自动检测终端背景色，决定初始主题</li>
 *   <li>提供手动切换（toggle / setDarkMode）</li>
 *   <li>提供 {@link #currentTheme()} 获取当前 {@link TuiTheme} 对象（兼容现有 Widget）</li>
 *   <li>通过 {@link #addChangeListener} 通知主题变更</li>
 * </ol>
 */
public final class ThemeManager {

    private static final String THEME_DARK = "dark";
    private static final String THEME_LIGHT = "light";

    private final StyleEngine styleEngine;
    private volatile TuiTheme currentTheme;
    private Consumer<Boolean> changeListener;

    public ThemeManager() {
        this.styleEngine = StyleEngine.create();
        loadThemes();
        // 启动时自动检测
        Boolean detected = OsThemeDetector.isDarkMode();
        boolean isDark = (detected != null) ? detected : true;
        activateTheme(isDark);
    }

    // ==================== 主题加载 ====================

    private void loadThemes() {
        try {
            styleEngine.loadStylesheet(THEME_DARK, "/themes/tui-dark.tcss");
            styleEngine.loadStylesheet(THEME_LIGHT, "/themes/tui-light.tcss");
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load TCSS theme files", e);
        }
    }

    // ==================== 主题切换 ====================

    /**
     * 切换 dark/light 主题。
     */
    public void toggle() {
        boolean isDark = !isDarkMode();
        activateTheme(isDark);
    }

    private void activateTheme(boolean isDark) {
        String themeName = isDark ? THEME_DARK : THEME_LIGHT;
        styleEngine.setActiveStylesheet(themeName);
        this.currentTheme = isDark ? TuiTheme.modernDark() : TuiTheme.modernLight();
        if (changeListener != null) {
            changeListener.accept(isDark);
        }
    }

    /**
     * 当前是否为暗色模式。
     */
    public boolean isDarkMode() {
        return THEME_DARK.equals(styleEngine.getActiveStylesheet().orElse(THEME_DARK));
    }

    // ==================== 查询 ====================

    /**
     * 设置是否为暗色模式。
     */
    public void setDarkMode(boolean isDark) {
        if (isDark == isDarkMode()) return;
        activateTheme(isDark);
    }

    /**
     * 获取当前主题名称 ("dark" / "light")。
     */
    public String activeThemeName() {
        return styleEngine.getActiveStylesheet().orElse(THEME_DARK);
    }

    /**
     * 获取底层 StyleEngine（供高级用途）。
     */
    public StyleEngine styleEngine() {
        return styleEngine;
    }

    /**
     * 获取当前 {@link TuiTheme} 对象（兼容现有 Widget 渲染）。
     */
    public TuiTheme currentTheme() {
        return currentTheme;
    }

    // ==================== 监听器 ====================

    /**
     * 注册主题变更监听器。
     *
     * @param listener 接收 boolean: true=dark, false=light
     */
    public void addChangeListener(Consumer<Boolean> listener) {
        this.changeListener = listener;
        // 立即通知当前状态
        listener.accept(isDarkMode());
    }
}
