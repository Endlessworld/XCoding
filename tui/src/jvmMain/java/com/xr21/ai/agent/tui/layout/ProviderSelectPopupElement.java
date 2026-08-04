/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 */
package com.xr21.ai.agent.tui.layout;

import com.xr21.ai.agent.tui.AppState;
import com.xr21.ai.agent.tui.ProviderInfo;
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
 * DSL Element 版 Provider 管理弹框
 * 支持列出、添加、编辑、禁用 Provider
 */
@Slf4j
public class ProviderSelectPopupElement {
    private final AppState appState;
    private Runnable onProviderRefresh;
    private java.util.function.BiConsumer<String, String> onProviderSet;
    private java.util.function.Consumer<String> onProviderDisable;
    private java.util.function.Consumer<String> onProviderEnable;
    private java.util.function.Consumer<String> onProviderSwitch;
    private int hoverIndex = -1;

    public ProviderSelectPopupElement(AppState appState) {
        this.appState = appState;
    }

    public ProviderSelectPopupElement onProviderRefresh(Runnable callback) {
        this.onProviderRefresh = callback;
        return this;
    }

    public ProviderSelectPopupElement onProviderSet(java.util.function.BiConsumer<String, String> callback) {
        this.onProviderSet = callback;
        return this;
    }

    public ProviderSelectPopupElement onProviderDisable(java.util.function.Consumer<String> callback) {
        this.onProviderDisable = callback;
        return this;
    }

    public ProviderSelectPopupElement onProviderEnable(java.util.function.Consumer<String> callback) {
        this.onProviderEnable = callback;
        return this;
    }

    public ProviderSelectPopupElement onProviderSwitch(java.util.function.Consumer<String> callback) {
        this.onProviderSwitch = callback;
        return this;
    }

    public Element build() {
        List<Element> items = new ArrayList<>();

        // Provider 列表
        if (appState.providers.isEmpty()) {
            items.add(text("  (暂无 Provider，点击下方按钮添加)"));
        } else {
            for (int i = 0; i < appState.providers.size(); i++) {
                ProviderInfo provider = appState.providers.get(i);
                boolean isSelected = i == appState.providerSelectIndex;
                boolean isHovered = i == hoverIndex;
                String prefix = isSelected ? "▸ " : "  ";
                String statusIcon = provider.enabled ? "●" : "○";
                String label = String.format("%s %s %s [%s] %s",
                        prefix, statusIcon, provider.id, provider.apiType, provider.baseUrl);
                items.add(isSelected ? text(label).bold() : text(label));
            }
        }

        // 操作按钮（根据选中 Provider 状态动态显示禁用/启用）
        items.add(text(""));
        String toggleLabel = getToggleLabel();
        items.add(text(" [F5] 刷新  [E] 编辑URL  " + toggleLabel + "  [A] 添加  [Enter] 确认  [Esc] 关闭")
                .id("provider-actions"));

        return dialog("模型厂商管理",
                column(items.toArray(new Element[0]))
                        .id("provider-select-content")
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
                .id("provider-select-popup")
                .focusable();
    }

    private EventResult handleKeyEvent(KeyEvent event) {
        log.info("ProviderSelectPopupElement handleKeyEvent {}", event);
        int maxIndex = Math.max(0, appState.providers.size() - 1);

        if (event.isUp()) {
            if (appState.providerSelectIndex > 0) appState.providerSelectIndex--;
            return EventResult.HANDLED;
        }
        if (event.isDown()) {
            if (appState.providerSelectIndex < maxIndex) appState.providerSelectIndex++;
            return EventResult.HANDLED;
        }
        if (event.isConfirm() || event.code() == KeyCode.ENTER) {
            confirmSelection();
            return EventResult.HANDLED;
        }
        if (event.isCancel()) {
            appState.closeProviderPopup();
            return EventResult.HANDLED;
        }
        // F5 刷新
        if (event.code() == KeyCode.F5) {
            if (onProviderRefresh != null) {
                onProviderRefresh.run();
            }
            return EventResult.HANDLED;
        }
        // E 编辑 URL
        if (event.isChar('e') || event.isChar('E')) {
            editSelectedProvider();
            return EventResult.HANDLED;
        }
        // D 切换禁用/启用
        if (event.isChar('d') || event.isChar('D')) {
            toggleSelectedProvider();
            return EventResult.HANDLED;
        }
        // A 添加
        if (event.isChar('a') || event.isChar('A')) {
            addNewProvider();
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }

    private void confirmSelection() {
        if (appState.providerSelectIndex >= 0 && appState.providerSelectIndex < appState.providers.size()) {
            ProviderInfo selected = appState.providers.get(appState.providerSelectIndex);
            appState.currentProviderId = selected.id;
            // 触发切换回调，通知 Agent 切换到该 Provider
            if (onProviderSwitch != null) {
                onProviderSwitch.accept(selected.id);
            }
        }
        appState.closeProviderPopup();
    }

    private void editSelectedProvider() {
        if (appState.providerSelectIndex >= 0 && appState.providerSelectIndex < appState.providers.size()) {
            ProviderInfo selected = appState.providers.get(appState.providerSelectIndex);
            log.info("Edit provider URL: {} current URL: {}", selected.id, selected.baseUrl);
            // 简化实现：通过 onProviderSet 回调设置，实际 URL 编辑需要输入框支持
            if (onProviderSet != null) {
                onProviderSet.accept(selected.id, selected.baseUrl);
            }
            // 编辑后刷新列表
            if (onProviderRefresh != null) {
                onProviderRefresh.run();
            }
        }
    }

    private void toggleSelectedProvider() {
        if (appState.providerSelectIndex >= 0 && appState.providerSelectIndex < appState.providers.size()) {
            ProviderInfo selected = appState.providers.get(appState.providerSelectIndex);
            if (selected.enabled) {
                // 禁用：先更新本地状态，再通知 Agent
                selected.setEnabled(false);
                if (onProviderDisable != null) {
                    onProviderDisable.accept(selected.id);
                }
            } else {
                // 启用：先更新本地状态，再通知 Agent
                selected.setEnabled(true);
                if (onProviderEnable != null) {
                    onProviderEnable.accept(selected.id);
                }
            }
        }
    }

    private void addNewProvider() {
        log.info("Add new provider requested");
        // 简化实现：通过 onProviderSet 回调添加新 Provider
        if (onProviderSet != null) {
            onProviderSet.accept("new-provider", "https://api.example.com/v1");
        }
        // 添加后刷新列表
        if (onProviderRefresh != null) {
            onProviderRefresh.run();
        }
    }

    /**
     * 根据当前选中 Provider 的启用状态返回操作标签
     */
    private String getToggleLabel() {
        if (appState.providerSelectIndex >= 0 && appState.providerSelectIndex < appState.providers.size()) {
            ProviderInfo selected = appState.providers.get(appState.providerSelectIndex);
            return selected.enabled ? "[D] 禁用" : "[D] 启用";
        }
        return "[D] 禁用";
    }
}
