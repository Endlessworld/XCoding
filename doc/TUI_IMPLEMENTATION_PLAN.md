# XAgent TUI — 实施计划 (Implementation Plan)

> 版本: v1.1 (tamboui 版)
> 更新: 2025-07-17
> 状态: 基于代码实现持续更新
> 关联文档: [TUI_PRD.md](./TUI_PRD.md)
> 阅读和主动维护 E:\local-github\ai-agents\doc\spac目录下的知识和经验
---

## 1. 项目概述

XAgent TUI 是一个基于 **tamboui** 框架构建的终端用户界面，作为 AI Agent 的交互客户端。本项目采用 **Java 17+** 编写 UI
层（Widget 渲染、事件处理、状态管理），**Kotlin** 编写 ACP 桥接层（协议通信、事件适配）。

### 1.1 模块架构

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
│   ├── ChatPanelWidget.java     # 对话消息面板（Markdown 增量渲染 + 工具调用组件）
│   ├── ToolCallWidget.java      # 工具调用卡片（入参/出参/状态动态更新）
│   ├── InfoPanelWidget.java     # 信息面板（Token/配置选项/模型/模式/Todo/连接状态）
│   ├── InputPanelWidget.java    # 输入面板
│   ├── SessionListPopupWidget.java  # 会话列表弹窗（含模型/模式切换区）
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

---

## 2. 数据模型层

### 2.1 ChatMessage (`ChatMessage.java`)

| 字段              | 类型              | 说明                                                |
|-----------------|-----------------|---------------------------------------------------|
| `id`            | `String`        | UUID 前8位                                          |
| `role`          | `MessageRole`   | USER/ASSISTANT/SYSTEM/TOOL_CALL/TOOL_RESULT/ERROR |
| `timestamp`     | `LocalDateTime` | 消息创建时间                                            |
| `content`       | `String`        | 消息内容（原始 Markdown 文本）                              |
| `renderedLines` | `List<Line>`    | 已缓存的 Markdown 渲染结果（增量更新，避免闪烁）                     |
| `isStreaming`   | `boolean`       | 流式接收中（渲染 `▌` 光标）                                  |
| `isExpanded`    | `boolean`       | 工具消息展开状态（Space 切换）                                |
| `toolCallId`    | `String`        | 关联的 ACP `ToolCallId`（工具调用消息专用）                    |

### 2.2 Session (`Session.java`)

| 字段                        | 类型                  | 说明                      |
|---------------------------|---------------------|-------------------------|
| `id`                      | `String`            | UUID 前8位                |
| `name`                    | `String`            | 会话名（默认 `"New Session"`） |
| `messages`                | `List<ChatMessage>` | 消息列表                    |
| `createdAt` / `updatedAt` | `LocalDateTime`     | 创建/更新时间                 |

### 2.3 TodoItem (`TodoItem.java`)

| 字段          | 类型              | 说明                                           |
|-------------|-----------------|----------------------------------------------|
| `id`        | `String`        | UUID 前8位                                     |
| `content`   | `String`        | 任务内容                                         |
| `status`    | `TodoStatus`    | PENDING/IN_PROGRESS/COMPLETED/FAILED/SKIPPED |
| `priority`  | `TodoPriority`  | HIGH/MEDIUM/LOW                              |
| `createdAt` | `LocalDateTime` | 创建时间                                         |

### 2.4 TokenUsage (`TokenUsage.java`)

| 字段                 | 类型       | 说明                           |
|--------------------|----------|------------------------------|
| `promptTokens`     | `long`   | Prompt 令牌数（当前未从 ACP 分离，显示 0） |
| `completionTokens` | `long`   | 生成令牌数（同上）                    |
| `totalTokens`      | `long`   | 总令牌数（`UsageUpdate` 更新）       |
| `costUsd`          | `double` | 估算费用（当前未实现）                  |

---

## 3. 状态管理层 (`AppState.java`)

`AppState` 采用**单一全局可变状态**模式，所有 UI 状态集中管理，通过 `TuiRunner.runOnRenderThread()` 保证单线程顺序访问。

### 3.1 核心状态字段

| 类别        | 字段                                                                                        | 说明                                                                                |
|-----------|-------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------|
| 会话        | `sessions`, `currentSessionIndex`, `totalSessions`                                        | 多会话管理                                                                             |
| 输入        | `inputBuffer`, `inputCursorPos`, `inputHistory`, `inputHistoryIndex`, `inputScrollOffset` | 输入框状态与历史                                                                          |
| 滚动        | `scrollOffset`                                                                            | ChatPanel 滚动偏移（`Integer.MAX_VALUE` 表示底部）                                          |
| 焦点        | `focusPanel`                                                                              | 当前焦点面板（CENTER/INPUT/LEFT）                                                         |
| 弹窗        | `isSessionListPopupVisible`, `sidebarSelectedIndex`                                       | 会话列表弹窗                                                                            |
| 连接        | `connectionState`, `agentName`, `agentVersion`, `modelName`, `errorMessage`               | ACP 连接元数据                                                                         |
| **模型/模式** | `currentModelId`, `currentModeId`, `availableModels`, `availableModes`                    | ACP `availableModels`/`availableModes` + `CurrentModelUpdate`/`CurrentModeUpdate` |
| **配置**    | `configOptions`                                                                           | ACP `configOptions` 当前值（`auto_approve`/`mode`/`model`）                            |
| 数据        | `todos`, `tokenUsage`, `isStreaming`                                                      | Agent 运行时数据                                                                       |

### 3.2 关键操作方法

| 方法                                                               | 功能                                     |
|------------------------------------------------------------------|----------------------------------------|
| `sendMessage(String)`                                            | 添加用户消息，清空输入，标记 `isStreaming = true`    |
| `appendStreamingContent(String)`                                 | 追加到当前 ASSISTANT 流式消息，或新建流式消息           |
| `appendThoughtContent(String)`                                   | 追加到 SYSTEM 流式消息（前缀 `💭 `）              |
| `addToolCall(String, String)`                                    | 添加 TOOL_CALL 消息，自动 `finishStreaming()` |
| `appendToolCallUpdate(String)`                                   | 追加到当前流式工具调用消息                          |
| `addToolResult(String)`                                          | 添加 TOOL_RESULT 消息（超 500 字符自动截断）        |
| `finishStreaming()`                                              | 关闭当前流式消息标志                             |
| `newSession()` / `closeCurrentSession()` / `clearConversation()` | 会话生命周期                                 |
| `scrollUp/Down/PageUp/PageDown()`                                | 滚动操作                                   |
| `inputHistoryPrev/Next()`                                        | 输入历史导航                                 |
| `focusNext/Previous()`                                           | 焦点切换（CENTER → INPUT → CENTER 循环）       |
| `toggleSessionListPopup()`                                       | 切换弹窗，焦点切到 LEFT                         |
| `toggleLastToolMessage()`                                        | 反转最近工具消息的 `isExpanded`                 |

---

## 4. UI 渲染层

所有面板实现 `Widget.render(Rect area, Buffer buffer)`，使用 `Block` 容器 + `Line/Span` 构建样式文本行。

### 4.1 布局分区

采用**三面板 + 状态栏**纵向布局：

```
┌──────────────────────────────┬────────────────────┐
│ ChatPanelWidget (75%)        │ InfoPanelWidget    │
│                              │ (25%)              │
├──────────────────────────────┴────────────────────┤
│ InputPanelWidget (全宽, min(5, height/5))       │
├─────────────────────────────────────────────────┤
│ StatusBarWidget (高度 1)                        │
└─────────────────────────────────────────────────┘
```

### 4.2 各 Widget 实现

#### ChatPanelWidget

- **边框**: 焦点 `DOUBLE`，非焦点 `ROUNDED`
- **消息渲染**: Header（角色 Emoji + `HH:mm` 时间戳）+ Content
- **Markdown 增量渲染**:
    - ASSISTANT 消息内容按 Markdown 语法解析（标题/粗体/斜体/行内代码/代码块/列表/引用/分隔线/链接/表格）
    - 流式 chunk 到来时，仅解析**新增部分**，追加到 `renderedLines` 缓存，避免整段重绘闪烁
    - 代码块使用独立背景色 + 等宽字体样式渲染
- **角色颜色**: USER→LIGHT_BLUE, ASSISTANT→LIGHT_GREEN, SYSTEM→LIGHT_YELLOW, TOOL→LIGHT_MAGENTA, ERROR→LIGHT_RED
- **流式指示**: `isStreaming` 为 true 时追加 `▌`
- **滚动**: `scrollOffset` 控制，`Integer.MAX_VALUE` 自动对齐底部
- **工具调用组件**: 见 `ToolCallWidget`
- **滚动提示**: 右下角显示 `↑ offset/max 底部` 或 `↓ 更多消息`
- **空状态**: 显示 `"开始新的对话"` 和 `"输入消息后按 Enter 发送"`

#### ToolCallWidget

- **组件结构**: 独立卡片，包含 Header（工具名 + 状态图标）+ 入参区 + 出参区
- **状态动态更新**: `IN_PROGRESS` → `COMPLETED`/`FAILED`，边框颜色随状态变化（进行中黄色，成功绿色，失败红色）
- **入参展示**: 显示 `ToolCallUpdate` 中的 `content`（工具调用参数），JSON 格式化或纯文本
- **出参展示**: 状态变为 `COMPLETED`/`FAILED` 后，追加显示结果内容 + `locations`（文件位置）
- **折叠/展开**: Space 键切换 `isExpanded`，折叠时仅显示 Header + 第一行摘要
- **定位**: 通过 `toolCallId` 匹配同一工具的多次 `ToolCallUpdate`，在同一卡片上更新状态和内容

#### InfoPanelWidget

- **固定边框**: `ROUNDED`（不随焦点变化）
- **Token 用量**: 显示 Prompt/生成/总计/速度(tokens/s)，从 ACP `UsageUpdate` 和 Agent 内部统计获取（`promptTokens` +
  `completionTokens` + `totalTokens`）
- **当前配置**: 展示 ACP `configOptions` 当前选定项：
    - `auto_approve`: 布尔开关样式
    - `mode`: 下拉选择（Agent / Workers）
    - `model`: 下拉选择（从 `availableModels` 列表）
- **模型/模式信息**: 当前模型（`currentModel`，来自 `CurrentModelUpdate`）、当前模式（`currentMode`，来自 `CurrentModeUpdate`）
- **Todo 列表**: 完成进度 `(completed/total)`，每项含优先级圆点 + 状态图标 + 内容
- **连接状态**: 5 种状态带颜色图标，会话计数，当前时间（每秒刷新）

#### InputPanelWidget

- **边框**: 焦点 `DOUBLE`
- **占位符**: 空输入时显示 `> 输入指令...  [Enter 发送, Alt+Enter 换行]`
- **多行**: 按 `\n` 分割，超高度自动滚动
- **前缀**: 非空输入每行前加 `>`

#### SessionListPopupWidget

- **弹窗覆盖**: 居中浮动
- **边框**: `DOUBLE`
- **双区域布局**:
    - **左侧/上部**: 会话列表。当前会话 `●`，选中 `▸`，其他空格
    - **右侧/下部**: 模型/模式切换区。`Tab` 切换焦点到此区域
- **模型切换**: 下拉框展示 `availableModels` 列表（从 ACP `availableModels` 属性获取），`Enter` 调用
  `clientSession.setModel(modelId)`
- **模式切换**: 下拉框展示 `availableModes` 列表，`Enter` 调用 `clientSession.setMode(modeId)`
- **提示**: `↑↓ 选择  Tab 切换区域  Enter 确认  Esc 关闭`

#### StatusBarWidget

- **单行**: 无 Block，直接 `buffer.setLine()`
- **字段**: Agent 名 + 版本、连接状态（带颜色）、模型名、会话计数、当前时间
- **刷新**: `ScheduledExecutorService` 每秒触发 `requestRender()`

### 4.3 主题系统 (`TuiTheme.java`)

`modernDark()` 定义 28 个语义化颜色常量，全部映射到 tamboui `Color` 枚举。

| 语义                 | 颜色            | 用途      |
|--------------------|---------------|---------|
| `borderNormal`     | GRAY          | 非焦点面板边框 |
| `borderFocused`    | LIGHT_CYAN    | 焦点面板边框  |
| `userMessage`      | LIGHT_BLUE    | 用户消息    |
| `assistantMessage` | LIGHT_GREEN   | AI 消息   |
| `systemMessage`    | LIGHT_YELLOW  | 系统/思考消息 |
| `toolMessage`      | LIGHT_MAGENTA | 工具调用/结果 |
| `errorMessage`     | LIGHT_RED     | 错误消息    |
| `statusConnected`  | LIGHT_GREEN   | 已连接状态   |
| `statusError`      | LIGHT_RED     | 错误状态    |
| `selectedText`     | LIGHT_CYAN    | 弹窗选中项   |

---

## 5. 事件处理层 (`TambouiTuiApp.java`)

`TambouiTuiApp` 同时实现 `EventHandler`（键盘输入处理）和 `Renderer`（帧渲染）。

### 5.1 键盘事件处理流程

```
KeyEvent → handle() → handleKeyEvent() → 分类处理
```

处理优先级（按代码顺序）：

1. **弹窗拦截**: 弹窗可见时，↑/↓/Enter/Escape 被会话列表弹窗消费
2. **方向键上下文**: UP/DOWN 根据焦点面板区分（CENTER 滚动 vs INPUT 历史）
3. **翻页/导航**: PageUp/PageDown/Home/End/←/→ 直接操作 AppState
4. **Ctrl 组合键**: Ctrl+C/N/W/Q/P/K 执行对应 Action
5. **Tab 焦点**: Tab/Shift+Tab 循环切换焦点（CENTER → INPUT → CENTER）
6. **Escape**: 关闭弹窗
7. **Enter**: 焦点在 INPUT 时发送消息
8. **Backspace/Delete**: 输入编辑
9. **字符输入**: Alt+Enter 插入换行，Space 展开工具（CENTER 焦点时），普通字符追加

### 5.2 快捷键完整映射

| 快捷键           | 处理逻辑                                                                |
|---------------|---------------------------------------------------------------------|
| `Enter`       | `focusPanel == INPUT` 时调用 `sendMessage()`                           |
| `Ctrl+C`      | `isStreaming` 时 `cancel()` + `finishStreaming()`，否则 `runner.quit()` |
| `Ctrl+N`      | `appState.newSession()`                                             |
| `Ctrl+W`      | `appState.closeCurrentSession()`                                    |
| `Ctrl+Q`      | `runner.quit()`                                                     |
| `Ctrl+P`      | `appState.toggleSessionListPopup()`（弹窗内含会话列表 + 模型/模式切换区）            |
| `Ctrl+K`      | `appState.clearConversation()`                                      |
| `Tab`         | `focusNext()`（CENTER→INPUT→CENTER）                                  |
| `Shift+Tab`   | `focusPrevious()`（反向）                                               |
| `↑`           | CENTER: `scrollUp()` / INPUT: `inputHistoryPrev()`                  |
| `↓`           | CENTER: `scrollDown()` / INPUT: `inputHistoryNext()`                |
| `←/→`         | 移动 `inputCursorPos`                                                 |
| `PageUp/Down` | `scrollPageUp/Down()`                                               |
| `Home`        | `scrollOffset = 0`                                                  |
| `End`         | `scrollOffset = Integer.MAX_VALUE`                                  |
| `Backspace`   | 删除光标前字符                                                             |
| `Delete`      | 删除光标后字符                                                             |
| `Space`       | CENTER 焦点时 `toggleLastToolMessage()` / 弹窗内折叠/展开工具调用卡片               |
| `Alt+Enter`   | 在 `inputCursorPos` 处插入 `\n`                                         |
| `Esc`         | 关闭会话列表弹窗                                                            |

### 5.3 生命周期管理

| 阶段                 | 操作                                                        |
|--------------------|-----------------------------------------------------------|
| **初始化**            | 创建 `TuiConfig` → `TuiRunner.create()` → 启动状态栏定时器 → 连接 ACP |
| **运行**             | `tui.run(this, this)` 进入事件循环                              |
| **清理** (`finally`) | `scheduler.shutdownNow()` → `acpBridge.disconnect()`      |

### 5.4 ACP 回调处理

`ConnectionCallback` 全部通过 `runner.runOnRenderThread()` 切换至渲染线程：

| 回调               | 操作                                                                   |
|------------------|----------------------------------------------------------------------|
| `onConnected`    | 更新 `connectionState=CONNECTED`，设置 `agentName/agentVersion/modelName` |
| `onDisconnected` | 更新 `connectionState=DISCONNECTED`                                    |
| `onEvent`        | 调用 `event.apply(appState)` 更新状态                                      |
| `onError`        | 更新 `connectionState=DISCONNECTED_ERROR`，设置 `errorMessage`            |

---

## 6. ACP 通信层

### 6.1 架构分层

```
TambouiMain.kt
    ├── TambouiTuiApp (Java)
    │   └── AcpBridge 接口
    │       └── TambouiAcpBridge (Kotlin)
    │           ├── AcpClientManager
    │           │   ├── WebSocket 模式 (Ktor Client)
    │           │   └── Stdio 模式 (ProcessBuilder)
    │           └── AcpEventAdapter
    └── AppState
```

### 6.2 `TambouiAcpBridge.kt` — 桥接层

| 职责                           | 说明                                                                  |
|------------------------------|---------------------------------------------------------------------|
| 实现 `TambouiTuiApp.AcpBridge` | 为 Java 侧提供同步风格的回调接口                                                 |
| 协程封装                         | 所有 ACP SDK 调用包裹在 `CoroutineScope(Dispatchers.IO)` 中                 |
| 事件转换                         | `handleEvent()` 将 `Event.SessionUpdateEvent` 转为 `AcpEventAdapter`   |
| 参数解析                         | `parseConfig()` 解析 `--command`、`--ws-url`、`--ws-server-port` CLI 参数 |

### 6.3 `AcpClientManager.kt` — 客户端管理器

#### ACP 会话配置与模型切换接口

WebSocket 模式下，`ClientSession` 提供以下配置操作方法（均需通过 Kotlin 协程调用）：

| 方法                                 | ACP SDK 接口                                                                 | 说明                             | 代码参考                                |
|------------------------------------|----------------------------------------------------------------------------|--------------------------------|-------------------------------------|
| `setModel(modelId)`                | `clientSession.setModel(ModelId)`                                          | 切换当前会话使用的 AI 模型                | `AgiAgentSession.setModel()`        |
| `setMode(modeId)`                  | `clientSession.setMode(SessionModeId)`                                     | 切换 Agent 运行模式（Agent / Workers） | `AgiAgentSession.setMode()`         |
| `setConfigOption(configId, value)` | `clientSession.setConfigOption(SessionConfigId, SessionConfigOptionValue)` | 修改 `configOptions` 配置项         | `AgiAgentSession.setConfigOption()` |
| `availableModels`                  | `clientSession.availableModels` (property)                                 | 获取当前 Agent 支持的模型列表             | `AgiAgentSession.availableModels`   |
| `availableModes`                   | `clientSession.availableModes` (property)                                  | 获取当前 Agent 支持的模式列表             | `AgiAgentSession.availableModes`    |
| `configOptions`                    | `clientSession.configOptions` (property)                                   | 获取当前配置选项及默认值                   | `AgiAgentSession.configOptions`     |

**`AgiAgentSession` 当前提供的 `configOptions` 示例**:

| ID             | 类型      | 名称           | 当前值       | 选项                                  |
|----------------|---------|--------------|-----------|-------------------------------------|
| `auto_approve` | boolean | Auto Approve | `true`    | —                                   |
| `mode`         | select  | mode         | `Workers` | Agent / Workers                     |
| `model`        | select  | model        | 默认模型      | 从 `AiModels.availableModels()` 动态生成 |

#### 连接模式

| 模式                | 触发条件           | 实现                                                                    |
|-------------------|----------------|-----------------------------------------------------------------------|
| **WebSocket（默认）** | 未传 `--command` | Ktor `HttpClient` + `acpProtocolOnClientWebSocket()`                  |
| **外部 WebSocket**  | 传 `--ws-url`   | 直接连接指定 URL                                                            |
| **内部 WebSocket**  | 默认             | 后台 `Thread` 启动 `launchWebSocketServer(AgiAgent(), "127.0.0.1", 9988)` |
| **Stdio（回退）**     | 传 `--command`  | `ProcessBuilder` + 手动 JSON-RPC 握手                                     |

#### WebSocket 模式连接流程

1. `connectWebSocket()` → `ConnectionState.CONNECTING`
2. 启动内部服务器（如需要）→ `delay(800)`
3. `HttpClient` 连接 WebSocket
4. `Client.initialize()` → 获取 AgentInfo
5. `Client.newSession()` → 获取 `ClientSession`
6. `ConnectionState.CONNECTED`

#### Stdio 模式连接流程

1. `connectStdio()` → `ProcessBuilder` 启动子进程
2. `performHandshake()`: 发送 `initialize` → 发送 `session/new` → 提取 `sessionId`
3. `ConnectionState.CONNECTED`

#### 消息发送

| 模式        | 方法                                                                    |
|-----------|-----------------------------------------------------------------------|
| WebSocket | `clientSession.prompt(listOf(Text(content))).collect { handler(it) }` |
| Stdio     | 手动构建 `session/prompt` JSON-RPC 请求，`sendRaw()`                         |

#### 事件收集

- **WebSocket**: 事件通过 `Flow.collect()` 直接流入 `eventHandler`
- **Stdio**: `startEventCollection()` 启动后台协程，`receiveEvents()` 逐行读取子进程输出。当前实现将每行简单包装为
  `AgentMessageChunk`（**注意**：Stdio 模式的事件解析较粗糙，未完整解析 JSON-RPC 通知）

#### 断开与清理

`disconnect()` 按序执行：取消 `eventCollectorJob` → 关闭 `protocol`/`httpClient` → 中断 `serverThread` → 销毁 `process` →
清空引用 → 状态置 `DISCONNECTED`

### 6.4 `AcpEventAdapter.kt` — 事件适配器

将 Kotlin `SessionUpdate` 转为 Java `TambouiTuiApp.AcpEvent.apply(AppState)`：

| ACP 事件                | AppState 操作                                                                                |
|-----------------------|--------------------------------------------------------------------------------------------|
| `AgentMessageChunk`   | `appendStreamingContent(text)` → **增量 Markdown 渲染**，缓存到 `renderedLines`                    |
| `AgentThoughtChunk`   | `appendThoughtContent(text)`（前缀 `💭 `）                                                     |
| `ToolCallUpdate`      | **动态更新工具调用卡片**: `IN_PROGRESS` 创建/更新入参 → `COMPLETED`/`FAILED` 追加出参结果。通过 `toolCallId` 定位同一卡片 |
| `PlanUpdate`          | `clearTodos()` + 遍历 `addTodo(content, status, priority)`                                   |
| `UsageUpdate`         | `updateTokenUsage(prompt, completion, total)` 更新分项 Token 统计                                |
| `CurrentModeUpdate`   | 更新 `AppState.currentModeId`，`InfoPanelWidget` 实时刷新                                         |
| `CurrentModelUpdate`  | 更新 `AppState.currentModelId`，`InfoPanelWidget` + `StatusBarWidget` 实时刷新                    |
| `ConfigOptionsUpdate` | 更新 `AppState.configOptions`，`InfoPanelWidget` 配置区刷新                                        |

---

## 7. 数据流与线程安全

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

**线程安全策略**:

- 所有 `AppState` 修改通过 `runner.runOnRenderThread()` 排队到渲染线程
- 键盘事件天然在渲染线程处理
- ACP 回调（来自 IO 协程）通过 `runOnRenderThread()` 切换
- 状态栏定时器（后台线程）通过 `runOnRenderThread()` 触发渲染

---

## 8. 已实现功能清单

### 8.1 阶段一：核心框架 + ACP 基础通信（MVP）

| 模块        | 关键交付                                      | 实现文件                    | 状态 |
|-----------|-------------------------------------------|-------------------------|----|
| 依赖配置      | tamboui + ACP SDK 依赖就绪                    | `build.gradle.kts`      | 完成 |
| ACP 客户端   | WebSocket 模式 + Stdio 回退                   | `AcpClientManager.kt`   | 完成 |
| ACP 握手    | `initialize` + `newSession`               | `AcpClientManager.kt`   | 完成 |
| 状态管理      | `AppState` 完整实现                           | `AppState.java`         | 完成 |
| TUI 初始化   | `TuiRunner` + `EventHandler` + `Renderer` | `TambouiTuiApp.java`    | 完成 |
| 三面板布局     | `ChatPanel` + `InfoPanel` + `InputPanel`  | `layout/*.java`         | 完成 |
| 事件处理      | `KeyEvent` → `AppState` 更新                | `TambouiTuiApp.java`    | 完成 |
| 输入面板      | 键盘输入 + 基础编辑 + 多行 + 历史                     | `InputPanelWidget.java` | 完成 |
| ACP 事件处理  | 6 种事件解析 → `AppState` 更新                   | `AcpEventAdapter.kt`    | 完成 |
| ChatPanel | 消息流渲染 + 角色区分 + 滚动                         | `ChatPanelWidget.java`  | 完成 |
| 状态栏       | 连接状态 + 模型 + 时间 + 定时刷新                     | `StatusBarWidget.java`  | 完成 |
| 集成联调      | 全链路串联测试                                   | `TambouiMain.kt`        | 完成 |

### 8.2 阶段二：交互完善

| 功能                  | 说明                                   | 实现文件                                            | 状态                                        |
|---------------------|--------------------------------------|-------------------------------------------------|-------------------------------------------|
| 流式打字机效果             | 逐 token 追加 + `▌` 光标闪烁                | `ChatPanelWidget.java`                          | 完成                                        |
| 工具调用可视化             | 工具调用卡片 + 结果 + Space 展开/折叠            | `AppState.java`                                 | 完成                                        |
| 会话列表交互              | 新建/关闭/切换/弹窗选择完整逻辑                    | `AppState.java` + `SessionListPopupWidget.java` | 完成                                        |
| 输入历史 UI             | `↑/↓` 导航历史输入                         | `AppState.java`                                 | 完成                                        |
| Todo 实时更新           | `InfoPanel` Todo 对接 ACP `PlanUpdate` | `AcpEventAdapter.kt` + `InfoPanelWidget.java`   | 完成                                        |
| Token 统计            | `InfoPanel` Token 用量动态更新             | `AcpEventAdapter.kt`                            | 完成（仅 totalTokens，prompt/completion 分离待完善） |
| 焦点切换                | `Tab/Shift+Tab` 面板切换 + 边框高亮          | `TambouiTuiApp.java` + `*Widget.java`           | 完成                                        |
| 消息时间戳               | 每条消息带 `HH:mm` 时间戳                    | `ChatPanelWidget.java`                          | 完成                                        |
| 会话计数                | 状态栏显示 `当前/总计`                        | `StatusBarWidget.java`                          | 完成                                        |
| Windows Terminal 检测 | 启动时检测并提示                             | `TambouiMain.kt`                                | 完成                                        |

---

## 9. 待实现 / 待完善项

### 9.1 已知缺陷与改进点

| 问题                 | 影响                                                                                                                                                                                                                                     | 建议修复                                                                                                                                     |
|--------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------|
| **Markdown 渲染缺失**  | 当前仅纯文本按 `\n` 分行，无 Markdown 解析（标题/粗体/代码块等全部按纯文本显示）                                                                                                                                                                                      | 实现轻量级 Markdown 解析器，支持增量渲染，流式 chunk 到来时仅解析新增部分追加到 `renderedLines`                                                                         |
| **工具调用组件缺失**       | 当前仅简单文本消息，无入参/出参分离卡片，无状态动态更新（边框颜色变化），无 `toolCallId` 关联                                                                                                                                                                                 | 新增 `ToolCallWidget`，`ChatMessage` 增加 `toolCallId` 字段，通过 `toolCallId` 匹配同一工具的多次 `ToolCallUpdate`                                          |
| **面板宽度未调整**        | 当前 `chatWidth = (int) (width * 0.65)`，InfoPanel 占 35%                                                                                                                                                                                  | 改为 `chatWidth = (int) (width * 0.75)`，InfoPanel 占 25%                                                                                    |
| **ACP 模型/配置未接入**   | `AppState` 无 `currentModelId`/`currentModeId`/`configOptions`/`availableModels` 字段；`AcpClientManager` 无 `setModel`/`setMode`/`setConfigOption` 方法；`AcpEventAdapter` 未处理 `CurrentModeUpdate`/`CurrentModelUpdate`/`ConfigOptionsUpdate` | 扩展 `AppState` 和 `AcpEventAdapter`，在 `AcpClientManager` 中暴露 `setModel`/`setMode`/`setConfigOption`，在 `SessionListPopupWidget` 中增加模型/模式下拉框 |
| **Stdio 模式事件解析粗糙** | （暂时不修复） 仅将每行简单包装为 `AgentMessageChunk`，未正确解析 JSON-RPC 通知，ToolCall/Todo/Usage 等事件无法显示                                                                                                                                                    | 实现完整 NDJSON 解析器，按 `method` 字段分发到对应 `SessionUpdate` 类型                                                                                    |
| **Token 分离统计未接入**  | `UsageUpdate` 仅更新 `totalTokens`，`promptTokens` 和 `completionTokens` 始终为 0                                                                                                                                                              | 确认 ACP SDK `UsageUpdate` 是否包含分项数据，或按事件类型累加计算                                                                                             |
| **输入光标不可见**        | 当前仅渲染文本，无可见光标指示                                                                                                                                                                                                                        | tamboui 提供光标 API 时接入，或手动在光标位置渲染反色字符                                                                                                      |
| **多行输入光标移动**       | `←/→` 仅简单增减 `inputCursorPos`，未处理跨行边界                                                                                                                                                                                                   | 实现二维光标位置管理（`cursorLine` + `cursorCol`）                                                                                                   |
| **消息复制**           | 无选中/复制功能                                                                                                                                                                                                                               | 实现消息索引选择 + 内容复制到剪贴板                                                                                                                      |
| **代码块复制**          | 无代码块识别与复制                                                                                                                                                                                                                              | 解析 Markdown 代码块标记，渲染时添加复制提示                                                                                                              |
| **会话自动命名**         | 始终为 `"New Session"`                                                                                                                                                                                                                    | 基于首条用户消息截取前 20 字符作为会话名                                                                                                                   |
| **会话上限**           | 无上限检查                                                                                                                                                                                                                                  | 在 `newSession()` 中添加 `sessions.size() >= 50` 的提示或拦截                                                                                      |
| **Agent 进程崩溃自动重连** | 当前仅断开，无自动重连逻辑                                                                                                                                                                                                                          | 在 `onDisconnected/onError` 中启动指数退避重连定时器                                                                                                  |
| **费用计算**           | `TokenUsage.costUsd` 始终为 0.0                                                                                                                                                                                                           | 接入模型单价表，按 `totalTokens` 估算费用                                                                                                             |
| **上下文占用百分比**       | PRD 要求显示上下文窗口百分比                                                                                                                                                                                                                       | 接入模型最大上下文参数，计算并显示百分比                                                                                                                     |
| **输入字符计数**         | PRD 要求右下角显示字符数                                                                                                                                                                                                                         | 在 `InputPanelWidget` 右下角渲染 `inputBuffer.length()`                                                                                        |
| **粘贴支持**           | `Ctrl+V` / `Shift+Insert` 未处理                                                                                                                                                                                                          | 接入终端 bracketedPaste 或手动处理粘贴事件                                                                                                            |

### 9.2 PRD 阶段三/四待实现功能

| 功能                     | PRD 优先级 | 当前状态                                          |
|------------------------|---------|-----------------------------------------------|
| **Markdown 增量渲染**      | P0      | 未实现。当前纯文本按 `\n` 分行                            |
| **工具调用组件（入参/出参/状态更新）** | P0      | 未实现。当前为简单文本消息                                 |
| **ACP 模型/模式/配置展示与切换**  | P0      | 未实现。`AppState` 无相关字段，`AcpClientManager` 无相关方法 |
| 命令面板（Ctrl+P 模糊搜索命令）    | P2      | 未实现。当前 Ctrl+P 为会话列表 + 模型切换                    |
| 主题切换（暗色/亮色）            | P2      | 未实现。当前仅 `modernDark()`                        |
| 会话持久化（本地 JSON 存储）      | P2      | 未实现                                           |
| 外部 WebSocket 远程连接      | P2      | 参数已支持，待完善文档和测试                                |
| 自定义快捷键                 | P3      | 未实现                                           |
| 错误处理与自动重连              | P2      | 仅基础断开状态，无重连逻辑                                 |
| GraalVM 原生镜像           | P3      | 未实现                                           |
| 多语言国际化                 | P3      | 未实现                                           |
| 用户文档                   | P2      | 未实现                                           |

---

## 10. 关键技术决策记录 (ADR)

### ADR-1: Java 写 UI，Kotlin 写桥接

**决策**: UI 层使用 Java 17，ACP 通信层使用 Kotlin。

**理由**: tamboui 的 Java API 更成熟；ACP SDK 为 Kotlin 优先，协程和 Flow 表达更自然；通过 `TambouiTuiApp.AcpBridge` 接口解耦。

### ADR-2: 单一全局可变状态（AppState）

**决策**: 使用 public 字段的直接修改模式，而非不可变状态快照。

**理由**: TUI 为单线程渲染模型，所有状态修改通过 `runOnRenderThread()` 串行化；避免不可变对象的高频创建开销；代码更简洁。

**风险**: 若未来引入多线程后台任务，需确保所有回调切回渲染线程。

### ADR-3: WebSocket 为默认连接模式

**决策**: 默认启动内部 WebSocket 服务器，Stdio 为显式回退。

**理由**: WebSocket 模式通过 ACP SDK 获得完整类型安全的事件流；Stdio 模式需手动维护 JSON-RPC 协议，容易出错；内部服务器模式对终端用户透明。

### ADR-4: 工具结果自动截断（500 字符）

**决策**: `addToolResult()` 将超过 500 字符的结果截断。

**理由**: 防止大文件读取等工具结果撑爆 ChatPanel 滚动区域。

**待改进**: 保留完整内容到字段，`isExpanded` 时显示原始内容而非截断版。

---

## 11. 构建与运行

### 11.1 入口点

```kotlin
// TambouiMain.kt
fun main(args: Array<String>) {
    detectWindowsTerminal()
    val app = TambouiTuiApp()
    val bridge = TambouiAcpBridge(app.appState)
    app.setAcpBridge(bridge)
    app.start()
}
```

### 11.2 CLI 参数

| 参数                        | 说明                          | 示例                              |
|---------------------------|-----------------------------|---------------------------------|
| `--command <cmd...>`      | Stdio 模式：启动 Agent 子进程       | `--command java -jar agent.jar` |
| `--ws-url <url>`          | 连接外部 WebSocket Agent        | `--ws-url ws://host:9988/acp`   |
| `--ws-server-port <port>` | 内部 WebSocket 服务器端口（默认 9988） | `--ws-server-port 9999`         |

### 11.3 运行方式

```bash
# Gradle 运行
./gradlew :library:runTui

# 或指定参数
./gradlew :library:runTui --args="--command java -jar agent.jar"
```
