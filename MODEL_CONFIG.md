# 模型配置说明

## 概述

`LocalAgent` 现在支持通过 JSON 配置文件来管理 AI 模型配置。配置文件默认位于用户目录下的 `~/.agi_working/models.json`。

## 配置文件位置

- **默认路径**: `~/.agi_working/models.json`
  - Windows: `C:\Users\<用户名>\.agi_working\models.json`
  - Linux/macOS: `~/.agi_working/models.json`

## 配置文件格式

配置文件使用供应商（Provider）和模型（Model）分离的方式，避免在多个模型中重复配置 `baseUrl` 和 `apiKey`：

```json
{
  "providers": [
    {
      "providerId": "volcengine",
      "baseUrl": "https://ark.cn-beijing.volces.com/api/v3",
      "apiKey": "your-volcengine-api-key-here"
    },
    {
      "providerId": "deepseek",
      "baseUrl": "https://api.deepseek.com/v1",
      "apiKey": "your-deepseek-api-key-here"
    }
  ],
  "models": [
    {
      "modelId": "kimi-k2-5",
      "modelName": "kimi-k2.5",
      "temperature": 0.65,
      "maxTokens": 3000,
      "providerId": "volcengine",
      "isDefault": true
    },
    {
      "modelId": "kimi-k2-1",
      "modelName": "kimi-k2.1",
      "temperature": 0.65,
      "maxTokens": 3000,
      "providerId": "volcengine",
      "isDefault": false
    },
    {
      "modelId": "deepseek-v3-2",
      "modelName": "deepseek-v3.2",
      "temperature": 0.75,
      "maxTokens": 4000,
      "providerId": "deepseek",
      "isDefault": false
    }
  ]
}
```

## 配置字段说明

### Provider 配置字段

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `providerId` | String | 是 | 供应商标识符，用于模型引用 |
| `baseUrl` | String | 是 | API 域础 URL |
| `apiKey` | String | 是 | API 密钥 |

### Model 配置字段

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `modelId` | String | 是 | 模型ID，用于客户端标识和选择模型（推荐使用） |
| `modelName` | String | 是 | 模型名称（实际发送给API的模型名称） |
| `temperature` | Double | 否 | 温度参数，控制输出的随机性（默认 0.65） |
| `maxTokens` | Integer | 否 | 最大令牌数（可选） |
| `providerId` | String | 否 | 引用的供应商标识符（推荐使用） |
| `baseUrl` | String | 否 | API 基础 URL（如果不使用 providerId） |
| `apiKey` | String | 否 | API 密钥（如果不使用 providerId） |
| `isDefault` | Boolean | 否 | 是否为默认模型（默认 false） |

**注意**：
- `modelId` 和 `modelName` 的区别：
  - `modelId`：用于客户端标识和选择模型，可以是简洁的标识符（如 `kimi-k2-5`）
  - `modelName`：实际发送给API的模型名称，需要符合API规范（如 `kimi-k2.5`）
- 如果使用 `providerId`，`baseUrl` 和 `apiKey` 可以省略（会从 provider 配置中获取）
- 如果同时提供 `providerId` 和 `baseUrl`/`apiKey`，则使用模型自身的配置（覆盖 provider 配置）
- 如果不使用 `providerId`，则必须提供 `baseUrl` 和 `apiKey`

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
  "providers": [
    {
      "providerId": "volcengine",
      "baseUrl": "https://ark.cn-beijing.volces.com/api/v3",
      "apiKey": "your-volc-api-key"
    },
    {
      "providerId": "deepseek",
      "baseUrl": "https://api.deepseek.com/v1",
      "apiKey": "your-deepseek-api-key"
    }
  ],
  "models": [
    {
      "modelId": "kimi-k2-5",
      "modelName": "kimi-k2.5",
      "temperature": 0.65,
      "maxTokens": 3000,
      "providerId": "volcengine",
      "isDefault": true
    },
    {
      "modelId": "deepseek-v3-2",
      "modelName": "deepseek-v3.2",
      "temperature": 0.75,
      "maxTokens": 4000,
      "providerId": "deepseek",
      "isDefault": false
    }
  ]
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

**使用新格式（推荐）**：

```json
{
  "providers": [
    {
      "providerId": "volcengine",
      "baseUrl": "https://ark.cn-beijing.volces.com/api/v3",
      "apiKey": "your-volc-api-key"
    },
    {
      "providerId": "deepseek",
      "baseUrl": "https://api.deepseek.com/v1",
      "apiKey": "your-deepseek-api-key"
    }
  ],
  "models": [
    {
      "modelId": "kimi-k2-5",
      "modelName": "kimi-k2.5",
      "providerId": "volcengine",
      "isDefault": true
    },
    {
      "modelId": "kimi-k2-1",
      "modelName": "kimi-k2.1",
      "providerId": "volcengine",
      "isDefault": false
    },
    {
      "modelId": "deepseek-v3-2",
      "modelName": "deepseek-v3.2",
      "providerId": "deepseek",
      "isDefault": false
    }
  ]
}
```

**优势**：
- 只需配置一次 `baseUrl` 和 `apiKey`
- 新增同一供应商的模型时，只需添加模型配置，无需重复 API 信息
- 更容易管理和维护多个供应商的配置
- `modelId` 和 `modelName` 分离，便于客户端使用和API调用

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