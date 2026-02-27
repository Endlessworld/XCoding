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
   - 多模型提供商支持（火山引擎、OpenRouter、MiniMax 等）
   - 动态 API 密钥和基础 URL 配置
   - 温度和最大令牌数自定义

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
| `Bash` | 执行终端命令 | 运行构建命令、脚本 |
| `BashOutput` | 获取后台命令输出 | 监控长时间运行进程 |
| `KillShell` | 终止后台命令 | 停止不需要的进程 |

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
│   │   │       │   └── AiModels.java    # AI 模型配置
│   │   │       ├── entity/              # 数据实体
│   │   │       ├── interceptors/        # 拦截器实现
│   │   │       ├── tools/               # 工具实现
│   │   │       └── utils/               # 工具类
│   │   └── jvmTest/                    # 测试代码
│   ├── build.gradle.kts               # 模块构建配置
│   ├── native-reflect-config.json     # GraalVM 反射配置
│   └── native-resource-config.json    # GraalVM 资源配置
├── build.gradle.kts                   # 根构建配置
├── settings.gradle.kts                # 项目设置
└── gradle/                           # Gradle 包装器
```

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

### AI 模型配置
项目支持多个 AI 模型提供商，通过环境变量配置：
```bash
# 火山引擎
export AI_VOLC_BASE_URL="https://..."
export AI_VOLC_API_KEY="your-api-key"

# OpenRouter
export AI_OPEN_ROUTER_BASE_URL="https://..."
export AI_OPEN_ROUTER_API_KEY="your-api-key"

# 默认使用 Kimi K2.5 模型
```

### ACP 协议支持
- 通过标准输入/输出与客户端通信
- 支持 MCP (Model Context Protocol) 服务器集成
- 实时工具调用状态更新

## 🔧 拦截器系统

项目包含以下拦截器，优化智能体行为：

1. **ContextEditingInterceptor** - 上下文编辑，管理令牌数量
2. **ToolErrorInterceptor** - 工具错误处理
3. **ToolRetryInterceptor** - 工具调用重试（最大 2 次）
4. **LargeResultEvictionInterceptor** - 大结果驱逐
5. **FilesystemInterceptor** - 文件系统操作拦截
6. **AcpTodoListInterceptor** - ACP 任务列表管理（Plan 模式）

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

### 4. 工具调用重试
- 工具失败时自动重试 2 次
- 重试间隔：1 秒 → 1.5 秒 → 最大 5 秒

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
在 `AiModels` 枚举中添加新条目：
```java
MY_NEW_MODEL("model-name", temperature, maxTokens, baseUrlSupplier, apiKeySupplier)
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