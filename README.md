# AI Agents Multiplatform

一个基于 Kotlin Multiplatform 和 Jetpack Compose 构建的跨平台 AI 智能助手桌面应用程序。本项目支持 Windows、macOS、Linux 等多个平台，并集成了 Spring AI 和阿里巴巴 AI 框架，提供强大的对话能力和 Agent 工作流支持。

## ✨ 功能特性

- **跨平台支持**：基于 Kotlin Multiplatform，一次编写，多平台运行
- **现代化 UI**：使用 Jetpack Compose 构建精美、响应式的用户界面
- **智能对话**：集成 Spring AI 和阿里巴巴 AI，提供流畅的 AI 对话体验
- **会话管理**：支持创建、查看、删除对话会话
- **Markdown 渲染**：支持代码高亮和 Markdown 格式输出
- **流式响应**：实时显示 AI 生成的内容，支持打字机效果
- **主题切换**：支持浅色/深色模式跟随系统设置
- **工具调用**：支持 Agent 工具调用和结果显示

## 🛠 技术栈

| 技术 | 用途 |
|------|------|
| **Kotlin** | 主要编程语言 |
| **Kotlin Multiplatform** | 跨平台支持 |
| **Jetpack Compose** | UI 框架 |
| **Spring AI** | AI 集成框架 |
| **Spring AI Alibaba** | 阿里巴巴 AI 集成 |
| **Reactor** | 响应式编程 |
| **Compose Desktop** | 桌面应用支持 |
| **Hutool** | Java 工具库 |
| **Jackson** | JSON 处理 |
| **Lanterna** | 终端 UI（可选） |

## 📁 项目结构

```
ai-agents-multiplatform/
├── build.gradle.kts          # 根项目构建配置
├── settings.gradle.kts       # 项目设置
├── gradle/
│   ├── libs.versions.toml    # 依赖版本管理
│   └── wrapper/              # Gradle 包装器
├── library/                  # 主库模块
│   ├── build.gradle.kts      # 库构建配置
│   └── src/
│       ├── jvmMain/          # JVM 平台代码
│       │   └── kotlin/
│       │       └── com/xr21/ai/agent/
│       │           ├── gui/          # 桌面 GUI 组件
│       │           │   ├── ChatApplication.kt    # 应用入口
│       │           │   ├── HomeScreen.kt         # 首页
│       │           │   ├── SessionDetailScreen.kt # 会话详情
│       │           │   ├── SettingsScreen.kt     # 设置页面
│       │           │   ├── LocalChatService.kt   # 聊天服务
│       │           │   ├── components/           # UI 组件
│       │           │   └── model/                # 数据模型
│       │           └── entity/                   # 实体类
│       ├── androidMain/      # Android 平台代码
│       ├── iosMain/          # iOS 平台代码
│       ├── linuxX64Main/     # Linux 平台代码
│       └── jvmTest/          # JVM 测试代码
├── images/                   # 项目截图和资源
├── .github/
│   └── workflows/            # GitHub Actions 流程
├── LICENSE                   # Apache 2.0 许可证
└── README.md                 # 项目说明文档
```

## 🚀 快速开始

### 环境要求

- **JDK 17** 或更高版本
- **Gradle 8.14.3**（项目已包含 Gradle Wrapper）
- **Windows/macOS/Linux** 操作系统

### 构建项目

```bash
# 克隆项目
git clone https://github.com/your-username/ai-agents-multiplatform.git
cd ai-agents-multiplatform

# 构建项目
./gradlew build

# 运行应用（桌面）
./gradlew :library:run
```

### 创建桌面安装包

```bash
# 创建 DMG（macOS）
./gradlew :library:createDistributable

# 创建 MSI（Windows）
./gradlew :library:createDistributable

# 创建 DEB（Linux）
./gradlew :library:createDistributable
```

安装包生成位置：`library/build/compose/binaries/main/`

## 📦 发布到 Maven Central

本项目已配置 Maven Central 自动发布功能。

### 配置发布凭据

在 `~/.gradle/gradle.properties` 中添加以下配置：

```properties
# Maven Central 发布凭据
SONATYPE_USERNAME=your-username
SONATYPE_PASSWORD=your-password

# GPG 签名（可选）
SIGNING_KEY_ID=your-key-id
SIGNING_KEY=your-private-key
SIGNING_PASSWORD=your-key-password
```

### 发布命令

```bash
# 发布到 Maven Central
./gradlew :library:publish

# 发布并签名
./gradlew :library:publishAndSign
```

## 🔧 配置说明

### AI 服务配置

项目支持配置不同的 AI 服务提供商。在使用前，请确保配置相应的 API 密钥：

```kotlin
// Spring AI OpenAI 配置示例
spring.ai.openai.api-key=your-openai-api-key
spring.ai.openai.chat.options.model=gpt-4

// Spring AI Alibaba 配置示例
spring.ai.dashscope.api-key=your-dashscope-api-key
```

### 应用设置

- **主题模式**：支持浅色、深色、跟随系统三种模式
- **发送行为**：消息发送后可选择停留在列表页或跳转到会话详情
- **历史记录**：支持清除所有对话历史

## 🧩 模块说明

### GUI 模块

| 模块 | 描述 |
|------|------|
| `ChatApplication` | 应用入口，负责初始化和路由管理 |
| `HomeScreen` | 首页，展示会话卡片列表 |
| `SessionDetailScreen` | 会话详情页，展示对话内容 |
| `SettingsScreen` | 设置页面，配置应用选项 |
| `LocalChatService` | 聊天服务，处理消息发送和接收 |

### 核心组件

- **ChatInput**: 消息输入框组件
- **MessageBubble**: 消息气泡组件
- **SessionCard**: 会话卡片组件
- **MarkdownRenderer**: Markdown 渲染组件
- **CodeHighlighter**: 代码高亮组件

## 🤝 贡献指南

欢迎贡献代码！请遵循以下步骤：

1. Fork 本仓库
2. 创建特性分支：`git checkout -b feature/amazing-feature`
3. 提交更改：`git commit -m 'Add amazing feature'`
4. 推送分支：`git push origin feature/amazing-feature`
5. 创建 Pull Request

## 📄 许可证

本项目采用 Apache License 2.0 许可证。详情请参阅 [LICENSE](LICENSE) 文件。

## 🙏 致谢

- [Kotlin](https://kotlinlang.org/) - 编程语言
- [Jetpack Compose](https://jetpackcompose.dev/) - UI 框架
- [Spring AI](https://spring.io/projects/spring-ai) - AI 集成框架
- [阿里巴巴 AI](https://www.aliyun.com/product/dashscope) - AI 服务支持
- [Gradle](https://gradle.org/) - 构建工具

---

**注意**：本项目是一个模板项目，您可以根据需要修改包名、依赖配置和发布信息。