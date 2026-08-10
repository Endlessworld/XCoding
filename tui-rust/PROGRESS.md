# XAgent TUI (Rust) — 进度跟踪

> 配合 [doc/TUI_RUST_PLAN.md](../doc/TUI_RUST_PLAN.md) 使用。
> 规则：每完成/推进一个任务，立即在本文档登记状态与日期。

## 进度总览

| 阶段 | 内容 | 状态 | 最近更新 |
|------|------|------|---------|
| 一 | 骨架 + ACP 最小闭环（MVP） | 🟢 完成（可编译、零警告） | 2025-07-17 |
| 二 | 交互完善（工具卡片/多会话/Markdown） | 🔵 进行中 | 2025-07-17 |
| 三 | WebSocket 远程/持久化/重连（可选） | ⚪ 待开始 | - |

## 阶段一（MVP）详细进度

### 骨架与编译
- [x] Cargo 工程：`tui-rust` crate + 依赖 + 可编译
- [x] 修复 build_err.txt 中 17 个编译错误（SDK API 对齐）
- [x] 零警告构建（main.rs 加 `#![allow(dead_code)]` 承接阶段二占位）

### ACP 客户端（src/acp/）
- [x] client.rs：WebSocket 连接（HttpClient with_endpoint）+ 握手 + 建会话 + 事件循环
- [x] 事件分发：AgentMessageChunk / AgentThoughtChunk / ToolCallUpdate / Plan / UsageUpdate / CurrentModeUpdate / ConfigOptionUpdate
- [x] session.modes() 正确区分当前模式与可用模式（新增 AcpEvent::AvailableModes）

### 状态与 UI
- [x] AppState 完整结构 + 事件动作方法（send_message / append_streaming / todo / token）
- [x] 三面板布局：ChatPanel / InfoPanel / InputPanel + StatusBar
- [x] 输入发送：Enter 发送 + 流式显示

### 事件循环
- [x] run_loop 用 tokio::select! 聚合键盘/ACP/Tick；退出时发送 AcpRequest::Shutdown

## 阶段二（交互完善）详细进度

### 进行中
- [x] 工具调用卡片（ToolCallWidget + 状态动态更新 + 折叠）——新增 src/ui/tool_call.rs，chat.rs 接入
- [x] 模型/模式切换调 SDK——基于协议 v1 `SetSessionConfigOptionRequest("model"/"mode")` 打通：弹窗选中项实时下发 `AcpRequest::SetModel/SetMode`，后端回推 ConfigOptionUpdate 刷新状态（联调验证：model 4 项 / mode plan-accept_edits-yolo 3 项）
- [x] Markdown 流式渲染（pulldown-cmark 或自研）——自研轻量解析器 src/ui/markdown.rs，chat.rs 对 Assistant 启用
- [x] 输入历史 / 滚动 / Todo/Token 实时更新——输入历史草稿保留（↑↓ 导航不丢草稿）、Alt+Enter 多行换行、滚动底部跟随修复（BOTTOM 映射到最新内容而非顶部）
- [x] Windows 终端检测——main.rs 启动前用 `crossterm::ansi_support::supports_ansi()` 检测，经典 conhost 等不支持 ANSI 的终端直接提示退出，避免 ratatui 界面乱码

## 遇到的问题

| 日期 | 问题 | 解决方案 | 状态 |
|------|------|---------|------|
| 2025-07-17 | SDK v1 ActiveSession 无 set_model/set_mode API | 阶段二切换仅改本地状态，待确认后端 ACP 协议 | ✅ 已解决 |
| 2025-07-17 | v2 ToolCallUpdate 字段为 MaybeUndefined<T>，.into_option() 误解析为 IntoOption 显式 impl | 改用 `take()`/`value()` 取 Option | ✅ 已解决 |
| 2025-07-17 | session.modes() 的 current_mode 与可用模式互相污染 | 拆分 AcpEvent::CurrentMode + AvailableModes | ✅ 已解决 |
| 2025-07-17 | 滚动 BOTTOM 误映射到 offset=0，内容超一屏后看不到最新回复 | BOTTOM 改为映射到 total-max_rows（跟随最新） | ✅ 已解决 |
| 2025-07-17 | 占位提示“Alt+Enter 换行”未实现，Enter 一律发送 | Enter 分支检测 ALT 修饰符插入换行 | ✅ 已解决 |
| 2025-07-17 | 输入历史 ↑ 导航直接丢弃当前草稿 | InputState 增加 draft 字段，进入历史导航前保存、返回时恢复 | ✅ 已解决 |
| 2025-07-17 | **端到端联调：后端 jar 协商 ACP 协议 v1，client.rs 用 `Client.v2()` 握手报 `peer negotiated 1`** | client.rs 改写为 v1：`Client.builder()` + `on_receive_notification(SessionNotification)` + `NewSessionRequest` + `SetSessionConfigOptionRequest("model"/"mode")`；事件字段用 v1（`opt.id` / `fields.status` / `used`/`size`） | ✅ 已解决 |
| 2025-07-17 | 后端 model/mode 配置仅对 IntelliJ 2026 客户端暴露，普通客户端只返回 thought_level | 初始化 `client_info(Implementation::new("IntelliJ IDEA", "2026.1"))`，暴露 model(4)/mode(3) 配置 | ✅ 已解决 |
| 2025-07-17 | Todo 列表无后端数据源：`AcpEvent::Plan` 已定义且 app.rs 已处理，但 `SessionUpdate::PlanUpdate` 受 SDK `unstable_plan_operations` feature 门控，Cargo.toml 未启用，client.rs `handle_update` 落入 `_ => {}` 分支未转发 | 需在 Cargo.toml 启用 `unstable_plan_operations` feature 并在 client.rs 增加 PlanUpdate 分支；待确认后端是否发送 plan_update 后再接入 | ⏳ 待定

