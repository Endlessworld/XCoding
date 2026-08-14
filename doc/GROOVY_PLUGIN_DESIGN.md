# XAgent Groovy 动态插件机制 — 需求详细设计（agent-plugins 对齐版）

> 版本: v0.3 (对齐 Agent Plugins v1.0.0 协议)
> 更新: 2026-08-14
> 定位: 在 `library` 模块建立 **“智能体自写 Groovy 插件”** 机制，使脚本具备 skill 同等的发现模型，并能 **常驻挂载** 进当前运行 agent —— 注册工具 / 监听事件 / 提供服务；同时将插件**打包模型对齐 Agent Plugins 规范**，支持跨客户端分发与 `plugin.json` 清单驱动的发现。
> 参考协议: `doc/agent-plugins1.0.0.md`（Agent Plugins Specification v1.0.0）
> 参考实现: `tools/GroovyScriptTool.java` / `utils/GroovyToolBindings.java` / `tools/ConversationCompactionTool.java` / `agent/LocalAgent.java` / `interceptors/WorkerInterceptor.java`
> 对齐机制: skill 目录扫描加载（`FileSystemSkillRegistry` + `SkillsAgentHook`）
> 参考范式: deepseek-harness `cordis-host-runner` + `tool-cordis`（动态插件常驻运行时的参照系）
> **实现状态: 阶段一已完成（2026-08-14），阶段二进行中** —— `plugins/` 包落地：GroovyPluginRegistry /
>   GroovyPluginLoader / GroovyPluginParser / ClosureToolCallback / GroovyPluginHook / GroovyPluginInterceptor，
>   已接入 LocalAgent 装配链三处；阶段二新增 PluginContext 能力容器（白名单 inject）、ConversationAccess
>   （工作流上下文读写）、init/close 生命周期、GroovyToolBindings 的 inject/plugin() 通道。
>   测试：GroovyPluginLoaderTest 4 用例（含生命周期/注入白名单）通过。
---

## 0. 重构说明（v0.2 → v0.3）

v0.2 确立了“挂载型插件”内核：进程级 `GroovyPluginRegistry` + 装配链三处并入（tools / hooks / interceptors）。但 v0.2 的**包模型仍是“松散 .groovy 文件”**（`~/.agents/tools/*.groovy`），与生态通用、可分发、可跨客户端移植的插件标准脱节。

v0.3 的优化主线：**保留 v0.2 的常驻挂载内核不变，仅把“打包 / 发现 / 清单”层对齐到 Agent Plugins v1.0.0 协议**。

主要改动：

1. **包模型升级**：从“松散 .groovy 文件” → “目录包 + `plugin.json` 清单”（协议 §4 / §5）。一个插件 = 一个目录，根目录必须有 `plugin.json`。
2. **引入清单（manifest）**：闭式 schema（`$schema` / `name` / `version` / `description` / `author` / `homepage` / `repository` / `license` / `keywords` / `extensions`，§5.2）。客户端特定数据全部收敛进 `extensions`（§8.1）。
3. **Groovy 能力作为 XAgent 客户端扩展**：Groovy 组件挂到自有扩展命名空间（草案 `com.xr21.agent`），通过 §8 的 `extensions` 清单数据 + 顶层扩展目录双表示。
4. **保留便捷开发模式（legacy）**：松散 `.groovy` 文件仍自动加载，运行期包装为合成清单；标准分发走目录包。
5. **引入 `PLUGIN_ROOT` / `PLUGIN_DATA`**：脚本环境约定对齐 §9.1，路径占位展开对齐 §9.2。
6. **路径围栏 & 失败非致命**：对齐 §4.1 路径包含规则与 §6 / §7 / §11 的失败隔离语义。

> v0.2 的两条路线仍成立：**A. 构建期加载**（启动扫描并入装配链，本期推荐）与 **B. 运行时热挂载**（阶段二，需验证 graph 动态 tools）。v0.3 只改动包层，不动这两条挂载路线。
---

## 1. 背景与目标

### 1.1 背景

`GroovyScriptTool` 已能在脚本内通过注入的 `tools` 对象动态编排已注册工具，但它是 **call-once 调用型**：脚本执行完即销毁，无法跨会话复用，也无法向运行中的 agent 注入常驻能力。v0.2 解决了“常驻挂载”，但仍缺少**可分发、可移植的打包与发现标准**。

三个缺口（v0.2 已覆盖“常驻/沉淀/参与运行时”）+ 一个新缺口：

1. **无法常驻**：脚本能力随返回即消失（v0.2 用 registry + 装配链解决）；
2. **无法沉淀复用**：一次性脚本逻辑无法像 skill 一样落盘自动发现（v0.2 解决）；
3. **无法参与运行时**：脚本不能注册工具 / 监听 / 提供服务（v0.2 解决）；
4. **无法跨客户端分发**（v0.3 新增）：没有 `plugin.json` 清单与标准目录约定，脚本只能被 XAgent 私有发现，无法被其他 agent-plugins 客户端理解或分发。

### 1.2 目标

- **P1（发现模型）**：以 Agent Plugins 目录包为统一发现单元，支持 `plugin.json` 清单驱动的扫描加载，兼容 skill 同等的按需可用；
- **P2（常驻挂载）**：脚本注册工具（进模型工具集）、监听事件、提供服务/状态，并持续存在于当前运行 agent（保留 v0.2 内核）；
- **P3（工作流上下文）**：脚本可读写对话记录与工作流状态（复用 `ConversationCompactionTool` 的 `ToolContext` 链路）；
- **P4（协议对齐，v0.3）**：插件包符合 Agent Plugins v1.0.0（清单闭式 schema、固定发现位置、`extensions` 命名空间、`PLUGIN_ROOT`/`PLUGIN_DATA`、路径围栏、失败非致命）。

### 1.3 非目标（本期不做）

- 不做跨进程/容器级插件沙箱（仅进程内受限 ClassLoader + `SecureASTCustomizer`）；
- 不做图形化插件管理界面（仅目录约定 + 日志）；
- 不支持 Java/Kotlin 源码插件（仅 .groovy 脚本）；
- 不做“运行中热卸载正在执行插件”（只做启动期快照式加载 + 可选增量重载）；
- **不做 `mcp.json` MCP 服务器组件的实现**：协议将 skills 与 MCP 定义为两个组件类型，v1 我们只实现 Groovy 扩展 + 兼容发现 skill 组件；对 `mcp.json` 采取“忽略不支持组件类型”的合规姿势（§6.2 / §11.3）。

---

## 2. 设计原则与概念模型

### 2.1 核心命题不变：XAgent 没有 ctx 单例，挂载点 = 装配链 + 注册表

同 v0.2：cordis 插件的 `apply(ctx)` 依赖常驻 Context；XAgent 的挂载点是 `ReactAgent.builder()` 构建期装配链（`.tools/.hooks/.interceptors`）。**“常驻挂载” = 进程级注册表 `GroovyPluginRegistry` + 并入 ReactAgent 装配链三处。** v0.3 不改变这一内核。

### 2.2 三类挂载能力 ↔ XAgent 真实挂载点

| 插件能力（cordis 语义） | XAgent 挂载点（真实代码） | 说明 |
|---|---|---|
| 注册工具（进模型工具集） | `staticToolCallbackProvider` 的 `tools` 列表（`LocalAgent.java:192-211`）→ `ReactAgent.builder().tools(...)`（L330） | 工具闭包包成 `ToolCallback` |
| 监听事件 / 改写请求 | `getHooks` 的 `List<Hook>`（L356-385）→ `builder.hooks(...)`（L331） | `SkillsAgentHook` 是现成先例 |
| 提供服务 / 状态 | `getInterceptors` 的 `List<Interceptor>`（L214-277）→ `builder.interceptors(...)`（L333） | `ContextEditingInterceptor` 是现成先例 |

### 2.3 与 skill 机制对齐（不变）

沿用 skill 的目录约定、扫描时机、覆盖规则、自动加载骨架，仅把“解析挂载”换成 Groovy 脚本 → `GroovyPluginRegistry`。

### 2.4 协议层的概念映射（v0.3 新增）

把协议概念映射到本项目：

| Agent Plugins 概念（§3） | XAgent 落地 |
|---|---|
| Plugin（包单元） | 一个目录包，根目录含 `plugin.json`（或 legacy 合成清单） |
| Manifest（`plugin.json`） | 闭式 schema 校验后加载；XAgent 私有数据收敛进 `extensions["com.xr21.agent"]` |
| Component（组件） | v1 只实现 **Groovy 扩展**这一种“自有组件”；按协议忽略不支持的组件类型 |
| Client（运行时） | XAgent 本身（发现 / 安装 / 加载 / 执行 Groovy 组件） |
| Extension namespace | `com.xr21.agent`（草案，见 §12 待确认） |
| Extension directory | 插件内顶层 `com.xr21.agent/` 目录，存放脚本引用到的捆绑资源 |
| `PLUGIN_ROOT` / `PLUGIN_DATA` | 注入脚本 bindings 的两个保留变量（§6.3） |

### 2.5 两条挂载路线（不变）

| 路线 | 语义 | 实现 | 满足 |
|---|---|---|---|
| **A. 构建期加载** | 启动时从目录包扫描并入装配链 | `GroovyPluginLoader` + registry + 装配链三处并入 | P1+P2（进工具集） |
| **B. 运行时热挂载** | 脚本执行中途注册，下一轮模型调用即生效 | 每轮从 registry 动态组装 tools（见 §7.3） | P2 完整 + “真·常驻” |

v0.3 推荐**先做 A**（最小闭环、无运行时突变风险），B 作为阶段二/三。
---

## 3. 目录约定与发现（包模型对齐协议）

### 3.1 标准模式：目录包 + `plugin.json`（协议 §4 / §5）

一个插件 = 一个目录，根目录必须有 `plugin.json`。组件通过清单 + 固定位置发现。XAgent 支持的“自有组件”是 **Groovy 扩展**，挂到命名空间 `com.xr21.agent`（§8 客户端扩展双表示）。

```text
~/.agents/plugins/                   # 全局级：agent-plugins 目录包根
  my-tools/
    ├── plugin.json                  # 必需：清单（闭式 schema）
    ├── entry.groovy                 # Groovy 入口（清单 entrypoints 声明，以 ./ 引用）
    ├── com.xr21.agent/              # 顶层扩展目录：捆绑资源（§8.2）
    │   ├── references/runbook.md
    │   └── scripts/helper.groovy
    ├── skills/                      # （可选）skill 组件：协议固定位置，按需发现
    │   └── summarize/SKILL.md
    ├── LICENSE
    └── CHANGELOG.md
<cwd>/.agents/plugins/               # 项目级：覆盖同名全局级
  <plugin-name>/                     # 同名覆盖（项目优先）
    └── plugin.json
```

`plugin.json` 示例（XAgent 扩展命名空间声明 Groovy 入口）：

```json
{
  "$schema": "https://agent-plugins.org/schemas/1.0.0/plugin.schema.json",
  "name": "my-tools",
  "version": "1.2.0",
  "description": "示例：含天气工具 + 模型前钩子 + 状态拦截器的 Groovy 插件包",
  "extensions": {
    "com.xr21.agent": {
      "groovy": {
        "entrypoints": ["./entry.groovy", "./com.xr21.agent/scripts/helper.groovy"]
      }
    }
  }
}
```

- **清单闭式校验**：只允许 §5.2 规定的顶层字段；未知字段**报告并忽略**（非致命）；`$schema`/`name` 缺失或类型错 → **拒绝整个插件**（致命，§5.3）。
- **`name` 约束**：1-64 字符、`a-z0-9-._`、首尾字母数字、无连续 `--` / `..`（§5.5）。
- **entrypoints 路径**：必须是 `./` 开头的插件相对路径，解析后必须落在插件根内（§4.1 规则 4）。
- **不支持的组件类型**（如 `mcp.json`、其他客户端命名空间）：忽略，不报错（§6.2 / §11.3）。

### 3.2 便捷模式（legacy，兼容 v0.2）

保留 v0.2 的松散 `.groovy` 文件作为**快速原型**入口，运行期自动包装为合成清单：

```text
~/.agents/tools/*.groovy            # 或 ~/.agents/skills/tools/*.groovy
<cwd>/.agents/tools/*.groovy
```

- 每个文件包装成 `name = 文件名`（去 `.groovy`）、`extensions["com.xr21.agent"].groovy.entrypoints = ["./<name>.groovy"]` 的合成清单；
- 由同一加载器处理，路径围栏 / 命名去重 / 失败跳过与标准模式一致；
- **文档标注 legacy**：新插件建议直接用目录包，便于携带资源与跨客户端分发。

### 3.3 覆盖规则

- **项目级优先**：同名插件，项目级覆盖全局级（含标准目录包与 legacy 文件统一去重）；
- **加载失败跳过**：单个插件清单/脚本解析失败仅告警并跳过，不拖垮 agent 启动（§6.2 / §11.3 失败隔离）；
- **命名冲突**：注册的工具名 / hook 名 / interceptor 名不得与内置或已注册重复，重复则拒绝该条并告警。

### 3.4 加载时机

在 `LocalAgent.buildAgent` / `createAgent` 阶段，与 `SkillsAgentHook` 同一时机扫描两级目录包（§3.1）与 legacy 文件（§3.2）。默认随 agent 启动加载一次（快照式，路线 A）；热更新复用 skill `autoReload` 思路做增量重载（阶段三）。

### 3.5 发现流程（协议对齐）

```text
扫描插件根目录
  → 读 plugin.json（legacy 文件→合成清单）
  → $schema 识别（不识别的版本→拒绝；§5.2）
  → 闭式校验未知字段（报告并忽略）
  → 校验 name（致命失败→拒绝；§5.3）
  → 读 extensions["com.xr21.agent"]（无→忽略，视为其他客户端扩展；§8.1）
  → 解析 groovy.entrypoints（./ 相对 + 根内包含校验；§4.1）
  → 逐入口 GroovyShell 编译加载，产出“插件描述”（tools/hooks/interceptors）
  → GroovyPluginRegistry 登记
  → LocalAgent 装配时并入 tools/hooks/interceptors
```

> 任何一个入口失败：跳过该入口并告警，继续加载其他入口/插件（协议失败隔离语义）。
---

## 4. 脚本导出契约（插件怎么写）

v0.3 统一为**一种**推荐写法：脚本返回“插件描述 Map”，含 `name` 与 `tools/hooks/interceptors` 三块（可只含部分）。此契约与打包模型解耦：同一份脚本既可用于目录包入口，也可用于 legacy 松散文件。

### 4.1 推荐：脚本返回插件描述 Map

```groovy
// entry.groovy —— 目录包 my-tools 的 Groovy 入口
return [
    name: "my-tools",                       // 建议与 plugin.json 的 name 一致
    description: "演示常驻插件：天气工具 + 模型前钩子 + 状态拦截器",
    tools: [
        [
            name: "weather",
            description: "查询指定城市当前天气",
            inputSchema: [type:"object",
                properties:[city:[type:"string", description:"城市名"]],
                required:["city"]],
            run: { Map args ->
                // 可访问 tools.xxx 编排宿主工具；PLUGIN_ROOT/PLUGIN_DATA 已注入（见 §6.3）
                return [temperature: 25, condition: "晴"]
            }
        ]
    ],
    hooks: [
        [name: "beforeModel", systemPrompt: { -> "我的插件状态：今天天气查询可用。" }]
    ],
    interceptors: [
        [name: "stateCounter", apply: { OverAllState state -> state.updateState("plugin_count", ...) }]
    ]
]
```

### 4.2 脚本内运行时注册（路线 B 用，对应 cordis `tools.register`）

`GroovyToolBindings` 增加**可变注册通道** `plugin(name, desc)`（见 §5.3），脚本可在执行中途把能力登记进 `GroovyPluginRegistry`。

```groovy
// 一次性脚本也能注册常驻能力（name 与已加载插件冲突→拒绝该条）
tools.plugin("my-tools", [ tools: [...], hooks: [...], interceptors: [...] ])
```

### 4.3 元信息来源优先级（工具）

1. 插件描述 `tools[]` 中每条的 `name/description/inputSchema`；
2. 运行时 `plugin(name, desc)` 实参；
3. 兜底：插件名 + 工具索引（name）+ 空 schema（需脚本内自行校验入参）。

---

## 5. 核心组件设计

### 5.1 GroovyPluginRegistry（新增 · 常驻基座，不变）

进程级单例注册表，持有“已加载插件 + 已注册工具/hook/interceptor”，是 cordis registry + fiber 生命周期在 XAgent 的对应物。v0.3 额外登记**插件来源**（包路径 / legacy）与**命名空间**，供审计与去重。

```java
public final class GroovyPluginRegistry {
    private static final GroovyPluginRegistry INSTANCE = new GroovyPluginRegistry();
    private final Map<String, GroovyPlugin> plugins = new ConcurrentHashMap<>();
    private final Map<String, ToolCallback> toolCallbacks = new ConcurrentHashMap<>();
    private final List<Hook> hooks = new CopyOnWriteArrayList<>();
    private final List<Interceptor> interceptors = new CopyOnWriteArrayList<>();

    public static GroovyPluginRegistry get() { return INSTANCE; }

    public void register(GroovyPlugin plugin) { ... }        // 登记插件及其三类能力
    public void unregister(String name) { ... }              // 卸载（阶段三）
    public List<ToolCallback> toolCallbacks() { ... }
    public List<Hook> hooks() { ... }
    public List<Interceptor> interceptors() { ... }
}
```

### 5.2 GroovyPluginLoader（由 v0.2 的 GroovyToolScriptLoader 演进）

职责对齐协议：

- **清单解析校验**：读 `plugin.json` → 闭式 schema 校验 → 提取 `extensions["com.xr21.agent"].groovy.entrypoints`；
- **legacy 合成**：松散 `.groovy` → 合成清单；
- **路径围栏**：校验入口 `./` 相对且落于插件根内（§4.1）；
- **编译加载**：独立 `GroovyClassLoader` 隔离脚本类；
- **环境注入**：向脚本 bindings 注入 `PLUGIN_ROOT` / `PLUGIN_DATA`（§6.3）；
- **失败隔离**：单个入口失败跳过并告警，继续其余。

### 5.3 GroovyToolBindings 扩展（新增 `plugin` 通道，不变）

保留现有只读调用能力，**新增**运行时注册入口（当前 `setProperty` 抛异常，不能靠属性赋值）：

```java
public Object plugin(String name, Map<String, Object> desc) {
    GroovyPluginRegistry.get().register(GroovyPluginParser.parse(name, desc));
    return Map.of("success", true, "pluginId", name);
}
```

### 5.4 ClosureToolCallback（新增，不变）

把脚本返回/注册的 `run` 闭包适配为 Spring AI `ToolCallback`。关键：闭包 `delegate`/`owner` 绑定到宿主上下文对象而非脚本 `this`，保证闭包跨脚本存活时仍能访问宿主工具与 `ToolContext`。

### 5.5 装配链并入（LocalAgent，三处，不变）

在 `staticToolCallbackProvider`、`getHooks`、`getInterceptors` 三处从 registry 拉取插件能力并入（详见 §7）。

### 5.6 能力上下文容器（v0.3 新增 · 依赖注入缺口）

**问题**：现有 `GroovyToolBindings` 只持有 `availableTools + toolContext`（`GroovyScriptTool.java:174`）。但 ShellTools、WorkerTool、MsgTool、ContextCacheTool、SummarizationHook 等依赖 `ClientSessionOperations`、`ChatModel`、`ReactAgent` workers、共享缓存等**外部对象**。插件脚本需要按需获取这些能力，而不是 `new` 一个对象。

**方案**：引入 `PluginContext`（能力上下文容器），在加载时把宿主可用能力注册进去，脚本通过**受控 API** 获取：

```java
public class PluginContext {
    private final ToolContext toolContext;
    private final ClientSessionOperations client;   // ACP 回传（MsgTool/通知）
    private final ChatModel chatModel;              // SummarizationHook 等需要
    private final Map<String, ReactAgent> workers;  // WorkerTool 注入
    private final ConversationAccess conversation;  // §6 工作流上下文
    private final Map<String, ToolCallback> sharedTools; // 共享缓存/跨插件工具
    // 读取方法：getToolContext() / getClient() / getChatModel() / ...
}
```

脚本通过 bindings 暴露的受限门面访问（而非直接拿到容器引用）：

```groovy
def client = tools.inject("client")       // 返回 ClientSessionOperations（ACP 回传）
def model  = tools.inject("chatModel")   // 返回 ChatModel（用于 LLM 钩子）
def w      = tools.inject("workers")     // 返回 worker map
```

**注入白名单**：`inject(key)` 只放行预定义的 key（`client`/`chatModel`/`conversation`/`workers`/`sharedCache` 等），禁止注入任意宿主对象；未白名单的 key 返回 `{success:false, error:"unknown capability"}`。防止脚本拿到任意内部对象破坏隔离。

> 依赖注入是 ShellTools / WorkerTool / MsgTool / ContextCacheTool / SummarizationHook 等“可移植层”插件化的**前置条件**（见 §5.8 分层评估）。

### 5.7 插件生命周期与状态（v0.3 新增）

现有 `GroovyScriptTool` 是 call-once：每次执行 new 一个 shell，无状态。常驻插件需要**实例级状态**与**生命周期**：

- **每插件状态实例**：`GroovyPluginRegistry` 为每个插件持有**一个**脚本实例/闭包容器（而非每次调用重建），供 ShellTools 会话、ContextCacheTool 缓存等跨调用保留状态。
- **init 钩子**：加载时执行一次 `init(PluginContext ctx)`，让插件初始化状态、注册能力；
- **close 钩子**：卸载/热重载时执行 `close()`，释放 Shell 会话、关闭缓存引用、解绑 worker（阶段三）。

```groovy
return [
    name: "my-tools",
    init: { PluginContext ctx ->
        // 一次性初始化：建状态、连服务
        state.cache = [:]
    },
    close: { ->
        // 卸载时清理：关会话、释放资源
        state.cache = null
    },
    tools: [ ... ],
    hooks: [ ... ],
    interceptors: [ ... ]
]
```

- **共享单例注册表**：ContextCacheTool（进程缓存）、共享 worker map 等**有状态单例**应注册进 registry，插件通过 `inject("sharedCache")` 引用同一个实例，而不是各自 new 导致状态割裂。

### 5.8 插件化范围评估（v0.3 新增 · 内置 vs 插件边界）

基于对 `tools/`（16 个）与 `interceptors/`（7 个）源码及 `LocalAgent.buildAgent` 装配链的评估，**不是所有工具/拦截器都适合插件化**。结论：**能力型可插件化，基础设施型/安全边界型应保留为内置**。分三层：

| 层级 | 组成 | 是否插件化 | 说明 |
|---|---|---|---|
| **系统层（宿主地基）** | `GroovyScriptTool`、`SkillsAgentHook`、`FilesystemInterceptor`、重试/错误拦截器（Model/Tool/ToolError Retry）、`HumanInTheLoopHook`、`ContextEditingInterceptor`（默认） | ❌ 内置 | 插件运行的**地基与安全边界**，绝不可被插件绕过或卸载 |
| **能力层（应用）** | `WebTool`/`SleepTool`、6 个文件工具（Read/Write/SmartEdit/Grep/Glob/ListFiles）、`SummarizationHook`、第三方新工具 | ✅ 首选插件化 | 无状态、自包含、示范价值高 |
| **可移植层（需先补 DI/状态）** | `ShellTools`、`WorkerTool`/`MsgTool`、`ContextCacheTool`、`WorkerInterceptor`、`AcpTodoList` 系列、`ConversationCompactionTool` | 🟡 阶段二 | 依赖 `ClientSessionOperations`/`ChatModel`/workers/共享缓存/`ToolContextHelper`，需先建 PluginContext（§5.6）与生命周期（§5.7） |

**逐项说明（工具）**：

| 工具 | 是否插件化 | 关键依赖/约束 |
|---|---|---|
| Read/Write/SmartEdit/Grep/Glob/ListFiles | ✅ | 无状态、自包含；可作插件模板 |
| WebTool / SleepTool | ✅ | 无状态，仅依赖 ToolContext |
| ShellTools | 🟡 | 881 行、持会话状态、ToolContext 强绑定；需有状态插件单例（§5.7） |
| ContextCacheTool | 🟡 | 持进程缓存，与 ContextEditingInterceptor 强耦合；需共享单例注入 |
| ConversationCompactionTool | 🟡 | 依赖 ToolContextHelper 读写 OverAllState；暴露 conversation 能力（§6） |
| WorkerTool | 🟡 | 依赖 `Map<String, ReactAgent> workers`；需注入 workers |
| MsgTool | 🟡 | 依赖 ClientSessionOperations；需注入 client |
| AcpWriteTodosTool | 🟡 | 与 AcpTodoListInterceptor 耦合；本身轻量 |
| GroovyScriptTool | ❌ | **是插件宿主本身**，属基础设施 |
| ToolKindFind | ❌ | 非工具，静态映射函数 |

**逐项说明（拦截器/钩子）**：

| 拦截器 | 类型 | 是否插件化 | 说明 |
|---|---|---|---|
| FilesystemInterceptor | ModelInterceptor | ❌ | **安全边界本身**（路径/读写/系统提示）；插件必须在其约束下运行 |
| ModelRetryInterceptor / ToolRetryInterceptor / ToolErrorInterceptor | Model/Tool | ❌ | 可靠性基础设施，顺序敏感 |
| ContextEditingInterceptor | ModelInterceptor | 🟡 默认内置 | 上下文管理；若插件化需共享 ContextCacheTool |
| WorkerInterceptor | ModelInterceptor | 🟡 | 需注入 ChatModel + 默认 tools |
| AcpTodoListInterceptor | ModelInterceptor | 🟡 | 提供 write_todos；轻量 |
| SkillsAgentHook | Hook | ❌ | 插件发现机制本身（skill 扫描） |
| HumanInTheLoopHook | Hook | ❌ | 审批/权限安全边界 |
| SummarizationHook | **Hook**（非 Interceptor） | ✅ | 独立，依赖 ChatModel（注入即可） |

> **注意**：`SummarizationHook` 虽在 `interceptors/` 包内，但继承 `MessagesModelHook` 是 **Hook** 不是 Interceptor——插件契约里须区分两类。

**务实建议**：插件机制完成后先做**能力层**插件化（开箱即用、示范价值高），把几个内置工具改写为 `.groovy` 插件作范例；**不要**把安全/重试/宿主钩子插件化——那是把地基挖出来当砖。系统层保留内置，只让它能**发现与注册**插件。

---

## 6. 工作流上下文访问（conversation 绑定）与协议环境约定

### 6.1 能力

对齐 `ConversationCompactionTool`，插件脚本可读改写工作流状态与对话记录。其链路（真实代码已验证）：

```java
OverAllState state = ToolContextHelper.getState(toolContext).orElse(null);
Map<String,Object> stateForUpdate = ToolContextHelper.getStateForUpdate(toolContext).orElse(null);
stateForUpdate.put("messages", ReplaceAllWith.of(newMessages)); // 整体替换
```

### 6.2 脚本可用 API（不变）

```groovy
def msgs = tools.conversation.messages()          // 读当前对话记录（List<Map>）
def val  = tools.conversation.state("outputKey") // 读任意工作流状态值
tools.conversation.replaceMessages(newList)      // ReplaceAllWith 整体替换对话
tools.conversation.setState("key", value)        // 写入状态（普通值走 AppendStrategy 合并）
tools.conversation.appendMessage([role:"assistant", text:"..."]) // 追加一条消息
```

消息双向转换、写回时机与语义同 v0.2（§6.3/§6.4）：

| 方向 | 处理 |
|---|---|
| Message → Map | 提取 role/text/toolCalls/responses 等字段，供脚本友好读取 |
| Map → Message | 按 role 构造对应 Message 类型，工具响应需保留 id 配对 |

- `replaceMessages` 必须构造 `ReplaceAllWith.of(list)`，否则 `AppendStrategy` 不整体替换；
- 写回在工具调用返回后、下一模型调用前由框架消费；脚本不应自行触碰 `stateForUpdate` 之外结构。

### 6.3 协议环境变量（v0.3 新增，§9）

插件脚本与宿主约定对齐协议 §9.1 / §9.2。XAgent 脚本**在进程内运行，不另起子进程**，因此把两个保留变量注入脚本 bindings（等效于子进程环境）：

- `PLUGIN_ROOT`：插件根目录的绝对路径（读取捆绑脚本 / 配置）；
- `PLUGIN_DATA`：客户端管理的**可写持久目录**（每次启动保证存在，跨插件更新保留，卸载可删）。

```groovy
def cfgPath = "${PLUGIN_ROOT}/com.xr21.agent/references/runbook.md"
def cacheDir = "${PLUGIN_DATA}/my-tools"
```

- **占位展开**：脚本 bindings 中，入口内出现的 `${PLUGIN_ROOT}` / `${PLUGIN_DATA}` 由加载器在编译前做**单次非递归**文本替换（§9.2）；替换引入的文本不再二次扫描；
- **围栏**：`PLUGIN_ROOT` 解析后必须落在插件根内（§4.1）；超出则拒绝该访问；
- **保留名**：脚本不可用 `tools.conversation.setState("PLUGIN_ROOT", ...)` 覆盖这两个保留变量；与 `env` 键保留规则对齐（§9.2）；
- **可写性**：`PLUGIN_DATA` 目录由客户端创建并确保可写，跨更新保留（§9.1）。

---

## 7. 工具装配集成（LocalAgent）

### 7.1 装配链改动（三处）

`staticToolCallbackProvider`（tools）、`getHooks`（hooks）、`getInterceptors`（interceptors）三处并入 `GroovyPluginRegistry` 能力。

### 7.2 加载位置建议

- **tools 并入**：必须在 `new GroovyScriptTool(tools)` 前（保证脚本内可编排插件工具）；
- **hooks / interceptors 并入**：与 `SkillsAgentHook` / 既有拦截器同级；
- 插件的 `run` / `hook` / `interceptor` 闭包如需工作流上下文，由 `GroovyToolBindings` 统一注入 `toolContext`。

> **顺序约束（v0.3 强化，安全边界）**：拦截器**顺序敏感**——重试类（Model/Tool/ToolError Retry）应包裹工具调用、`FilesystemInterceptor` 须先行做路径校验。插件注入的 interceptors 需声明**相对内置的前后顺序/优先级**（如 `after=Filesystem`、`before=ModelRetry`），默认追加到内置之后，防止插件破坏重试与安全语义。插件的文件工具**必须复用 `FilesystemInterceptor` 的 ToolContext 校验链路**，禁止自带绕过路径校验的“裸 IO”。

### 7.3 路线 B：运行时热挂载（✅ 已验证并落地，2026-08-14）

- **框架能力验证结论**：`spring-ai-alibaba 2.0.0-M1.1` **原生支持动态工具注入**，无需修改
  `ReactAgent.builder().tools(...)` 可变性：
  - `ModelRequest.dynamicToolCallbacks`：模型请求级动态工具列表（拦截器可注入）；
  - `AgentLlmNode.buildChatClientRequestSpec`：将 dynamicToolCallbacks 合并进本轮模型调用，
    并存入 `config.context().put(RunnableConfig.DYNAMIC_TOOL_CALLBACKS_METADATA_KEY, ...)`；
  - `AgentToolNode.resolveFromConfigMetadata`：从 config metadata 解析并执行动态工具。
- **落地实现**：新增 `PluginDynamicToolsInterceptor`（`ModelInterceptor`），在 `interceptModel`
  中把 `GroovyPluginRegistry` 当前插件工具注入 `ModelRequest.dynamicToolCallbacks`；
  去重策略——跳过已在 `ModelRequest.tools`（节点工具名列表）中的工具，只注入运行期
  新增的插件工具。已接入 `LocalAgent.getInterceptors`（AcpTodoListInterceptor 之后）。
- 效果：脚本执行中途用 `tools.plugin(...)` 注册的新工具，**下一轮模型调用即对模型可见、
  可被调用执行**（真·常驻热挂载）。测试：`PluginDynamicToolsInterceptorTest` 4 用例通过
  （空透传/注入新增/跳过已并入/部分注入）。
---

## 8. 安全设计（硬约束）

插件脚本长期落盘、每次自动加载、持续存在，且能读写对话与工作流状态、注入钩子/拦截器，风险高于一次性脚本。

### 8.1 隔离

- 独立 `GroovyClassLoader`，隔离脚本类与宿主类；
- `SecureASTCustomizer` 限制脚本：禁止 imports 任意类（白名单仅放行安全工具包）、禁止反射访问 `java.lang.Runtime`/`ProcessBuilder` 等危险类、禁止 `Class.forName`/`getRuntime`/`exec`。

### 8.2 权限分层

| 能力 | 默认策略 |
|---|---|
| 读对话/状态（`messages()`/`state()`） | 放行 |
| 写对话/状态（`replaceMessages`/`setState`） | 默认放行，yolo 模式外可设审批 |
| 调任意工具（`tools.xxx`） | 走现有工具权限（`FilesystemInterceptor` 等） |
| 注册钩子/拦截器 | 默认放行（本地脚本，同 bash 信任级），可设审批 |

### 8.3 路径围栏（v0.3 强化，协议 §4.1）

- `plugin.json` 未解析到插件根内 → 拒绝插件；
- Groovy 入口未落于插件根内 → 该入口视为无效，跳过；
- 运行期访问 `PLUGIN_ROOT` 之外路径 → 拒绝访问。

### 8.4 防滥用

- 复用 `GroovyScriptTool` 的超时中断 + 输出上限（200_000 字符）防护；
- 单个插件加载/执行失败不影响整体（协议失败隔离）；
- 记录插件来源（全局/项目 + 包/legacy）便于审计；
- 钩子/拦截器注册数上限，防止插件无限叠加拖慢每轮模型调用；
- `PLUGIN_DATA` 目录在启动时创建并确认可写，避免脚本写失败（§9.1）。

### 8.5 依赖注入与生命周期安全（v0.3 新增）

- **注入白名单**：`tools.inject(key)` 仅放行预定义 key（§5.6），不返回任意宿主对象；防止脚本窃取内部引用绕过隔离。
- **有状态实例的卸载**：插件 `close()` 未执行完/抛异常不得阻止卸载，需 try/finally 强制释放；Shell 会话、缓存、worker 绑定在卸载时一并清理（§5.7）。
- **拦截器顺序不可被插件改写**：插件只能声明**追加位置**（after/before 某内置），不能移除或重排内置安全/重试拦截器（§7.2）。
- **安全边界不可绕过**：插件文件的任何 IO 必须走 `FilesystemInterceptor` 校验，禁止 `inject` 出可直接写盘的原生对象（§7.2）。

---

## 9. 协议一致性清单（Agent Plugins v1.0.0）

> 对照 `doc/agent-plugins1.0.0.md` 的合规项，逐条说明 XAgent 落地姿态。**√ = 本期实现；◐ = 部分/兼容姿势；× = 不做。**

| 协议条款 | 要求摘要 | XAgent 落地 |
|---|---|---|
| §4.1 包模型 | 插件为目录；路径须落于插件根内 | √ 目录包 + 运行期路径围栏 |
| §5.1 清单位置 | 根目录必须有 `plugin.json` | √ 标准模式强制；legacy 合成清单 ◐ |
| §5.2 闭式 schema | 仅允许约定顶层字段；未知字段报告并忽略 | √ |
| §5.3 必填字段 | `$schema`/`name` 必填，缺失→拒绝 | √ |
| §5.5 名称约束 | 1-64 字符、`a-z0-9-._`、无连续分隔 | √ |
| §5.6 / §8.1 extensions | 客户端数据进命名空间；忽略未实现命名空间 | √ `com.xr21.agent` |
| §8.2 扩展目录 | 顶层目录以命名空间命名，承载文件 | √ `com.xr21.agent/` |
| §6 组件发现 | 固定位置发现；缺失不算错 | √ Groovy 扩展经清单 entrypoints 发现 |
| §6.2 缺失位置 | 固定位置缺失≠错误 | √ |
| §7.1 Skills | skill 组件固定 `skills/` | ◐ 兼容发现（沿用现有 skill 机制） |
| §7.2 MCP | `mcp.json` 服务器组件 | × 本期不实现，按“不支持组件”忽略 |
| §9.1 子进程环境 | 提供 `PLUGIN_ROOT`/`PLUGIN_DATA` | √ 注入脚本 bindings（进程内等效） |
| §9.2 占位展开 | 仅 `${PLUGIN_ROOT}`/`${PLUGIN_DATA}` 单次非递归展开 | √ |
| §11.3 失败隔离 | 单组件/入口失败不影响其他 | √ 逐入口跳过 + 告警 |
| §11.1 至少一组件 | 支持至少一种组件类型 | √ Groovy 扩展 |

> 说明：协议将 skill 与 MCP 定义为唯二标准组件（§7）。Groovy 扩展作为 XAgent **客户端扩展**（§8）承载，不冒充标准组件；MCP 按“忽略不支持组件类型”合规。若未来 Groovy 插件需暴露 MCP 能力，可在 `mcp.json` 内引用插件捆绑的服务器可执行文件（阶段三可选）。
---

## 10. 实施阶段规划

### 阶段一：清单解析 + 目录包发现 + 工具注册（最小闭环，路线 A）—— ✅ 已完成（2026-08-14）

| 模块 | 交付 | 状态 |
|---|---|---|
| `GroovyPluginLoader` | 目录包 `plugin.json` 解析校验 + legacy 合成 + 入口围栏 + GroovyShell 加载 | ✅ |
| `GroovyPluginRegistry` | 进程级常驻注册表（工具/hook/interceptor 三类索引 + 来源/命名空间登记） | ✅ |
| `ClosureToolCallback` | 闭包 ↔ ToolCallback 适配，delegate 绑定宿主上下文（委托 FunctionToolCallback） | ✅ |
| `PLUGIN_ROOT`/`PLUGIN_DATA` 注入 | 加载器编译前注入 + `${}` 单次展开 + 路径围栏 | ✅ |
| 接入 `LocalAgent` | tools 并入（构造 GroovyScriptTool 前）+ hooks/interceptors 并入 | ✅ |
| 验证 | `my-tools/plugin.json + entry.groovy`；GroovyPluginLoaderTest（加载/工具调用/非法 schema 拒绝）通过 | ✅ |

### 阶段二：工作流上下文 + 运行时热挂载 —— 核心已完成（2026-08-14），可移植层迁移【已搁置】

> **搁置说明（2026-08-14）**：可移植层迁移（ShellTools/WorkerTool/MsgTool/ContextCacheTool 插件化）经用户确认**明确搁置**，
> 除非后续明确决定开始移植，否则不推进。PluginContext 能力容器已就绪，未来随时可启动。

- ✅ `PluginContext` 能力容器（§5.6）：白名单注入 client/chatModel/conversation/workers/sharedCache；
- ✅ `ConversationAccess`：封装 ToolContextHelper 读写（messages/state/replaceMessages/setState/appendMessage）；
- ✅ 消息双向转换器（Message ↔ Map）；
- ✅ 插件实例生命周期 init/close（§5.7）+ 每插件状态实例（脚本局部变量跨调用保留）；
- ✅ `GroovyToolBindings` 增加 `inject(key)`/`conversation` 访问 + `plugin(name, desc)` 运行时注册通道；
- ⏸ 可移植层迁移：将 ShellTools / WorkerTool / MsgTool / ContextCacheTool 改为插件（经 PluginContext 注入依赖）——**已搁置**；
- ⬜ 路线 B：动态 tools 注入（验证 graph 是否支持；不支持则 interceptModel 注入）；
- ⬜ 验证：插件脚本读取对话 → 截断 → 写回，复用 ConversationCompactionTool 安全切割逻辑。
- 实现说明：mergeDesc 现合并 init/close（首条生效）；reload 先 unregisterAll()（逐个 close()）再重扫；
  LocalAgent 在 buildAgent 中以完整 PluginContext（client/chatModel）触发 loader（staticToolCallbackProvider 幂等并入）。

### 阶段三（可选）：热重载、MCP 桥接与安全加固

- 复用 skill `autoReload` 思路做插件增量重载（卸载时执行 `close()` 释放状态，§5.7）；
- 拦截器顺序声明（after/before 内置）生效（§7.2）；
- `SecureASTCustomizer` 完整白名单 + 版本/句柄管理；
- 插件来源审计日志；
- （可选）Groovy 插件经 `mcp.json` 暴露为 MCP 服务器（捆绑可执行文件 + `PLUGIN_ROOT` 相对 command）。

---

## 11. 风险与依赖

| 风险/注意点 | 说明/缓解 |
|---|---|
| 安全隔离 | 脚本能读写对话/状态、注入钩子/拦截器且长期落盘，必须用 SecureASTCustomizer + 独立 ClassLoader 收口 |
| 闭包跨脚本存活 | 闭包持有脚本 ClassLoader 引用，需把 delegate/owner 绑到宿主对象避免类卸载泄漏 |
| ToolContext 可用性 | 需验证脚本工具执行栈中 ToolContextHelper.getState 能取到 OverAllState（ConversationCompactionTool 已证明可行） |
| 装配链快照 | 路线 A 只在构建期并入（构建后固定）；要做真·常驻热挂载需路线 B（动态 tools） |
| ReactAgent tools 动态性 | graph 的 `builder.tools(list)` 构建后是否可变需验证；不可变则必须走 interceptModel 注入 |
| 协议闭式 schema 的演进 | `$schema` 版本升级需维护兼容映射（协议 §10）；未知版本拒绝并报告 |
| `PLUGIN_DATA` 生命周期 | 需跨更新保留、卸载删除；目录并发与清理策略需与安装管理约定 |
| schema 质量 | 自动加载工具 name/desc/schema 差会污染模型工具集；失败跳过 + 告警 + 内置名去重 |
| 双目录根（`plugins/` vs `tools/`） | legacy 与标准目录并存可能造成混淆；建议标准分发统一走 `~/.agents/plugins/` |
| **依赖注入缺口** | ShellTools/WorkerTool/MsgTool/ContextCacheTool 需外部对象，插件化前必须先建 PluginContext（§5.6）与注入白名单（§8.5） |
| **有状态实例泄漏** | 有状态插件（Shell 会话/缓存/worker）需每插件实例 + init/close 生命周期（§5.7）；卸载不干净会泄漏资源 |
| **拦截器顺序被破坏** | 插件注入的 interceptor 若乱序会破坏重试/安全语义；需 after/before 内置的追加式顺序（§7.2/§8.5） |
| **安全边界被绕过** | 插件若自带裸 IO 工具可绕过 FilesystemInterceptor 路径校验；必须强制复用其校验链路（§7.2） |

---

## 12. 待确认事项

- [ ] 扩展命名空间用 `com.xr21.agent` 还是 `ai.xr21.agent`（需控制反向域名一致性，协议 §8）；
- [ ] 插件根目录统一为 `~/.agents/plugins/`（新）还是沿用 `~/.agents/tools/`（legacy 兼容）——建议 `plugins/` 为标准根、`tools/` 仅作 legacy；
- [ ] Groovy 入口默认进入模型主工具集，还是按需启用 / 由模型 tools.names 探索；
- [ ] 路线 B 是否本期做：ReactAgent `builder.tools(list)` 构建后能否动态变更（决定热挂载可行性）；
- [ ] 写对话（replaceMessages/setState）与注册 hook/interceptor 是否在非 yolo 模式加审批；
- [ ] 是否支持插件间工具互相调用（A 插件调 B 插件注册的工具；建议通过共享 `GroovyToolBindings` 或 registry 查询实现）；
- [ ] 是否在插件包内携带协议要求的 `schemas/` 或外部校验依赖，还是仅本地内联校验闭式 schema（协议 §5.2 禁止加载时拉取 schema）；
- [ ] `PluginContext` 注入能力边界（§5.6）：`client`/`chatModel`/`workers` 等是否全部对插件开放，还是按信任级分档（系统级/可信/沙箱）；
- [ ] 插件是否允许自带**状态与生命周期**（§5.7 init/close），还是阶段一先做无状态插件、状态支持放到阶段二；
- [ ] 插件拦截器顺序（§7.2）：是否本期实现 `after`/`before` 内置声明，还是统一追加到内置之后。
