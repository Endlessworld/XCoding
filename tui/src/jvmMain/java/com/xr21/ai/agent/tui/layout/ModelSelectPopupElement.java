/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 */
package com.xr21.ai.agent.tui.layout;

import com.xr21.ai.agent.tui.AppState;
import com.xr21.ai.agent.tui.ModelInfo;
import dev.tamboui.style.Color;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.TextElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

import static dev.tamboui.toolkit.Toolkit.*;

/**
 * DSL Element 版模型选择弹框
 * 内部管理选中索引、上下导航、确认选择等逻辑
 */
@Slf4j
public class ModelSelectPopupElement {
    private final AppState appState;
    private Runnable onModelConfirm;
    private int hoverIndex = -1; // 鼠标悬停的模型索引

    public ModelSelectPopupElement(AppState appState) {
        this.appState = appState;
    }

    public ModelSelectPopupElement onModelConfirm(Runnable callback) {
        this.onModelConfirm = callback;
        return this;
    }

    public Element build() {
        List<Element> items = new ArrayList<>();
        for (int i = 0; i < appState.availableModels.size(); i++) {
            int index = i; // 用于 lambda 捕获
            ModelInfo model = appState.availableModels.get(i);
            boolean isSelected = i == appState.modelSelectIndex;
            boolean isHovered = i == hoverIndex;
            boolean isCurrent = model.id.equals(appState.currentModelId);
            String prefix = isSelected ? "▸ " : "  ";
            String suffix = isCurrent ? " ✓" : "";
            String label = prefix + (model.name.isEmpty() ? model.id : model.name) + suffix;
            items.add(isSelected ? text(label).bold() : text(label));
        }
        items.add(text("  ↑↓ 选择  Enter 确认  Esc 关闭"));
        return dialog("选择模型",
                column(items.toArray(new Element[0]))
                        .id("model-select-content")
                        .focusable()
                        .onKeyEvent(this::handleKeyEvent)
                        .onMouseEvent(event -> {
                            // 鼠标移出模型列表时清除悬停状态
                            if (event.kind() == MouseEventKind.MOVE) {
                                hoverIndex = -1;
                                return EventResult.HANDLED; // 返回 HANDLED 触发重绘
                            }
                            return EventResult.UNHANDLED;
                        })
        )
                .id("model-select-popup")
                .focusable();
    }

    private EventResult handleKeyEvent(KeyEvent event) {
        log.info("ModelSelectPopupElement handleKeyEvent {}", event);
        if (event.isUp()) {
            if (appState.modelSelectIndex > 0) appState.modelSelectIndex--;
            return EventResult.HANDLED;
        }
        if (event.isDown()) {
            if (appState.modelSelectIndex < appState.availableModels.size() - 1) appState.modelSelectIndex++;
            return EventResult.HANDLED;
        }
        boolean isEnter = event.isConfirm() || event.code() == KeyCode.ENTER;
        if (isEnter) {
            confirmSelection();
            return EventResult.HANDLED;
        }
        if (event.isCancel()) {
            appState.closeModelPopup();
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }

    private void confirmSelection() {
        if (appState.modelSelectIndex >= 0 && appState.modelSelectIndex < appState.availableModels.size()) {
            ModelInfo selected = appState.availableModels.get(appState.modelSelectIndex);
            appState.currentModelId = selected.id;
            appState.modelName = selected.name.isEmpty() ? selected.id : selected.name;
        }
        appState.closeModelPopup();
        if (onModelConfirm != null) {
            onModelConfirm.run();
        }
    }
}
