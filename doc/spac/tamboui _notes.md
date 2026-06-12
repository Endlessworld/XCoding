# TUI框架 tamboui 使用经验笔记

> 从（E:\local-github\tamboui）源码学习

## DialogElement vs PanelElement 事件处理差异

### DialogElement

- **背景处理**：自动清除背景，不透明
- **事件处理**：`handleKeyEvent()` 会消费所有事件（无条件返回 `EventResult.HANDLED`）
- **问题**：DialogElement 的 `handleKeyEvent()` 不会调用通过 `.onKeyEvent()` 注册的 keyHandler
- **机制**：
    1. 先调用 `super.handleKeyEvent()` 转发给 children
    2. 如果 children 返回 `HANDLED`，就返回 `HANDLED`
    3. 否则处理 `ESCAPE` 和 `ENTER` 键
    4. 最后无条件返回 `HANDLED`（模态消费所有事件）

### PanelElement

- **背景处理**：即使设置了 `.bg(Color.BLACK)`，背景色仍然透明
- **事件处理**：可以正确接收和处理事件
- **问题**：无法实现不透明背景，导致与下层文本重叠

## 弹框事件处理解决方案

### 最终方案

使用 `DialogElement`，将事件处理器放在 Dialog 的子元素（如 `column`）上：

```java
return dialog("标题",
              column(items)
                .

focusable()
                .

onKeyEvent(this::handleKeyEvent)
)
        .

id("popup-id")
        .

focusable();
```

### 关键点

1. **Dialog 不透明**：DialogElement 自动清除背景，不需要额外设置
2. **子元素处理事件**：将 `.onKeyEvent()` 和 `.onMouseEvent()` 注册在子元素（column）上
3. **事件转发机制**：Dialog 的 `handleKeyEvent()` 会先转发事件给 children，如果 children 返回 `HANDLED`，Dialog 就不会消费该事件
4. **聚焦设置**：Dialog 和子元素都需要设置 `.focusable()`

### 失败的方案

1. **Dialog + Dialog 级别的 onKeyEvent**：Dialog 的 handleKeyEvent 不会调用 Dialog 级别的 onKeyEvent
2. **Panel + 背景色**：Panel 的 `.bg()` 设置无效，仍然透明
3. **Panel + Stack 背景遮罩**：尝试在 stack 中添加 `text("").bg(Color.BLACK).fill()` 等作为背景遮罩，但仍然透明
4. **全局处理器处理弹框事件**：虽然可行，但不够优雅，违背了 Tamboui 的事件路由设计

## 最佳实践

1. **使用 DialogElement 实现弹框**：因为它自动处理不透明背景
2. **事件处理器注册在子元素**：避免 Dialog 消费所有事件
3. **遵循 Tamboui 的事件路由机制**：让元素自己处理事件，而不是在全局处理器中特殊处理
4. **聚焦管理**：确保 Dialog 和子元素都设置了 `.focusable()`


DialogElement 的 handleKeyEvent() 会无条件消费所有事件（最后一步返回 HANDLED）。虽然它会先转发给 children，但：
1.
当焦点在 Dialog 上时，EventRouter 调用 dialog.handleKeyEvent(event, true)
2.
Dialog 的 handleKeyEvent() 转发给 children（column）
3.
Column 的 handler 处理 ↑/↓ → 返回 HANDLED
4.
Dialog 返回 HANDLED
这个流程理论上应该能工作，但实际中 DialogElement 的实现可能没有正确转发 ↑/↓ 给 children（文档明确指出 Dialog 只处理 ESCAPE 和 ENTER，其他键可能被直接消费）。
修复方案：将焦点从 Dialog 元素转移到其内部的 column（内容元素）上，这样 EventRouter 会直接调用 column 的 handleKeyEvent()，完全绕过 Dialog 的事件消费逻辑