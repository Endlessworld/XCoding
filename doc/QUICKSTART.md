# XAgent 快速开始指南

## 🚀 快速启动

### 环境要求
- JDK 17+
- 有效的 AI 模型 API 密钥（火山引擎、DeepSeek、OpenRouter等）

### 1. 下载运行
#### 方式一：使用预构建JAR包
```bash
# 下载最新版本的XAgent-xxx-all.jar
java -jar XAgent-0.0.1-all.jar
```

#### 方式二：从源码构建
```bash
git clone https://github.com/xr21/XAgent.git
cd XAgent
./gradlew :library:fatJar
java -jar library/build/libs/XAgent-0.0.1-all.jar
```

### 2. 环境变量配置
根据你使用的AI提供商配置对应的环境变量：

```bash
# 火山引擎豆包
export AI_VOLC_BASE_URL="https://ark.cn-beijing.volces.com/api/coding/v3"
export AI_VOLC_API_KEY="your-api-key"

# DeepSeek
export AI_DEEPSEEK_BASE_URL="https://api.deepseek.com"
export AI_DEEPSEEK_API_KEY="your-api-key"

# OpenRouter
export AI_OPEN_ROUTER_BASE_URL="https://openrouter.ai/api"
export AI_OPEN_ROUTER_API_KEY="your-api-key"
```

### 3. IDE集成

#### JetBrains IDEA/Clion/PyCharm等
1. 打开设置 → AI Assistant → 自定义代理
2. 添加配置：
```json
{
  "agent_servers": {
    "X Agent": {
      "command": "java",
      "args": [
        "-jar",
        "你的XAgent路径/XAgent-0.0.1-all.jar"
      ]
    }
  }
}
```
3. 保存并重启IDE即可使用

#### VSCode
1. 安装 ACP 客户端插件
2. 在配置中添加上述相同配置即可使用

## ✅ 验证安装
在IDE的AI助手输入框中输入："列出当前目录结构"，如果能正确返回目录列表则说明安装成功。

## 💡 首次使用建议
1. 先尝试简单的文件操作任务，熟悉工具能力
2. 对于复杂任务，可以使用"帮我规划一下任务"触发任务列表模式
3. 遇到权限提示时，根据实际情况选择允许/拒绝操作