---
name: bash
description: Use when executing shell/terminal commands through the Bash tool, or driving persistent interactive shell sessions (BashOutput / ShellInput / KillShell). Covers Bash tool parameters (mode/timeout), command and path quoting, output and encoding rules, and hard-won interactive REPL best practices.
metadata:
  short-description: "Bash tool usage and interactive shell guide"
  author: Endless
---

## `Bash` 终端命令执行

在支持超时的持久 shell 会话中执行命令。参数 mode 决定执行方式：
- "once"（默认）：在一次性 shell 中执行单条命令，命令完成或超时后返回结果并销毁会话。
- "interactive"：启动持久后台交互式 shell，立即返回 bash_id，之后用 ShellInput 发送命令、BashOutput 读取输出、KillShell 终止。适合在同一个 shell 中连续执行多条命令并保持状态（环境变量、工作目录）的场景。

重要提示：这个工具用于终端操作，比如 git、npm、docker 等。不要用它来做文件操作（读、写、编辑、搜索、查找文件，除非查找的文件在工作空间之外）——请使用专门的工具。

行为：
- 如果命令在超时时间内完成，结果立即返回，会话关闭。
- 如果命令在超时时间内未完成，则返回交互式 shell 会话，允许你继续使用 ShellInput 和 BashOutput 工具与之交互。
Usage notes:
- 命令参数是必需的。
- 需要超时参数，且至少 1000 毫秒（1 秒），最多 600000 毫秒（10 分钟）。
- 如果你能用 5 到 10 个单词清晰简洁地描述这个命令的作用，会非常有帮助。
- 如果输出超过 30000 字符，输出会被截断后再返回给你。
- 使用 Bash 命令时，Windows 平台支持 CRLF，但建议生成文件内容时使用 LF，以确保跨平台兼容性
- 在用 Bash 编译项目时，只输出编译错误或成功消息
- 务必注意多个命令之间的串行/并行顺序和依赖关系
- 不要用换行来分隔命令（引号字符串中换行是可以的）
- 如果一定要用 Bash 写入或读取文件，务必在任何读取或写入文件的命令中指定编码为 UTF-8，且写入文件只能使用无 BOM UTF-8，其它一切编码或者 BOM 头都将损坏文件导致无法编译
- 关于当前操作系统、默认 shell、文件系统/目录约定以及已安装的开发工具（SDK）版本与位置，参见系统提示中动态探测的运行环境。

交互式会话最佳实践（真实环境验证）：
- Windows 下直接输入 python 可能解析到 WindowsApps 的 App 执行别名占位（stub），不会真正启动 Python。若环境由 uv 管理，应改用 uv run python 进入虚拟环境（可用 which/where python 排查真实解析路径）。
- 进入 REPL 类程序（如 python/node）时，推荐带 -i 强制交互模式，例如 uv run python -i，否则可能停留在启动阶段而不进入交互式提示符。
- 因本工具是管道连接（非真实 TTY），REPL 不会显示提示符（如 python 的 >>> ），但命令仍会被正常解析执行、输出也会被捕获；切勿因无提示符而误判为未进入。
- 首次启动 REPL（如 uv run python）常有初始化/建环境延迟，务必先等待程序就绪（出现版本或欢迎输出）后再用 ShellInput 发送命令，否则命令可能被程序启动前已排空的 stdin 消耗而丢失。
- REPL 的版本/欢迎信息通常打印到 stderr，命令结果打印到 stdout，两者分开展示属正常现象，均应读取确认。
