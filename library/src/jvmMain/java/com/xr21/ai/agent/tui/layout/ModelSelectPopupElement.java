/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 */
package com.xr21.ai.agent.tui.layout;

import com.xr21.ai.agent.tui.AppState;
import com.xr21.ai.agent.tui.ModelInfo;
import com.xr21.ai.agent.tui.TuiTheme;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyEvent;

import java.util.ArrayList;
import java.util.List;

import static dev.tamboui.toolkit.Toolkit.*;

/**
 * DSL Element 版模型选择弹框
 */
public class ModelSelectPopupElement {
    private final AppState appState;
    private final TuiTheme theme;
    private Runnable onModelConfirm;

    public ModelSelectPopupElement(AppState appState, TuiTheme theme) {
        this.appState = appState;
        this.theme = theme;
    }

    public ModelSelectPopupElement onModelConfirm(Runnable callback) {
        this.onModelConfirm = callback;
        return this;
    }

    public Element build() {
        List<Element> items = new ArrayList<>();
        for (int i = 0; i < appState.availableModels.size(); i++) {
            ModelInfo model = appState.availableModels.get(i);
            boolean isSelected = i == appState.modelSelectIndex;
            boolean isCurrent = model.id.equals(appState.currentModelId);
            String prefix = isSelected ? "▸ " : "  ";
            String suffix = isCurrent ? " ✓" : "";
            items.add(text(prefix + (model.name.isEmpty() ? model.id : model.name) + suffix));
        }
        items.add(text("  ↑↓ 选择  Enter 确认  Esc 关闭"));

        return dialog("选择模型",
                column(items.toArray(new Element[0]))
        )
                .id("model-select-popup")
                .onKeyEvent(this::handleKeyEvent);
    }

    private EventResult handleKeyEvent(KeyEvent event) {
        if (event.isUp()) {
            appState.modelSelectUp();
            return EventResult.HANDLED;
        }
        if (event.isDown()) {
            appState.modelSelectDown();
            return EventResult.HANDLED;
        }
        if (event.isConfirm()) {
            String modelId = appState.confirmModelSelection();
            if (modelId != null && onModelConfirm != null) {
                onModelConfirm.run();
            }
            return EventResult.HANDLED;
        }
        if (event.isCancel()) {
            appState.closeModelPopup();
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }
}
