# XAgent TUI 实现计划与进度跟踪

> **关联文档**: [TUI_PRD.md](./TUI_PRD.md)  
> **版本**: v2.1  
> **更新日期**: 2025-07-17  
> **状态**: 开发中

---
## 实施说明:
 实施过程中对于 mordant 和acp 协议不明确的地方 直接查看其源码：
 mordant：  E:\local-github\mordant
 acp ：  E:\local-github\kotlin-sdk
## 阶段一：核心框架 + ACP 基础通信（MVP）

> **目标**: 可用的最小闭环 TUI —— 启动 → 输入 → Agent 回复 → 显示  
> **建议实施顺序**: 按编号顺序，子步骤编号前的 `1.x.y` 表示所属主任务

### 1.1 依赖配置与项目基础设施 ✅

| 编号 | 子任务 | 涉及文件 | 状态 | 实际完成情况 |
|------|--------|----------|------|-------------|
| 1.1.1 | `libs.versions.toml` 添加 mordant 版本号 | `gradle/libs.versions.toml` | ✅ COMPLETED | `mordant = "3.0.2"` 已定义 |
| 1.1.2 | `build.gradle.kts` 添加 mordant 依赖 | `library/build.gradle.kts` | ✅ COMPLETED | 已添加 mordant、mordant-coroutines、mordant-markdown、mordant-jvm-jna |
| 1.1.3 | 创建 TUI 源码目录及包结构 | `library/src/jvmMain/kotlin/com/xr21/ai/agent/tui/` | ✅ COMPLETED | 已创建 `acp/ config/ event/ layout/ state/ util/` 6个子包19个文件 |
| 1.1.4 | 创建 Gradle run task `runTui` | `library/build.gradle.kts` | ✅ COMPLETED | `mainClass = "com.xr21.ai.agent.tui.MainKt"` 已注册 |

**检查点**: `./gradlew :library:runTui` 能启动一个空的 main 方法 ✅

---

### 1.2 TUI 配置模块与 CLI 参数解析 ✅

| 编号 | 子任务 | 涉及文件 | 状态 | 实际完成情况 |
|------|--------|----------|------|-------------|
| 1.2.1 | 定义 `TuiConfig` 数据类 | `tui/config/TuiConfig.kt` | ✅ COMPLETED | 布局比例、颜色方案(12色)、输入/状态栏高度、消息/会话上限、自动重连等配置 |
| 1.2.2 | 实现 CLI 参数解析（简单手动解析） | `tui/Main.kt` | ✅ COMPLETED | 支持 `--command`、`--help` 参数解析 |
| 1.2.3 | 实现 Windows Terminal 检测逻辑 | `tui/Main.kt` | ⬜ PENDING | 旧 `XAgentTui.kt` 有但已删除，需在新入口重新实现 |

**输出**: `Main.main()` 能解析 CLI 参数生成 `TuiConfig` 实例 ✅

---

### 1.3 ACP Client 管理器 — Stdio Transport ✅

| 编号 | 子任务 | 涉及文件 | 状态 | 实际完成情况 |
|------|--------|----------|------|-------------|
| 1.3.1 | 定义 `ConnectionState` 枚举 | `tui/acp/ConnectionState.kt` | ✅ COMPLETED | 5种状态：DISCONNECTED/CONNECTING/CONNECTED/RECONNECTING/DISCONNECTED_ERROR |
| 1.3.2 | 实现 `AcpClientManager` 类骨架 | `tui/acp/AcpClientManager.kt` | ✅ COMPLETED | 含 connect/sendMessage/receiveEvents(Flow)/disconnect/interrupt 方法 |
| 1.3.3 | 实现 `startStdio()` — 启动 Agent 子进程 | `tui/acp/AcpClientManager.kt` | ✅ COMPLETED | NDJSON 协议通信，JSON-RPC 风格的 initialize/session/new/session/prompt 请求，支持握手流程 |
| 1.3.4 | 实现 `connectWebSocket()` 骨架 | `tui/acp/AcpClientManager.kt` | ⏸️ DEFERRED | 预留方法，暂未实现 |
| 1.3.5 | 实现 `disconnect()` — 关闭连接 | `tui/acp/AcpClientManager.kt` | ✅ COMPLETED | 含协程作用域清理和 eventCollectorJob 取消 |

**输出**: `AcpClientManager` 能启动 Agent 子进程，完成 ACP 握手 ✅

---

### 1.4 ACP 协议握手（initialize + newSession）✅

| 编号 | 子任务 | 涉及文件 | 状态 | 实际完成情况 |
|------|--------|----------|------|-------------|
| 1.4.1 | 实现 ACP Transport 适配 | `tui/acp/AcpClientManager.kt` | ✅ COMPLETED | 基于 ProcessBuilder Stdio 的 NDJSON 通信，手动解析 JSON-RPC 风格的请求/响应 |
| 1.4.2 | 实现 ACP initialize 握手 | `tui/acp/AcpClientManager.kt` | ✅ COMPLETED | `performHandshake()` 中发送 initialize 请求并等待响应 |
| 1.4.3 | 实现 `createClient()` 与 `Client` 实例化 | `tui/acp/AcpClientManager.kt` | ✅ COMPLETED | connect() 方法完成子进程启动和握手，通过 CoroutineScope 管理生命周期 |
| 1.4.4 | 实现 `createSession(cwd)` | `tui/acp/AcpClientManager.kt` | ✅ COMPLETED | `performHandshake()` 中发送 session/new 请求，解析返回的 sessionId |
| 1.4.5 | 存储 AgentInfo 到可观察状态 | `tui/acp/AcpClientManager.kt` | ✅ COMPLETED | 通过 appState.connectionState / agentName / modelName 存储 |

**输出**: TUI 能完成 initialize + newSession 握手 ✅ **已完成**


---

### 1.5 应用状态管理 ✅

| 编号 | 子任务 | 涉及文件 | 状态 | 实际完成情况 |
|------|--------|----------|------|-------------|
| 1.5.1 | 定义 `AppState` 数据类 | `tui/state/AppState.kt` | ✅ COMPLETED | 247行完整实现：Session/ChatMessage/TodoItem/TokenUsage 数据模型 |
| 1.5.2 | 定义 `ChatMessage` 数据类 | `tui/state/AppState.kt` | ✅ COMPLETED | data class ChatMessage：role/content/timestamp/isStreaming/metadata |
| 1.5.3 | 定义 `PanelType` 枚举 | `tui/layout/AppLayout.kt` | ✅ COMPLETED | LEFT/CENTER/RIGHT/INPUT 四面板类型 |
| 1.5.4 | 实现 `AppState` 集中管理 | `tui/state/AppState.kt` | ✅ COMPLETED | 可变对象模式：会话CRUD、输入历史、流式追加/完成、滚动、Todo管理 |

**输出**: 全局状态模型定义完成，`AppState` 可驱动状态更新 ✅

---

### 1.6 Mordant Terminal 初始化和基本渲染 ✅

| 编号 | 子任务 | 涉及文件 | 状态 | 实际完成情况 |
|------|--------|----------|------|-------------|
| 1.6.1 | 创建 `TuiApp` 主类 — 初始化 Terminal | `tui/TuiApp.kt` | ✅ COMPLETED | `Terminal()` 实例化，`enterRawMode()` 调用 |
| 1.6.2 | 实现 TUI 生命周期管理 | `tui/TuiApp.kt` | ✅ COMPLETED | `start()` 方法：init→connectAgent→render→eventLoop→cleanup 完整流程 |
| 1.6.3 | 实现终端退出后的状态恢复 | `tui/TuiApp.kt` | ⚠️ SKELETON | `cleanup()` 中调用 acpClient.disconnect()，退出原始模式待完善 |
| 1.6.4 | 全量重绘基础框架 | `tui/TuiApp.kt` | ✅ COMPLETED | `render()` 调用 `mainLayout.render()` 全量重绘，ACP 事件触发自动重绘 |

**输出**: `TuiApp.start()` 能启动终端、连接 Agent、清理退出 ✅

---

### 1.7 四分区布局引擎 ⚠️

| 编号 | 子任务 | 涉及文件 | 状态 | 实际完成情况 |
|------|--------|----------|------|-------------|
| 1.7.1 | 实现 `AppLayout` — 整体布局管理 | `tui/layout/AppLayout.kt` | ✅ COMPLETED | mordant table 三列布局，动态适配终端宽度（Windows mode con / $COLUMNS / 默认120） |
| 1.7.2 | 创建各面板的 Widget | `tui/layout/SidebarPanel.kt` `ChatPanel.kt` `InfoPanel.kt` `InputPanel.kt` `StatusBar.kt` | ✅ COMPLETED | 5个面板均实现基本渲染（含数据绑定） |
| 1.7.3 | 实现面板间焦点切换 | `tui/layout/AppLayout.kt` `tui/state/AppState.kt` | ✅ COMPLETED | Tab/Shift+Tab 焦点切换（LEFT→CENTER→RIGHT→INPUT循环），边框 DOUBLE/ROUNDED 高亮 |

**输出**: 四分区的 TUI 布局渲染完成 ⚠️（缺焦点切换和动态适配）

---

### 1.8 事件循环与键盘输入 ✅

| 编号 | 子任务 | 涉及文件 | 状态 | 实际完成情况 |
|------|--------|----------|------|-------------|
| 1.8.1 | 实现键盘原始输入监听 | `tui/event/InputHandler.kt` | ✅ COMPLETED | 111行完整实现：方向键/Home/End/PageUp/PageDown/Ctrl组合/ShiftTab |
| 1.8.2 | 定义 `KeyBinding` 映射表 | `tui/event/KeyBinding.kt` | ✅ COMPLETED | KeyEvent(25种)/Action(19种)/16个快捷键映射，Enter 也映射为 SEND_MESSAGE |
| 1.8.3 | 实现 `EventLoop` — 主事件循环 | `tui/event/EventLoop.kt` | ✅ COMPLETED | 读键→resolveAction→分发Handler循环，ACP 事件通过后台协程收集触发重绘 |
| 1.8.4 | 实现输入历史 | `tui/state/AppState.kt` | ✅ COMPLETED | inputHistoryPrev/Next 在 AppState 中完整实现 |

**输出**: 键盘事件循环运行，Enter/Ctrl+Enter 均可发送消息 ✅

---

### 1.9 输入面板实现 ✅

| 编号 | 子任务 | 涉及文件 | 状态 | 实际完成情况 |
|------|--------|----------|------|-------------|
| 1.9.1 | 实现 `InputPanel` — 输入框 | `tui/layout/InputPanel.kt` | ⚠️ SKELETON | 单行输入展示，空时占位文字，**缺多行支持和滚动** |
| 1.9.2 | 实现发送逻辑 | `tui/TuiApp.kt` | ✅ COMPLETED | `sendMessage()` 更新 AppState 后调用 `acpClient.sendPrompt()` 发送到 Agent |
| 1.9.3 | 实现中断逻辑 | `tui/TuiApp.kt` | ✅ COMPLETED | Ctrl+C 触发 cancelResponse()，调用 acpClient.sendCancel() + finishStreaming() |

**输出**: 用户可输入文本，Ctrl+Enter/Enter 发送到 Agent，Ctrl+C 中断 ✅

---

### 1.10 ACP 事件处理与消息渲染 ✅

| 编号 | 子任务 | 涉及文件 | 状态 | 实际完成情况 |
|------|--------|----------|------|-------------|
| 1.10.1 | 实现 `AcpEventProcessor` — 事件流处理 | `tui/acp/AcpEventProcessor.kt` | ✅ COMPLETED | 106行完整实现：解析text/done/error/todo/todo_status/token/agent/model共8种事件 |
| 1.10.2 | 处理流式文本 | `tui/acp/AcpEventProcessor.kt` | ✅ COMPLETED | `appendStreamingContent()` 追加到当前消息 |
| 1.10.3 | 处理思考过程 | `tui/acp/AcpEventProcessor.kt` | ⬜ PENDING | 当前事件定义未区分thought chunk |
| 1.10.4 | 处理 ToolCall 和 ToolCallUpdate | `tui/acp/AcpEventProcessor.kt` | ⬜ PENDING | 当前事件定义未包含tool_call事件 |
| 1.10.5 | 处理完成事件 | `tui/acp/AcpEventProcessor.kt` | ✅ COMPLETED | `finishStreaming()` 设置 isStreaming=false |

**输出**: Agent 回复流式显示、思考过程可见、工具调用显示卡片 ⚠️（缺thought/tool_call事件定义）

---

### 1.11 ChatPanel — 对话界面 ✅

| 编号 | 子任务 | 涉及文件 | 状态 | 实际完成情况 |
|------|--------|----------|------|-------------|
| 1.11.1 | 实现 `ChatPanel` — 消息流渲染 | `tui/layout/ChatPanel.kt` | ✅ COMPLETED | 消息列表按角色加emoji前缀渲染，流式▌光标 |
| 1.11.2 | 实现滚动 | `tui/layout/ChatPanel.kt` | ⚠️ SKELETON | `scrollOffset` 在 AppState 中实现，但 ChatPanel 未使用 Viewport |
| 1.11.3 | 实现流式文本更新 | `tui/layout/ChatPanel.kt` | ✅ COMPLETED | ACP 事件通过 startEventCollection → processEvent → render() 触发重绘 |
| 1.11.4 | 实现消息时间戳 | `tui/layout/ChatPanel.kt` | ⬜ PENDING | ChatMessage 有 timestamp 字段但未在渲染中使用 |

**输出**: 对话界面可显示用户消息和 Agent 流式回复 ✅

---

### 1.12 状态栏 ⚠️

| 编号 | 子任务 | 涉及文件 | 状态 | 实际完成情况 |
|------|--------|----------|------|-------------|
| 1.12.1 | 实现 `StatusBar` — 底部状态栏 | `tui/layout/StatusBar.kt` | ⚠️ SKELETON | Agent名+连接状态(5种图标)+模型+会话数+时间 |
| 1.12.2 | 实现状态栏定时刷新 | `tui/layout/StatusBar.kt` | ⬜ PENDING | 缺协程定时更新系统时间 |

**输出**: 底部状态栏显示连接状态和相关信息 ⚠️

---

### 1.13 集成与端到端联调 ✅

| 编号 | 子任务 | 涉及文件 | 状态 | 实际完成情况 |
|------|--------|----------|------|-------------|
| 1.13.1 | 实现 `TuiApp.start()` 完整串联 | `tui/TuiApp.kt` | ✅ COMPLETED | start() → enterRawMode → connectToAgent → render → eventLoop → cleanup 完整流程 |
| 1.13.2 | 实现发送消息的完整通路 | `tui/TuiApp.kt` | ✅ COMPLETED | 输入→AppState→acpClient.sendPrompt()→Agent 回复→acpProcessor→render() 全链路 |
| 1.13.3 | Gradle runTui 任务可正常启动和关闭 | `library/build.gradle.kts` | ✅ COMPLETED | 任务已注册，传入 projectDir 作为工作目录 |
| 1.13.4 | 基础错误处理 | `tui/` | ✅ COMPLETED | 连接失败/发送失败/中断均有错误处理和状态更新 |

**输出**: 完整的端到端 MVP TUI 可用 ✅ **已完成**

---

## 阶段二：交互完善

| 编号 | 任务 | 涉及文件 | 状态 | 备注 |
|------|------|----------|------|------|
| 2.1 | 流式输出和打字机效果 | `tui/layout/ChatPanel.kt` | ⬜ PENDING | 逐字符/逐 token 渲染，需对接 ACP 事件流触发局部重绘 |
| 2.2 | Markdown 渲染（代码块、表格等） | `tui/util/MarkdownParser.kt` | ⬜ PENDING | 骨架已完成，需集成到 ChatPanel |
| 2.3 | 工具调用的展开/折叠 | `tui/layout/ChatPanel.kt` | ⬜ PENDING | 可折叠 Panel，需扩展 ACP 事件定义 |
| 2.4 | 会话列表的新建/切换/删除 | `tui/layout/SidebarPanel.kt` | ⬜ PENDING | 骨架已完成，需完整的交互逻辑 |
| 2.5 | 输入历史和导航 | `tui/state/AppState.kt` | ⬜ PENDING | 状态层已实现，需接入 InputPanel UI |
| 2.6 | Todo List 实时更新 | `tui/layout/InfoPanel.kt` | ⬜ PENDING | 渲染已完成，需对接 ACP todo 事件 |
| 2.7 | 右侧信息面板（Token/上下文） | `tui/layout/InfoPanel.kt` | ⬜ PENDING | 骨架已完成 |
| 2.8 | 状态管理优化（全量重绘策略） | `tui/TuiApp.kt` | ⬜ PENDING | StateFlow 驱动 Widget 重建 |

**阶段二目标**: 完整的交互体验 —— 流式输出、Markdown、工具调用可视化

---

## 阶段三：高级功能

| 编号 | 任务 | 涉及文件 | 状态 | 备注 |
|------|------|----------|------|------|
| 3.1 | WebSocket 传输支持（远程连接） | `tui/acp/AcpClientManager.kt` | ⬜ PENDING | `--connect ws://...` |
| 3.2 | 命令面板（Ctrl+P） | `tui/event/` | ⬜ PENDING | 类似 VSCode 的命令面板 |
| 3.3 | 主题切换（暗色/亮色） | `tui/config/TuiConfig.kt` | ⬜ PENDING | |
| 3.4 | 快捷键绑定自定义 | `tui/event/KeyBinding.kt` | ⬜ PENDING | 可配置快捷键 |
| 3.5 | 搜索/过滤功能 | `tui/` | ⬜ PENDING | 会话搜索、内容搜索 |
| 3.6 | 会话持久化（JSON 文件存储） | `tui/state/` | ⬜ PENDING | 本地会话历史存储 |
| 3.7 | /ask /edit /run 模式切换 | `tui/layout/InputPanel.kt` | ⬜ PENDING | 输入模式切换 |
| 3.8 | 配置 UI | `tui/` | ⬜ PENDING | 设置面板 |
| 3.9 | 多 Agent 支持（低优先级） | `tui/` | ⬜ PENDING | 同时连接多个 Agent |

**阶段三目标**: 高级功能完善，提升用户体验

---

## 阶段四：优化与发布

| 编号 | 任务 | 涉及文件 | 状态 | 备注 |
|------|------|----------|------|------|
| 4.1 | 性能优化（帧率、内存） | `tui/` | ⬜ PENDING | |
| 4.2 | 终端兼容性测试 | `tui/` | ⬜ PENDING | Windows Terminal、iTerm2、Alacritty、tmux |
| 4.3 | 错误处理与恢复 | `tui/` | ⬜ PENDING | Agent 进程崩溃重连 |
| 4.4 | 用户文档 | `doc/` | ⬜ PENDING | 使用指南 |
| 4.5 | GraalVM 原生镜像支持 | `library/build.gradle.kts` | ⬜ PENDING | 低优先级 |
| 4.6 | 多语言支持 | `tui/` | ⬜ PENDING | |
| 4.7 | ACP authenticate 完整流程 | `tui/acp/` | ⬜ PENDING | 第一阶段仅回车确认 |

**阶段四目标**: 稳定可靠，可发布

---

## 进度总览

### 阶段一子任务统计 (53个)

| 状态 | 数量 | 占比 | 进度条 |
|------|:----:|:----:|:------:|
| ✅ COMPLETED | 33 | 62.3% | █████████████░░░░░░ |
| ⚠️ SKELETON | 5 | 9.4% | ██░░░░░░░░░░░░░░░░░ |
| ⬜ PENDING | 13 | 24.5% | █████░░░░░░░░░░░░░░ |
| ⏸️ DEFERRED | 1 | 1.9% | ░░░░░░░░░░░░░░░░░░░ |
| ❌ BLOCKED | 1 | 1.9% | ░░░░░░░░░░░░░░░░░░░ |

```
阶段一 [██████████████████░░]  72%  (38/53 已完成或骨架就绪)
阶段二 [░░░░░░░░░░░░░░░░░░░░]   0%  (0/8)
阶段三 [░░░░░░░░░░░░░░░░░░░░]   0%  (0/9)
阶段四 [░░░░░░░░░░░░░░░░░░░░]   0%  (0/7)
```

> **注**: 进度百分比按「已开始（COMPLETED + SKELETON）/ 总数」计算，38/53 ≈ 72%

### 已完成子任务明细 (31个)

| 子任务 | 文件 | 说明 |
|--------|------|------|
| 1.1.1 | `gradle/libs.versions.toml` | mordant 3.0.2 版本定义 |
| 1.1.2 | `library/build.gradle.kts` | 4个 mordant 依赖 |
| 1.1.3 | `tui/` 目录结构 | 6个子包19个文件 |
| 1.1.4 | `library/build.gradle.kts` | runTui Gradle 任务 |
| 1.2.1 | `tui/config/TuiConfig.kt` | 完整配置数据类 |
| 1.2.2 | `tui/Main.kt` | CLI 参数解析 |
| 1.3.1 | `tui/acp/ConnectionState.kt` | 5种连接状态枚举 |
| 1.3.2 | `tui/acp/AcpClientManager.kt` | 完整类实现（NDJSON通信） |
| 1.3.3 | `tui/acp/AcpClientManager.kt` | 子进程启动+NDJSON通信+握手 |
| 1.3.5 | `tui/acp/AcpClientManager.kt` | 含协程作用域清理 |
| 1.4.1 | `tui/acp/AcpClientManager.kt` | NDJSON JSON-RPC 协议适配 |
| 1.4.2 | `tui/acp/AcpClientManager.kt` | initialize 握手 |
| 1.4.3 | `tui/acp/AcpClientManager.kt` | Client 实例化管理 |
| 1.4.4 | `tui/acp/AcpClientManager.kt` | session/new 创建 |
| 1.4.5 | `tui/acp/AcpClientManager.kt` | AgentInfo 状态存储 |
| 1.5.1 | `tui/state/AppState.kt` | AppState 247行完整实现 |
| 1.5.2 | `tui/state/AppState.kt` | ChatMessage 数据类 |
| 1.5.3 | `tui/layout/AppLayout.kt` | PanelType 枚举 |
| 1.5.4 | `tui/state/AppState.kt` | 集中状态管理 |
| 1.6.1 | `tui/TuiApp.kt` | Terminal 初始化 |
| 1.6.2 | `tui/TuiApp.kt` | 完整生命周期（含ACP连接） |
| 1.6.4 | `tui/TuiApp.kt` | ACP事件触发自动重绘 |
| 1.7.2 | `tui/layout/*Panel.kt` | 5个面板Widget |
| 1.8.1 | `tui/event/InputHandler.kt` | 111行键盘输入解析 |
| 1.8.2 | `tui/event/KeyBinding.kt` | 完整快捷键映射 |
| 1.8.3 | `tui/event/EventLoop.kt` | 完整事件循环 |
| 1.8.4 | `tui/state/AppState.kt` | 输入历史 |
| 1.9.2 | `tui/TuiApp.kt` | 发送逻辑对接ACP |
| 1.9.3 | `tui/TuiApp.kt` | 中断逻辑(Ctrl+C) |
| 1.10.1 | `tui/acp/AcpEventProcessor.kt` | 8种ACP事件处理 |
| 1.10.2 | `tui/acp/AcpEventProcessor.kt` | 流式文本追加 |
| 1.10.5 | `tui/acp/AcpEventProcessor.kt` | 完成事件处理 |
| 1.11.1 | `tui/layout/ChatPanel.kt` | 消息流渲染 |
| 1.11.3 | `tui/layout/ChatPanel.kt` | ACP事件触发重绘 |
| 1.13.1 | `tui/TuiApp.kt` | 完整串联 |
| 1.13.2 | `tui/TuiApp.kt` | 消息完整通路 |
| 1.13.3 | `library/build.gradle.kts` | runTui任务 |
| 1.13.4 | `tui/TuiApp.kt` | 基础错误处理 |

---

## 当前聚焦

**当前阶段**: 阶段一（MVP）—— 53 个子任务中 **33 完成**、**5 骨架就绪**、**15 待办/推迟/阻塞**

**下一优先任务**:
1. ~~1.7.3 焦点切换~~ — 已完成（Tab/Shift+Tab 四面板循环 + 边框高亮）
2. **1.6.3 终端退出恢复** — exitRawMode/showCursor 完善
3. ~~1.7.1 动态列宽~~ — 已完成（Windows mode con / $COLUMNS / 默认120）
4. **1.9.1 多行输入** — 输入框多行支持
5. **阶段二任务** — 流式打字机、Markdown渲染等

---

## 状态标记说明

| 标记 | 含义 | 说明 |
|------|------|------|
| ✅ COMPLETED | 已完成 | 代码已实现并可通过编译 |
| ⚠️ SKELETON | 骨架就绪 | 基本结构完成，但缺关键实现或含 TODO |
| 🔄 IN PROGRESS | 进行中 | 正在开发中 |
| ⬜ PENDING | 未开始 | 尚未实现 |
| ❌ BLOCKED | 阻塞中 | 依赖前置任务 |
| ⏸️ DEFERRED | 推迟 | 暂不实现，留待后续阶段 |