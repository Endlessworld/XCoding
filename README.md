# AI Agents Spring Shell Application

## 项目概述

本项目已成功使用Spring Shell重构原有的AI Agent应用程序，将原本的交互式命令行界面转换为更现代化的Spring Shell框架。

## 重构内容

### 1. 依赖管理
- 添加了Spring Shell依赖 (3.3.2)
- 保留了Alibaba AI Graph核心功能
- 移除了不必要的Spring AI自动配置以避免API密钥问题

### 2. 应用架构
- **AiAgentApplication**: Spring Boot应用程序入口
- **ShellConfig**: Spring Shell配置类，管理Bean依赖
- **InteractiveCommands**: 交互式会话命令
- **FileCommands**: 文件操作命令

### 3. 核心功能

#### 交互式命令
- `chat <message>` - 发送消息给AI助手
- `session list/create/switch/current` - 会话管理
- `history` - 查看当前会话历史
- `clear` - 清空当前会话历史
- `save [filename]` - 保存会话到文件
- `load <filename>` - 从文件加载会话
- `feedback <message>` - 发送反馈给AI助手
- `help-commands` - 显示帮助信息

#### 文件操作命令
- `ls [path]` - 列出目录内容
- `read <path>` - 读取文件内容
- `write <path> <content>` - 写入文件内容
- `grep <pattern> [path]` - 在文件中搜索内容
- `mkdir <path>` - 创建目录
- `rm <path>` - 删除文件或目录
- `pwd` - 显示当前工作目录
- `find <pattern> [path]` - 查找文件

### 4. 配置说明

#### application.properties
```properties
spring.main.web-application-type=none
spring.ai.openai.audio.speech.enabled=false
spring.ai.openai.audio.transcription.enabled=false
```

#### 环境变量
项目需要以下环境变量来配置AI模型：
- `AI_MINIMAX_BASE_URL` - MiniMax API基础URL
- `AI_MINIMAX_API_KEY` - MiniMax API密钥
- `AI_OPENAPI_BASE_URL` - OpenAI兼容API基础URL
- `AI_OPENAPI_API_KEY` - OpenAI兼容API密钥

## 运行方式

### 编译项目
```bash
mvn clean compile
```

### 运行应用
```bash
mvn spring-boot:run
```

### 使用示例
```shell
shell:>chat 你好
shell:>session list
shell:>history
shell:>ls src/main/java
shell:>read pom.xml
shell:>grep "spring-boot" pom.xml
```

## 技术栈

- **Spring Boot 3.5.0** - 应用框架
- **Spring Shell 3.3.2** - 命令行框架
- **Alibaba AI Graph 1.1.0.0** - AI图框架
- **Hutool 5.8.33** - 工具库
- **Lombok** - 代码简化
- **Java 21** - 运行环境

## 项目结构

```
src/main/java/com/xr21/ai/agent/
├── AiAgentApplication.java          # 应用程序入口
├── LocalAgent.java                   # 本地智能体核心
├── config/
│   ├── ShellConfig.java             # Spring Shell配置
│   ├── AiModels.java                # AI模型配置
│   └── SpringAiExcludeConfig.java   # Spring AI排除配置
├── commands/
│   ├── InteractiveCommands.java     # 交互式命令
│   └── FileCommands.java            # 文件操作命令
├── session/
│   └── ConversationSessionManager.java # 会话管理
├── entity/
│   ├── AgentOutput.java             # AI输出实体
│   └── ConversationMessage.java     # 对话消息实体
└── utils/
    └── SinksUtil.java               # 工具类
```

## 重构优势

1. **模块化设计**: 将功能拆分为不同的命令类，便于维护和扩展
2. **标准化框架**: 使用Spring Shell标准框架，提供更好的用户体验
3. **配置管理**: 通过Spring Boot统一管理配置和依赖
4. **可扩展性**: 易于添加新的命令和功能
5. **类型安全**: 使用Spring的依赖注入，提供更好的类型安全

## 注意事项

1. 确保设置了正确的环境变量以访问AI服务
2. 应用程序配置为非Web模式 (`spring.main.web-application-type=none`)
3. 禁用了不需要的Spring AI音频功能以避免启动错误

## 后续改进建议

1. 添加命令自动补全功能
2. 实现命令历史记录
3. 添加配置文件验证
4. 实现更友好的错误处理
5. 添加单元测试和集成测试
