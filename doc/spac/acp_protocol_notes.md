# ACP 协议使用经验笔记

> 来源：从 kotlin-sdk（E:\local-github\kotlin-sdk）源码学习的经验

## SessionUpdate 事件类型

ACP 协议中，Agent 向客户端推送的实时更新通过 `SessionUpdate` sealed class 表示：

| 事件类型                | JSON discriminator    | 说明             |
|---------------------|-----------------------|----------------|
| `AgentMessageChunk` | `agent_message_chunk` | Agent 回复文本流    |
| `AgentThoughtChunk` | `agent_thought_chunk` | Agent 思考过程流    |
| `ToolCall`          | `tool_call`           | 新工具调用开始        |
| `ToolCallUpdate`    | `tool_call_update`    | 工具调用状态/结果更新    |
| `PlanUpdate`        | `plan`                | Plan/Todo 列表更新 |
| `UsageUpdate`       | `usage_update`        | Token 用量更新     |
| `SessionInfoUpdate` | `session_info_update` | 会话标题/时间更新      |

### 关键字段

**AgentMessageChunk / AgentThoughtChunk:**

- `content: ContentBlock` — 内容块，通常是 `ContentBlock.Text(text=...)`
- `messageId: MessageId?` — 消息 ID（不稳定 API）

**ToolCall / ToolCallUpdate:**

- `toolCallId: ToolCallId` — 工具调用唯一 ID
- `title: String` — 显示标题
- `kind: ToolKind?` — 工具类型（read/edit/delete/execute/think/fetch 等）
- `status: ToolCallStatus?` — 状态（pending/in_progress/completed/failed）
- `content: List<ToolCallContent>` — 输出内容
- `locations: List<ToolCallLocation>` — 文件位置
- `rawInput: JsonElement?` — 原始输入参数
- `rawOutput: JsonElement?` — 原始输出结果

**PlanUpdate:**

- `entries: List<PlanEntry>` — Plan 条目列表

**UsageUpdate:**

- `used: Long` — 已用 token 数
- `size: Long` — 总上下文大小
- `cost: Cost?` — 费用信息（amount + currency）

### ToolKind 枚举

```kotlin
READ, EDIT, DELETE, MOVE, SEARCH, EXECUTE, THINK, FETCH, SWITCH_MODE, OTHER
```

### ToolCallStatus 枚举

```kotlin
PENDING, IN_PROGRESS, COMPLETED, FAILED
```

## 当前项目的事件处理现状

项目中 `AcpEventProcessor` 使用的是**简化字符串协议**：

```
text:<content>
thought:<content>
tool_call:<name>|<args>
tool_call_update:<content>
tool_result:<content>
todo:<content>
todo_status:<id>:<status>
token:<prompt>,<completion>,<total>
agent:<name>/<version>
model:<name>
```

这是 NDJSON 协议之上的应用层简化。真实的 ACP 协议传输的是 JSON 对象，通过 `sessionUpdate` discriminator 区分类型。

## 建议的演进方向

1. **短期**：保持现有字符串协议，但扩展字段以支持更多 ACP 特性
2. **中期**：接入 acp-model 库，使用 kotlinx.serialization 反序列化真实 JSON SessionUpdate
3. **长期**：直接使用 kotlin-sdk 的 Client 类，而非手动解析 NDJSON

## 序列化要点

acp-model 使用 kotlinx.serialization，关键配置：

- `ACPJson` 是 SDK 内部配置的 Json 实例
- `SessionUpdateSerializer` 处理多态反序列化，未知类型回退到 `UnknownSessionUpdate`
- `JsonClassDiscriminator("sessionUpdate")` 用于区分不同事件类型
