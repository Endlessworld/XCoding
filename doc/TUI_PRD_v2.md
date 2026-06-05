# XAgent TUI — 产品需求文档 v2.0

> 版本: v2.0
> 更新: 2025-07-18
> 状态: Draft
> 关联: [TUI_IMPLEMENTATION_PLAN.md](./TUI_IMPLEMENTATION_PLAN.md)

---

## 1. 产品定位与愿景

### 1.1 产品概述

**XAgent TUI** 是一个基于 **mordant** 库构建的**终端用户界面（Terminal UI）**，作为 AI Agent 的交互客户端。受 [Claude Code](https://github.com/anthropics/claude-code)、[Codex CLI](https://github.com/openai/codex)、[OpenCode](https://github.com/sst/opencode)、[ChatGPT Terminal](https://github.com/aandrew-me/tgpt) 等主流 AI Agent 终端产品的启发，提供**纯键盘驱动**、**流式实时**、**多会话管理**的完整 Agent 交互体验。

### 1.2 核心价值主张

| 价值 | 描述 | 对标产品 |
|------|------|---------|
| **纯终端体验** | 无需浏览器/GUI，SSH/Codespace 环境友好 | Claude Code, Codex CLI |
| **键盘驱动** | Vim 风格快捷键，零鼠标依赖 | Claude Code |
| **流式实时** | 逐 token 打字机效果，所见即所得 | ChatGPT, Claude Code |
| **多会话管理** | 侧边栏会话列表，轻松切换上下文 | Codex CLI |
| **信息面板** | 实时 Token 统计 + Todo 任务追踪 | Claude Code |
| **协议标准化** | 基于 ACP (Agent Client Protocol) 通信 | — |
| **轻量跨平台** | JVM 构建，全平台一致体验 | — |

### 1.3 目标用户画像

| 用户类型 | 使用场景 | 核心需求 |
|---------|---------|---------|
| **AI Agent 重度用户** | 日常开发中高频使用 Agent | 高效切换会话、快速查看上下文 |
| **SSH 远程开发者** | 通过 SSH 连接远程服务器 | 无需 GUI 的纯终端交互 |
| **CI/CD 集成者** | 自动化流水线中交互式操作 | 可编程、可脚本化 |
| **键盘效率追求者** | 偏好键盘高于鼠标 | Vim 式快捷键、零鼠标依赖 |