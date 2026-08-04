/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 */
package com.xr21.ai.agent.tui.layout;

import com.xr21.ai.agent.tui.AppState;
import com.xr21.ai.agent.tui.ConfigOption;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEventKind;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

import static dev.tamboui.toolkit.Toolkit.*;

/**
 * DSL Element 版配置选项选择弹框
 * 支持 boolean 类型（开/关切换）和 select 类型（选项列表选择）
 */
@Slf4j
public class ConfigSelectPopupElement {
    private final AppState appState;
    private final ConfigOption configOption;
    private int hoverIndex = -1;
    private Runnable onConfigConfirm;

    public ConfigSelectPopupElement(AppState appState, ConfigOption configOption) {
        this.appState = appState;
        this.configOption = configOption;
        // configSelectIndex 已在 AppState.toggleConfigPopup 中初始化
    }

    public ConfigSelectPopupElement onConfigConfirm(Runnable callback) {
        this.onConfigConfirm = callback;
        return this;
    }

    public Element build() {
        List<Element> items = new ArrayList<>();

        if ("boolean".equals(configOption.type)) {
            // boolean 类型：显示 开/关 两个选项
            String[] boolOptions = {"开", "关"};
            for (int i = 0; i < boolOptions.length; i++) {
                int index = i;
                boolean isSelected = i == appState.configSelectIndex;
                boolean isHovered = i == hoverIndex;
                String prefix = isSelected ? "▸ " : "  ";
                String label = prefix + boolOptions[i];
                items.add(isSelected ? text(label).bold() : text(label));
            }
        } else if (configOption.options != null && !configOption.options.isEmpty()) {
            // select 类型：显示所有选项
            for (int i = 0; i < configOption.options.size(); i++) {
                int index = i;
                boolean isSelected = i == appState.configSelectIndex;
                boolean isHovered = i == hoverIndex;
                boolean isCurrent = configOption.options.get(i).equals(configOption.currentValue);
                String prefix = isSelected ? "▸ " : "  ";
                String suffix = isCurrent ? " ✓" : "";
                String label = prefix + configOption.options.get(i) + suffix;
                items.add(isSelected ? text(label).bold() : text(label));
            }
        }

        items.add(text("  ↑↓ 选择  Enter 确认  Esc 关闭"));
        return dialog(configOption.name,
                column(items.toArray(new Element[0]))
                        .id("config-select-content")
                        .focusable()
                        .onKeyEvent(this::handleKeyEvent)
                        .onMouseEvent(event -> {
                            if (event.kind() == MouseEventKind.MOVE) {
                                hoverIndex = -1;
                                return EventResult.HANDLED;
                            }
                            return EventResult.UNHANDLED;
                        })
        )
                .id("config-select-popup")
                .focusable();
    }

    private EventResult handleKeyEvent(KeyEvent event) {
        log.info("ConfigSelectPopupElement handleKeyEvent {}", event);
        int maxIndex;
        if ("boolean".equals(configOption.type)) {
            maxIndex = 1;
        } else {
            maxIndex = (configOption.options != null ? configOption.options.size() : 1) - 1;
        }

        if (event.isUp()) {
            if (appState.configSelectIndex > 0) appState.configSelectIndex--;
            return EventResult.HANDLED;
        }
        if (event.isDown()) {
            if (appState.configSelectIndex < maxIndex) appState.configSelectIndex++;
            return EventResult.HANDLED;
        }
        boolean isEnter = event.isConfirm() || event.code() == KeyCode.ENTER;
        if (isEnter) {
            confirmSelection();
            return EventResult.HANDLED;
        }
        if (event.isCancel()) {
            appState.closeConfigPopup();
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }

    private void confirmSelection() {
        String newValue;
        if ("boolean".equals(configOption.type)) {
            newValue = String.valueOf(appState.configSelectIndex == 0);
        } else if (configOption.options != null && appState.configSelectIndex < configOption.options.size()) {
            newValue = configOption.options.get(appState.configSelectIndex);
        } else {
            newValue = configOption.currentValue;
        }
        appState.setConfigOptionValue(configOption.id, newValue);
        appState.closeConfigPopup();
        if (onConfigConfirm != null) {
            onConfigConfirm.run();
        }
    }
}
