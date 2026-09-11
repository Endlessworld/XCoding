# 模型配置说明

## 概述

`LocalAgent` 现在支持通过 JSON 配置文件来管理 AI 模型配置。配置文件默认位于用户目录下的 `~/.agi_working/models.json`。

## 配置文件位置

- **默认路径**: `~/.agi_working/models.json`
  - Windows: `C:\Users\<用户名>\.agi_working\models.json`
  - Linux/macOS: `~/.agi_working/models.json`

## 配置文件格式

配置文件使用供应商（Provider）分组的方式，在供应商下直接列出其支持的模型名称，最大程度简化模型配置：

```json
{
  "default_provider": "Go",
  "default_model": "deepseek-v4-flash",
  "providers": {
    "Zen": {
      "base_url": "https://opencode.ai/zen/v1",
      "api_key": "public",
      "models": [
        "mimo-v2.5-free",
        "ling-3.0-flash-fin-free"
      ]
    },
    "Go": {
      "base_url": "https://opencode.ai/zen/go/v1",
      "api_key": "sk-xxxxx",
      "models": [
        "minimax-m3",
        "kimi-k3",
        "deepseek-v4-flash"
      ]
    }
  }
}
```

## 配置字段说明

### 顶层字段

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `default_provider` | String | 否 | 默认供应商名称（对应 `providers` 的 key） |
| `default_model` | String | 否 | 默认模型名称 |

### Provider 配置字段

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `base_url` | String | 是 | API 基础 URL |
| `api_key` | String | 是 | API 密钥 |
| `models` | String[] | 是 | 该供应商支持的模型名称列表 |

**注意**：
- `providers` 是以供应商名称为 key 的对象（map），其 key 即 `providerId`
- `models` 中每个名称会生成一个模型配置：`modelId` 与 `modelName` 均等于该名称，`baseUrl`/`apiKey` 继承所属供应商
- `temperature` 统一使用默认值 0.65；`default_model` 指定默认模型，若同时提供 `default_provider` 则需匹配所属供应商

## 工作原理

1. **启动时加载**: 当 `LocalAgent` 初始化时，会自动尝试加载 JSON 配置文件
2. **自动创建**: 如果配置文件不存在，系统会自动在 `~/.agi_working/models.json` 创建默认配置文件
3. **Provider 解析**: 系统会自动将 `providerId` 引用解析为实际的 `baseUrl` 和 `apiKey`
4. **默认模型**: 如果配置文件中存在 `isDefault: true` 的模型，该模型将作为默认模型
5. **模型选择**: 当客户端请求特定模型时，系统会优先从 JSON 配置中查找
6. **回退机制**: 如果 JSON 配置中没有找到指定模型，系统会回退到 `AiModels` 枚举中定义的模型

## 配置优先级

配置的优先级从高到低为：

1. **JSON 配置文件** (`models.json`)
2. **环境变量** (通过 `AiModels` 枚举定义)
3. **枚举默认值**

## 使用示例

### 1. 自动创建配置文件（推荐）

首次启动应用时，如果配置文件不存在，系统会自动创建默认配置文件：

```bash
# 直接启动应用
./gradlew :library:runAcpAgent
```

应用会在 `~/.agi_working/models.json` 自动创建配置文件，日志输出：

```
INFO  Model config file not found at: /home/user/.agi_working/models.json, creating default config file
INFO  Created config directory: /home/user/.agi_working
INFO  Created default model config file at: /home/user/.agi_working/models.json
INFO  Please edit the config file and update the apiKey field with your actual API key
```

### 2. 手动创建配置文件

如果你想手动创建配置文件：

```bash
# 创建配置目录
mkdir -p ~/.agi_working

# 复制示例配置文件
cp library/src/jvmMain/resources/models.json.example ~/.agi_working/models.json

# 编辑配置文件
vim ~/.agi_working/models.json
```

### 2. 配置你的模型

编辑 `~/.agi_working/models.json`，填入你的实际 API 信息：

```json
{
  "default_provider": "volcengine",
  "default_model": "kimi-k2.5",
  "providers": {
    "volcengine": {
      "base_url": "https://ark.cn-beijing.volces.com/api/v3",
      "api_key": "your-volc-api-key",
      "models": ["kimi-k2.5", "kimi-k2.1"]
    },
    "deepseek": {
      "base_url": "https://api.deepseek.com/v1",
      "api_key": "your-deepseek-api-key",
      "models": ["deepseek-v3.2"]
    }
  }
}
```

### 3. 启动应用

```bash
./gradlew :library:runAcpAgent
```

应用启动时会自动加载配置并显示日志：

```
INFO  Loaded 1 model configurations from: /home/user/.agi_working/models.json
INFO  Loaded default model configuration from JSON: modelName=kimi-k2.5
INFO  ChatModel initialized successfully
```

## 高级用法

### 多模型配置

你可以配置多个模型，并通过 ACP 协议动态切换。

**配置格式**：

```json
{
  "default_provider": "volcengine",
  "default_model": "kimi-k2.5",
  "providers": {
    "volcengine": {
      "base_url": "https://ark.cn-beijing.volces.com/api/v3",
      "api_key": "your-volc-api-key",
      "models": ["kimi-k2.5", "kimi-k2.1"]
    },
    "deepseek": {
      "base_url": "https://api.deepseek.com/v1",
      "api_key": "your-deepseek-api-key",
      "models": ["deepseek-v3.2"]
    }
  }
}
```

**优势**：
- 只需配置一次 `base_url` 和 `api_key`
- 新增同一供应商的模型时，只需在 `models` 列表中添加名称
- 更容易管理和维护多个供应商的配置

### 客户端切换模型

通过 ACP 协议的 `set_session_model` 请求切换模型：

```json
{
  "type": "set_session_model",
  "modelId": "deepseek-v3.2"
}
```

## 错误处理

- **配置文件不存在**: 系统会记录日志并继续使用枚举定义的模型
- **配置文件格式错误**: 系统会记录错误日志并回退到枚举定义
- **模型未找到**: 如果请求的模型在 JSON 和枚举中都不存在，会抛出异常

## 安全建议

1. **文件权限**: 确保 `~/.agi_working` 目录和 `models.json` 文件只有当前用户可读
   ```bash
   chmod 700 ~/.agi_working
   chmod 600 ~/.agi_working/models.json
   ```

2. **API 密钥管理**: 不要将包含真实 API 密钥的 `models.json` 提交到版本控制系统

3. **环境变量**: 对于敏感信息，你也可以继续使用环境变量方式配置

## 故障排查

### 问题：配置未生效

**解决方案**:
1. 检查配置文件路径是否正确
2. 检查 JSON 格式是否正确
3. 查看应用日志中的错误信息

### 问题：模型加载失败

**解决方案**:
1. 验证 API 密钥是否正确
2. 检查 `baseUrl` 是否可访问
3. 查看完整错误堆栈

### 问题：找不到配置文件

**解决方案**:
1. 确保已创建 `~/.agi_working` 目录
2. 确保配置文件名为 `models.json`
3. 检查文件权限

## modelId 和 modelName 的区别

在新的配置格式中，我们引入了 `modelId` 和 `modelName` 两个字段：

### modelId
- **用途**: 用于客户端标识和选择模型
- **格式**: 可以是简洁的标识符，便于记忆和使用
- **示例**: `kimi-k2-5`, `deepseek-v3-2`
- **使用场景**: 
  - 客户端通过 `modelId` 来选择和切换模型
  - 在 ACP 协议的 `set_session_model` 请求中使用
  - 在 UI 界面中显示给用户

### modelName
- **用途**: 实际发送给 API 的模型名称
- **格式**: 必须符合 API 供应商的规范
- **示例**: `kimi-k2.5`, `deepseek-v3.2`
- **使用场景**:
  - 发送给 AI API 的请求中
  - API 调用时使用的模型标识符

### 为什么需要分离？

1. **API 兼容性**: 不同的 AI 供应商使用不同的模型名称格式（有的带点，有的带斜杠）
2. **客户端友好**: `modelId` 可以使用简洁、统一的格式，便于客户端使用
3. **灵活性**: 可以在不影响客户端的情况下更改 API 模型名称
4. **向后兼容**: 旧的配置只使用 `modelName`，系统仍然支持


## 相关代码

- `ModelConfig.java`: 模型配置数据类
- `ProviderConfig.java`: 供应商配置数据类
- `ModelsConfig.java`: 模型配置容器类
- `ModelConfigLoader.java`: 配置加载器，支持新旧格式
- `AiModels.java`: 模型枚举和配置管理
- `LocalAgent.java`: Agent 实现，初始化时加载配置