# GitHub Actions 工作流说明

本项目配置了多个 GitHub Actions 工作流来实现自动化构建和原生编译。

## 工作流概览

### 1. CI 工作流 (`.github/workflows/ci.yml`)

**触发条件**: 推送到 `main` 或 `develop` 分支，或针对这些分支的 Pull Request

**功能**:
- 快速测试和构建
- 运行单元测试
- 构建 Fat JAR
- 使用缓存优化构建速度

**适用场景**: 日常开发、代码检查、PR 验证

### 2. Native Build 工作流 (`.github/workflows/native.yml`)

**触发条件**: 
- 推送到 `main` 分支
- 针对 `main` 分支的 Pull Request  
- 手动触发（可选择特定平台）

**功能**:
- 跨平台原生编译（Linux、macOS、Windows）
- 手动触发时可选择特定平台
- 使用 GraalVM Native Image

**适用场景**: 原生编译测试、发布准备

### 3. Build and Native Compile 工作流 (`.github/workflows/build.yml`)

**触发条件**: 
- 推送到 `main` 或 `develop` 分支
- 针对这些分支的 Pull Request
- 手动触发

**功能**:
- 完整的构建流程
- 跨平台原生编译
- 自动发布到 GitHub Releases（仅 main 分支）
- 构建产物上传

**适用场景**: 完整发布流程、版本发布

## 手动触发

### Native Build 工作流
可以在 GitHub Actions 页面手动触发，并选择特定平台：
- `all`: 构建所有平台
- `linux`: 仅构建 Linux
- `macOS`: 仅构建 macOS  
- `windows`: 仅构建 Windows

### Build 工作流
支持手动触发，会执行完整的构建和发布流程。

## 构建产物

### JAR 文件
- **Fat JAR**: `XAgent-0.0.1-all.jar` - 包含所有依赖的可执行 JAR

### 原生可执行文件
- **Linux**: `XAgent` - Linux 原生可执行文件
- **macOS**: `XAgent` - macOS 原生可执行文件
- **Windows**: `XAgent.exe` - Windows 原生可执行文件

## 缓存策略

所有工作流都配置了 Gradle 缓存来加速构建：
- 缓存 Gradle 依赖和 wrapper
- 基于文件内容哈希的缓存键
- 支持跨构建的缓存复用

## 环境变量

- `GRAALVM_VERSION`: `23.1.0` - GraalVM 版本
- `JAVA_VERSION`: `17` - Java 版本

## 发布

当代码推送到 `main` 分支时，Build 工作流会：
1. 构建所有平台的产物
2. 创建 GitHub Release
3. 上传构建产物到 Release

## 性能优化

1. **并行构建**: 各平台原生编译并行执行
2. **缓存复用**: Gradle 依赖缓存减少下载时间
3. **条件执行**: 根据触发条件选择性执行任务
4. **分阶段构建**: 先测试通过后再进行耗时的原生编译

## 故障排除

### 常见问题

1. **原生编译失败**
   - 检查 GraalVM 版本兼容性
   - 确认所有依赖支持原生编译
   - 查看构建日志中的反射配置提示

2. **缓存问题**
   - 清除缓存：在 Actions 页面手动清除缓存
   - 更新缓存键：修改 `gradle` 相关文件

3. **权限问题**
   - 确认 `GITHUB_TOKEN` 有足够权限
   - 检查仓库设置中的 Actions 权限

### 调试技巧

1. **启用调试日志**: 在工作流中添加 `ACTIONS_STEP_DEBUG` secret
2. **本地测试**: 使用 `act` 工具本地运行 Actions
3. **分步执行**: 注释掉部分步骤逐步调试

## 自定义配置

### 修改 GraalVM 版本
在相关工作流文件中修改 `GRAALVM_VERSION` 环境变量。

### 添加新平台
1. 在工作流中添加新的 job
2. 配置对应的运行环境
3. 设置平台特定的依赖和构建参数

### 修改构建参数
在 `library/build.gradle.kts` 中的 `graalvmNative` 配置块修改构建参数。
