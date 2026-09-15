# plugin.json Manifest Reference（清单参考）

> 来源：`doc/agent-plugins1.0.0.md` §4-§5（协议） + `doc/GROOVY_PLUGIN_DESIGN.md` §3（XAgent 落地）

## 位置与加载

- 清单必须位于插件根目录的 `plugin.json`。
- 客户端在发现/执行任何组件前必须先解析并校验清单。
- 每个插件仅一个标准清单；无其他文件可覆盖核心字段。

## 闭式 Schema（仅允许的顶层字段）

| 字段 | 类型 | 必需 | 说明 |
|---|---|---|---|
| `$schema` | string | ✅ | 必须为 `https://agent-plugins.org/schemas/1.0.0/plugin.schema.json` |
| `name` | string | ✅ | 见下方名称约束 |
| `version` | string | | 推荐 SemVer，用于更新检查与缓存新鲜度 |
| `description` | string | | 简短说明 |
| `author` | object | | 仅允许 `name`/`email`/`url` 三个字符串字段 |
| `homepage` | string | | 文档/主页 URL |
| `repository` | string | | 源码仓库 URL |
| `license` | string | | SPDX 标识符（推荐） |
| `keywords` | string[] | | 搜索与发现标签 |
| `extensions` | object | | 客户端特定数据，按扩展命名空间 |

**失败语义**：
- 未知顶层字段 → 报告并**忽略**（非致命），继续加载。
- `$schema`/`name` 缺失、类型错或违反约束 → **拒绝整个插件**（致命），不发现/执行任何组件。
- `extensions` 非对象 → 报告并忽略该字段。

## 名称约束（§5.5）

| 约束 | 要求 |
|---|---|
| 长度 | 1-64 字符 |
| 字符集 | `a-z` `0-9` `-` `.`（仅小写字母数字、连字符、点） |
| 首尾 | 必须为字母数字 |
| 重复 | 不允许连续 `--` 或 `..` |

合法：`my-plugin`、`acme.tools`、`lint3r`。非法：`My-Plugin`（大写）、`-start`（首连字符）、`has--double`（连续连字符）、`too.many..dots`（连续点）、空串。

## extensions 命名空间（XAgent = com.xr21.agent）

客户端特定数据必须放在反向域名命名空间下（§8.1）。XAgent 的 Groovy 能力挂在 `com.xr21.agent`：

```json
"extensions": {
  "com.xr21.agent": {
    "groovy": {
      "entrypoints": ["./entry.groovy", "./com.xr21.agent/scripts/helper.groovy"]
    }
  }
}
```

- `entrypoints` 路径必须以 `./` 开头（插件相对路径），解析后必须落在插件根内（§4.1 规则 4）。
- 客户端必须**忽略**未实现命名空间的内容，不校验其值。
- 不支持的组件类型（`mcp.json`、其他客户端命名空间）→ 忽略，不报错。

## 完整示例

```json
{
  "$schema": "https://agent-plugins.org/schemas/1.0.0/plugin.schema.json",
  "name": "dev-env-check",
  "version": "1.0.0",
  "description": "检测当前系统各种开发环境（Java/Node/Python/Git/Docker等）的安装状态与版本",
  "keywords": ["dev", "environment", "toolchain", "detect"],
  "extensions": {
    "com.xr21.agent": {
      "groovy": {
        "entrypoints": ["./entry.groovy"]
      }
    }
  }
}
```

可复用空白模板见 `templates/plugin.json`。
