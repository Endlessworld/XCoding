# Linux Native Build Environment for XAgent
FROM ubuntu:22.04

# 设置环境变量
ENV GRAALVM_VERSION=23.1.0
ENV JAVA_VERSION=17
ENV GRAALVM_HOME=/opt/graalvm-ce-java${JAVA_VERSION}
ENV PATH=$GRAALVM_HOME/bin:$PATH

# 安装基础依赖
RUN apt-get update && apt-get install -y \
    build-essential \
    libssl-dev \
    wget \
    curl \
    unzip \
    git \
    && rm -rf /var/lib/apt/lists/*

# 下载并安装 GraalVM
RUN wget https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-${GRAALVM_VERSION}/graalvm-ce-java${JAVA_VERSION}-linux-amd64-${GRAALVM_VERSION}.tar.gz \
    && tar -xzf graalvm-ce-java${JAVA_VERSION}-linux-amd64-${GRAALVM_VERSION}.tar.gz \
    && mv graalvm-ce-java${JAVA_VERSION}-${GRAALVM_VERSION} $GRAALVM_HOME \
    && rm graalvm-ce-java${JAVA_VERSION}-linux-amd64-${GRAALVM_VERSION}.tar.gz

# 安装 GraalVM Native Image 组件
RUN gu install native-image

# 验证安装
RUN native-image --version
RUN java -version

# 设置工作目录
WORKDIR /workspace

# 创建 Gradle 缓存目录
RUN mkdir -p /root/.gradle

# 设置 Gradle 缓存目录权限
RUN chown -R root:root /root/.gradle

# 暴露端口（如果需要）
EXPOSE 8080

# 默认命令
CMD ["/bin/bash"]
