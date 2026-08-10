# XAgent TUI (Rust) — 多会话交接文档

> 供后续会话快速恢复上下文。每次结束任务前更新「当前状态」与「下一步」。

## 构建与运行

```bash
cd D:\IdeaProjects\agi_working\tui-rust
cargo build          # 验证编译（当前应零警告）
cargo run -- --ws-url ws://127.0.0.1:8080/acp
cargo run -- --cwd <path>
```

## 代码结构

```
tui-rust/src/
├── main.rs            # CLI 解析 + tokio runtime + 启动 UI
├── app.rs             # 事件循环、handle_key/handle_acp、run_loop
├── state.rs           # AppState + InputState/ScrollState/PopupState
├── model.rs           # ChatMessage/Session/TodoItem/TokenUsage/枚举
├── theme.rs           # modernDark 配色
├── acp/
│   ├── client.rs      # WebSocket 连接 + 事件循环（AcpClient）
│   └── event.rs       # AcpEvent/TuiEvent 定义
└── ui/
    ├── chat.rs / info.rs / input.rs / status_bar.rs / session_popup.rs
    ├── markdown.rs       # Markdown 轻量渲染器（自研）
    └── tool_call.rs      # ToolCallWidget 工具调用卡片
```

## 当前状态

- **编译**：`cargo build` 通过，零警告
- **阶段**：阶段一 MVP 完成；阶段二进行中
- **已交付**：ToolCallWidget 工具调用卡片（src/ui/tool_call.rs）；Markdown 轻量渲染器（src/ui/markdown.rs，自研，chat.rs 对 Assistant 启用）；模型/模式切换已打通（**协议 v1** `SetSessionConfigOptionRequest("model"/"mode")`，弹窗选择实时下发 `AcpRequest::SetModel/SetMode`，后端 ConfigOptionUpdate 回推刷新）；输入/滚动完善（InputState.draft 历史草稿保留、Alt+Enter 多行换行、chat.rs 滚动 BOTTOM 跟随最新内容修复）；Windows 终端检测（main.rs 启动前 `crossterm::ansi_support::supports_ansi()`，不支持 ANSI 直接提示退出）；**端到端联调已验证**（启动后端 jar，握手→建会话→切换 model/mode→流式回复全链路打通）
- **已知缺口**：Todo 列表无后端数据源——`AcpEvent::Plan` 已定义并在 app.rs 处理，但 `PlanUpdate` 受 SDK `unstable_plan_operations` feature 门控（Cargo.toml 未启用），client.rs 未转发；需启用该 feature 并确认后端发送 plan_update 后再接入
- **SDK 依赖**：本地 path 引用 `D:/IdeaProjects/rust-sdk`

## 下一步建议

1. ~~工具调用卡片（ToolCallWidget 独立 widget）~~ ✅ 已完成
2. ~~模型/模式切换~~ ✅ 已完成（基于协议 v2 `set_config_option`，弹窗选择实时下发 SDK）
3. ~~Markdown 流式渲染~~ ✅ 已完成（自研轻量解析器）
4. ~~输入历史 / 滚动 / Todo/Token 实时更新~~ ✅ 已完成（输入历史草稿保留、Alt+Enter 多行换行、滚动底部跟随修复）
5. ~~端到端联调~~ ✅ 已完成（见下方「联调记录」：v1 协议 + IntelliJ client_info 全链路验证通过）
6. ~~Windows 终端检测~~ ✅ 已完成（main.rs `supports_ansi()` 启动检测）
7. ⏳ Todo 列表数据源：启用 SDK `unstable_plan_operations` feature，client.rs 增加 `SessionUpdate::PlanUpdate` 分支转发 `AcpEvent::Plan`，并确认后端是否发送 plan_update（当前 InfoPanel Todo 区无数据）

## 关键决策与约束

- **后端仅协商 ACP 协议 v1**：client.rs 使用 `Client.builder()`（v1），非 `Client.v2()`
- v1 建会话用 `NewSessionRequest::new(cwd)`，取 `session_id` 后用 session_id 发请求
- 模型/模式切换用 `SetSessionConfigOptionRequest(session_id, "model"/"mode", value)`
- 后端 model/mode 配置仅对 IntelliJ 2026 客户端暴露：初始化需 `InitializeRequest::new(V1).client_info(Implementation::new("IntelliJ IDEA", "2026.1"))`
- v1 事件字段：`SessionConfigOption.id`（非 config_id）、`ToolCallUpdate.fields.status/raw_input/raw_output`（非直接字段）、`UsageUpdate.used/size`、`ContentChunk.content`
- `ToolCallStatus` 仅 Pending/InProgress/Completed（无 Failed）
- `ConfigOptionUpdate` 的 `config_options` 是 `Option<Vec<SessionConfigOption>>`，需 unwrap
- 停止信号：v1 无 `StateUpdate::Idle`，靠 `AppendThought` 触发 finish_streaming

## 联调记录

- 启动后端：`java -jar app\build\libs\XAgent-0.0.1-all.jar --acp --ws-server 8080`
- 验证工具：`cargo run --example e2e_check -- --ws-url ws://127.0.0.1:8080/acp`
- 已验证链路：握手(XCoding v1.0.0) → 建会话(3 config_options) → set_config_option(thought_level/model/mode) → prompt → 流式 AgentMessageChunk + AgentThoughtChunk + UsageUpdate
- model 选项：deepseek-v4-pro / deepseek-v4-flash / mimo-v2.5-pro(默认) / minimax-m3
- mode 选项：plan / accept_edits(默认) / yolo
- ⚠️ 控制台中文乱码为 Windows GBK 终端显示问题，数据本身正确

