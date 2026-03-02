# XAgent 编码智能体项目指南

## 🎯 项目概述

**XAgent** 是一个基于 Spring AI 和 Alibaba AI 构建的多平台 AI Agent 库，通过 ACP (Agent Client Protocol) 协议与客户端通信，提供完整的本地文件操作和终端命令执行能力。

## 🏗️ 项目架构

### 核心组件

1. **AcpAgent** - ACP 协议主入口
   - 管理 ACP 会话生命周期
   - 处理客户端请求和流式响应
   - 集成 MCP 服务器工具
   - 支持多模态输入处理

2. **LocalAgent** - 本地 Agent 核心实现
   - 基于 Reactor 的响应式处理
   - 文件操作工具集（读/写/编辑/搜索）
   - Shell 命令执行
   - 上下文管理和令牌清理
   - 错误处理和重试机制

3. **AiModels** - AI 模型配置
   - 基于 JSON 配置文件的模型管理
   - 多供应商支持（通过 providerId 引用）
   - 支持推理模型（thinking/reasoningEffort）
   - 动态温度和最大令牌数配置

### 技术栈

- **语言**: Java 17, Kotlin 1.9.24
- **框架**: Spring AI 1.1.0, Spring AI Alibaba 1.1.0.0
- **协议**: ACP SDK 0.9.0-SNAPSHOT
- **构建工具**: Gradle 8.x
- **原生支持**: GraalVM Native Image

## 🛠️ 可用工具集

### 文件操作工具
| 工具名称 | 描述 | 使用场景 |
|---------|------|---------|
| `grep` | 文件内容搜索 | 查找特定代码模式、错误信息 |
| `glob` | 文件模式匹配 | 批量查找特定类型的文件 |
| `edit_file` | 编辑文件内容 | 修改代码、配置文件 |
| `write_file` | 创建/写入文件 | 创建新文件或覆盖内容 |
| `read_file` | 读取文件内容 | 查看代码、日志、配置文件 |
| `ls` | 列出目录内容 | 探索项目结构 |

### 终端命令工具
| 工具名称 | 描述 | 使用场景 |
|---------|------|---------|
| `Bash` | 执行终端命令 | 运行构建命令、脚本、Git操作 |
| `BashOutput` | 获取后台命令输出 | 监控长时间运行进程 |
| `KillShell` | 终止后台命令 | 停止不需要的进程 |

### 上下文缓存工具
| 工具名称 | 描述 | 使用场景 |
|---------|------|---------|
| `contextCacheTool` | 指针数据读取器 | 重新获取超长工具调用参数/结果 |

### 其他工具
| 工具名称 | 描述 | 使用场景 |
|---------|------|---------|
| `WebSearch` | 网络搜索 | 查找文档、API 参考 |
| `FeedBack` | 用户反馈收集 | 确认操作、获取输入 |
| `write_todos` | ACP 任务管理 | 复杂任务规划（ACP Plan 模式） |

## 📁 项目结构

```
ai-agents/
├── library/                          # 主要模块
│   ├── src/
│   │   ├── jvmMain/
│   │   │   └── java/com/xr21/ai/agent/
│   │   │       ├── agent/
│   │   │       │   ├── AcpAgent.java    # ACP 协议主入口
│   │   │       │   └── LocalAgent.java  # 本地 Agent 实现
│   │   │       ├── config/
│   │   │       │   ├── AiModels.java    # AI 模型配置入口
│   │   │       │   ├── ModelConfigLoader.java  # 配置加载器
│   │   │       │   └── ModelsConfig.java # 配置数据类
│   │   │       ├── entity/              # 数据实体
│   │   │       ├── interceptors/       # 拦截器实现
│   │   │       ├── tools/               # 工具实现
│   │   │       └── utils/               # 工具类
│   │   └── jvmTest/                    # 测试代码
│   ├── build.gradle.kts               # 模块构建配置
│   ├── native-reflect-config.json     # GraalVM 反射配置
│   └── native-resource-config.json    # GraalVM 资源配置
├── build.gradle.kts                   # 根构建配置
├── settings.gradle.kts                # 项目设置
└── gradle/                            # Gradle 包装器
```

### 📦 Entity 数据实体

| 类名 | 描述 |
|-----|------|
| `AcpSession` | ACP 会话状态，管理 sessionId、threadId、cwd 和历史记录 |
| `AgentOutput` | Agent 输出结构，包含节点、时间戳、数据、消息、令牌使用量等 |
| `CancellableRequest` | 可取消请求，支持中断执行中的工具调用（如后台进程） |
| `ToolResult` | 标准化工具结果封装，支持 ACP Schema 特性（内容、位置、终端等） |

### 🔧 Interceptors 拦截器

| 拦截器 | 描述 |
|-------|------|
| `AcpTodoListInterceptor` | ACP 任务列表管理，支持 Plan 模式的任务状态更新 |
| `ContextEditingInterceptor` | 上下文编辑，管理令牌数量，支持合并连续 UserMessage |
| `FilesystemInterceptor` | 文件系统操作拦截，验证路径安全性 |
| `ToolRetryInterceptor` | 工具调用重试，失败时自动重试最多 2 次 |

### 🛠️ Tools 工具实现

#### 文件操作工具
| 工具 | 描述 |
|-----|------|
| `ReadFileTool` | 读取文件内容，支持分页读取 |
| `WriteFileTool` | 创建或覆盖文件内容 |
| `EditFileTool` | 编辑文件内容（小步修改） |
| `GrepTool` | 文件内容搜索 |
| `GlobTool` | 文件模式匹配 |
| `ListFilesTool` | 列出目录内容 |

#### 终端命令工具
| 工具 | 描述 |
|-----|------|
| `ShellTools` | 终端命令执行（包含 Bash、BashOutput、KillShell） |

#### 上下文管理工具
| 工具 | 描述 |
|-----|------|
| `ContextCacheTool` | 指针数据读取器，用于重新获取超长工具调用参数/结果 |
| `AcpWriteTodosTool` | ACP 任务管理，支持 Plan 模式 |

#### 其他工具
| 工具 | 描述 |
|-----|------|
| `FeedBackTool` | 用户反馈收集 |
| `WebSearchTool` | 网络搜索 |
| `ToolKindFind` | 工具类型查找 |

### ⚙️ Utils 工具类

| 类名 | 描述 |
|-----|------|
| `DefaultTokenCounter` | 默认令牌计数器 |
| `GitignoreUtil` | .gitignore 文件解析和路径匹配，支持缓存和线程安全 |
| `Json` | JSON 序列化/反序列化工具（基于 Jackson） |
| `SinksUtil` | Reactive Streams 流处理工具，用于 SSE 推送 |
| `ToolsUtil` | MCP 工具加载器，支持 STDIO 和 HTTP 模式 |

## 🚀 快速开始

### 环境要求
- JDK 17+
- GraalVM（可选，用于原生编译）
- 有效的 AI 模型 API 密钥

### 运行方式

1. **作为 ACP Agent 运行**
   ```bash
   ./gradlew :library:runAcpAgent
   ```

2. **使用 Fat JAR**
   ```bash
   java -jar library/build/libs/library-1.0.0-all.jar
   ```

3. **原生可执行文件（需要 GraalVM）**
   ```bash
   ./gradlew :library:nativeCompile
   ./library/build/native/nativeCompile/ai-agents
   ```

## 📋 编码智能体工作流程

### 1. 项目探索
- 使用 `ls` 查看目录结构
- 使用 `read_file` 读取关键文件（README.md、build.gradle.kts 等）
- 使用 `grep` 搜索特定代码模式

### 2. 代码编辑最佳实践
- **小步修改**: 每次最多修改 3 行内容
- **精确搜索**: 避免使用 `**/*` 模糊搜索，使用明确的关键字
- **文件验证**: 修改前先读取文件内容确认
- **备份意识**: 重要修改前考虑备份或版本控制

### 3. 构建和测试
- 使用 `Bash` 执行 Gradle 命令
- 监控构建过程使用 `Bash` 和 `BashOutput`
- 测试失败时查看错误日志

### 4. 复杂任务规划
- 对于复杂任务（3+ 步骤），使用 `write_todos` 工具
- 遵循 ACP Plan 协议：标记 IN_PROGRESS 前不开始工作
- 完成任务后立即标记 COMPLETED

## ⚙️ 配置说明

### AI 模型配置（JSON 配置文件）

项目使用 JSON 配置文件管理 AI 模型，支持多供应商配置：

**配置文件位置**: `library/src/main/resources/models-config.json`

```json
{
  "providers": [
    {
      "providerId": "volcengine",
      "baseUrl": "https://ark.cn-beijing.volces.com/api/v3",
      "apiKey": "${AI_VOLC_API_KEY}"
    },
    {
      "providerId": "openrouter",
      "baseUrl": "https://openrouter.ai/api/v1",
      "apiKey": "${AI_OPEN_ROUTER_API_KEY}"
    }
  ],
  "models": [
    {
      "modelId": "kimi-k2.5",
      "modelName": "moonshot-k2.5",
      "temperature": 0.65,
      "maxTokens": 500000,
      "providerId": "volcengine",
      "isDefault": true
    }
  ]
}
```

**配置说明**:
- `providers`: 定义 API 供应商配置（baseUrl 和 apiKey）
- `models`: 定义模型配置，通过 `providerId` 引用供应商
- `isDefault`: 指定默认使用的模型
- 支持环境变量引用（如 `${AI_VOLC_API_KEY}`）

### ACP 协议支持
- 通过标准输入/输出与客户端通信
- 支持 MCP (Model Context Protocol) 服务器集成
- 实时工具调用状态更新

## 🔧 拦截器系统

项目包含以下拦截器，优化智能体行为：

1. **ContextEditingInterceptor** - 上下文编辑，管理令牌数量，支持合并连续UserMessage
2. **ToolErrorInterceptor** - 工具错误处理
3. **ToolRetryInterceptor** - 工具调用重试（最大 2 次）
4. **FilesystemInterceptor** - 文件系统操作拦截
5. **AcpTodoListInterceptor** - ACP 任务列表管理（Plan 模式）

## 💡 使用技巧

### 文件操作
```java
// 读取文件
read_file("/path/to/file.java")

// 搜索内容
grep("TODO", "/src/main/java")

// 编辑文件（小步修改）
edit_file("/path/to/file.java", "old code", "new code")

// 创建文件
write_file("/path/to/newfile.txt", "content")
```

### 终端命令
```java
// 执行命令
Bash("gradle build", 120000, "Build project with Gradle")

// 后台运行
Bash("long-running-process", null, null, true)

// 获取输出
BashOutput("shell_1234567890")

// 终止进程
KillShell("shell_1234567890")
```

### 上下文缓存（处理超长输出）
```java
// 上下文编辑拦截器会自动将超长内容转换为指针
// 指针格式: $ref+工具调用id

// 使用 contextCacheTool 重新获取内容
contextCacheTool(["$ref_tool_call_123", "$ref_tool_call_456"])
```

### 任务规划
```java
// 复杂任务使用 write_todos
write_todos([
  {content: "分析项目结构", status: "IN_PROGRESS", priority: "HIGH"},
  {content: "实现功能A", status: "PENDING", priority: "MEDIUM"},
  {content: "测试功能A", status: "PENDING", priority: "MEDIUM"}
])
```

## 🐛 常见问题

### 1. 文件操作权限
- 所有文件操作仅限工作目录（cwd）内
- 使用绝对路径确保正确性

### 2. 命令执行超时
- 默认超时 120 秒（2 分钟）
- 长时间运行命令使用后台模式

### 3. 上下文限制
- 上下文令牌限制：500,500 令牌
- 超出时会自动清理，保留最近 8 条工具消息
- 超长内容自动转换为指针，使用 `contextCacheTool` 重新获取

### 4. 工具调用重试
- 工具失败时自动重试 2 次
- 重试间隔：1 秒 → 1.5 秒 → 最大 5 秒

### 5. Git 操作
- 仅在用户明确请求时创建提交
- 避免使用交互式命令（如 `git rebase -i`）
- 不强制推送到 main/master 分支

## 📚 扩展开发

### 添加新工具
在 `tools` 包中创建新工具类：
```java
public class MyCustomTool {
    @Tool(description = "工具描述")
    public String execute(String param) {
        // 实现逻辑
    }
}
```

### 添加新模型
在 JSON 配置文件中添加模型配置：
```json
{
  "models": [
    {
      "modelId": "my-new-model",
      "modelName": "provider-model-name",
      "temperature": 0.7,
      "maxTokens": 4096,
      "providerId": "my-provider"
    }
  ]
}
```

## 🔒 安全注意事项

1. **文件权限**: 仅操作工作目录内文件
2. **命令执行**: 避免执行危险命令
3. **敏感信息**: 不处理 .env、credentials.json 等敏感文件
4. **用户确认**: 重要操作前使用 FeedBack 工具确认

## 📞 支持与贡献

- 查看详细文档：README.md
- 报告问题：GitHub Issues
- 贡献代码：Pull Requests

---

**最后更新**: 2026年2月
**项目状态**: 活跃开发中
**协议**: Apache License 2.0