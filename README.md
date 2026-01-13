# AI Agents

## 项目简介

AI Agents 是一个本地AI Agent项目，基于 Spring AI 框架构建，提供强大的本地化人工智能代理能力。该项目支持多种AI模型集成，具备完整的工具生态系统，可在终端环境中与用户进行交互。

## 核心特性

- **多模型支持**：集成 OpenAI 和阿里巴巴AI模型
- **工具生态系统**：提供丰富的工具集，包括文件系统操作、代码搜索、网络搜索等
- **终端用户界面**：基于 Lanterna 的交互式终端界面
- **会话管理**：支持多会话管理，保持对话上下文
- **拦截器机制**：灵活的消息拦截和处理机制
- **跨平台支持**：支持 Windows、Linux、macOS 平台运行

## 技术栈

- **Java 21**：使用最新LTS版本
- **Spring AI 1.1.0**：AI应用开发框架
- **Spring Framework 6.2.0**：核心框架
- **Reactor 3.6.0**：响应式编程支持
- **Jackson 2.17.0**：JSON处理
- **Hutool 5.8.33**：工具类库
- **Lanterna 3.1.2**：终端UI库

## 项目结构

```
ai-agents/
├── src/main/java/com/xr21/ai/agent/
│   ├── AiAgentApplication.java      # 应用入口
│   ├── LocalAgent.java              # 核心Agent实现
│   ├── config/
│   │   └── AiModels.java            # AI模型配置
│   ├── entity/
│   │   ├── AgentOutput.java         # Agent输出实体
│   │   └── ConversationMessage.java # 对话消息实体
│   ├── interceptors/
│   │   ├── ContextEditingInterceptor.java   # 上下文编辑拦截器
│   │   ├── FilesystemInterceptor.java       # 文件系统拦截器
│   │   └── ToolRetryInterceptor.java        # 工具重试拦截器
│   ├── session/
│   │   ├── ConversationSession.java         # 对话会话
│   │   ├── ConversationSessionManager.java  # 会话管理器
│   │   └── SessionInfo.java                 # 会话信息
│   ├── tools/
│   │   ├── ContextCacheTool.java    # 上下文缓存工具
│   │   ├── DefaultTokenCounter.java # 默认Token计数器
│   │   ├── EditFileTool.java        # 文件编辑工具
│   │   ├── FeedBackTool.java        # 反馈工具
│   │   ├── GlobTool.java            # 文件匹配工具
│   │   ├── GrepTool.java            # 文本搜索工具
│   │   ├── Json.java                # JSON工具
│   │   ├── ListFilesTool.java       # 列出文件工具
│   │   ├── ReadFileTool.java        # 读取文件工具
│   │   ├── WebSearchTool.java       # 网络搜索工具
│   │   └── WriteFileTool.java       # 写入文件工具
│   ├── tui/
│   │   └── AITerminalUI.java        # 终端UI界面
│   └── utils/
│       ├── Json.java                # JSON工具类
│       └── SinksUtil.java           # Sinks工具类
└── src/main/resources/
    └── prompt/
        └── 提示词-小说创作助手.md    # 提示词模板
```

## 快速开始

### 环境要求

- JDK 21 或更高版本
- Maven 3.6+
- 至少 512MB 可用内存

### 编译项目

```bash
# 使用Maven编译
./mvnw clean package

# 或使用系统Maven
mvn clean package
```

### 运行应用

**方式一：直接运行JAR包**

```bash
java -jar target/ai-agents-1.0.0.jar
```

**方式二：运行可执行文件（Windows）**

```bash
start.bat
# 或直接双击
target/ai-agents-1.0.0.exe
```

**方式三：使用Maven运行**

```bash
./mvnw spring-boot:run
```

## 配置说明

### API密钥配置

项目支持通过环境变量配置AI模型API密钥：

```bash
# OpenAI API Key
export OPENAI_API_KEY="your-api-key"

# 阿里巴巴API Key
export ALIBABA_API_KEY="your-api-key"
```

### 模型配置

在代码中通过 `AiModels.java` 配置使用的AI模型，支持自定义模型参数和端点。

## 可用工具

| 工具名称 | 功能描述 |
|---------|---------|
| ReadFileTool | 读取文件内容 |
| WriteFileTool | 创建或写入文件 |
| EditFileTool | 编辑文件内容 |
| ListFilesTool | 列出目录文件 |
| GlobTool | 按模式匹配文件 |
| GrepTool | 在文件中搜索文本 |
| WebSearchTool | 执行网络搜索 |
| FeedBackTool | 收集用户反馈 |
| ContextCacheTool | 管理上下文缓存 |

## 开发指南

### 添加新工具

1. 继承 `Tool` 接口或基类
2. 实现工具逻辑和参数解析
3. 注册到 Agent 配置中

### 自定义拦截器

项目提供了拦截器机制，支持在消息处理的不同阶段进行干预：

- `ContextEditingInterceptor`：编辑对话上下文
- `FilesystemInterceptor`：文件系统操作拦截
- `ToolRetryInterceptor`：工具调用失败重试

## 构建产物

编译完成后会在 `target/` 目录生成以下文件：

- `ai-agents-1.0.0.jar`：可执行的JAR包
- `ai-agents-1.0.0-jar-with-dependencies.jar`：包含所有依赖的胖JAR
- `ai-agents-1.0.0.exe`：Windows可执行文件

## 打包说明

### Windows EXE打包

项目使用 Launch4j 插件将 JAR 打包为 Windows 可执行文件：

```bash
mvn package -Dlaunch4j
```

### GraalVM原生镜像

支持使用 GraalVM 构建原生可执行文件：

```bash
mvn package -Pnative
```

## 二次开发指南

本章节为希望对本项目进行二次开发的开发者提供详细的指导信息。无论您是想添加新功能、集成新的AI模型，还是扩展工具集，本指南都将帮助您快速上手。

### 开发环境搭建

在开始二次开发之前，您需要确保开发环境满足以下要求。首先，JDK 21是必须的，因为项目使用了Java 21的最新特性，包括模式匹配、密封类等语法糖。其次，Maven 3.6或更高版本用于管理项目依赖，推荐使用IDEA或VSCode作为开发IDE，这样可以获得更好的代码补全和重构支持。克隆项目仓库后，建议首先运行一次完整的构建流程，确保所有依赖都能正常下载，项目能够成功编译。这一步可以帮助您确认环境配置的正确性，避免在后续开发过程中遇到环境相关的问题。

```bash
# 克隆项目（如果尚未克隆）
git clone https://your-repo-url/ai-agents.git
cd ai-agents

# 首次构建
./mvnw clean package -DskipTests

# 运行测试确保环境正常
./mvnw test
```

### 项目架构解析

理解项目的整体架构对于二次开发至关重要。AI Agents项目采用分层架构设计，从上到下依次为表现层、业务逻辑层和基础设施层。表现层由tui包下的AITerminalUI类负责，它封装了与用户交互的所有终端界面逻辑，使用Lanterna库实现丰富的文本用户界面。业务逻辑层是项目的核心，包含LocalAgent类（AI Agent的主实现）、各拦截器（interceptors包）以及工具集（tools包）。这一层负责处理用户输入、调用AI模型、执行工具并返回结果。基础设施层则包括配置类（config包）、实体类（entity包）和会话管理（session包），这些组件为上层业务逻辑提供必要的数据支持和状态管理。

LocalAgent类是整个系统的中枢，它协调了AI模型调用、工具执行和消息处理的全流程。当用户输入一条消息时，消息首先经过拦截器链的处理，然后发送给AI模型生成响应。如果响应中包含工具调用指令，LocalAgent会解析指令并执行相应的工具，最后将工具执行结果整合到响应中返回给用户。理解这一流程对于添加新功能和调试问题都非常重要。

### 添加新工具

工具是AI Agent与外部世界交互的桥梁，添加新工具是最常见的二次开发需求之一。以添加一个数据库查询工具为例，首先需要在tools包下创建新的工具类，该类需要实现Spring AI框架定义的Tool接口或其基础类。在工具类中，您需要定义工具的名称、功能描述以及参数模式，这些元数据会被AI模型用于决定何时调用该工具。工具的实现逻辑应该尽量保持原子性和幂等性，即单次调用只完成一个明确的任务，多次调用相同输入应该产生一致的结果。

```java
package com.xr21.ai.agent.tools;

import org.springframework.ai.tool.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 数据库查询工具示例
 * 用于执行只读数据库查询操作
 */
public class DatabaseQueryTool {
    
    @Tool(description = "执行SQL查询并返回结果集")
    public String queryDatabase(
        @ToolParam(description = "要执行的SQL查询语句", required = true) String sql
    ) {
        // 参数验证
        if (sql == null || sql.trim().isEmpty()) {
            return "错误：SQL查询语句不能为空";
        }
        
        // 安全检查：只允许SELECT语句
        if (!sql.trim().toUpperCase().startsWith("SELECT")) {
            return "错误：为了安全起见，只允许执行SELECT查询";
        }
        
        try {
            // 执行查询逻辑
            // return jdbcTemplate.queryForList(sql);
            return "查询结果示例";
        } catch (Exception e) {
            return "查询失败：" + e.getMessage();
        }
    }
}
```

完成工具类实现后，需要将工具注册到Agent配置中。找到LocalAgent类的初始化代码，添加新工具的实例。工具在注册时会自动收集其@Tool和@ToolParam注解中的元数据，这些信息会被传递给AI模型，使模型能够了解工具的功能并正确生成调用参数。

### 自定义AI模型集成

项目当前支持OpenAI和阿里巴巴的AI模型，如果需要集成其他模型（如Claude、GLM、文心一言等），需要进行模型适配层的开发。首先在config包下创建新的模型配置类，定义该模型的API端点、认证方式和超时参数等。然后需要实现或适配ChatModel接口，创建对应模型的客户端实现。这一过程中最重要的工作是处理请求和响应的格式转换，确保自定义模型的API格式能够转换为项目内部统一的消息格式。

```java
package com.xr21.ai.agent.config;

import org.springframework.ai.chat.ChatModel;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * 自定义AI模型配置示例
 */
public class CustomAiModels {
    
    /**
     * 配置自定义模型
     */
    public ChatModel customChatModel() {
        // 这里实现自定义模型的客户端
        // 需要处理：
        // 1. HTTP客户端初始化
        // 2. 请求序列化
        // 3. 响应解析
        // 4. 错误处理和重试
        return null; // 占位符，实际实现需要引入对应SDK
    }
}
```

集成新模型时还需要考虑Token计算方式、上下文窗口限制、流式响应支持等因素。建议参考项目中已有的OpenAI和阿里云模型实现，按照相同的设计模式进行适配，这样可以保持代码风格的一致性，也更容易与其他功能模块集成。

### 开发最佳实践

在进行二次开发时，遵循项目的最佳实践可以显著提高代码质量和可维护性。首先，所有新增的工具类都应该添加完整的JavaDoc注释，包括类功能说明、参数说明和返回值说明，这些信息会被AI模型用于理解工具的用途。其次，工具的输入参数应该进行严格的验证，拒绝不符合要求的输入并给出清晰的错误提示，这可以避免很多运行时错误。

错误处理是另一个需要重点关注的方面。工具执行过程中可能遇到各种异常情况，良好的实践是捕获异常后返回结构化的错误信息，而不是直接抛出堆栈跟踪。错误信息应该对AI模型和用户都有意义，帮助模型理解问题并决定是否重试或请求用户修正输入。

```java
// 良好的错误处理示例
public String readFile(String path) {
    try {
        // 参数验证
        if (path == null || path.isEmpty()) {
            return "错误：文件路径不能为空";
        }
        
        // 路径安全检查
        File file = new File(path);
        if (!file.exists()) {
            return "错误：文件不存在：" + path;
        }
        
        // 读取文件
        return Files.readString(file.toPath());
        
    } catch (SecurityException e) {
        return "错误：没有权限读取文件：" + path;
    } catch (IOException e) {
        return "错误：读取文件失败：" + e.getMessage();
    }
}
```

性能优化方面，如果工具的执行时间较长，建议实现异步执行机制，避免阻塞Agent的主流程。对于涉及网络请求的工具，应该配置合理的超时时间和重试策略。同时，注意及时释放资源，如数据库连接、网络连接等，避免资源泄漏导致的性能问题。

### 调试技巧

调试AI Agent应用有其特殊性，因为涉及AI模型的不确定性输出。以下是一些实用的调试技巧。在开发新工具时，可以先在主函数中直接调用工具方法进行测试，验证其基本功能是否正常，然后再将其集成到Agent中。项目的日志配置在src/main/resources/logback.xml中，可以通过调整日志级别获取更详细的执行信息。

```bash
# 开启详细日志运行
java -jar target/ai-agents-1.0.0.jar --logging.level.com.xr21.ai.agent=DEBUG
```

如果遇到Agent行为异常，可以在代码中添加断点调试，跟踪消息在拦截器链中的处理过程。LocalAgent类中的关键方法如executeTools、buildPrompt等都适合设置断点。对于工具调用相关的问题，可以检查工具的输入参数是否正确解析，以及返回值是否被正确处理。

### 常见开发场景

以下是几个常见的二次开发场景及其实现要点，供开发者参考。

**场景一：添加新的拦截器**。拦截器用于在消息处理流程中插入自定义逻辑，例如日志记录、内容过滤、权限检查等。创建新的拦截器需要实现Interceptor接口或在现有拦截器基础上扩展。在拦截器中可以实现before和after方法，分别在消息处理前后执行自定义逻辑。

**场景二：扩展终端界面**。AITerminalUI类封装了所有界面交互逻辑，如果需要添加新的界面元素或交互模式，可以扩展该类。项目使用了Lanterna库，它提供了丰富的终端UI组件，包括文本框、列表、菜单等，可以充分利用这些组件来增强用户体验。

**场景三：集成外部API**。集成第三方API通常需要创建新的工具类来封装API调用。在实现时注意处理认证信息的安全存储，建议使用环境变量或配置文件管理API密钥，而不是硬编码在代码中。同时实现合理的错误处理和重试机制，提高工具的健壮性。

### 贡献代码

欢迎社区贡献者为本项目贡献代码！为了保证代码质量，请遵循以下流程。首先Fork项目仓库，在您的个人账户下创建项目副本。然后在本地创建功能分支进行开发，分支命名建议使用feature/或fix/前缀。完成开发后编写或更新相应的测试用例，确保测试通过。最后提交Pull Request，详细描述您的改动内容和动机。项目的维护者会尽快审核您的请求，如有问题会及时反馈。

## 许可证

本项目基于 Apache License 2.0 许可证开源。

## 贡献者

感谢所有为这个项目做出贡献的人！

## 联系方式

- 项目维护者：XR21 AI
- 问题反馈：请通过 GitHub Issues 提交
