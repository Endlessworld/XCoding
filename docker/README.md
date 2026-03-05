# XAgent Native Build Docker 环境

这个项目提供了用于 XAgent native 编译的 Docker 环境，支持 Linux、macOS 和 Windows 三个平台。

## 📁 目录结构

```
docker/
├── native-build/
│   ├── ubuntu.Dockerfile    # Linux native 编译环境
│   ├── macos.Dockerfile      # macOS native 编译环境
│   └── windows.Dockerfile    # Windows native 编译环境
├── build-native-images.sh    # Bash 构建脚本
├── build-native-images.ps1   # PowerShell 构建脚本
└── README.md                 # 本文档
```

## 🏗️ 镜像配置

### 基础环境

- **GraalVM**: 23.1.0
- **Java**: 17
- **Gradle**: 通过项目 wrapper 自动配置

### 平台特定依赖

#### Linux (Ubuntu 22.04)
- `build-essential` - 编译工具链
- `libssl-dev` - SSL 开发库

#### macOS
- 使用 Homebrew 安装基础工具
- 支持 macOS 原生编译

#### Windows (Windows Server Core LTSC2022)
- 使用 Chocolatey 包管理器
- PowerShell 环境
- 支持 Windows 原生编译

## 🚀 使用方法

### 快速开始

#### 1. 构建所有平台镜像

**Linux/macOS:**
```bash
chmod +x docker/build-native-images.sh
./docker/build-native-images.sh all
```

**Windows:**
```powershell
docker build --build-arg GRAALVM_VERSION=23.1.0 --build-arg JAVA_VERSION=17 --tag xagent/native-build-windows:graalvm-23.1.0 --file docker/native-build/windows.Dockerfile .
```

#### 2. 构建特定平台镜像

```bash
# 仅构建 Linux 镜像
./docker/build-native-images.sh linux

# 仅构建 macOS 镜像
./docker/build-native-images.sh macos

# 仅构建 Windows 镜像
./docker/build-native-images.sh windows
```

### 高级选项

#### 指定镜像标签
```bash
./docker/build-native-images.sh all --tag latest
```

#### 不使用缓存构建
```bash
./docker/build-native-images.sh all --no-cache
```

#### 构建并推送到镜像仓库
```bash
./docker/build-native-images.sh all --push
```

#### PowerShell 示例
```powershell
# 构建所有镜像并标记为 v1.0.0
.\docker\build-native-images.ps1 all -Tag "v1.0.0"

# 构建 Linux 镜像并推送
.\docker\build-native-images.ps1 linux -Push

# 不使用缓存构建 Windows 镜像
.\docker\build-native-images.ps1 windows -NoCache
```

## 🐳 使用镜像进行编译

### Linux 环境
```bash
# 运行容器
docker run -it --rm \
  -v $(pwd):/workspace \
  xagent/native-build:linux:graalvm-23.1.0

# 在容器内执行编译
./gradlew :library:nativeCompile
```

### macOS 环境
```bash
# 运行容器（需要支持 macOS 容器的 Docker 环境）
docker run -it --rm \
  -v $(pwd):/workspace \
  xagent/native-build:macos:graalvm-23.1.0

# 在容器内执行编译
./gradlew :library:nativeCompile
```

### Windows 环境
```powershell
# 运行容器
docker run -it --rm `
  -v "${PWD}:/workspace" `
  xagent/native-build:windows:graalvm-23.1.0

# 在容器内执行编译
.\gradlew.bat :library:nativeCompile
```

## 🔧 开发和调试

### 进入容器调试
```bash
# Linux
docker run -it --rm -v $(pwd):/workspace xagent/native-build:linux:graalvm-23.1.0 /bin/bash

# Windows
docker run -it --rm -v "${PWD}:/workspace" xagent/native-build:windows:graalvm-23.1.0 powershell
```

### 检查环境
```bash
# 检查 GraalVM 版本
native-image --version

# 检查 Java 版本
java -version

# 检查 Gradle 版本
./gradlew --version
```

## 📋 镜像标签说明

- `xagent/native-build-linux:graalvm-23.1.0` - Linux 环境
- `xagent/native-build-macos:graalvm-23.1.0` - macOS 环境  
- `xagent/native-build-windows:graalvm-23.1.0` - Windows 环境

## ⚠️ 注意事项

### macOS 容器
- macOS 容器需要特殊的 Docker 环境支持
- 在普通的 Docker Desktop 上可能无法运行
- 建议在 macOS 主机上直接使用 GraalVM

### Windows 容器
- 需要支持 Windows 容器的 Docker 环境
- 仅在 Windows 主机上可用
- 需要启用 Windows 容器模式

### 性能优化
- 首次构建可能需要较长时间下载依赖
- 建议使用 `--no-cache` 选项时谨慎操作
- 可以考虑使用 Docker 镜像缓存优化构建速度

## 🛠️ 故障排除

### 常见问题

1. **Docker 权限问题**
   ```bash
   sudo usermod -aG docker $USER
   # 重新登录或重启
   ```

2. **Windows 容器模式**
   ```powershell
   # 切换到 Windows 容器模式
   & 'C:\Program Files\Docker\Docker\DockerCli.exe' -SwitchWindowsEngine
   ```

3. **macOS 容器支持**
   - 需要使用支持 macOS 容器的 Docker 版本
   - 或者直接在 macOS 主机上安装 GraalVM

### 清理镜像
```bash
# 删除构建的镜像
docker rmi xagent/native-build-linux:graalvm-23.1.0
docker rmi xagent/native-build-macos:graalvm-23.1.0
docker rmi xagent/native-build-windows:graalvm-23.1.0

# 清理所有相关镜像
docker images | grep xagent/native-build | awk '{print $3}' | xargs docker rmi
```

## 📚 参考资料

- [GraalVM Native Image 官方文档](https://docs.graalvm.org/enterprise/23.1.0/reference-manual/native-image/)
- [Docker 官方文档](https://docs.docker.com/)
- [Gradle Native Build 插件](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html)
