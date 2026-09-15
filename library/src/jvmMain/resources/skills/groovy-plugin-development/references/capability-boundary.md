# Plugin Capability Boundary（插件能力边界）

> 来源：`doc/GROOVY_PLUGIN_DESIGN.md` §5.8（插件化范围）、§7.2（顺序约束）、§8（安全设计）、§8.5（依赖注入与生命周期安全）

## 一、插件能做什么（能力边界内）

| 能力 | 说明 |
|---|---|
| 注册工具 | 通过 `tools[]` 或运行时 `tools.plugin(name, desc)` 注册，进模型工具集，可被模型调用 |
| 注册 hooks | 监听/改写模型请求（`hooks[]`，如 BEFORE_AGENT 注入状态） |
| 注册 interceptors | 拦截/改写模型调用（`interceptors[]`，追加式，不能重排内置） |
| 读写工作流上下文 | `tools.conversation`：messages/state/replaceMessages/setState/appendMessage |
| 编排宿主工具 | `tools.xxx(...)` 调用宿主已注册工具（走现有工具权限） |
| 白名单注入宿主能力 | `tools.inject(key)`：仅 client/chatModel/conversation/workers/sharedCache |
| 使用插件目录 | `PLUGIN_ROOT`（只读捆绑资源）/ `PLUGIN_DATA`（可写持久目录，启动保证存在） |
| 每插件状态 + 生命周期 | 顶层变量跨调用保留；`init(ctx)` 初始化 / `close()` 清理（try/finally 保证执行） |
| 运行时热挂载 | `tools.plugin(...)` 注册的新工具下一轮模型调用即对模型可见可调用 |

## 二、插件不能做什么（能力边界外 · 硬约束）

| 禁区 | 说明 | 依据 |
|---|---|---|
| 注入白名单外对象 | `inject(key)` 仅放行预定义 key，其余返回 `{success:false, error:"unknown capability"}` | §5.6 / §8.5 |
| 移除/重排内置拦截器 | 插件拦截器只能**追加**（默认在内置之后），可声明 after/before 某内置，不能破坏重试与安全语义 | §7.2 / §8.5 |
| 绕过文件路径校验 | 插件任何 IO 必须复用 FilesystemInterceptor 校验链路，禁止自带"裸 IO"绕过 | §7.2 / §8.5 |
| 覆盖保留变量 | 不可 `tools.conversation.setState("PLUGIN_ROOT", ...)` 覆盖保留名 | §9.2 |
| 越出插件根访问 | `PLUGIN_ROOT` 解析后必须落于插件根内，越界拒绝访问（路径围栏） | §4.1 / §8.3 |
| 危险反射/类加载 | 禁止 import 任意类、反射访问 Runtime/ProcessBuilder、`Class.forName`/`getRuntime`/`exec`（SecureASTCustomizer 限制） | §8.1 |
| 名称冲突 | 注册的工具/hook/interceptor 名不得与内置或已注册重复，冲突拒绝该条 | §3.3 |
| 无限资源消耗 | 输出上限 200_000 字符、超时中断、钩子/拦截器注册数上限 | §8.4 |

## 三、什么适合插件化 / 什么必须保留内置（§5.8）

| 层级 | 组成 | 是否插件化 | 说明 |
|---|---|---|---|
| **系统层（宿主地基）** | GroovyScriptTool、SkillsAgentHook、FilesystemInterceptor、重试/错误拦截器（Model/Tool/ToolError Retry）、HumanInTheLoopHook、ContextEditingInterceptor（默认） | ❌ 内置 | 插件运行的**地基与安全边界**，绝不可被插件绕过或卸载 |
| **能力层（应用）** | WebTool/SleepTool、6 个文件工具（Read/Write/SmartEdit/Grep/Glob/ListFiles）、SummarizationHook、第三方新工具 | ✅ 首选插件化 | 无状态、自包含、示范价值高 |
| **可移植层（需 DI/状态）** | ShellTools、WorkerTool/MsgTool、ContextCacheTool、WorkerInterceptor、AcpTodoList 系列、ConversationCompactionTool | 🟡 依赖 PluginContext | 依赖 client/chatModel/workers/共享缓存，需先具备注入能力（已搁置） |

**原则**：能力型可插件化，基础设施型/安全边界型保留内置。不要把地基挖出来当砖——系统层只负责**发现与注册**插件。

## 四、插件边界自查清单（开发前核对）

- [ ] 功能属于能力层（无状态/自包含）而非系统层（安全/重试/宿主）
- [ ] 工具名/钩子名/拦截器名不与内置或已注册重复
- [ ] 所有文件 IO 走 FilesystemInterceptor 校验链路，无裸 IO
- [ ] 拦截器只声明追加位置，不尝试移除/重排内置
- [ ] 只用白名单内 inject key，不试图注入任意宿主对象
- [ ] 不覆盖 PLUGIN_ROOT/PLUGIN_DATA 保留名，不越出插件根访问路径
- [ ] 不 import 任意类 / 反射 Runtime / Class.forName / exec
- [ ] description 写清功能与参数（模型按此决定调用）
