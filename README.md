# AI Agents

一个多平台的 AI Agent 库，基于 Spring AI 和 Alibaba AI 构建，支持 ACP (Agent Client Protocol) 协议与客户端通信。

## 项目概述

本项目是一个功能丰富的本地 AI Agent 实现，提供以下核心能力：

- **ACP 协议支持** - 通过标准输入/输出传输层与客户端通信
- **多模型支持** - 集成多个 AI 模型提供商（DeepSeek、Kimi、GLM、MiniMax 等）
- **文件操作工具** - 完整的本地文件系统操作能力
- **终端命令执行** - 安全的 Shell 命令执行支持
- **MCP 集成** - 支持 Model Context Protocol 服务器扩展
- **会话管理** - 持久化会话状态和历史记录
- **拦截器系统** - 可配置的请求/响应拦截处理

## 技术栈

- **语言**: Java 17, Kotlin 1.9.24
- **框架**: 
  - Spring AI 1.1.0
  - Spring AI Alibaba 1.1.0.0
  - Reactor Core 3.6.0
- **协议**: ACP SDK 0.9.0-SNAPSHOT
- **工具库**: Hutool 5.8.33, Jackson 2.17.0
- **构建工具**: Gradle 8.x
- **原生支持**: GraalVM Native Image

## 项目结构

```
ai-agents/
├── library/                          # 主要模块
│   ├── src/
│   │   ├── jvmMain/
│   │   │   └── java/com/xr21/ai/agent/
│   │   │       ├── AcpLocalAgent.java    # ACP 协议主入口
│   │   │       ├── LocalAgent.java       # 本地 Agent 实现
│   │   │       ├── AsyncAgentClient.java # 异步客户端
│   │   │       ├── config/
│   │   │       │   └── AiModels.java     # AI 模型配置
│   │   │       ├── entity/
│   │   │       │   └── AgentOutput.java  # Agent 输出实体
│   │   │       ├── interceptors/         # 拦截器实现
│   │   │       ├── tools/                # 工具实现
│   │   │       └── utils/                # 工具类
│   │   └── jvmTest/                      # 测试代码
│   ├── build.gradle.kts                  # 模块构建配置
│   ├── native-reflect-config.json        # GraalVM 反射配置
│   └── native-resource-config.json       # GraalVM 资源配置
├── images/                               # 文档图片
├── build.gradle.kts                      # 根构建配置
├── settings.gradle.kts                   # 项目设置
└── gradle/                               # Gradle 包装器
```

## 核心组件

### AcpLocalAgent

主要的 ACP Agent 实现，负责：
- 管理 ACP 会话生命周期
- 处理客户端初始化、新会话、加载会话请求
- 处理用户提示并流式返回响应
- 集成 MCP 服务器工具

### LocalAgent

本地 Agent 核心实现，提供：
- 基于 Reactor 的响应式处理
- 文件操作工具集（读/写/编辑/搜索）
- Shell 命令执行
- 上下文管理和令牌清理
- 错误处理和重试机制

### AiModels

枚举形式的 AI 模型配置，支持：
- 多模型提供商（火山引擎、OpenRouter、MiniMax 等）
- 动态 API 密钥和基础 URL 配置
- 温度和最大令牌数自定义

## 可用工具

| 工具名称 | 描述 |
|---------|------|
| `grep` | 文件内容搜索 |
| `glob` | 文件模式匹配 |
| `edit_file` | 编辑文件内容 |
| `write_file` | 创建/写入文件 |
| `read_file` | 读取文件内容 |
| `ls` | 列出目录内容 |
| `execute_terminal_command` | 执行终端命令 |

## 快速开始

### 环境要求

- JDK 17+
- GraalVM（可选，用于原生编译）
- 有效的 AI 模型 API 密钥

### 环境变量配置

根据使用的模型提供商，配置以下环境变量：

```bash
# 火山引擎
export AI_VOLC_BASE_URL="https://..."
export AI_VOLC_API_KEY="your-api-key"

# OpenRouter
export AI_OPEN_ROUTER_BASE_URL="https://..."
export AI_OPEN_ROUTER_API_KEY="your-api-key"

# MiniMax
export AI_MINIMAX_BASE_URL="https://..."
export AI_MINIMAX_API_KEY="your-api-key"

# 其他提供商...
```

### 构建项目

```bash
# 克隆项目
git clone https://github.com/your-username/ai-agents.git
cd ai-agents

# 构建项目
./gradlew build

# 构建 Fat JAR
./gradlew :library:fatJar
```

### 运行方式

#### 1. 作为 ACP Agent 运行

```bash
./gradlew :library:runAcpAgent
```

#### 2. 运行异步客户端

```bash
./gradlew :library:runAsyncAgentClient
```

#### 3. 使用 Fat JAR

```bash
java -jar library/build/libs/library-1.0.0-all.jar
```

#### 4. 原生可执行文件（需要 GraalVM）

```bash
./gradlew :library:nativeCompile
# 生成的可执行文件位于 build/native/nativeCompile/ai-agents
./gradlew :library:nativeRun
```

## 配置说明

### Maven Central 发布配置

项目已配置为可发布到 Maven Central：

```bash
./gradlew publishToMavenCentral
```

发布配置位于 `library/build.gradle.kts`，包含：
- 项目坐标：`com.xr21:ai-agents:1.0.0`
- 许可证：Apache-2.0
- SCM 信息

### GraalVM 原生编译

#### 前置要求

1. **安装 GraalVM**
   - 下载 GraalVM for JDK 17（推荐使用 GraalVM Community 或 Oracle GraalVM）
   - 设置 `JAVA_HOME` 指向 GraalVM 安装目录
   - 确保 `native-image` 工具可用

```bash
# 验证 GraalVM 安装
java -version
native-image --version
```

2. **安装 Native Image 组件**（如未预装）

```bash
# 使用 Gu 安装 native-image 组件
gu install native-image
```

#### 配置文件说明

项目包含以下 Native Image 配置文件：

| 文件 | 说明 |
|------|------|
| `native-reflect-config.json` | 反射配置，注册需要反射访问的类 |
| `native-resource-config.json` | 资源配置，包含需要打包的资源文件 |

#### 构建步骤

```bash
# 1. 清理并构建项目
./gradlew clean build

# 2. 构建 Fat JAR（必需，nativeCompile 依赖此任务）
./gradlew :library:fatJar

# 3. 编译为原生可执行文件
./gradlew :library:nativeCompile
```

构建完成后，原生可执行文件位置：
- **Windows**: `library/build/native/nativeCompile/ai-agents.exe`
- **Linux/macOS**: `library/build/native/nativeCompile/ai-agents`

#### 运行原生应用

```bash
# 方式 1：使用 Gradle 任务
./gradlew :library:nativeRun

# 方式 2：直接执行编译后的文件
# Windows
library\build\native\nativeCompile\ai-agents.exe

# Linux/macOS
./library/build/native/nativeCompile/ai-agents
```

#### 构建参数优化

项目在 `build.gradle.kts` 中已配置以下优化参数：

```kotlin
buildArgs.addAll(
    "--no-fallback",                    // 不生成回退配置
    "--allow-incomplete-classpath",     // 允许不完整类路径
    "--report-unsupported-elements-at-runtime", // 运行时报告不支持的元素
    "-H:+ReportExceptionStackTraces",   // 报告异常堆栈
    "--enable-url-protocols=http,https", // 启用 HTTP/HTTPS 协议
    "--enable-all-security-services"    // 启用所有安全服务
)

// 性能优化
buildArgs.addAll(
    "-O3",                              // 最高级别优化
    "--gc=G1"                           // 使用 G1 垃圾回收器
)
```

#### 常见问题

**问题 1：`native-image` 命令未找到**

确保 GraalVM 正确安装且 `bin` 目录在 `PATH` 环境变量中。

**问题 2：反射相关错误**

检查 `native-reflect-config.json` 是否包含所有需要反射访问的类。

**问题 3：资源文件缺失**

在 `native-resource-config.json` 中添加缺失的资源模式。

**问题 4：构建内存不足**

增加 JVM 堆内存：
```bash
export JAVA_OPTS="-Xmx4g"
./gradlew :library:nativeCompile
```

#### 原生镜像优势

| 特性 | JVM 模式 | 原生模式 |
|------|---------|---------|
| 启动时间 | ~5-10 秒 | ~0.1-0.5 秒 |
| 内存占用 | ~200-500 MB | ~50-150 MB |
| 部署复杂度 | 需要 JRE | 单文件部署 |
| 跨平台 | 一次编译，到处运行 | 需分平台编译 |

## 拦截器系统

内置拦截器包括：

1. **ContextEditingInterceptor** - 上下文编辑，管理令牌数量
2. **ToolErrorInterceptor** - 工具错误处理
3. **ToolRetryInterceptor** - 工具调用重试
4. **LargeResultEvictionInterceptor** - 大结果驱逐
5. **FilesystemInterceptor** - 文件系统操作拦截

## 会话模式

支持多种会话模式：
- **Chat** - 对话模式
- **Agent** - 智能体模式
- **Plan** - 规划执行模式

## 开发指南

### 添加新工具

在 `tools` 包中创建新工具类，使用 `@Tool` 注解标记方法：

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

## 测试

运行测试：

```bash
./gradlew test
```

查看测试报告：

```bash
open library/build/reports/tests/test/index.html
```

## 许可证

本项目采用 Apache License 2.0 许可证。详见 [LICENSE](LICENSE) 文件。

## 贡献

欢迎提交 Issue 和 Pull Request！

## 联系方式

- GitHub: [@xr21](https://github.com/your-username)
- 项目主页：https://github.com/your-username/ai-agents

---

*最后更新：2026 年 2 月*
