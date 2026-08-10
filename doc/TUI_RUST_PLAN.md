# XAgent TUI (Rust) — 实现方案

> 版本: v0.1 (方案草案)
> 更新: 2025-07-17
> 定位: 参考 [TUI_PRD.md](./TUI_PRD.md) / [TUI_IMPLEMENTATION_PLAN.md](./TUI_IMPLEMENTATION_PLAN.md)（Java/Kotlin 版），在 `tui-rust` 模块用 Rust 重写。
> 参考 SDK: D:\IdeaProjects\rust-sdk（agentclientprotocol/rust-sdk，ACP 官方 Rust SDK）
> 技术栈: Rust 2024 + tokio + ratatui + agent-client-protocol

---

## 1. 目标与范围

把 Java/Kotlin 版 TUI 的同等功能用 Rust 实现，作为终端下连接 ACP Agent 的原生二进制客户端。

### 1.1 核心价值
- 单一原生二进制，无 JVM 依赖，启动 < 200ms
- 纯终端体验、Vim 风格快捷键、流式逐 token 渲染
- 复用 ACP 官方 Rust SDK 的 Client / Session / 事件流

### 1.2 功能范围（对齐 Java 版 MVP + 阶段二）
- 三面板布局：ChatPanel / InfoPanel / InputPanel + 状态栏
- ACP 连接（Stdio 为主）、握手、建会话、流式 prompt
- 事件处理：AgentMessageChunk / ToolCallUpdate / PlanUpdate / UsageUpdate / CurrentModel/CurrentMode/ConfigOptions
- 多会话管理、会话列表弹窗、模型/模式切换
- 输入面板：多行、历史、光标、滚动

---

## 2. 技术选型

| 层        | 技术                       | 说明                                        |
|----------|--------------------------|-------------------------------------------|
| 语言/版本   | Rust 2024 edition         | 本机 rustc 1.97.1，满足 SDK 的 rust-version 1.88 |
| 异步运行时   | tokio                     | 事件循环 + 异步 IO（与 SDK 一致）                   |
| 终端 UI   | ratatui                   | Widget/Buffer 渲染、Block/Line/Span 体系          |
| 事件输入   | crossterm                  | 键盘/鼠标事件、备用屏幕、raw mode                   |
| ACP SDK | agent-client-protocol (path) | 本地引用 D:\IdeaProjects\rust-sdk              |
| 序列化     | serde / serde_json          | 事件/配置解析                                  |
| Markdown | (可选) pulldown-cmark 或自研   | 流式渲染，阶段二接入                            |
| 构建      | Cargo + workspace          | 独立 crate `tui-rust`                        |

### 2.1 SDK 依赖方式
```toml
[dependencies]
agent-client-protocol = { path = "D:/IdeaProjects/rust-sdk/src/agent-client-protocol" }
tokio = { version = "1", features = ["rt-multi-thread", "macros", "sync", "time", "io-std", "process"] }
ratatui = "0.29"
crossterm = "0.28"
serde = { version = "1", features = ["derive"] }
serde_json = "1"
futures = "0.3"
thiserror = "2"
anyhow = "1"
```

---

## 3. 目录结构（tui-rust）

```
agi_working/tui-rust/
├── Cargo.toml
└── src/
    ├── main.rs            # 入口：CLI 解析、初始化 tokio runtime、启动 TUI
    ├── app.rs             # TuiApp：EventHandler + Renderer + 生命周期
    ├── state.rs           # AppState：全局可变状态（会话/消息/输入/Todo/Token/焦点/滚动）
    ├── model.rs           # ChatMessage / Session / TodoItem / TokenUsage / 枚举
    ├── theme.rs           # TuiTheme：modernDark 配色
    ├── acp/
    │   ├── mod.rs
    │   ├── bridge.rs      # AcpBridge：连接/会话/prompt/取消 接口
    │   ├── client.rs      # AcpClient：Stdio 连接 + 握手 + 事件循环
    │   └── event.rs       # SessionUpdate → 命令/事件 转换
    ├── ui/
    │   ├── mod.rs
    │   ├── chat.rs        # ChatPanelWidget
    │   ├── tool_call.rs   # ToolCallWidget（工具调用卡片）
    │   ├── info.rs        # InfoPanelWidget
    │   ├── input.rs       # InputPanelWidget
    │   ├── session_popup.rs # SessionListPopupWidget（会话+模型/模式）
    │   └── status_bar.rs  # StatusBarWidget
    └── event/
        ├── mod.rs
        ├── key.rs         # 键盘事件映射
        └── app_event.rs   # 内部事件（ACP/Timer/UI）
```
---

## 4. ACP 集成设计（对接 rust-sdk）

### 4.1 连接流程（WebSocket 模式，MVP 默认）
后端启动：`java -jar app/build/libs/XAgent-0.0.1-all.jar --acp --ws-server <port>`
默认端口 8080、绑定 0.0.0.0、路径 `/acp`，连接 URL `ws://127.0.0.1:<port>/acp`。

```rust
// agent-client-protocol-http 的 HttpClient：传入 ws:// scheme 自动走 WebSocket (tungstenite)
let transport = agent_client_protocol_http::HttpClient::with_endpoint(
    "ws://127.0.0.1:8080/acp"
)?;   // with_endpoint 保留精确路径 /acp

Client.v2()
    .connect_with(transport, async |cx| {
        let mut session = cx.build_session(cwd).start_session().await?;
        // 进入事件循环...
        Ok(())
    })
    .await?;
```

**依赖**：需引入 `agent-client-protocol-http` crate 并启用 `client` feature。
**CLI 参数**：`--ws-url <url>`（默认 `ws://127.0.0.1:8080/acp`）、`--ws-port <port>`（默认 8080）、`--cwd <path>`。

### 4.2 会话交互
| 操作          | rust-sdk 调用                                        |
|-------------|---------------------------------------------------|
| 发送 prompt   | `session.send_prompt(text)`                         |
| 读取更新       | `session.read_update()` → `SessionMessage`           |
| 事件分发       | `MatchDispatch::new(d).if_notification(SessionNotification)` |
| 取消          | `SentRequest.cancel()` 或 connection send_cancel     |

### 4.3 SessionUpdate → TUI 事件映射
| SessionUpdate 变体             | TUI 动作（写 AppState）                    |
|------------------------------|------------------------------------------|
| `AgentMessageChunk(Text)`    | `append_streaming_content(text)`          |
| `AgentThoughtChunk`          | `append_thought_content(text)`            |
| `ToolCallUpdate`             | `update_tool_call(id, status, content)`   |
| `PlanUpdate`                 | `clear_todos()` + `add_todo(...)`          |
| `UsageUpdate`                | `update_token_usage(prompt, completion, total)` |
| `CurrentModelUpdate`         | `set_current_model(id)`                   |
| `CurrentModeUpdate`          | `set_current_mode(id)`                    |
| `ConfigOptionsUpdate`        | `set_config_options(map)`                 |
| `StopReason`（SessionMessage） | `finish_streaming()` + 刷新渲染            |

### 4.4 传输方式
- **WebSocket（默认，MVP）**：`agent-client-protocol-http::HttpClient` 传 `ws://` URL，tungstenite 连接后端 jar 的 `/acp` 端点
- **Stdio（可选）**：`AcpAgent` 启动 Agent 子进程；或 `Stdio::new()` 连接已就绪 stdin/stdout（自测）

---

## 5. 状态管理（AppState）

`AppState` 为全局可变状态，用 `Arc<RwLock<AppState>>`（或渲染线程独占 + 通道投递）保证并发安全。

```rust
pub struct AppState {
    pub sessions: Vec<Session>,
    pub current_session: usize,
    pub input: InputState,
    pub scroll: ScrollState,
    pub focus: FocusPanel,
    pub popup: PopupState,
    pub connection: ConnectionState,
    pub current_model: Option<String>,
    pub current_mode: Option<String>,
    pub available_models: Vec<String>,
    pub available_modes: Vec<String>,
    pub config_options: Vec<ConfigOption>,
    pub todos: Vec<TodoItem>,
    pub token_usage: TokenUsage,
    pub is_streaming: bool,
}
```

**并发模型**：
- 键盘事件与渲染在 UI 线程（`tokio::select!` 主循环）
- ACP 事件循环在独立 tokio 任务，通过 `mpsc::UnboundedSender<TuiEvent>` 投递到 UI 线程
- 所有 `AppState` 修改由 UI 线程串行执行，避免锁竞争
---

## 6. UI 渲染层（ratatui）

布局对齐 Java 版：ChatPanel(75%) + InfoPanel(25%) / InputPanel / StatusBar。

```rust
// 主 render 入口
fn render(frame: &mut Frame, app: &AppState, theme: &TuiTheme) {
    let chunks = Layout::vertical([
        Constraint::Percentage(75),  // Chat + Info 行
        Constraint::Length(input_h), // Input
        Constraint::Length(1),       // StatusBar
    ]).split(frame.area());
    // 上半区再横向 split：Chat(75%) / Info(25%)
}
```

### 6.1 Widget 清单
| Widget               | 实现要点                                          |
|---------------------|-----------------------------------------------|
| `ChatPanelWidget`    | Block 边框（焦点 DOUBLE）；消息 Header + 内容；滚动；流式 `▌` 光标 |
| `ToolCallWidget`     | 工具调用卡片：Header + 入参/出参 + 状态颜色，Space 折叠          |
| `InfoPanelWidget`    | Token / Todo / 模型/模式 / 配置 / 连接状态              |
| `InputPanelWidget`   | 多行输入、占位符、历史、光标位置                            |
| `SessionListPopup`   | 居中弹窗：会话列表 + 模型/模式切换区（用 `Clear` widget）       |
| `StatusBarWidget`    | 单行：Agent名 + 连接状态 + 模型 + 会话计数 + 时间           |

### 6.2 Markdown 流式渲染（阶段二）
- 用 `pulldown-cmark` 解析；缓存已渲染节点，chunk 到来仅追加新增部分
- 代码块背景色、行内代码、列表、表格支持

---

## 7. 事件处理与快捷键

`TuiEvent` 枚举聚合所有输入源：
```rust
enum TuiEvent {
    Key(crossterm::event::KeyEvent), // 键盘
    Acp(AcpEvent),                   // ACP 事件循环投递
    Tick,                            // 每秒刷新状态栏时间
}
```

主循环用 `tokio::select!` 同时监听 crossterm 事件与 ACP 通道。快捷键映射对齐 Java 版：

| 快捷键       | 动作                    |
|-----------|-----------------------|
| Enter      | 发送消息                 |
| Ctrl+C     | 中断 / 退出               |
| Ctrl+N/W/Q/P/K | 新建/关闭/退出/弹窗/清空   |
| Tab/Shift+Tab | 焦点切换                |
| ↑/↓/PageUp/Down/Home/End | 滚动/历史/导航 |
| Space      | 展开/折叠工具调用          |
| Esc        | 关闭弹窗                 |

---

## 8. 实施阶段规划

### 阶段一：骨架 + ACP 最小闭环（MVP）
| 模块          | 交付                                       |
|-------------|------------------------------------------|
| Cargo 工程    | `tui-rust` crate + 依赖 + 可编译               |
| ACP 客户端     | Stdio 连接 + 握手 + 建会话 + 事件循环            |
| AppState     | 完整状态结构 + 事件动作方法                        |
| 三面板布局      | ChatPanel + InfoPanel + InputPanel + 状态栏  |
| 输入/发送       | 输入框 + Enter 发送 + 流式显示                  |
| 事件处理        | 6 种 SessionUpdate → AppState 更新            |
| 集成联调        | 全链路：启动→输入→Agent 回复→显示               |

### 阶段二：交互完善
- 工具调用卡片（ToolCallWidget + 状态动态更新 + 折叠）
- 多会话 + 会话列表弹窗 + 模型/模式切换（调用 SDK `setModel`/`setMode`）
- Markdown 流式渲染、输入历史、滚动、Todo/Token 实时更新、状态栏时间定时刷新
- Windows 终端检测（crossterm supports_ansi）

### 阶段三（可选）
- WebSocket 远程连接、会话持久化、粘贴支持、Agent 崩溃自动重连

---

## 9. 风险与依赖

| 风险/注意点                     | 说明/缓解                                             |
|-----------------------------|--------------------------------------------------|
| SDK 依赖路径                 | 用 `path` 指向本地 rust-sdk，需 `cargo build` 能拉取 workspace 依赖 |
| ratatui 版本                  | 以本机可用版本为准，API 可能随版本微调                        |
| Stdio 事件完整性               | SDK `read_update()` 已封装通知/请求分发，比 Java 版 Stdio 更可靠  |
| Windows raw mode + ratatui 兼容 | crossterm 支持 Windows Terminal，需在真终端验证             |
| 编译速度/体积                  | 首次 `cargo build` 拉取 rust-sdk workspace 较慢           |
| 取消/中断语义                 | 依赖 SDK `SentRequest::cancel()` 能力，需在联调中验证          |

---

## 10. 待确认事项
- [ ] 目标 Agent 的启动命令（`--command`）与工作目录默认值
- [ ] 是否优先接入 WebSocket 传输（SDK 需 `agent-client-protocol-http`）
- [ ] Markdown 渲染是否引入第三方 crate 还是自研轻量解析器
- [ ] 模型/模式切换依赖 SDK `setModel`/`setMode` 具体 API（需确认 ActiveSession 暴露方式）
