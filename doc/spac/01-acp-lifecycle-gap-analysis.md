# AcpClientManager ACP 生命周期缺口分析与增强方案

> 基于 ACP SDK（kotlin-sdk）源码研读，对比 `AcpClientManager.kt` 当前实现，分析生命周期缺口并提出增强方案。

## 1. 概述

本文档基于对 ACP Kotlin SDK 和 Tamboui TUI 框架的源码研读，分析现有 `AcpClientManager` 在 ACP 生命周期支持方面的缺口，并提出增强方案。

## 2. ACP SDK 核心生命周期分析

### 2.1 三层架构

ACP SDK 的生命周期由三层构成：

```
┌─────────────────────────────────────────────┐
│  Client (com.agentclientprotocol.client)     │
│  - 连接级生命周期管理                         │
│  - initialize / authenticate / logout         │
│  - newSession / loadSession / forkSession     │
│  - resumeSession / listSessions               │
│  - NES Session 管理                           │
│  - Provider 管理 (list/set/disable)           │
├─────────────────────────────────────────────┤
│  ClientSession (interface)                    │
│  - 会话级生命周期管理                         │
│  - prompt / cancel / close                    │
│  - setMode / setModel / setConfigOption       │
│  - 状态流: currentMode / currentModel         │
│    / configOptions (均为 StateFlow)           │
├─────────────────────────────────────────────┤
│  Protocol (com.agentclientprotocol.protocol)  │
│  - 传输级生命周期管理                         │
│  - start / close                              │
│  - 请求/响应/通知路由                         │
│  - 取消请求 (CancelRequest)                   │
│  - 超时管理 (gracefulRequestCancellation)     │
└─────────────────────────────────────────────┘
```

### 2.2 Client 生命周期方法

| 方法 | 状态 | 说明 |
|------|------|------|
| `initialize(clientInfo)` | ✅ Stable | 初始化连接，返回 AgentInfo |
| `authenticate(methodId)` | ✅ Stable | 认证 |
| `logout()` | ⚠️ Unstable | 登出 |
| `newSession(params, factory)` | ✅ Stable | 创建新会话 |
| `loadSession(id, params, factory)` | ✅ Stable | 加载已有会话 |
| `forkSession(id, params, factory)` | ⚠️ Unstable | 分支会话 |
| `resumeSession(id, params, factory)` | ⚠️ Unstable | 恢复会话 |
| `listSessions()` | ⚠️ Unstable | 列出会话 |
| `startNesSession()` | ⚠️ Unstable | NES 会话 |
| `listProviders()` | ⚠️ Unstable | 列出 Provider |
| `setProvider()` | ⚠️ Unstable | 设置 Provider |
| `disableProvider()` | ⚠️ Unstable | 禁用 Provider |

### 2.3 ClientSession 生命周期方法

| 方法 | 状态 | 说明 |
|------|------|------|
| `prompt(content)` | ✅ Stable | 发送消息，返回 Event Flow |
| `cancel()` | ✅ Stable | 取消当前 turn |
| `close()` | ⚠️ Unstable | 关闭会话 |
| `setMode(modeId)` | ✅ Stable | 设置模式 |
| `setModel(modelId)` | ⚠️ Unstable | 设置模型 |
| `setConfigOption(id, value)` | ⚠️ Unstable | 设置配置选项 |

### 2.4 Protocol 生命周期方法

| 方法 | 状态 | 说明 |
|------|------|------|
| `start()` | ✅ Core | 启动协议处理 |
| `close()` | ✅ Core | 关闭协议 |
| `sendRequestRaw()` | ✅ Core | 发送请求 |
| `sendNotificationRaw()` | ✅ Core | 发送通知 |
| `cancelPendingIncomingRequests()` | ✅ Core | 取消所有入站请求 |
| `cancelPendingOutgoingRequests()` | ✅ Core | 取消所有出站请求 |

### 2.5 Event 模型

```kotlin
sealed class Event {
    class SessionUpdateEvent(val update: SessionUpdate) : Event()
    class PromptResponseEvent(val response: PromptResponse) : Event()
}
```

`ClientSession.prompt()` 返回 `Flow<Event>`，这是 ACP 事件流的核心消费方式。

### 2.6 ClientSessionOperations 接口

客户端必须实现的回调接口，包含：
- `requestPermissions()` - 权限请求
- `notify()` - 非绑定通知
- 文件系统操作 (fsReadTextFile / fsWriteTextFile)
- 终端操作 (terminalCreate / terminalKill / terminalOutput / terminalRelease / terminalWaitForExit)
- Elicitation 操作 (createElicitation / completeElicitation)


## 3. 现有 AcpClientManager 分析

### 3.1 当前架构

```
AcpClientManager
├── WebSocket 模式
│   ├── HttpClient → Protocol → Client → ClientSession
│   └── 内部 WebSocket Server (launchWebSocketServer)
├── Stdio 模式 (向后兼容)
│   ├── Process → BufferedReader
│   └── 手动 JSON-RPC 握手
└── 状态管理
    ├── ConnectionState (DISCONNECTED/CONNECTING/CONNECTED/RECONNECTING/DISCONNECTED_ERROR)
    └── sessionId
```

### 3.2 已实现的功能

- ✅ `connect(config)` - 连接（WebSocket + Stdio）
- ✅ `sendPrompt(content)` - 发送消息
- ✅ `sendCancel()` - 取消
- ✅ `disconnect()` - 断开
- ✅ `setModel()` / `setMode()` / `setConfigOption()` - 配置
- ✅ `startEventCollection()` - 事件收集
- ✅ `receiveEvents()` - 事件流（Stdio）
- ✅ `availableModels` / `availableModes` / `configOptions` - 查询

## 4. ACP 能力覆盖全景

### 4.1 连接层 (Protocol/Transport)

| 能力 | SDK 提供 | AcpClientManager 覆盖 |
|------|----------|----------------------|
| WebSocket 传输 | `WebSocketTransport` | ✅ 已实现 |
| Stdio 传输 | `StdioTransport` | ✅ 已实现 |
| Protocol 生命周期(start/close) | `Protocol` | ✅ 已实现 |
| 请求/响应处理 | `RpcMethodsOperations` | ⚠️ 部分(手动 JSON) |
| 取消请求 | `CancelRequest` | ❌ 未使用 SDK 机制 |

### 4.2 客户端初始化 (Client.initialize)

| 能力 | SDK 提供 | AcpClientManager 覆盖 |
|------|----------|----------------------|
| initialize(clientInfo) → AgentInfo | ✅ | ✅ 已实现(返回值缓存为 `agentInfo` 字段，供认证/Provider 流程复用) |
| availableAuthMethods | 派生自 `AgentInfo.authMethods` | ✅ 已暴露(`availableAuthMethods` 只读属性) |
| authenticate(methodId) | ✅ | ✅ 已实现(基于 SDK `Client.authenticate`) |
| authenticate(provider, token) | — | ✅ 已实现(便捷重载，按 `provider` 在 `availableAuthMethods` 中匹配首个命中项；`token` 仅保留签名，遵循 ACP 标准协议 token 由 Agent 端在 method 子流程中收集) |
| logout() | ✅ | ✅ 已实现(Unstable，调用 SDK `Client.logout()`) |
| listProviders() | ✅ | ✅ 已实现(Unstable，调用 SDK `Client.listProviders()`) |
| setProvider() | ✅ | ✅ 已实现(Unstable，调用 SDK `Client.setProvider(id, apiType, baseUrl, headers?)`) |
| disableProvider() | ✅ | ✅ 已实现(Unstable，调用 SDK `Client.disableProvider(id)`) |

### 4.3 会话生命周期 (Session)

| 能力 | SDK 提供 | AcpClientManager 覆盖 |
|------|----------|----------------------|
| newSession() | ✅ | ✅ 已实现 |
| loadSession() | ✅ | ✅ 已实现(包装) |
| forkSession() | ✅ | ✅ 已实现(包装) |
| resumeSession() | ✅ | ✅ 已实现(包装) |
| listSessions() (Flow) | ✅ | ❌ 未实现(仅本地列表) |
| session.close() | ✅ | ✅ 已实现 |
| session.cancel() | ✅ | ✅ 已实现 |
| session.prompt() → Flow<Event> | ✅ | ✅ 已实现 |
| 多会话管理 | 内部支持 | ✅ 已实现(本地 map) |

> 现状注记（2026-01）：`loadSession` / `forkSession` / `resumeSession` / `switchSession` / `closeSessionById` 在 `AcpClientManager` 与 `TambouiAcpBridge` 中均已实现，`TuiApp.AcpBridge` 接口也声明了对应 `default` 方法，但 TUI Java 侧尚无任何调用点（`grep acpBridge\.|loadSession|resumeSession|switchSession` 在 `library/src/jvmMain/java` 下 0 命中）。`listSessions()` 仍待实现（本次未做）。

### 4.4 会话配置 (Session Config)

| 能力 | SDK 提供 | AcpClientManager 覆盖 |
|------|----------|----------------------|
| setMode() | ✅ | ✅ 已实现 |
| setModel() | ✅ | ✅ 已实现 |
| setConfigOption() | ✅ | ✅ 已实现 |
| currentMode StateFlow | ✅ | ✅ 已暴露 |
| currentModel StateFlow | ✅ | ✅ 已暴露 |
| configOptions StateFlow | ✅ | ✅ 已暴露 |
| modesSupported | ✅ | ❌ 未暴露 |
| modelsSupported | ✅ | ❌ 未暴露 |
| configOptionsSupported | ✅ | ❌ 未暴露 |

### 4.5 事件系统 (Event)

| 能力 | SDK 提供 | AcpClientManager 覆盖 |
|------|----------|----------------------|
| Event sealed class | ✅ (2 子类型) | ✅ 全部覆盖（`SessionUpdateEvent` + `PromptResponseEvent`） |
| Event.SessionUpdateEvent | ✅ | ✅ 已处理（`AcEventAdapter` 覆盖 12 子类型 + 1 兜底） |
| Event.PromptResponseEvent | ✅ | ✅ 已完整覆盖（`PromptResponseEventAdapter`） |
| PromptResponse.stopReason (StopReason enum) | ✅ 5 值 | ✅ 已用（`setStopReason`） |
| PromptResponse.userMessageId (Unstable) | ✅ | ✅ 已用（`AppState.lastUserMessageId`） |
| PromptResponse.usage (Unstable) | ✅ | ✅ 已用（`AppState.setTokenUsage` 一站式写入） |
| ├─ Usage.inputTokens | ✅ | ✅ 已用（→ `TokenUsage.promptTokens`） |
| ├─ Usage.outputTokens | ✅ | ✅ 已用（→ `TokenUsage.completionTokens`） |
| ├─ Usage.totalTokens | ✅ | ✅ 已用（→ `TokenUsage.totalTokens`） |
| ├─ Usage.thoughtTokens (Unstable) | ✅ | ✅ 已用（→ `TokenUsage.thoughtTokens`） |
| ├─ Usage.cachedReadTokens (Unstable) | ✅ | ✅ 已用（→ `TokenUsage.cachedReadTokens`） |
| └─ Usage.cachedWriteTokens (Unstable) | ✅ | ✅ 已用（→ `TokenUsage.cachedWriteTokens`） |
| SessionUpdate.UsageUpdate.used (Unstable) | ✅ | ✅ 已用（→ `TokenUsage.totalTokens`） |
| SessionUpdate.UsageUpdate.size (Unstable) | ✅ | ✅ 已用（→ `TokenUsage.contextWindowSize`） |
| SessionUpdate.UsageUpdate.cost (Unstable) | ✅ | ✅ 已用（→ `TokenUsage.costUsd` + `costCurrency`） |
| _meta 字段（协议扩展） | ✅ | ❌ 未用（架构层面；可作未来增强） |

### 4.6 NES (Next Edit Suggestions)

| 能力 | SDK 提供 | AcpClientManager 覆盖 |
|------|----------|----------------------|
| startNesSession() | ✅ | ❌ 未实现 |
| getNesSession() | ✅ | ❌ 未实现 |
| NES suggest/accept/reject | ✅ | ❌ 未实现 |
| Document didOpen/Change/Close | ✅ | ❌ 未实现 |

### 4.7 Elicitation (不稳定 API)

| 能力 | SDK 提供 | AcpClientManager 覆盖 |
|------|----------|----------------------|
| ElicitationOperations | ✅ | ⚠️ 基础实现(TuiClientOperations) |
| createElicitation | ✅ | ✅ 基础实现 |
| completeElicitation | ✅ | ✅ 基础实现 |
| ElicitationSessionStore | ✅ | ❌ 未使用 |
| GlobalElicitationHandler | ✅ | ❌ 未实现 |

### 4.8 客户端操作 (ClientSessionOperations)

| 能力 | SDK 提供 | AcpClientManager 覆盖 |
|------|----------|----------------------|
| requestPermissions | ✅ | ✅ 已实现(自动批准) |
| notify | ✅ | ✅ 已实现(空实现) |
| fsReadTextFile | ✅ | ✅ 已实现 |
| fsWriteTextFile | ✅ | ✅ 已实现 |
| terminalCreate | ✅ | ✅ 已实现 |
| terminalKill | ✅ | ✅ 已实现 |
| terminalOutput | ✅ | ✅ 已实现 |
| terminalRelease | ✅ | ✅ 已实现 |
| terminalWaitForExit | ✅ | ✅ 已实现 |

## 5. 生命周期缺口详细分析

### 5.1 认证生命周期缺口

ACP SDK 的 `Client` 类提供了完整的认证生命周期：
- `authenticate(methodId)` — 执行认证
- `logout()` — 登出
- `listProviders()` — 列出可用 provider
- `setProvider()` — 配置 provider
- `disableProvider()` — 禁用 provider

当前 `AcpClientManager` 仅通过 `setConfigOption` 模拟认证，未使用 SDK 的标准认证流程。

**影响**：无法使用 ACP 协议的标准认证机制，当 Agent 要求认证时会失败。

### 5.2 NES 生命周期缺口

ACP SDK 提供了完整的 NES (Next Edit Suggestions) 支持：
- `startNesSession()` — 启动 NES 会话
- `getNesSession()` — 获取已有 NES 会话
- `suggest()` / `accept()` / `reject()` — 建议生命周期
- `didOpen()` / `didChange()` / `didClose()` / `didSave()` / `didFocus()` — 文档事件

当前 `AcpClientManager` 完全没有 NES 支持。

**影响**：无法使用 ACP 的代码建议功能。

### 5.3 会话发现缺口

ACP SDK 的 `Client.listSessions()` 返回一个冷 `Flow<SessionInfo>`，支持分页查询 Agent 端的所有会话。

当前 `AcpClientManager` 的 `sessionList` / `activeSessionIds` 仅返回本地缓存的会话 ID 列表，无法发现 Agent 端的远程会话。

**影响**：无法列出 Agent 端已存在的远程会话，`loadSession` 需要用户预先知道 sessionId。

### 5.4 Elicitation 处理缺口

ACP SDK 的 `ElicitationSessionStore` 提供了从 ElicitationId 到 SessionId 的映射管理，支持自动淘汰和按 session 批量清理。

当前 `TuiClientOperations` 仅实现了基础的 `createElicitation`（自动 Accept）和 `completeElicitation`（空实现），未使用 `ElicitationSessionStore`。

**影响**：无法正确处理 URL 模式的 elicitation 完成回调。

### 5.5 协议层能力缺口

ACP SDK 的 `Protocol` 提供了：
- `cancelPendingIncomingRequests()` — 取消所有正在处理的请求
- `cancelPendingOutgoingRequests()` — 取消所有等待响应的请求
- `getOutgoingRequestSessionId()` — 获取请求关联的会话 ID

当前 `AcpClientManager` 未暴露这些协议层能力。

**影响**：无法在断开连接时优雅地取消进行中的请求。

### 5.6 生命周期状态细化

当前 `AcpLifecycleState` 定义了 6 个状态：
```
CREATED → CONNECTING → INITIALIZED → SESSION_ACTIVE → DISCONNECTED → DESTROYED
```

建议考虑增加以下状态以覆盖更多场景：
- `AUTHENTICATING` — 正在认证
- `AUTHENTICATED` — 已认证
- `RECONNECTING` — 正在重连（与 CONNECTING 区分）
- `ERROR` — 错误状态（与 DISCONNECTED 区分）

## 6. 缺口汇总与优先级

### 6.1 缺口汇总表

| 缺口 | 严重性 | 说明 |
|------|--------|------|
| ❌ 缺少 `authenticate()` | 🔴 High | 无法处理需要认证的 Agent |
| ❌ 缺少 `logout()` | 🟡 Medium | 无法登出 |
| ❌ 缺少 `session.close()` | 🔴 High | 无法优雅关闭会话 |
| ❌ 缺少 `loadSession()` | 🟡 Medium | 无法恢复已有会话 |
| ❌ 缺少 `forkSession()` | 🟢 Low | 无法分支会话 (Unstable) |
| ❌ 缺少 `resumeSession()` | 🟡 Medium | 无法恢复会话 (Unstable) |
| ❌ 缺少 `listSessions()` | 🟢 Low | 无法列出会话 (Unstable) |
| ❌ 缺少重连机制 | 🔴 High | 连接断开后不会自动重连 |
| ❌ 缺少生命周期监听器 | 🔴 High | 外部无法监听状态变化 |
| ❌ 缺少错误恢复策略 | 🟡 Medium | 错误后无法自动恢复 |
| ❌ 缺少 Provider 管理 | 🟢 Low | 无法管理 Provider (Unstable) |
| ❌ 缺少 NES Session | 🟢 Low | 无法创建 NES 会话 (Unstable) |
| ❌ 缺少 Elicitation 处理 | 🟡 Medium | 未实现 Elicitation 回调 |
| ❌ 缺少 PromptResponse 处理 | 🟡 Medium | 未消费 PromptResponseEvent |
| ❌ `disconnect()` 未调用 session.close() | 🟡 Medium | 断开时未通知 Agent |
| ❌ 缺少 Protocol 级错误处理 | 🟡 Medium | Protocol 错误未捕获 |

### 6.2 优先级排序

| 优先级 | 缺口 | 理由 |
|--------|------|------|
| P0 | 认证生命周期 | 影响基本连接可用性 |
| P1 | 会话发现(listSessions) | 影响多会话管理完整性 |
| P2 | 协议层能力暴露 | 影响断开连接的优雅性 |
| P3 | NES 支持 | 新功能，非阻塞 |
| P4 | Elicitation 增强 | 不稳定 API，低优先级 |
| P5 | 状态细化 | 优化体验，非功能性 |

## 7. 增强方案设计

### 7.1 设计目标

1. **完整覆盖 ACP 生命周期**：支持所有 Stable 和关键 Unstable API
2. **事件驱动架构**：基于 Kotlin Flow 和 StateFlow 的状态管理
3. **自动重连**：可配置的自动重连策略
4. **错误恢复**：分级错误恢复机制
5. **可扩展性**：插件式的生命周期监听器
6. **向后兼容**：不破坏现有 API

### 7.2 架构设计

```
AcpClientManager (增强版)
├── 连接层 (ConnectionLayer)
│   ├── WebSocketConnector
│   ├── StdioConnector
│   └── ReconnectStrategy
├── 会话层 (SessionLayer)
│   ├── SessionManager (多会话支持)
│   ├── SessionLifecycle
│   └── SessionConfigManager
├── 事件层 (EventLayer)
│   ├── EventBus (Flow-based)
│   ├── EventDispatcher
│   └── EventCollector
├── 生命周期层 (LifecycleLayer)
│   ├── LifecycleRegistry
│   ├── LifecycleListener
│   └── StateMachine
└── 配置层 (ConfigLayer)
    ├── ProviderManager
    └── AuthManager
```

### 7.3 状态机设计

```
         ┌──────────┐
         │  CREATED  │
         └────┬─────┘
              │ connect()
         ┌────▼──────┐
         │ CONNECTING │◄────────────┐
         └────┬───────┘            │
              │ initialize()       │
         ┌────▼──────┐             │
         │INITIALIZED│             │
         └────┬──────┘             │
              │ newSession()       │
         ┌────▼──────┐             │
         │ SESSION   │             │
         │  ACTIVE   │             │
         └────┬──────┘             │
              │ disconnect()       │
              │ 或 error           │
         ┌────▼──────┐             │
         │DISCONNECTED├──auto──────┘
         └────┬──────┘
              │ destroy()
         ┌────▼──────┐
         │ DESTROYED │
         └───────────┘
```

### 7.4 新增 API 设计

```kotlin
class AcpClientManager(private val appState: AppState) {
    // === 生命周期状态 ===
    val lifecycleState: StateFlow<AcpLifecycleState>
    val lifecycleEvents: Flow<AcpLifecycleEvent>
    
    // === 认证 ===
    suspend fun authenticate(methodId: AuthMethodId): Result<AuthenticateResponse>
    suspend fun logout(): Result<Unit>
    
    // === 会话管理 ===
    suspend fun loadSession(sessionId: SessionId): Result<ClientSession>
    suspend fun forkSession(sessionId: SessionId): Result<ClientSession>
    suspend fun resumeSession(sessionId: SessionId): Result<ClientSession>
    suspend fun closeSession(): Result<Unit>
    fun listSessions(): Flow<SessionInfo>
    
    // === 重连 ===
    suspend fun reconnect(): Result<Unit>
    fun setReconnectStrategy(strategy: ReconnectStrategy)
    
    // === 生命周期监听 ===
    fun addLifecycleListener(listener: AcpLifecycleListener)
    fun removeLifecycleListener(listener: AcpLifecycleListener)
    
    // === Provider 管理 (Unstable) ===
    suspend fun listProviders(): Result<ListProvidersResponse>
    suspend fun setProvider(id: String, apiType: LlmProtocol, baseUrl: String): Result<Unit>
    suspend fun disableProvider(id: String): Result<Unit>
    
    // === 增强的事件处理 ===
    suspend fun sendPrompt(content: String): Flow<Event>  // 返回 Flow 而非 Unit
}
```

### 7.5 重连策略

```kotlin
sealed class ReconnectStrategy {
    data class FixedInterval(val intervalMs: Long) : ReconnectStrategy()
    data class ExponentialBackoff(
        val initialIntervalMs: Long = 1000,
        val maxIntervalMs: Long = 30000,
        val multiplier: Double = 2.0
    ) : ReconnectStrategy()
    data class Custom(val strategy: suspend (attempt: Int) -> Long) : ReconnectStrategy()
    object NoReconnect : ReconnectStrategy()
}
```

### 7.6 生命周期事件

```kotlin
sealed class AcpLifecycleEvent {
    object Connecting : AcpLifecycleEvent()
    data class Connected(val agentInfo: AgentInfo) : AcpLifecycleEvent()
    data class SessionCreated(val sessionId: SessionId) : AcpLifecycleEvent()
    object SessionClosed : AcpLifecycleEvent()
    data class Disconnected(val reason: DisconnectReason) : AcpLifecycleEvent()
    data class Reconnecting(val attempt: Int) : AcpLifecycleEvent()
    data class Error(val throwable: Throwable) : AcpLifecycleEvent()
    object Destroyed : AcpLifecycleEvent()
}
```

## 8. 实施路线图

### Phase 1: 核心增强 (高优先级 / 已完成)
- ✅ 基础生命周期状态机
- ✅ 生命周期事件/监听器
- ✅ 重连策略
- ✅ 多会话管理基础

### Phase 2: 会话管理增强 (中优先级 / 当前)
- 多会话增强(load/fork/resume/switch)
- PromptResponseEvent 支持
- Elicitation 基础支持

### Phase 3: 高级功能 (低优先级 / 建议)
- 认证生命周期(authenticate/logout)
- 会话发现(listSessions)
- 协议层能力暴露

### Phase 4: 未来
- NES 支持
- Elicitation 完整支持
- 生命周期状态细化
- Provider 管理
- NES Session 支持
- 完整的错误恢复策略

## 9. 兼容性说明

- 所有现有 API 保持向后兼容
- Stdio 模式保持可用但不再增强
- 新增 API 默认使用 WebSocket 模式
- 重连机制默认启用 (ExponentialBackoff)

## 10. TUI 框架设计模式参考

### 10.1 Tamboui 核心模式

Tamboui 框架（Java）使用以下设计模式：
- **Widget 树**：组件化 UI 树
- **Backend 抽象**：终端后端抽象层
- **Layout 系统**：Cassowary 约束求解布局
- **事件驱动**：基于帧的渲染循环

### 10.2 可借鉴的设计

1. **生命周期回调模式**：Backend 接口的生命周期方法
2. **状态管理**：StatefulWidget 的状态管理
3. **错误处理**：统一的异常层次 (TamboUIException)
4. **配置管理**：Options 模式
