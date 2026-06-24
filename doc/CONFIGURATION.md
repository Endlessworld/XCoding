# XAgent 配置指南

## ⚙️ 配置文件说明

### AI模型配置文件
**文件位置**：`${user.home}\\.agi_working\\models.json`

配置文件采用JSON格式，包含providers和models两部分：

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
    },
    {
      "providerId": "openrouter",
      "baseUrl": "https://openrouter.ai/api",
      "apiKey": "${AI_OPEN_ROUTER_API_KEY}"
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
    },
    {
      "modelId": "deepseek-coder",
      "modelName": "deepseek-coder",
      "temperature": 0.7,
      "maxTokens": 32000,
      "providerId": "deepseek",
      "isDefault": false
    }
  ]
}
```

### 配置项说明

#### Providers（供应商配置）
| 字段 | 类型 | 说明 |
|------|------|------|
| providerId | string | 供应商唯一标识，自定义 |
| baseUrl | string | API端点地址 |
| apiKey | string | API密钥，支持环境变量引用（${变量名}） |

#### Models（模型配置）
| 字段 | 类型 | 说明 |
|------|------|------|
| modelId | string | 模型唯一标识，自定义 |
| modelName | string | 模型名称，需与服务提供商要求一致 |
| temperature | float | 采样温度，0-2之间，越高越有创造性 |
| maxTokens | integer | 最大生成令牌数 |
| providerId | string | 关联的供应商ID，对应providers中的providerId |
| isDefault | boolean | 是否为默认使用的模型 |

## 🔑 环境变量配置
你可以通过环境变量配置敏感信息，避免在配置文件中明文存储密钥：

| 环境变量名 | 说明 |
|-----------|------|
| AI_VOLC_BASE_URL | 火山引擎API地址 |
| AI_VOLC_API_KEY | 火山引擎API密钥 |
| AI_DEEPSEEK_BASE_URL | DeepSeek API地址 |
| AI_DEEPSEEK_API_KEY | DeepSeek API密钥 |
| AI_OPEN_ROUTER_BASE_URL | OpenRouter API地址 |
| AI_OPEN_ROUTER_API_KEY | OpenRouter API密钥 |
| AI_CUCLOUD_BASE_URL | 其他云服务API地址 |
| AI_CUCLOUD_API_KEY | 其他云服务API密钥 |
| AI_XIAOMI_API_KEY | 小米API密钥 |
| AI_SILICONFLOW_API_KEY | SiliconFlow API密钥 |

### Windows设置环境变量
```powershell
# 临时设置（当前会话有效）
$env:AI_VOLC_API_KEY = "your-api-key"

# 永久设置（需要重启终端）
[Environment]::SetEnvironmentVariable("AI_VOLC_API_KEY", "your-api-key", "User")
```

### macOS/Linux设置环境变量
```bash
# 临时设置
export AI_VOLC_API_KEY="your-api-key"

# 永久设置（添加到~/.bashrc或~/.zshrc）
echo 'export AI_VOLC_API_KEY="your-api-key"' >> ~/.zshrc
source ~/.zshrc
```

## 🎯 模型配置最佳实践
1. **默认模型选择**：推荐使用专门的编码模型作为默认，如：
   - 火山引擎豆包编码模型
   - DeepSeek Coder
   - Claude 3 Opus/Sonnet
   - GPT-4 Turbo

2. **参数配置建议**：
   - 编码任务：temperature 0.6-0.8，maxTokens >= 4096
   - 创意任务：temperature 0.8-1.2，maxTokens >= 8192
   - 分析任务：temperature 0.2-0.5，maxTokens >= 16384

3. **多模型切换**：
   - 可以配置多个模型，根据不同任务需求切换
   - 默认模型设为性能和速度均衡的型号
   - 大上下文任务单独配置专用模型

## 🔌 MCP 服务器配置
XAgent支持MCP（Model Context Protocol）服务器扩展，可以通过配置添加自定义工具：

### 配置示例
```json
{
  "mcpServers": {
    "my-custom-server": {
      "command": "node",
      "args": ["/path/to/mcp-server.js"],
      "env": {
        "API_KEY": "${MY_API_KEY}"
      }
    }
  }
}
```

MCP服务器会自动加载，其提供的工具会在Agent中自动可用。

### 权限配置
支持配置工具调用权限策略：
- Allow once：仅允许本次调用
- Allow always：始终允许该工具
- Reject once：仅拒绝本次调用
- Reject always：始终拒绝该工具

权限配置会保存在本地，无需重复确认。