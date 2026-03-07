# XAgent

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Java Version](https://img.shields.io/badge/Java-17%2B-b07219)](https://openjdk.org/)
[![Gradle](https://img.shields.io/badge/Gradle-8.x-02303a)](https://gradle.org/)

一个功能强大的本地 AI Agent 库，基于 Spring AI 和 Alibaba AI 构建，支持 ACP (Agent Client Protocol) 协议与客户端通信。

## 项目概述

XAgent 是一个生产级的本地 AI Agent 实现，专为编码任务设计，基于 Spring AI Alibaba Graph 和 ACP 协议构建，提供以下核心能力：

- 🤖 **ACP 协议支持** - 通过标准输入/输出（STDIO）与客户端通信，支持会话初始化、模式切换、模型切换
- 🧠 **多模型支持** - 支持任意 OpenAI 协议的模型服务（火山引擎、DeepSeek、OpenRouter、MiniMax 等），通过 JSON 配置管理
- 📁 **文件操作工具** - 完整的本地文件系统操作能力（读/写/编辑/搜索），支持路径安全验证
- 💻 **终端命令执行** - 安全的 Shell 命令执行支持
- 🔌 **MCP 集成** - 支持 Model Context Protocol 服务器扩展（STDIO/HTTP 模式）
- 💾 **会话管理** - 持久化会话状态和历史记录（FileSystemSaver）
- 🛡️ **权限系统** - 工具调用权限管理（Allow once / Allow always / Reject once / Reject always）
- 👥 **人机协作** - Human-in-the-loop 机制，敏感操作需用户确认
- 📋 **任务列表管理** - ACP Plan 模式的任务状态更新，支持复杂任务规划
- ✂️ **上下文编辑** - 动态上下文管理 实现智能体长期运行
- 👷 **Workers 模式** - 动态并行子代理模式，支持复杂任务分解
- 🎯 **Skills 技能系统** - 支持自定义技能加载（用户级/项目级）
- 🛡️ **拦截器系统** - 可配置的请求/响应拦截处理（上下文编辑、工具重试、大结果驱逐）
- ⚡ **原生支持** - GraalVM Native Image 编译，超快启动

## 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| **语言** | Java | 17 |
| **语言** | Kotlin | 1.9.24 |
| **框架** | Spring Framework | 6.2.0 |
| **框架** | Spring AI | 1.1.2 |
| **框架** | Spring AI Alibaba | 1.1.2.0 |
| **协议** | ACP SDK | 0.9.0-SNAPSHOT |
| **响应式** | Reactor Core | 3.6.0 |
| **JSON** | Jackson | 2.17.0 |
| **工具库** | Lombok | 1.18.30 |
| **构建工具** | Gradle | 8.x |
| **原生编译** | GraalVM Native Build Tools | 0.10.2 |
| **发布** | Maven Publish | 0.34.0 |

## 项目结构

```
XAgent/
├── library/                                    # 主模块
│   ├── src/
│   │   ├── jvmMain/
│   │   │   └── java/com/xr21/ai/agent/
│   │   │       ├── agent/
│   │   │       │   ├── AcpAgent.java          # ACP 协议主入口
│   │   │       │   └── LocalAgent.java        # 本地 Agent 核心
│   │   │       ├── config/
│   │   │       │   ├── AiModels.java          # AI 模型配置入口
│   │   │       │   ├── ModelConfigLoader.java # 配置加载器
│   │   │       │   └── ModelsConfig.java      # 配置数据类
│   │   │       ├── entity/
│   │   │       │   ├── AcpSession.java        # ACP 会话状态
│   │   │       │   ├── AgentOutput.java       # Agent 输出
│   │   │       │   ├── CancellableRequest.java # 可取消请求
│   │   │       │   └── ToolResult.java        # 工具结果
│   │   │       ├── interceptors/
│   │   │       │   ├── AcpTodoListInterceptor.java   # ACP 任务列表
│   │   │       │   ├── ContextEditingInterceptor.java # 上下文编辑
│   │   │       │   ├── FilesystemInterceptor.java    # 文件系统拦截
│   │   │       │   ├── ToolRetryInterceptor.java     # 工具重试
│   │   │       │   └── WorkerInterceptor.java        # Worker 拦截
│   │   │       ├── tools/
│   │   │       │   ├── AcpWriteTodosTool.java  # ACP 任务管理
│   │   │       │   ├── ContextCacheTool.java    # 上下文缓存
│   │   │       │   ├── EditFileTool.java         # 编辑文件
│   │   │       │   ├── FeedBackTool.java        # 用户反馈
│   │   │       │   ├── GlobTool.java             # 文件模式匹配
│   │   │       │   ├── GrepTool.java             # 文件搜索
│   │   │       │   ├── ListFilesTool.java        # 目录列表
│   │   │       │   ├── ReadFileTool.java         # 读取文件
│   │   │       │   ├── ShellTools.java          # Shell 命令执行
│   │   │       │   ├── ToolKindFind.java         # 工具类型查找
│   │   │       │   ├── WebSearchTool.java       # 网络搜索
│   │   │       │   ├── WorkerTool.java          # Worker 工具
│   │   │       │   └── WriteFileTool.java       # 写入文件
│   │   │       └── utils/
│   │   │           ├── DefaultTokenCounter.java  # 令牌计数
│   │   │           ├── GitignoreUtil.java         # Gitignore 解析
│   │   │           ├── Json.java                  # JSON 工具
│   │   │           ├── PermissionSettings.java   # 权限设置
│   │   │           ├── SinksUtil.java            # 流处理工具
│   │   │           └── ToolsUtil.java            # MCP 工具加载
│   │   └── jvmTest/                           # 测试代码
│   ├── build.gradle.kts                       # 模块构建配置
│   ├── native-reflect-config.json             # GraalVM 反射配置
│   └── native-resource-config.json            # GraalVM 资源配置
├── docker/                                     # Docker 配置
├── gradle/                                     # Gradle 包装器
├── build.gradle.kts                           # 根构建配置
└── settings.gradle.kts                        # 项目设置
```

## 核心组件

### AcpAgent

ACP 协议主入口，负责：
- 管理 ACP 会话生命周期（初始化、新建、加载会话）
- 处理客户端请求（initialize / newSession / loadSession / prompt）
- 流式返回响应
- 集成 MCP 服务器工具（STDIO/HTTP 模式）

### LocalAgent

本地 Agent 核心实现，基于 Spring AI Alibaba Graph 构建：
- React 模式（ReAct 推理 + 工具调用）
- 可配置的检查点保存器（MemorySaver / FileSystemSaver）
- 动态工具注册和管理
- 响应式流处理（Reactor）

### AiModels

JSON 配置形式的 AI 模型管理，支持：
- 多模型提供商（火山引擎、DeepSeek、OpenRouter、MiniMax 等）
- 动态 API 密钥和基础 URL 配置
- 温度和最大令牌数自定义

## 可用工具

### 文件操作工具

| 工具 | 描述 |
|------|------|
| `read_file` | 读取文件内容，支持分页 |
| `write_file` | 创建/覆盖文件 |
| `edit_file` | 精确字符串替换编辑 |
| `grep` | 文件内容搜索 |
| `glob` | 文件模式匹配 |
| `ls` | 列出目录内容 |

### 终端命令工具

| 工具 | 描述 |
|------|------|
| `Bash` | 执行终端命令 |
| `BashOutput` | 获取后台命令输出 |
| `KillShell` | 终止后台命令 |

### 其他工具

| 工具 | 描述 |
|------|------|
| `WebSearch` | 网络搜索 |
| `FeedBack` | 用户反馈收集 |
| `contextCacheTool` | 上下文缓存读取 |
| `write_todos` | ACP 任务管理 |

## 快速开始

### 环境要求

- JDK 17+
- GraalVM（可选，用于原生编译）
- 有效的 AI 模型 API 密钥

### 环境变量配置

```bash
# 火山引擎
export AI_VOLC_BASE_URL="https://ark.cn-beijing.volces.com/api/coding/v3"
export AI_VOLC_API_KEY="your-api-key"

# DeepSeek
export AI_DEEPSEEK_BASE_URL="https://api.deepseek.com"
export AI_DEEPSEEK_API_KEY="your-api-key"

# OpenRouter
export AI_OPEN_ROUTER_BASE_URL="https://openrouter.ai/api"
export AI_OPEN_ROUTER_API_KEY="your-api-key"

# MiniMax
export AI_CUCLOUD_BASE_URL="https://aigw-gzgy2.cucloud.cn:8443"
export AI_CUCLOUD_API_KEY="your-api-key"
```

### 构建项目

```bash
# 克隆项目
git clone https://github.com/xr21/XAgent.git
cd XAgent

# 构建项目
./gradlew build

# 构建 Fat JAR
./gradlew :library:fatJar

# 原生编译（需要 GraalVM）
./gradlew :library:nativeCompile
```

### 运行方式

#### 作为 ACP Agent 运行

```bash
./gradlew :library:runAcpAgent
```

#### 运行异步客户端

```bash
./gradlew :library:runAsyncAgentClient
```

#### 使用 Fat JAR

```bash
java -jar library/build/libs/XAgent-0.0.1-all.jar
```

#### 原生可执行文件

```bash
./gradlew :library:nativeCompile
# Windows: library\build\native\nativeCompile\XAgent.exe
# Linux/macOS: ./library/build/native/nativeCompile/XAgent
```

## AI 模型配置

项目使用 JSON 配置文件管理 AI 模型，配置文件位于 `${user.home}\.agi_working\models.json`：

```json
{
  "providers": [
    {
      "providerId": "volcengine",
      "baseUrl": "https://ark.cn-beijing.volces.com/api/coding/v3",
      "apiKey": "${AI_VOLC_API_KEY}"
    },
    {
      "providerId": "deepseek",
      "baseUrl": "https://api.deepseek.com",
      "apiKey": "${AI_DEEPSEEK_API_KEY}"
    }
  ],
  "models": [
    {
      "modelId": "doubao-seed-2.0-code",
      "modelName": "doubao-seed-2.0-code",
      "temperature": 0.75,
      "maxTokens": 8000,
      "providerId": "volcengine",
      "isDefault": true
    }
  ]
}
```

### 支持的模型
 所有OpenAI API格式且支持工具调用的模型

## 拦截器系统

项目包含以下拦截器，优化智能体行为：

| 拦截器 | 描述 |
|--------|------|
| `AcpTodoListInterceptor` | ACP 任务列表管理，处理 Plan 模式的任务状态更新 |
| `ContextEditingInterceptor` | 上下文编辑，管理令牌数量，支持合并连续 UserMessage |
| `FilesystemInterceptor` | 文件系统操作拦截，路径安全验证（工作目录限制、权限检查） |
| `ToolRetryInterceptor` | 工具调用重试，失败时自动重试最多 2 次 |
| `WorkerInterceptor` | Worker 拦截器，管理并行子代理的创建和结果处理 |

## GraalVM 原生编译

### 前置要求

1. 安装 GraalVM for JDK 17
2. 安装 native-image 组件：

```bash
gu install native-image
```

### 构建步骤

```bash
# 1. 清理并构建项目
./gradlew clean build

# 2. 构建 Fat JAR
./gradlew :library:fatJar

# 3. 编译为原生可执行文件
./gradlew :library:nativeCompile
```

### 原生镜像优势

| 特性 | JVM 模式 | 原生模式 |
|------|---------|---------|
| 启动时间 | ~5-10 秒 | ~0.1-0.5 秒 |
| 内存占用 | ~200-500 MB | ~50-150 MB |
| 部署复杂度 | 需要 JRE | 单文件部署 |

## 开发指南

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

在 `models.json` 配置文件中添加：

```json
{
  "providers": [
    {
      "providerId": "my-provider",
      "baseUrl": "https://api.myprovider.com",
      "apiKey": "${AI_MY_PROVIDER_API_KEY}"
    }
  ],
  "models": [
    {
      "modelId": "my-model",
      "modelName": "my-model-name",
      "temperature": 0.7,
      "maxTokens": 4096,
      "providerId": "my-provider",
      "isDefault": false
    }
  ]
}
```

## 测试

```bash
# 运行测试
./gradlew test

# 查看测试报告
open library/build/reports/tests/test/index.html
```

## GitHub Actions

项目配置了完整的 CI/CD 工作流：

- **CI** - 每次推送和 PR 自动运行测试和构建
- **Native Build** - 跨平台原生编译（Linux、macOS、Windows）
- **Release** - 标签发布自动创建 GitHub Release

## 许可证

本项目采用 Apache License 2.0 许可证。详见 [LICENSE](LICENSE) 文件。

## 贡献

欢迎提交 Issue 和 Pull Request！请先阅读 [CONTRIBUTING.md](CONTRIBUTING.md) 了解贡献指南。

---

*最后更新：2026 年 3 月*