# 贡献指南

感谢你对 XAgent 项目的关注！我们欢迎各种形式的贡献。

## 🚀 快速开始

### 环境准备
1. **Fork** 这个仓库到你的 GitHub 账户
2. **Clone** 你的 Fork 到本地
3. **设置** 开发环境

```bash
git clone https://github.com/your-username/ai-agents.git
cd ai-agents

# 设置上游仓库
git remote add upstream https://github.com/original-owner/ai-agents.git
```

### 环境要求
- JDK 17+
- Gradle 8.x
- Git

## 📝 开发流程

### 1. 创建分支
```bash
git checkout -b feature/your-feature-name
# 或
git checkout -b fix/your-bug-fix
```

### 2. 开发和测试
```bash
# 构建项目
./gradlew build

# 运行测试
./gradlew test

# 构建 Fat JAR
./gradlew :library:fatJar

# 原生编译（可选）
./gradlew :library:nativeCompile
```

### 3. 提交变更
```bash
# 添加变更
git add .

# 提交（使用清晰的提交信息）
git commit -m "feat: 添加新的工具功能"

# 推送到你的 Fork
git push origin feature/your-feature-name
```

### 4. 创建 Pull Request
1. 在 GitHub 上打开你的 Fork
2. 点击 "New Pull Request"
3. 填写 PR 模板
4. 等待代码审查

## 🏗️ 项目结构

```
ai-agents/
├── library/                    # 主要模块
│   ├── src/
│   │   ├── jvmMain/           # 主要源码
│   │   └── jvmTest/           # 测试代码
│   └── build.gradle.kts       # 模块构建配置
├── .github/                    # GitHub 配置
│   ├── workflows/             # GitHub Actions
│   └── ISSUE_TEMPLATE/        # Issue 模板
└── docs/                      # 文档
```

## 🧪 测试指南

### 运行测试
```bash
# 运行所有测试
./gradlew test

# 运行特定测试
./gradlew test --tests "com.xr21.ai.agent.YourTest"

# 运行测试并生成报告
./gradlew test jacocoTestReport
```

### 测试覆盖率
我们要求新功能必须有相应的测试覆盖：
- 单元测试覆盖率 > 80%
- 集成测试覆盖核心功能

### 原生编译测试
如果变更涉及原生编译，请确保：
```bash
./gradlew :library:nativeCompile
./library/build/native/nativeCompile/XAgent
```

## 📋 代码规范

### Java/Kotlin 代码风格
- 使用 4 个空格缩进
- 类名使用 PascalCase
- 方法和变量使用 camelCase
- 常量使用 UPPER_SNAKE_CASE

### 提交信息规范
使用 [Conventional Commits](https://www.conventionalcommits.org/) 格式：

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

类型说明：
- `feat`: 新功能
- `fix`: Bug 修复
- `docs`: 文档更新
- `style`: 代码格式化
- `refactor`: 重构
- `test`: 测试相关
- `chore`: 构建或工具相关

示例：
```
feat(agent): 添加文件搜索工具

- 实现基于正则表达式的文件搜索
- 支持递归搜索目录
- 添加搜索结果过滤功能

Closes #123
```

## 🔧 开发工具

### IDE 配置
推荐使用 IntelliJ IDEA 或 Eclipse：
- 导入项目时选择 Gradle 项目
- 配置 JDK 17
- 启用代码格式化

### 代码格式化
项目使用 ktlint 进行代码格式化：
```bash
./gradlew ktlintFormat  # 格式化代码
./gradlew ktlintCheck   # 检查代码格式
```

## 🐛 Bug 报告

### 报告 Bug
1. 检查是否已有相关 Issue
2. 使用 Bug Report 模板创建新 Issue
3. 提供详细的复现步骤
4. 包含环境信息和错误日志

### 修复 Bug
1. 创建分支 `fix/bug-description`
2. 添加测试用例复现 Bug
3. 修复代码
4. 确保测试通过
5. 提交 PR

## 💡 功能建议

### 提出新功能
1. 检查是否已有相关 Issue
2. 使用 Feature Request 模板
3. 详细描述功能需求和使用场景
4. 讨论实现方案

### 实现新功能
1. 创建分支 `feature/feature-name`
2. 设计和实现功能
3. 编写测试用例
4. 更新文档
5. 提交 PR

## 📚 文档贡献

### 文档类型
- API 文档
- 用户指南
- 开发文档
- README 更新

### 文档规范
- 使用 Markdown 格式
- 包含代码示例
- 保持内容准确和最新

## 🔄 发布流程

### 版本管理
- 使用语义化版本 (Semantic Versioning)
- 主分支用于开发
- 发布分支用于稳定版本

### 自动化发布
- GitHub Actions 自动构建和测试
- 标签推送自动创建 Release
- 跨平台原生编译

## 🤝 社区准则

### 行为准则
- 尊重所有贡献者
- 保持友好和包容
- 专注于技术讨论
- 帮助新贡献者

### 沟通渠道
- GitHub Issues: Bug 报告和功能请求
- GitHub Discussions: 一般讨论和问答
- Pull Requests: 代码审查和讨论

## 🏆 贡献者

感谢所有为这个项目做出贡献的人！

### 如何成为贡献者
1. 提交有效的 Pull Request
2. 参与 Issue 讨论
3. 改进文档
4. 推荐项目

## 📞 获取帮助

如果你在贡献过程中遇到问题：
1. 查看现有的 Issues 和 Discussions
2. 阅读项目文档
3. 创建新的 Issue 或 Discussion
4. 联系项目维护者

---

再次感谢你的贡献！🎉
