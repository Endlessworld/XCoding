# XAgent TUI — 产品需求文档 (PRD)

> 版本: v1.0 (tamboui 版)
> 更新: 2025-07-17
> 状态: Draft
> 技术框架: tamboui
> 关联文档: [TUI_IMPLEMENTATION_PLAN.md](./TUI_IMPLEMENTATION_PLAN.md)
> tamboui框架源码: E:\local-github\tamboui
> ACP协议源码: E:\local-github\kotlin-sdk
---

## 1. 产品定位

XAgent TUI 是一个基于 **tamboui** 框架构建的终端用户界面，作为 AI Agent 的交互客户端。

### 1.1 核心价值

- **纯终端体验**：无需浏览器/GUI，SSH 远程环境友好
- **键盘驱动**：Vim 风格快捷键，零鼠标依赖
- **流式实时**：逐 token 流式渲染，所见即所得
- **多会话**：同时管理多个独立 Agent 会话
- **轻量跨平台**：JVM 构建，Windows/macOS/Linux 全平台支持
- **协议标准化**：基于 ACP 协议通信，WebSocket 优先

### 1.2 目标用户

- AI Agent 终端重度用户
- SSH 远程开发环境开发者
- CI/CD 流水线中交互式 Agent 操作
- 偏好纯键盘工作流的开发者

---

## 2. 界面布局

### 2.1 三面板 + 状态栏布局示意

```
┌──────────────────────────────────────────────────────────────┐
│ ┌───────────────────────┬────────────────────┐              │
│ │  对话消息流            │  信息面板           │              │
│ │  (ChatPanel, 75%)     │  (InfoPanel, 25%)  │              │
│ │                       │                    │              │
│ │ 👤 你  [14:30]         │ 📊 Token 用量      │              │
│ │  用户消息内容           │  Prompt: 1.2K     │              │
│ │ 🤖 AI  [14:31]         │  生成: 3.5K       │              │
│ │  **AI 回复内容**        │  总计: 4.7K       │              │
│ │ 🔧 工具  [14:31]       │ 📋 Todo           │              │
│ │  tool_name(...)… [Space]│  ● ✓ 任务A        │              │
│ │ 📎 结果  [14:31]       │  ● ◌ 任务B        │              │
│ │  工具执行结果            │                    │              │
│ │                       │ ℹ 信息             │              │
│ │ ↓ 更多消息 (PageDown)   │  模型: gpt-4      │              │
│ ├───────────────────────┴────────────────────┤              │
│ │ Input: > 输入指令...  [Enter 发送, Alt+Enter 换行]       │
│ │        Ctrl+P 打开会话列表                              │
│ └──────────────────────────────────────────────┘           │
│ ● 已连接 │ 模型: gpt-4 │ 会话: 1/3 │ 14:30                │
└───────────────────────────────────────────────────────────┘
```

### 2.2 分区详解

#### 2.2.1 对话消息流 (ChatPanel)

| 功能                | 说明                                                          | 优先级    |
|-------------------|-------------------------------------------------------------|--------|
| 消息时间线             | 按时间排列，角色区分（emoji + 颜色）                                      | P0     |
| 流式渲染              | 实时追加 AI 回复内容，▌ 光标闪烁                                         | P0     |
| 消息角色              | USER/ASSISTANT/SYSTEM/TOOL_CALL/TOOL_RESULT/ERROR           | P0     |
| **Markdown 完整渲染** | 标题/粗体/斜体/代码块/行内代码/列表/引用/分隔线/链接/表格，流式渲染防闪烁                   | **P0** |
| 滚动支持              | PageUp/PageDown/Home/End                                    | P1     |
| 自动滚动              | 新消息自动滚到底部，手动滚动暂停                                            | P1     |
| **工具调用组件**        | 入参/出参分离展示，可折叠展开，状态动态更新（IN_PROGRESS→COMPLETED/FAILED），独立卡片样式 | **P0** |
| 消息时间戳             | 每条消息带时间戳显示                                                  | P2     |
| 消息复制              | 选中消息 Ctrl+C 复制                                              | P2     |
| Markdown 流式防闪烁    | 流式 chunk 到来时增量渲染 Markdown，避免整段重绘导致闪烁                        | P1     |
| 代码块复制             | 代码块右上角显示复制按钮                                                | P3     |

**消息渲染规则：**

| 角色          | Emoji | 颜色 (TuiTheme)                  | 前缀      |
|-------------|-------|--------------------------------|---------|
| USER        | 👤    | userMessage (LIGHT_BLUE)       | `👤 你`  |
| ASSISTANT   | 🤖    | assistantMessage (LIGHT_GREEN) | `🤖 AI` |
| SYSTEM      | ⚙     | systemMessage (LIGHT_YELLOW)   | `⚙ 系统`  |
| TOOL_CALL   | 🔧    | toolMessage (LIGHT_MAGENTA)    | `🔧 工具` |
| TOOL_RESULT | 📎    | 默认                             | `📎 结果` |
| ERROR       | ❌     | errorMessage (LIGHT_RED)       | `❌ 错误`  |

#### 2.2.2 信息面板 (InfoPanel)

| 区域           | 显示内容                                                                                | 优先级    |
|--------------|-------------------------------------------------------------------------------------|--------|
| **Token 用量** | Prompt/Completion/Total + 速度(tokens/s)，对接 ACP `UsageUpdate` 实时更新                    | **P0** |
| **当前配置**     | ACP `configOptions` 当前选定项：`auto_approve` (开关)、`mode` (Agent/Workers)、`model` (下拉选择) | **P0** |
| **模型/模式信息**  | 当前模型名称（`availableModels`）、当前模式（`availableModes`），对接 ACP `setModel`/`setMode`        | **P0** |
| Todo 列表      | Agent 发出的 todo 项状态追踪                                                                | P0     |
| 连接状态         | 5种连接状态显示                                                                            | P1     |
| 会话计数         | 当前会话数/总会话数                                                                          | P1     |
| 上下文占用        | 当前上下文窗口百分比                                                                          | P2     |
| 当前时间         | HH:mm 格式                                                                            | P1     |

**Todo 状态图标：**

| 状态 | 图标 | 说明 |
|------|------|------|
| PENDING | ○ | 待办 |
| IN_PROGRESS | ◌ | 进行中 |
| COMPLETED | ✓ | 已完成 |
| FAILED | ✗ | 失败 |
| SKIPPED | — | 跳过 |

**Todo 优先级图标：**

| 优先级    | 图标 | 颜色           |
|--------|----|--------------|
| HIGH   | ●  | LIGHT_RED    |
| MEDIUM | ●  | LIGHT_YELLOW |
| LOW    | ●  | LIGHT_BLUE   |

#### 2.2.3 输入面板 (InputPanel)

| 功能   | 说明                    | 优先级 |
|------|-----------------------|-----|
| 单行输入 | 基础文本输入                | P0  |
| 多行输入 | Alt+Enter 换行，Enter 发送 | P1  |
| 占位符  | 空输入时显示提示文字            | P1  |
| 输入历史 | ↑/↓ 导航历史输入            | P1  |
| 光标移动 | ←/→ 左右移动光标            | P1  |
| 输入滚动 | 多行超出面板高度自动滚动          | P2  |
| 输入计数 | 右下角显示字符数              | P2  |
| 粘贴支持 | Ctrl+V / Shift+Insert | P2  |

#### 2.2.4 状态栏 (StatusBar)

| 字段 | 格式 | 优先级 |
|------|------|--------|
| Agent 名称 | `XAgent v0.1` | P0 |
| 连接状态 | `● 已连接` / `◌ 连接中` / `○ 断开` / `◌ 重连中` / `✕ 错误` | P0 |
| 当前模型 | `模型: gpt-4` | P1 |
| 会话计数 | `会话: 3/5` | P1 |
| 当前时间 | `14:30` | P1 |

---

## 3. 快捷键系统

### 3.1 完整快捷键映射

| 快捷键         | Action                           | 功能                     | 优先级    |
|-------------|----------------------------------|------------------------|--------|
| `Enter`     | SEND_MESSAGE                     | 发送消息（输入框焦点时）           | P0     |
| `Ctrl+C`    | CANCEL_OR_INTERRUPT              | 中断当前响应 / 退出应用          | P0     |
| `Ctrl+N`    | NEW_SESSION                      | 新建会话                   | P0     |
| `Ctrl+W`    | CLOSE_SESSION                    | 关闭当前会话                 | P0     |
| `Ctrl+Q`    | QUIT_APP                         | 退出应用                   | P0     |
| `Ctrl+P`    | TOGGLE_SESSION_POPUP             | **会话列表弹窗 + 模型切换下拉框**   | **P0** |
| `Ctrl+K`    | CLEAR_CONVERSATION               | 清空当前对话                 | P1     |
| `Tab`       | FOCUS_NEXT                       | 焦点下一个面板                | P1     |
| `Shift+Tab` | FOCUS_PREVIOUS                   | 焦点上一个面板                | P1     |
| `↑`         | SCROLL_UP / INPUT_HISTORY_PREV   | 上滚 / 输入历史上一条           | P0     |
| `↓`         | SCROLL_DOWN / INPUT_HISTORY_NEXT | 下滚 / 输入历史下一条           | P0     |
| `←`         | CURSOR_LEFT                      | 输入光标左移                 | P1     |
| `→`         | CURSOR_RIGHT                     | 输入光标右移                 | P1     |
| `PageUp`    | SCROLL_PAGE_UP                   | 消息流上翻页                 | P1     |
| `PageDown`  | SCROLL_PAGE_DOWN                 | 消息流下翻页                 | P1     |
| `Home`      | SCROLL_TOP                       | 滚动到顶部                  | P1     |
| `End`       | SCROLL_BOTTOM                    | 滚动到底部                  | P1     |
| `Backspace` | DELETE_CHAR_BEFORE               | 删除光标前字符                | P0     |
| `Delete`    | DELETE_CHAR_AFTER                | 删除光标后字符                | P1     |
| `Space`     | TOGGLE_TOOL_EXPAND               | 展开/折叠工具消息（ChatPanel焦点） | P2     |
| `Alt+Enter` | INSERT_NEWLINE                   | 输入框换行                  | P1     |
| `Esc`       | CLOSE_POPUP                      | 关闭弹窗                   | P1     |

### 3.2 焦点切换

| 操作          | 效果                               |
|-------------|----------------------------------|
| `Tab`       | LEFT → CENTER → INPUT → LEFT（循环） |
| `Shift+Tab` | 同上（反向循环）                         |

焦点由 `AppState.focusPanel` 管理，共 3 个面板：LEFT（会话列表弹窗）、CENTER（对话流）、INPUT（输入框）。

### 3.3 会话列表弹窗内操作（Ctrl+P）

弹窗内分为两个区域：

| 区域       | 操作                                                       | 说明                         |
|----------|----------------------------------------------------------|----------------------------|
| **会话列表** | `↑/↓` 选择，`Enter` 切换，`Ctrl+N` 新建，`Ctrl+W` 关闭选中            | 与会话管理一致                    |
| **模型切换** | `Tab` 切换到模型选择区，`↑/↓` 选择模型，`Enter` 通过 ACP `setModel()` 切换 | 下拉框展示 `availableModels` 列表 |

---

## 4. 会话管理

### 4.1 会话生命周期

```
[*] --> 活跃: Ctrl+N / 启动
活跃 --> 活跃: 切换会话（Ctrl+P 弹窗选择）
活跃 --> 已关闭: Ctrl+W
已关闭 --> [*]
活跃 --> 已清空: Ctrl+K
已清空 --> 活跃: 发送消息
```

### 4.2 会话特性

| 特性       | 说明                                                                | 优先级    |
|----------|-------------------------------------------------------------------|--------|
| 默认会话     | 启动时自动创建第一个会话                                                      | P0     |
| 自动命名     | 基于首条用户消息截取（最长20字符）                                                | P1     |
| 会话上限     | 默认最多50个会话                                                         | P1     |
| 会话切换     | Ctrl+P 弹窗 → ↑/↓ 选择 → Enter 确认                                     | P1     |
| **模型切换** | Ctrl+P 弹窗内模型下拉框 → `availableModels` 列表 → Enter 调用 `setModel()` 切换 | **P0** |
| **模式切换** | Ctrl+P 弹窗内模式下拉框 → `availableModes` 列表 → Enter 调用 `setMode()` 切换   | **P0** |
| 会话持久化    | 保存到本地 JSON 文件                                                     | P3     |

---

## 5. ACP 协议集成

### 5.1 通信架构

```
┌──────────────────────────────────┐       ACP (WebSocket/Stdio)      ┌──────────────────┐
│   XAgent TUI (Client)             │ ◄─────────────────────────────► │  Agent 子进程    │
│                                   │  JSON-RPC over NDJSON          │  (ACP Server)    │
│  ┌────────────────────────────┐  │                                  │                  │
│  │ Java: TambouiTuiApp        │  │                                  │  ProcessBuilder  │
│  │  (EventHandler + Renderer) │  │                                  │  or WebSocket    │
│  │  └── AcpBridge (接口) ─────┼──┤                                  └──────────────────┘
│  │ Kotlin: TambouiAcpBridge   │  │
│  │  └── AcpClientManager      │  │
│  │   ├── WebSocket 模式       │  │
│  │   └── Stdio 模式 (回退)    │  │
│  └────────────────────────────┘  │
└──────────────────────────────────┘
```

### 5.2 连接模式

| 模式            | 优先级 | 说明                                                    |
|---------------|-----|-------------------------------------------------------|
| WebSocket（默认） | P0  | 自动启动内部 WebSocket 服务器（端口 9988），通过 kotlin-sdk Client 连接 |
| Stdio（回退）     | P1  | 通过 `--command` 参数启动 Agent 子进程，NDJSON 通信               |
| 外部 WebSocket  | P2  | 通过 `--ws-url` 参数连接远程 Agent 服务                         |

### 5.3 握手流程

```
TUI Client                              Agent
    │                                       │
    │─── initialize(clientInfo) ──────────►│
    │◄─── AgentInfo (capabilities) ────────│
    │                                       │
    │─── session/new(cwd) ────────────────►│
    │◄─── sessionId + AgentSession ────────│
    │                                       │
    │─── session/prompt(content) ─────────►│
    │◄─── [streaming events] ──────────────│ (逐帧推送)
    │◄─── session/update(notification) ────│ (异步通知)
    │                                       │
    │─── session/cancel ──────────────────►│ (中断)
    │◄─── cancelled ──────────────────────│
```

### 5.4 事件类型与处理

| ACP 事件                | TUI 处理                                                                     | 优先级    |
|-----------------------|----------------------------------------------------------------------------|--------|
| `AgentMessageChunk`   | `appendStreamingContent()` 追加，**增量 Markdown 渲染**                           | P0     |
| `AgentThoughtChunk`   | `appendThoughtContent()` 追加                                                | P1     |
| `ToolCallUpdate`      | **工具调用状态动态更新**：IN_PROGRESS 显示入参卡片 → COMPLETED/FAILED 追加出参结果，支持 Space 展开/折叠 | **P0** |
| `PlanUpdate`          | `clearTodos()` + `addTodo()` 更新 Todo 列表                                    | P1     |
| `UsageUpdate`         | `updateTokenUsage(prompt, completion, total)` 更新 Token 统计                  | P1     |
| `CurrentModeUpdate`   | 更新当前模式显示（`availableModes` 中的选中项）                                           | P1     |
| `CurrentModelUpdate`  | 更新当前模型显示（`availableModels` 中的选中项）                                          | P1     |
| `ConfigOptionsUpdate` | 更新 `configOptions` 配置面板中的当前值                                               | P1     |

### 5.5 传输方式

| 方式                     | 支持   | 说明                               |
|------------------------|------|----------------------------------|
| WebSocket（内部服务器）       | ✅ P0 | 默认模式，自动启动 Agent 内嵌 WebSocket 服务器 |
| WebSocket（远程连接）        | ✅ P2 | `--ws-url ws://host:port/acp`    |
| Stdio (ProcessBuilder) | ✅ P1 | `--command <agent_command>` 回退模式 |

### 5.6 子进程生命周期

| 阶段     | 操作                                  | 状态变化                     |
|--------|-------------------------------------|--------------------------|
| TUI 启动 | 创建 AcpClientManager                 | DISCONNECTED             |
| 连接建立   | connect() → initialize + newSession | CONNECTING → CONNECTED   |
| 事件收集   | startEventCollection() 启动后台协程       | CONNECTED                |
| 中断     | sendCancel()                        | CONNECTED                |
| 断开     | disconnect() 清理协程/进程/连接             | CONNECTED → DISCONNECTED |

---

## 6. 用户工作流

### 6.1 基础对话流

```
1. 启动 TUI（TambouiMain.kt）
2. 自动连接 Agent（WebSocket 模式，状态栏显示连接进度）
3. 在输入框输入消息
4. Enter 发送
5. Agent 流式回复（ChatPanel 逐 token 渲染）
6. 输入下一条消息继续对话
```

### 6.2 多会话工作流

```
1. Ctrl+N 新建会话
2. Ctrl+P 打开会话列表弹窗
3. ↑/↓ 选择其他会话
4. Enter 切换
5. 会话间独立上下文互不干扰
```

### 6.3 中断与重试

```
1. Agent 正在生成回复
2. Ctrl+C 中断当前生成
3. 修改输入框内容
4. Enter 重新发送
```

### 6.4 查看工具调用

```
1. Agent 执行工具调用时
2. ChatPanel 显示 🔧 工具调用卡片
3. 显示工具名称、参数
4. 工具完成后显示 📎 结果
5. Space 键展开/折叠长结果
```

---

## 7. 技术架构

### 7.1 模块划分

```
com.xr21.ai.agent.tui/
├── TambouiTuiApp.java          # 主应用：EventHandler + Renderer，生命周期管理
├── AppState.java                # 全局可变状态（会话/消息/输入/Todo/Token/焦点/滚动）
├── TuiTheme.java                # 主题定义（28色 modernDark 方案）
├── ChatMessage.java             # 消息模型
├── Session.java                 # 会话模型
├── PanelType.java               # 面板枚举（LEFT/CENTER/INPUT）
├── MessageRole.java             # 消息角色枚举
├── TodoItem.java                # Todo 项
├── TodoPriority.java            # Todo 优先级枚举
├── TodoStatus.java              # Todo 状态枚举
├── TokenUsage.java              # Token 用量
├── layout/
│   ├── ChatPanelWidget.java     # 对话消息面板（Block + Line/Span 渲染）
│   ├── InfoPanelWidget.java     # 信息面板（Token/Todo/模型/连接状态）
│   ├── InputPanelWidget.java    # 输入面板（多行/滚动/占位符）
│   ├── SessionListPopupWidget.java  # 会话列表弹窗
│   └── StatusBarWidget.java     # 状态栏
├── kotlin/
│   ├── TambouiMain.kt           # 入口 + CLI 参数解析 + Windows Terminal 检测
│   ├── TambouiAcpBridge.kt      # ACP 桥接层（Kotlin → Java 回调适配）
│   ├── acp/
│   │   ├── AcpClientManager.kt  # ACP 客户端管理器（WebSocket + Stdio）
│   │   └── ConnectionState.kt   # 连接状态枚举（5种）
│   └── config/
│       └── ACPConnectConfig.kt  # 连接配置数据类
```

### 7.2 技术栈

| 层       | 技术                  | 说明                                       |
|---------|---------------------|------------------------------------------|
| UI 框架   | tamboui             | Widget/Buffer/Block 渲染系统                 |
| UI 语言   | Java 17+            | TambouiTuiApp 实现 EventHandler + Renderer |
| ACP 桥接  | Kotlin              | TambouiAcpBridge 适配 ACP SDK 事件           |
| ACP SDK | kotlin-sdk          | Client/Protocol/Session 完整实现             |
| 通信      | WebSocket / Stdio   | 默认 WebSocket，回退 Stdio                    |
| 构建      | Gradle + Kotlin DSL | JVM 多平台项目                                |

### 7.3 数据流

```
┌──────────┐    KeyEvent    ┌──────────────┐   Action   ┌───────────┐
│ 终端输入   │ ────────────► │ EventHandler │ ──────────► │ AppState  │
│ (JLine3)  │               │  .handle()   │             │  更新     │
└──────────┘               └──────────────┘             └─────┬─────┘
                                                              │
                                                              ▼
┌──────────┐    ACP Event   ┌──────────────┐             ┌───────────┐
│ ACP 客户端 │ ────────────► │ AcpBridge    │ ───────────► │ Renderer  │
│ (WebSocket)│               │ 回调 → AppState│            │ .render() │
└──────────┘               └──────────────┘             └─────┬─────┘
                                                              │
                                                              ▼
                                                        ┌───────────┐
                                                        │ TuiRunner │
                                                        │ Frame渲染  │
                                                        └───────────┘
```

### 7.4 渲染策略

- **Widget 渲染**：每个面板实现 `Widget.render(Rect area, Buffer buffer)` 接口
- **Block 容器**：使用 `Block.builder()` 创建带边框/标题的容器
- **Line/Span 文本**：使用 `Line.from(Span.styled(...))` 构建带样式的文本行
- **触发时机**：键盘事件、ACP 事件、定时器（状态栏时间）
- **线程安全**：所有状态修改通过 `runner.runOnRenderThread()` 在渲染线程执行
- **定时刷新**：`ScheduledExecutorService` 每秒触发状态栏时间更新
- **Markdown 增量渲染**：流式 chunk 到来时仅渲染新增部分，避免整段文本重绘导致闪烁。通过缓存已渲染的 Markdown 节点树实现增量更新

### 7.5 状态管理

`AppState` 单一全局可变状态对象：

- **可变对象模式**：直接修改 public 字段
- **单线程模型**：所有状态修改在渲染线程进行
- **无状态快照**：每次渲染直接从 AppState 读取最新值
- **线程安全**：通过 TuiRunner.runOnRenderThread() 保证顺序访问

### 7.6 焦点管理

| 面板           | 焦点效果              | 切换方式 |
|--------------|-------------------|------|
| LEFT（会话列表弹窗） | 边框 DOUBLE 高亮      | Tab  |
| CENTER（对话流）  | 边框 DOUBLE 高亮      | Tab  |
| INPUT（输入框）   | 边框 DOUBLE 高亮，光标可见 | Tab  |

焦点切换由 `AppState.focusPanel` 管理，`Tab`/`Shift+Tab` 循环切换三个面板。

---

## 8. 非功能需求

### 8.1 性能指标

| 指标   | 目标        | 备注                 |
|------|-----------|--------------------|
| 启动时间 | < 2秒      | JVM 冷启动 + Agent 连接 |
| 帧率   | > 30fps   | Widget 渲染不卡顿       |
| 内存占用 | < 256MB   | JVM 堆内存            |
| 消息上限 | 500+ 条/会话 | 超出截断或分页            |
| 会话上限 | 50 个      | 超出提示               |
| 输入历史 | 100 条     | 循环覆盖               |

### 8.2 兼容性

| 平台 | 支持 | 说明 |
|------|------|------|
| Windows 10/11 | ✅ | 优先检测 Windows Terminal |
| macOS 12+ | ✅ | Terminal.app / iTerm2 |
| Linux | ✅ | 主流发行版 |
| 最小终端 | 80×24 | 低于此显示提示 |
| tmux/screen | ✅ | 支持嵌套终端 |

### 8.3 质量属性

- **可用性**：全键盘操作，零鼠标依赖
- **可恢复性**：Agent 进程崩溃后自动重连
- **可观测性**：状态栏实时显示连接状态
- **可扩展性**：Widget 模式支持新增面板
- **可配置性**：ACPConnectConfig 集中管理连接参数

---

## 9. 实施路线图

### 阶段一：核心框架 + ACP 基础通信（MVP）✅ 已完成

> **目标**: 可用的最小闭环 TUI —— 启动 → 输入 → Agent 回复 → 显示

| 模块        | 关键交付                                | 状态 |
|-----------|-------------------------------------|----|
| 依赖配置      | tamboui + ACP SDK 依赖就绪              | ✅  |
| ACP 客户端   | WebSocket 模式 + Stdio 回退             | ✅  |
| ACP 握手    | initialize + newSession             | ✅  |
| 状态管理      | AppState 完整实现                       | ✅  |
| TUI 初始化   | TuiRunner + EventHandler + Renderer | ✅  |
| 三面板布局     | ChatPanel + InfoPanel + InputPanel  | ✅  |
| 事件处理      | KeyEvent → AppState 更新              | ✅  |
| 输入面板      | 键盘输入 + 基础编辑                         | ✅  |
| ACP 事件处理  | 6种事件解析 → AppState 更新                | ✅  |
| ChatPanel | 消息流渲染 + 角色区分 + 滚动                   | ✅  |
| 状态栏       | 连接状态 + 模型 + 时间                      | ✅  |
| 集成联调      | 全链路串联测试                             | ✅  |

### 阶段二：交互完善

| 功能 | 说明 | 优先级 |
|------|------|--------|
| 流式打字机效果 | 逐 token 渲染 + 光标闪烁 | P0 |
| 工具调用可视化 | 展开/折叠工具调用卡片 | P1 |
| 会话列表交互 | 新建/切换/删除完整逻辑 | P1 |
| 输入历史 UI | ↑/↓ 导航历史消息 | P1 |
| Todo 实时更新 | InfoPanel Todo 对接 ACP | P1 |
| Token 统计 | InfoPanel Token 用量动态更新 | P1 |
| 焦点切换 | Tab/Shift+Tab 面板切换高亮 | P1 |

### 阶段三：高级功能

| 功能           | 说明            | 优先级 |
|--------------|---------------|-----|
| 命令面板         | Ctrl+P 模糊搜索命令 | P2  |
| 主题切换         | 暗色/亮色一键切换     | P2  |
| 会话持久化        | 本地 JSON 文件存储  | P2  |
| 输入增强         | 多行输入完善        | P2  |
| 外部 WebSocket | 远程 Agent 连接   | P2  |
| 自定义快捷键       | 可配置键绑定        | P3  |

### 阶段四：优化与发布

| 功能                  | 说明              | 优先级 |
|---------------------|-----------------|-----|
| Windows Terminal 检测 | 自动检测推荐 Terminal | P2  |
| 错误处理与重连             | 崩溃检测 + 自动重连     | P2  |
| 性能优化                | 帧率/内存优化         | P2  |
| 用户文档                | 使用指南            | P2  |
| GraalVM 原生镜像        | 低优先级            | P3  |
| 多语言支持               | 国际化             | P3  |
