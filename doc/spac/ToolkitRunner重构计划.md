# ToolkitRunner DSL 重构计划

## 目标
使用「方式二：ToolkitRunner.create() + Lambda」完全重构当前 TUI 实现，
替代低层 EventHandler/Renderer 接口和 Widget 渲染方式。

## 当前架构问题

1. `TuiApp` 实现低层 `EventHandler` + `Renderer` 接口
2. Widget 使用命令式 `render(Rect, Buffer)` 模式
3. 事件处理在 `handleKeyEvent`/`handleMouseEvent` 中手动路由
4. 焦点管理手动维护（`focusPanel`、`setFocused`）
5. 弹框手动计算位置和渲染
6. 依赖 `TfxIntegration` 动画效果（可移除）

## 目标架构

1. `TuiApp` 使用 `ToolkitRunner.create()` + Lambda 启动
2. 界面使用 DSL Element 树声明式构建
3. 事件处理通过 Element 的 `onKeyEvent`/`onMouseEvent` lambda 注册
4. 焦点管理由框架自动处理
5. 弹框使用 `dialog()` DSL 元素
6. 移除 `TfxIntegration` 依赖

## 执行步骤

### 步骤 1：创建 DSL 元素版 Widget（Element 版）
- [ ] `ChatPanelElement` — 替代 `ChatPanelWidget`
- [ ] `InfoPanelElement` — 替代 `InfoPanelWidget`
- [ ] `InputPanelElement` — 替代 `InputPanelWidget`
- [ ] `StatusBarElement` — 替代 `StatusBarWidget`
- [ ] `SessionListPopupElement` — 替代 `SessionListPopupWidget`
- [ ] `HelpPopupElement` — 替代 `HelpPopupWidget`
- [ ] `ModelSelectPopupElement` — 替代 `ModelSelectPopupWidget`

### 步骤 2：重构 TuiApp
- [ ] 移除 `EventHandler`/`Renderer` 接口实现
- [ ] 移除 `TfxIntegration` 依赖
- [ ] 使用 `ToolkitRunner.create()` + Lambda 启动
- [ ] 在 `render()` lambda 中构建 DSL 元素树
- [ ] 事件处理通过 Element lambda 注册

### 步骤 3：清理
- [ ] 删除旧的 Widget 类
- [ ] 移除不再需要的 import
- [ ] 验证编译通过

## 状态跟踪
- [ ] 步骤 1 完成
- [ ] 步骤 2 完成
- [ ] 步骤 3 完成
