# entry.groovy Entry Point Reference（入口契约参考）

> 来源：`doc/GROOVY_PLUGIN_DESIGN.md` §4-§6、§7.3（XAgent 落地）

## 脚本返回的插件描述 Map

Groovy 入口脚本顶层 `return` 一个 Map，是唯一的推荐写法（与打包模型解耦，目录包与 legacy 通用）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `name` | String | 建议与 plugin.json 的 name 一致 |
| `description` | String | 插件说明 |
| `version` | String | 可选 |
| `init` | Closure | 加载时执行一次 `init(PluginContext ctx)`，初始化状态/注册能力 |
| `close` | Closure | 卸载/热重载时执行 `close()`，释放资源 |
| `tools` | List\<Map\> | 注册的工具列表（进模型工具集） |
| `hooks` | List\<Map\> | 注册的钩子列表（监听/改写模型请求） |
| `interceptors` | List\<Map\> | 注册的拦截器列表（服务/状态/透传） |

## 工具定义（tools[]）

```groovy
[
    name: "my_tool",                        // 工具名（勿与内置/已注册冲突，冲突则拒绝该条）
    description: "工具说明（模型会读取，务必写清功能与参数）",
    inputSchema: [type: "object",           // JSON Schema
                  properties: [arg: [type: "string", description: "参数说明"]],
                  required: []],
    run: { Map args -> ... }                // 执行闭包，返回 Map（结构化结果）
]
```

`run` 闭包内可访问：`tools.xxx(...)`（编排宿主工具）、`PLUGIN_ROOT`/`PLUGIN_DATA`、脚本顶层状态变量（每插件状态实例跨调用保留）。

## hooks 定义（hooks[]）

Hooks 在 agent 执行各阶段（如 BEFORE_AGENT）触发，用于监听/改写模型请求。`run` 返回的 Map 会**合并进 agent 状态**；返回空 Map 表示不改写。

```groovy
[
    name: "pluginStatusHook",        // 钩子名（勿与内置/已注册冲突）
    position: "BEFORE_AGENT",        // 触发位置枚举（见宿主 Hook 定义，如 BEFORE_AGENT）
    run: { state, config ->          // 签名 (state, config)；返回 Map 合并进 agent 状态
        return [pluginInitialized: true]   // 需要注入状态时返回该 Map；不需要则返回 [:]
    }
]
```

> 注意：`SummarizationHook` 虽在 interceptors 包内，但继承 MessagesModelHook 是 **Hook** 不是 Interceptor——插件契约里须区分两类（GROOVY_PLUGIN_DESIGN §5.8）。

## interceptors 定义（interceptors[]）

Interceptors 拦截/改写模型调用（服务/状态/透传）。`apply` 的第二个参数是 handler，**必须调用 `handler.call(request)` 放行**，否则模型调用被截断。

```groovy
[
    name: "pluginPassthrough",       // 拦截器名（勿与内置/已注册冲突）
    type: "model",                   // 拦截器类型（如 model，见宿主 Interceptor 定义）
    apply: { request, handler ->     // 签名 (request, handler)；透传/改写模型调用
        // 可在此记录日志或改写 request
        return handler.call(request)  // 必须调用 handler.call 放行
    }
]
```

**顺序安全约束**：拦截器顺序敏感（重试类须包裹工具调用、FilesystemInterceptor 须先做路径校验）。插件注入的拦截器只能**追加**（默认在内置之后），可声明相对内置的前后位置（after/before 某内置），不能移除/重排内置安全与重试拦截器。

## 生命周期与每插件状态

```groovy
def pluginState = [cache: [:] , counter: 0]   // 顶层变量 = 每插件状态实例，跨调用保留

return [
    name: "my-tools",
    init: { ctx ->
        // 一次性初始化：建状态、连服务
        pluginState.counter = 0
        // 可用能力：ctx.inject('chatModel') / ctx.inject('client') 等（白名单）
    },
    close: { ->
        // 卸载时清理：关会话、释放资源（框架用 try/finally 保证执行）
        pluginState.cache = null
    },
    tools: [ ... ]
]
```

- 每插件**一个**脚本实例（非每次调用重建），供 Shell 会话、缓存等跨调用保留状态。
- `mergeDesc` 合并 init/close 时**首条生效**。

## bindings API（脚本内可用）

| API | 说明 |
|---|---|
| `tools.xxx(...)` | 编排调用宿主已注册工具，返回已解析对象 |
| `tools.inject(key)` | 白名单注入宿主能力：`client`/`chatModel`/`conversation`/`workers`/`sharedCache`；未白名单返回 `{success:false, error:"unknown capability"}` |
| `tools.conversation.messages()` | 读当前对话记录（List\<Map\>） |
| `tools.conversation.state(key)` | 读任意工作流状态值 |
| `tools.conversation.replaceMessages(list)` | 整体替换对话（必须 `ReplaceAllWith.of(list)`，否则不替换） |
| `tools.conversation.setState(key, value)` | 写入状态（普通值走 AppendStrategy 合并） |
| `tools.conversation.appendMessage([role:..., text:...])` | 追加一条消息 |
| `tools.plugin(name, desc)` | 运行时注册常驻能力（name 冲突拒绝该条；下一轮模型调用即对模型可见可调用） |

## 环境变量（§9，进程内等效注入）

| 变量 | 语义 |
|---|---|
| `PLUGIN_ROOT` | 插件根绝对路径（只读，读捆绑脚本/配置） |
| `PLUGIN_DATA` | 客户端管理的可写持久目录（启动时创建并确保可写、跨更新保留、卸载可删） |

- **占位展开**：入口内 `${PLUGIN_ROOT}`/`${PLUGIN_DATA}` 编译前**单次非递归**文本替换，替换引入的文本不再二次扫描。
- **保留名**：不可用 `tools.conversation.setState("PLUGIN_ROOT", ...)` 覆盖。
- **围栏**：`PLUGIN_ROOT` 解析后必须落在插件根内，越界拒绝访问。

## 工具元信息来源优先级

1. 描述 Map `tools[]` 中的 name/description/inputSchema；
2. 运行时 `tools.plugin(name, desc)` 实参；
3. 兜底：插件名 + 工具索引 + 空 schema（脚本内自行校验入参）。
