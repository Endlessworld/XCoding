---
name: github
description: Use when performing git or GitHub workflows - creating commits (git status/diff/log, staging, writing commit messages) or opening pull requests with the gh CLI. Provides the git safety protocol and the step-by-step commit and pull-request procedures.
metadata:
  short-description: "Git commit and GitHub pull-request guide"
  author: Endless
---

# Committing changes with git
只有在用户请求时才创建提交。如果不清楚，先问清楚。当用户要求你创建新的 git 提交时，请仔细遵循以下步骤：
git 安全协议：
    - 绝不要更新 git 配置
    - 除非用户明确请求，否则绝不要运行破坏性/不可逆的 git 命令（如 push --force、hard reset 等）
    - 除非用户明确请求，切勿跳过钩子（--no-verify、--no-gpg-sign 等）
    - 绝不要强制推送到主主机/主控，若用户请求时警告
    - 避免 git 提交 --amend。 只有在（1）用户明确请求修改，或（2）从提交前钩子添加编辑时，才使用 --amend。
    - 修改前：务必检查作者身份（git log -1 --format='%an %ae'）
    - 除非用户明确要求，否则绝不要提交更改。
1. 当所有命令都可能成功时，你可以在一次响应中调用多个工具，并行运行多个 Bash 工具调用以获得最佳性能。并行运行以下 bash 命令，分别使用 Bash 工具：
    - 运行 git status 命令查看所有未被追踪的文件。
    - 运行 git diff 命令，查看将提交的分阶段和非分阶段变更。
    - 运行 git log 命令查看最近的提交消息，以便遵循该仓库的提交消息样式。
2. 分析所有分阶段的更改（包括之前的和新添加的），并起草提交消息：
    - 总结变更的性质（例如新功能、现有功能的增强、修复错误、重构、文档等）。确保消息准确反映变更及其目的。
    - 不要提交可能包含秘密的文件（.env、credentials.json 等）。如果用户特别请求提交这些文件，请警告他们
    - 起草一条简洁（1-2 句）的提交信息，重点关注"为什么"而非"什么"
3. 当所有命令都可能成功时，你可以在同一响应中调用多个工具，并行运行以下 bash 命令：
    - 将相关的未追踪文件添加到暂存区。
    - 创建提交。
    - 提交完成后运行 git status 以验证成功。
4. 如果提交失败，原因是提交前的钩子变更，请重试一次。如果成功了但文件被钩子修改，请确认修改是否安全：
    - 检查作者身份：git log -1 --format='%an %ae'
    - 检查未推送：git status 显示"您的分支领先"
    - 如果两者都成立：修改你的提交。否则：创建新提交（切勿修改其他开发者的提交）
重要说明：
    - 除非用户明确要求，否则不要向远程仓库推送
    - 如果提交内容无更改（即无未追踪文件且无修改），则不要创建空提交

# 创建拉取请求
通过 Bash 工具使用 gh 命令处理所有与 GitHub 相关的任务，包括问题处理、拉取请求、检查和发布。如果给了你一个 Github URL，可以用 gh 命令获取所需信息。
重要提示：当用户要求你创建拉取请求时，请仔细按照以下步骤操作：
1. 你可以在一个响应中调用多个工具，以了解分支自主分支分岔以来的当前状态：
    - 运行 git status 命令查看所有未被追踪的文件
    - 运行 git diff 命令，查看将提交的分阶段和非分阶段更改
    - 检查当前分支是否跟踪远程分支并与远程节点保持同步，以便知道是否需要推送到远程节点
    - 运行 git log 命令，然后 'git diff [base-branch]...HEAD'，以理解当前分支的完整提交历史
2. 分析所有将包含在拉取请求中的变更，并起草拉取请求摘要
3. 当所有命令都可能成功时，你可以在同一响应中调用多个工具，并行运行以下 bash 命令：
    - 如有需要，创建新分支
    - 如有需要，带 -u 标志推送至远程
