# Windows Native Build Environment for XAgent (Simplified Version)
# This version uses direct downloads without Chocolatey for better reliability
FROM mcr.microsoft.com/windows/servercore:ltsc2022

# 设置环境变量
ENV GRAALVM_VERSION=21.3.3
ENV JAVA_VERSION=17
ENV GRAALVM_HOME=C:\graalvm-ce-java%JAVA_VERSION%
ENV PATH=%GRAALVM_HOME%\bin;%PATH%

# 使用 cmd 作为默认 shell
SHELL ["cmd", "/S", "/C"]

# 创建临时目录
RUN mkdir C:\temp

# 下载并安装 GraalVM (使用 PowerShell Invoke-WebRequest)
RUN %SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe -Command "Invoke-WebRequest -Uri 'https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-%GRAALVM_VERSION%/graalvm-ce-java%JAVA_VERSION%-windows-amd64-%GRAALVM_VERSION%.zip' -OutFile 'C:\temp\graalvm.zip'"

# 解压 GraalVM
RUN %SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe -Command "Expand-Archive -Path C:\temp\graalvm.zip -DestinationPath C:\; Remove-Item C:\temp\graalvm.zip; Move-Item -Path 'C:\graalvm-ce-java%JAVA_VERSION%-%GRAALVM_VERSION%' -Destination '%GRAALVM_HOME%'"

# 检查文件结构
RUN %SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe -Command "Get-ChildItem -Path '%GRAALVM_HOME%\bin' | Select-Object Name"

# 跳过 native-image 安装（在容器中可能失败）
# RUN %GRAALVM_HOME%\bin\gu.cmd install native-image

# 验证 Java 安装
RUN %GRAALVM_HOME%\bin\java.exe -version

# 设置工作目录
WORKDIR C:\\workspace

# 创建 Gradle 缓存目录
RUN mkdir C:\Users\ContainerAdministrator\.gradle

# 清理临时文件
RUN rmdir /S /Q C:\temp

# 暴露端口（如果需要）
EXPOSE 8080

# 默认命令
CMD ["cmd"]

