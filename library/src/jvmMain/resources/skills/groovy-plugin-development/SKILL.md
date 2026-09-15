---
name: groovy-plugin-development
description: Use when creating, developing, or debugging XAgent Groovy plugins — writing plugin.json manifest or entry.groovy export contract (tools/hooks/interceptors/init/close), registering tools into the running agent, handling PLUGIN_ROOT/PLUGIN_DATA, or fixing plugin load failures. 适用于创建/开发/调试本项目的 Groovy 插件。
metadata:
  short-description: "XAgent Groovy 插件开发指南（Agent Plugins v1.0.0 协议）"
  author: XR21
---

# Groovy Plugin Development (XAgent)

## Overview

XAgent 的插件机制：**一个插件 = 一个目录包（根目录含 `plugin.json` 清单）+ Groovy 入口脚本**。插件由 `GroovyPluginRegistry` 常驻挂载，在 agent 装配时把脚本声明的 **tools / hooks / interceptors** 三种能力并入运行中 agent，并支持 `init`/`close` 生命周期与每插件状态实例。

核心链路：**清单声明入口 → 脚本 return 描述 Map → 注册表常驻挂载 → 装配进 agent 的 tools/hooks/interceptors。**

## When to Use

Use when：
- 需要为 XAgent 创建新插件（工具/钩子/拦截器），常驻挂载进运行中的 agent
- 需要编写 `plugin.json` 清单或 `entry.groovy` 入口脚本
- 需要把一次性脚本能力沉淀为可分发、可复用、跨客户端移植的插件包
- 需要调试插件加载失败、工具未生效、钩子/拦截器不工作

Don't use when：
- 一次性脚本（执行完即销毁、无需复用）——直接用 `run_groovy_script`
- 编写 skill（那是 `writing-skills` 的职责）
- 插件化安全边界/基础设施（FilesystemInterceptor、重试拦截器、HumanInTheLoopHook、GroovyScriptTool 本身，见 GROOVY_PLUGIN_DESIGN §5.8）

## Plugin Package Layout（包模型）

插件位于两级发现根（同名项目级覆盖全局级）：
- 全局：`~/.agents/plugins/`
- 项目：`<cwd>/.agents/plugins/`

```text
<plugin-name>/
├── plugin.json                  # 必需：清单（闭式 schema）
├── entry.groovy                 # Groovy 入口（清单 entrypoints 声明，./ 相对）
├── com.xr21.agent/              # 可选：顶层扩展目录（捆绑资源）
│   ├── references/runbook.md
│   └── scripts/helper.groovy
├── skills/                      # 可选：skill 组件（协议固定位置）
└── LICENSE / CHANGELOG.md
```

legacy 便捷模式：`~/.agents/tools/*.groovy` 或 `<cwd>/.agents/tools/*.groovy` 松散文件自动加载并包装为合成清单（快速原型用；新插件建议目录包）。

## Development Workflow

1. **建目录包**：`<cwd>/.agents/plugins/<plugin-name>/` 下放 `plugin.json` 和 `entry.groovy`
2. **写清单**：闭式 schema（必填 `$schema`+`name`），入口声明在 `extensions["com.xr21.agent"].groovy.entrypoints`（详参 `references/manifest-reference.md`）
3. **写入口**：脚本 `return [name, description, tools, hooks, interceptors, init, close]` 描述 Map（详参 `references/entry-point-reference.md`）
4. **验证**：agent 启动日志无 "skip" 告警；模型可调用新工具且返回正常

## Quick Reference（快速参考）

### plugin.json 最小清单

```json
{
  "$schema": "https://agent-plugins.org/schemas/1.0.0/plugin.schema.json",
  "name": "my-tools",
  "version": "1.0.0",
  "description": "插件用途说明",
  "extensions": {
    "com.xr21.agent": {
      "groovy": { "entrypoints": ["./entry.groovy"] }
    }
  }
}
```

### entry.groovy 最小契约

```groovy
return [
    name: "my-tools",
    description: "插件说明",
    tools: [
        [
            name: "my_tool",
            description: "工具说明",
            inputSchema: [type: "object", properties: [arg: [type: "string", description: "参数说明"]], required: []],
            run: { Map args -> return [result: "ok"] }
        ]
    ]
]
```

### 脚本内绑定速查

| 绑定 | 用途 |
|---|---|
| `tools.xxx(...)` | 编排调用宿主已注册工具 |
| `tools.inject("client"/"chatModel"/"conversation"/"workers"/"sharedCache")` | 白名单注入宿主能力 |
| `tools.conversation.messages()/state()/replaceMessages()/setState()/appendMessage()` | 读写工作流上下文 |
| `tools.plugin(name, desc)` | 运行时注册常驻能力（下一轮模型调用生效） |
| `PLUGIN_ROOT` / `PLUGIN_DATA` | 插件根（只读）/ 可写持久目录（保留名，不可覆盖） |

## Plugin Capability Boundary（能力边界）

**能做什么**：注册 tools/hooks/interceptors（进模型工具集）、读写工作流上下文（`tools.conversation`）、白名单注入宿主能力（`tools.inject` 仅 client/chatModel/conversation/workers/sharedCache）、编排宿主工具（`tools.xxx`）、使用 `PLUGIN_ROOT`/`PLUGIN_DATA`、每插件状态 + init/close 生命周期、运行时热挂载（`tools.plugin`）。

**不能做什么（硬约束）**：注入白名单外对象；移除/重排内置安全与重试拦截器（只能追加）；绕过 FilesystemInterceptor 路径校验的裸 IO；覆盖 `PLUGIN_ROOT`/`PLUGIN_DATA` 保留名；越出插件根访问路径；import 任意类/反射 Runtime/`Class.forName`/`exec`；注册与内置或已注册重名的工具/hook/interceptor；无限资源消耗（输出上限 200k、超时、注册数上限）。

> 三层插件化范围（§5.8）：**系统层**（宿主地基/安全边界）❌ 内置，**能力层**（无状态工具/钩子）✅ 首选插件化，**可移植层**（需 DI/状态）🟡 依赖 PluginContext。完整清单与自查清单见 `references/capability-boundary.md`。

## Common Mistakes

| 症状 | 原因/修复 |
|---|---|
| 插件整体不加载 | 清单缺 `$schema`/`name` 或类型错 → 致命拒绝，补齐即可 |
| 某入口被跳过（日志 skip） | 入口路径非 `./` 开头，或解析后越出插件根 |
| 工具名冲突被拒 | 与内置/已注册工具重名 → 改名 |
| 工具 description 差 | 模型按描述决定是否调用，务必写清功能与参数 |
| `replaceMessages` 不生效 | 忘了 `ReplaceAllWith.of(list)`，AppendStrategy 不整体替换 |
| `inject` 返回 unknown capability | key 不在白名单；仅 client/chatModel/conversation/workers/sharedCache |
| 试图覆盖 PLUGIN_ROOT/PLUGIN_DATA | 保留名，不可 setState 覆盖 |
| 插件裸 IO 绕过校验 | 文件访问必须复用 FilesystemInterceptor 链路（安全边界） |
| 拦截器乱序破坏重试/安全 | 插件拦截器只能追加，不能移除/重排内置 |

## References

- `references/manifest-reference.md` — plugin.json 完整闭式 schema、名称约束、extensions 命名空间
- `references/entry-point-reference.md` — entry.groovy 导出契约、tools/hooks/interceptors 详细定义、bindings API、生命周期、环境变量
- `references/capability-boundary.md` — 插件能力边界（能做什么/不能做什么/三层插件化范围/自查清单）
- `templates/plugin.json` — 可复用清单模板
- `templates/entry.groovy` — 可复用入口模板（含 tools/hooks/interceptors 三类完整示例）

参考实现：项目内 `.agents/plugins/my-tools/` 与 `.agents/plugins/dev-env-check/`（完整范例）；设计文档 `doc/GROOVY_PLUGIN_DESIGN.md`、协议 `doc/agent-plugins1.0.0.md`。
