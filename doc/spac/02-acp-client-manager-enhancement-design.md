# AcpClientManager ACP 生命周期增强设计文档

## 1. 设计概述

本文档详细描述为 `AcpClientManager` 增加完整 ACP 生命周期支持的设计方案。

### 1.1 设计原则

1. **渐进增强**：在现有代码基础上增量修改，不破坏已有 API
2. **事件驱动**：基于 Kotlin Flow/StateFlow 实现响应式状态管理
3. **防御性编程**：所有新增 API 返回 `Result<T>`，异常不逃逸
4. **可观测性**：生命周期事件可监听、可追踪

### 1.2 现有代码定位

当前 `AcpClientManager` 位于：
```
library/src/jvmMain/kotlin/com/xr21/ai/agent/tui/acp/AcpClientManager.kt
```

## 2. 新增文件结构

```
acp/
├── AcpClientManager.kt          # 增强（修改现有文件）
├── ConnectionState.kt           # 不变
├── AcpLifecycleState.kt         # 新增：生命周期状态
├── AcpLifecycleEvent.kt         # 新增：生命周期事件
├── AcpLifecycleListener.kt      # 新增：生命周期监听器
├── ReconnectStrategy.kt         # 新增：重连策略
└── SessionManager.kt            # 新增：多会话管理器
```

## 3. 核心新增类型

### 3.1 AcpLifecycleState

```kotlin
enum class AcpLifecycleState {
    CREATED,
    CONNECTING,
    INITIALIZED,
    SESSION_ACTIVE,
    DISCONNECTED,
    DESTROYED
}
```

### 3.2 AcpLifecycleEvent

```kotlin
sealed class AcpLifecycleEvent {
    data class StateChanged(val oldState: AcpLifecycleState, val newState: AcpLifecycleState) : AcpLifecycleEvent()
    data class Connected(val agentName: String, val agentVersion: String) : AcpLifecycleEvent()
    data class SessionCreated(val sessionId: String) : AcpLifecycleEvent()
    data class Disconnected(val reason: String) : AcpLifecycleEvent()
    data class Reconnecting(val attempt: Int, val delayMs: Long) : AcpLifecycleEvent()
    data class ErrorOccurred(val error: Throwable) : AcpLifecycleEvent()
    object Destroyed : AcpLifecycleEvent()
}
```

### 3.3 AcpLifecycleListener

```kotlin
fun interface AcpLifecycleListener {
    fun onLifecycleEvent(event: AcpLifecycleEvent)
}
```

### 3.4 ReconnectStrategy

```kotlin
sealed class ReconnectStrategy {
    data class FixedInterval(val intervalMs: Long = 3000) : ReconnectStrategy()
    data class ExponentialBackoff(
        val initialIntervalMs: Long = 1000,
        val maxIntervalMs: Long = 30000,
        val multiplier: Double = 2.0
    ) : ReconnectStrategy()
    object NoReconnect : ReconnectStrategy()
}
```

## 4. AcpClientManager 增强方案

### 4.1 新增字段

```kotlin
// 生命周期状态
private val _lifecycleState = MutableStateFlow(AcpLifecycleState.CREATED)
val lifecycleState: StateFlow<AcpLifecycleState> = _lifecycleState.asStateFlow()

// 生命周期事件
private val _lifecycleEvents = MutableSharedFlow<AcpLifecycleEvent>(extraBufferCapacity = 64)
val lifecycleEvents: Flow<AcpLifecycleEvent> = _lifecycleEvents.asSharedFlow()

// 生命周期监听器
private val lifecycleListeners = CopyOnWriteArrayList<AcpLifecycleListener>()

// 重连策略
private var reconnectStrategy: ReconnectStrategy = ReconnectStrategy.ExponentialBackoff()

// 重连 Job
private var reconnectJob: Job? = null

// 主协程作用域
private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
```

### 4.2 状态转换方法

```kotlin
private fun transitionTo(newState: AcpLifecycleState) {
    val oldState = _lifecycleState.value
    _lifecycleState.value = newState
    emitEvent(AcpLifecycleEvent.StateChanged(oldState, newState))
}

private fun emitEvent(event: AcpLifecycleEvent) {
    _lifecycleEvents.tryEmit(event)
    lifecycleListeners.forEach { it.onLifecycleEvent(event) }
}
```

### 4.3 新增 API 实现

#### authenticate()

```kotlin
suspend fun authenticate(methodId: AuthMethodId): Result<AuthenticateResponse> {
    val client = acpClient ?: return Result.failure(IllegalStateException("未初始化"))
    return runCatching {
        client.authenticate(methodId)
    }
}
```

#### logout()

```kotlin
suspend fun logout(): Result<Unit> {
    val client = acpClient ?: return Result.failure(IllegalStateException("未初始化"))
    return runCatching {
        client.logout()
    }
}
```

#### closeSession()

```kotlin
suspend fun closeSession(): Result<Unit> {
    val session = clientSession ?: return Result.failure(IllegalStateException("无活跃会话"))
    return runCatching {
        session.close()
        clientSession = null
        sessionId = null
        transitionTo(AcpLifecycleState.INITIALIZED)
        emitEvent(AcpLifecycleEvent.SessionClosed)
    }
}
```

#### reconnect()

```kotlin
suspend fun reconnect(): Result<Unit> {
    val cfg = lastConfig ?: return Result.failure(IllegalStateException("无连接配置"))
    transitionTo(AcpLifecycleState.CONNECTING)
    disconnect()
    return connect(cfg)
}
```

### 4.4 重连机制

```kotlin
private fun startReconnectLoop(config: ACPConnectConfig) {
    reconnectJob?.cancel()
    reconnectJob = scope.launch {
        var attempt = 0
        while (isActive && _lifecycleState.value != AcpLifecycleState.DESTROYED) {
            attempt++
            val delayMs = when (val strategy = reconnectStrategy) {
                is ReconnectStrategy.FixedInterval -> strategy.intervalMs
                is ReconnectStrategy.ExponentialBackoff -> {
                    minOf(
                        strategy.initialIntervalMs * (strategy.multiplier.toLong().pow(attempt - 1)),
                        strategy.maxIntervalMs
                    )
                }
                is ReconnectStrategy.Custom -> strategy.strategy(attempt)
                ReconnectStrategy.NoReconnect -> break
            }
            emitEvent(AcpLifecycleEvent.Reconnecting(attempt, delayMs))
            delay(delayMs)
            val result = connect(config)
            if (result.isSuccess) break
        }
    }
}
```

### 4.5 增强的 disconnect()

```kotlin
fun disconnect() {
    reconnectJob?.cancel()
    reconnectJob = null
    
    // 先关闭会话（通知 Agent）
    runBlocking {
        clientSession?.let {
            runCatching { it.close() }
        }
    }
    
    // 原有清理逻辑
    isConnected = false
    eventCollectorJob?.cancel()
    protocol?.close()
    httpClient?.close()
    serverThread?.interrupt()
    process?.destroy()
    
    // 清理引用
    clientSession = null
    acpClient = null
    protocol = null
    httpClient = null
    process = null
    reader = null
    sessionId = null
    
    transitionTo(AcpLifecycleState.DISCONNECTED)
    emitEvent(AcpLifecycleEvent.Disconnected("主动断开"))
}
```

### 4.6 增强的 connect()

```kotlin
suspend fun connect(config: ACPConnectConfig): Result<Unit> {
    lastConfig = config
    transitionTo(AcpLifecycleState.CONNECTING)
    
    val result = if (config.agentCommand.isNotEmpty()) {
        connectStdio(config.agentCommand)
    } else {
        connectWebSocket(config)
    }
    
    if (result.isSuccess) {
        transitionTo(AcpLifecycleState.INITIALIZED)
        emitEvent(AcpLifecycleEvent.Connected(
            appState.agentName, appState.agentVersion
        ))
        emitEvent(AcpLifecycleEvent.SessionCreated(sessionId ?: ""))
    } else {
        transitionTo(AcpLifecycleState.DISCONNECTED)
        emitEvent(AcpLifecycleEvent.ErrorOccurred(result.exceptionOrNull()!!))
        
        // 自动重连
        if (config.autoReconnect) {
            startReconnectLoop(config)
        }
    }
    
    return result
}
```

## 5. 修改清单

### 5.1 修改 AcpClientManager.kt

| 修改类型 | 内容 | 影响范围 |
|---------|------|---------|
| 新增字段 | `_lifecycleState`, `_lifecycleEvents`, `lifecycleListeners`, `reconnectStrategy`, `reconnectJob`, `lastConfig` | 内部 |
| 新增方法 | `authenticate()`, `logout()`, `closeSession()`, `reconnect()`, `addLifecycleListener()`, `removeLifecycleListener()`, `setReconnectStrategy()` | 外部 API |
| 新增私有方法 | `transitionTo()`, `emitEvent()`, `startReconnectLoop()` | 内部 |
| 修改方法 | `connect()` - 增加状态转换和事件通知 | 行为增强 |
| 修改方法 | `disconnect()` - 增加 session.close() 和状态转换 | 行为增强 |
| 修改方法 | `connectWebSocket()` - 增加状态转换 | 行为增强 |
| 修改方法 | `connectStdio()` - 增加状态转换 | 行为增强 |

### 5.2 新增文件

| 文件 | 内容 |
|------|------|
| `AcpLifecycleState.kt` | 生命周期状态枚举 |
| `AcpLifecycleEvent.kt` | 生命周期事件密封类 |
| `AcpLifecycleListener.kt` | 生命周期监听器接口 |
| `ReconnectStrategy.kt` | 重连策略密封类 |

## 6. 向后兼容性

| 现有 API | 兼容性 | 说明 |
|----------|--------|------|
| `connect(config)` | ✅ 完全兼容 | 行为增强，返回值不变 |
| `sendPrompt(content)` | ✅ 完全兼容 | 不变 |
| `sendCancel()` | ✅ 完全兼容 | 不变 |
| `disconnect()` | ✅ 完全兼容 | 行为增强（增加 session.close） |
| `setModel()` | ✅ 完全兼容 | 不变 |
| `setMode()` | ✅ 完全兼容 | 不变 |
| `setConfigOption()` | ✅ 完全兼容 | 不变 |
| `startEventCollection()` | ✅ 完全兼容 | 不变 |
| `availableModels` | ✅ 完全兼容 | 不变 |
| `isActive` | ✅ 完全兼容 | 不变 |

## 7. 测试要点

1. **状态转换测试**：验证所有合法状态转换
2. **重连测试**：模拟断开后自动重连
3. **认证测试**：authenticate/logout 流程
4. **会话管理测试**：closeSession/loadSession
5. **事件测试**：验证所有生命周期事件正确发射
6. **并发测试**：多线程下的状态一致性
7. **兼容性测试**：现有功能不受影响
