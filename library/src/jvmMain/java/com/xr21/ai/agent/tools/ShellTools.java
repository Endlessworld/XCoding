/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.xr21.ai.agent.tools;

import com.agentclientprotocol.common.ClientSessionOperations;
import com.agentclientprotocol.model.ContentBlock;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.xr21.ai.agent.bridge.BridgeKt;
import com.xr21.ai.agent.entity.ToolResult;
import com.xr21.ai.agent.utils.SuspendKt;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.*;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static com.xr21.ai.agent.acp.AgiAgentKt.CLIENT_SESSION_CONTEXT_KEY;

/**
 * @author Christian Tzolov
 * @author Endless
 */
@Slf4j
public class ShellTools {

    // Minimum timeout value in milliseconds (1 second) - keep low so short commands don't block the agent
    public static final long MIN_TIMEOUT_MS = 1000;

    // Maximum timeout value in milliseconds (10 minutes)
    public static final long MAX_TIMEOUT_MS = 600000;

    public static final long DEFAULT_TIMEOUT_MS = 120000;

    // Max chars retained per stream (stdout/stderr) to prevent unbounded memory growth (OOM)
    public static final int MAX_OUTPUT_CHARS = 1_000_000;

    // Max chars returned per tool call for a single stream read
    public static final int MAX_RETURN_CHARS = 30000;

    // Shell sessions idle longer than this are auto-destroyed to prevent process/memory leaks
    public static final long SESSION_IDLE_TIMEOUT_MS = 10 * 60 * 1000;

    private static final AtomicLong SHELL_ID_SEQ = new AtomicLong();

    public static final Map<String, ShellSession> shellSessions = new ConcurrentHashMap<>();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            shellSessions.values().forEach(ShellSession::destroy);
            shellSessions.clear();
        }));
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Destroy shell sessions that are dead or idle for too long, to avoid leaking processes and memory.
     */
    static void cleanupIdleSessions() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, ShellSession> entry : shellSessions.entrySet()) {
            ShellSession session = entry.getValue();
            if (!session.isAlive() || (now - session.getLastActivity() > SESSION_IDLE_TIMEOUT_MS)) {
                log.warn("Cleaning up shell session: {} (alive={}, idle={}ms)",
                        entry.getKey(), session.isAlive(), now - session.getLastActivity());
                session.destroy();
                shellSessions.remove(entry.getKey());
            }
        }
    }

    // @formatter:off
	@Tool(name = "Bash", description = """
		在支持超时的持久壳会话中执行给定的bash命令。
	        参数 mode 决定执行方式：
	            - "once"（默认）：在一次性 shell 中执行单条命令，命令完成或超时后返回结果并销毁会话。
	            - "interactive"：启动持久后台交互式 shell（Windows: cmd.exe，Unix: bash -i），立即返回 bash_id，之后用 ShellInput 发送命令、BashOutput 读取输出、KillShell 终止。适合在同一个 shell 中连续执行多条命令并保持状态（环境变量、工作目录）的场景。
        重要提示：这个工具用于终端操作，比如git、npm、docker等。不要用它来做文件操作（读、写、编辑、搜索、查找文件 （除非查找的文件在工作空间之外））——请使用专门的工具。
        行为：
            - 如果命令在超时时间内完成，结果立即返回，会话关闭。
            - 如果命令在超时时间内未完成，则返回交互式shell会话，允许你继续使用ShellInput和BashOutput工具与之交互。
        在执行命令前，请遵循以下步骤：
        1. 目录验证：
            - 如果命令会创建新的目录或文件，首先使用“ls”来验证父目录的存在及其正确位置
            - 例如，在运行“mkdir foo/bar”之前，首先使用“ls foo”来确认“foo”是否存在且是指定的父目录
        2. 命令执行：
		- 总是引用包含双引号空格的文件路径（例如 cd “带空格/file.txt的路径”）
		- 正确引用的示例：
		    - cd "/Users/[REDACTED]/My Documents" (correct)
			- cd /Users/[REDACTED]/My Documents (incorrect - will fail)
			- python "/path/with spaces/script.py" (correct)
			- python /path/with spaces/script.py" (incorrect - will fail)
		- 确保引用正确后，执行命令。
		- 捕获命令的输出。

		Usage notes:
        - 命令参数是必需的。
		- 需要超时参数，且至少1000毫秒（1秒），最多600000毫秒（10分钟）。
		- 如果你能用5到10个单词清晰简洁地描述这个命令的作用，会非常有帮助。
		- 如果输出超过30000字符，输出会被截断后再返回给你。
        - 使用 Bash 命令时，Windows 平台支持 CRLF，但建议生成文件内容时使用 LF，以确保跨平台兼容性
        - 在用 Bash 编译项目时，只输出编译错误或成功消息
        - 务必注意多个命令之间的串行/并行顺序和依赖关系
        - 不要用换行来分隔命令（引号字符串中换行是可以的）
        - 如果一定要是用Bash写入或读取文件 务必在任何读取或写入文件的命令中指定编码为UTF-8,且写入文件只能使用无BOM UTF-8 其它一切编码或者BOM头都将损坏文件导致无法编译
        <如果当前是Windows系统>
            禁止使用PowerShell脚本
            禁止使用PowerShell脚本
            禁止使用PowerShell脚本
            先看当前环境是否存在 GNU coreutils 如果存在优先使用GNU coreutils
            C:\\Program Files\\coreutils\\coreutils.exe
            灵活组合使用C:\\Program Files\\coreutils\\bin中的各种coreutils工具
            否则使用cmd替代GNU coreutils完成对应功能
        </如果当前是Windows系统>
			<交互式会话最佳实践（真实环境验证）>
				- Windows 下直接输入 python 可能解析到 WindowsApps 的 App 执行别名占位（stub），不会真正启动 Python。若环境由 uv 管理，应改用 uv run python 进入虚拟环境（可用 which/where python 排查真实解析路径）。
				- 进入 REPL 类程序（如 python/node）时，推荐带 -i 强制交互模式，例如 uv run python -i，否则可能停留在启动阶段而不进入交互式提示符。
				- 因本工具是管道连接（非真实 TTY），REPL 不会显示提示符（如 python 的 >>> ），但命令仍会被正常解析执行、输出也会被捕获；切勿因无提示符而误判为未进入。
				- 首次启动 REPL（如 uv run python）常有初始化/建环境延迟，务必先等待程序就绪（出现版本或欢迎输出）后再用 ShellInput 发送命令，否则命令可能被程序启动前已排空的 stdin 消耗而丢失。
				- REPL 的版本/欢迎信息通常打印到 stderr，命令结果打印到 stdout，两者分开展示属正常现象，均应读取确认。
			</交互式会话最佳实践>
		# Committing changes with git
		只有在用户请求时才创建提交。如果不清楚，先问清楚。当用户要求你创建新的 git 提交时，请仔细遵循以下步骤：
        git安全协议：
            - 绝不要更新 git 配置
            - 除非用户明确请求，否则绝不要运行破坏性/不可逆的 git 命令（如 push --force、hard reset 等）
            - 除非用户明确请求，切勿跳过钩子（--no-verify、--no-gpg-sign 等）
            - 绝不要强制推送到主主机/主控，若用户请求时警告
            - 避免git提交——修正。 只有在（1）用户明确请求修改，或（2）从提交前钩子添加编辑（以下补充说明）时，才使用 --amend。
            - 修改前：务必检查作者身份（git log -1 --format='%an %ae'）
            - 除非用户明确要求，否则绝不要提交更改。非常重要的是，只有在明确要求时才承诺，否则用户会觉得你太主动了。
        1. 当所有命令都可能成功时，你可以在一次响应中调用多个工具，并行运行多个 Bash 工具调用以获得最佳性能。并行运行以下bash命令，分别使用Bash工具：
            - 运行 git 状态命令查看所有未被追踪的文件。
            - 运行git diff命令，查看将提交的分阶段和非分阶段变更。
            - 运行 git 日志命令查看最近的提交消息，以便遵循该仓库的提交消息样式。
        2. 分析所有分阶段的更改（包括之前的和新添加的），并起草提交消息：
            - 总结变更的性质（例如新功能、现有功能的增强、修复错误、重构、文档等）。确保消息准确反映变更及其目的（例如“添加”表示全新功能，“更新”表示对现有功能的增强，“修正”表示修复错误等）。
            - 不要提交可能包含秘密的文件（.env、credentials.json 等）。如果用户特别请求提交这些文件，请警告他们
            - 起草一条简洁（1-2句）的提交信息，重点关注“为什么”而非“什么”
            - 确保其准确反映变更及其目的
        3. 当所有命令都可能成功时，你可以在同一响应中调用多个工具，并行运行以下bash命令：
		- 将相关的未追踪文件添加到备用区域。
		- 创建提交，邮件结尾为：
		- 提交完成后运行 git 状态以验证成功。
		注意：git状态取决于提交完成，所以提交后顺序运行。
		4. 如果提交失败，原因是提交前的钩子变更，请重试一次。如果成功了但文件被钩子修改，请确认修改是否安全：
		- 检查作者身份：git log -1 --format='%an %ae'
		- 检查未推送：git状态显示“您的分支领先”
		- 如果两者都成立：修改你的提交。否则：创建新提交（切勿修改其他开发者的提交）
        重要说明：
		- 除非用户明确要求，否则不要向远程仓库推送
		- 如果提交内容无更改（即无未追踪文件且无修改），则不要创建空提交
        # 创建拉取请求
		通过 Bash 工具使用gh命令处理所有与 GitHub 相关的任务，包括问题处理、拉取请求、检查和发布。如果给了你一个 Github URL，可以用 gh 命令获取所需信息。
        重要提示：当用户要求你创建拉取请求时，请仔细按照以下步骤操作：
        1. 你可以在一个响应中调用多个工具，以了解分支自主分支分岔以来的当前状态：
            - 运行 git status 命令查看所有未被追踪的文件
            - 运行git diff命令，查看将提交的分阶段和非分阶段更改
            - 检查当前分支是否跟踪远程分支并与远程节点保持同步，以便知道是否需要推送到远程节点
            - 运行 git log 命令，然后 'git diff [base-branch]...HEAD“，以理解当前分支的完整提交历史
		2. 分析所有将包含在拉取请求中的变更，并起草拉取请求摘要
		3. 当所有命令都可能成功时，你可以在同一响应中调用多个工具，并行运行以下bash命令：
		- 如有需要，创建新分支
		- 如有需要，带 -u 标志推送至远程
		""")
	public Map<String, Object> bash(
            @ToolParam(description = "he command to execute")
			@JsonProperty(value = "command", required = true)
					@JsonPropertyDescription("The command to execute")
					String command,
			@JsonProperty(value = "timeout", required = true)
					@JsonPropertyDescription("Timeout in milliseconds. Must be at least 1000ms (1 second) and at most 600000ms (10 minutes).")
					Long timeout,
			@JsonProperty(value = "mode")
					@JsonPropertyDescription("Execution mode: 'once' (default) runs a single command in a temporary shell and returns when it finishes or times out. 'interactive' starts a persistent background shell that stays alive so you can send multiple commands via ShellInput and keep state (env vars, working directory) across commands. Use 'interactive' when you need to run several sequential commands in the same shell.")
					String mode,
            @ToolParam(description = "Clear, concise description of what this command does in 5-10 words, in active voice. Examples:\nInput: ls\nOutput: List files in current directory\n\nInput: git status\nOutput: Show working tree status\n\nInput: npm install\nOutput: Install package dependencies\n\nInput: mkdir foo\nOutput: Create directory 'foo'")
			        @JsonProperty(value = "title")
                    String title,
		    @JsonProperty(value = "cwd")
					@JsonPropertyDescription("Optional absolute working directory to run the command in. Defaults to the JVM's current working directory. Pass this when you need to run git/npm/mvn/build commands inside a specific project folder.")
				 String cwd,
			ToolContext context) { // @formatter:on

        // Generate unique shell ID for all executions
        String shellId = "shell_" + SHELL_ID_SEQ.incrementAndGet();

        cleanupIdleSessions();

        try {
            log.info("Bash tool called - command: {}, timeout: {}", command, timeout);
            log.debug("Tool context: {}", context.getContext());

            // Validate and normalize timeout
            long timeoutMs = validateTimeout(timeout);

            // Determine execution mode: 'interactive' (persistent background shell) or 'once' (default single command)
            boolean interactive = "interactive".equalsIgnoreCase(mode);

            // Determine the shell to use based on OS and mode
            String[] shellCommand;
            String os = System.getProperty("os.name").toLowerCase();
            if (interactive) {
                // Persistent background shell: stay alive, commands are sent via ShellInput
                if (os.contains("win")) {
                    shellCommand = new String[]{"cmd.exe"};
                } else {
                    shellCommand = new String[]{"/bin/bash", "-i"};
                }
            } else {
                if (os.contains("win")) {
                    shellCommand = new String[]{"cmd.exe", "/c", command};
                } else {
                    shellCommand = new String[]{"/bin/bash", "-c", command};
                }
            }

            ProcessBuilder processBuilder = new ProcessBuilder(shellCommand);
            processBuilder.redirectErrorStream(false);
            if (cwd != null && !cwd.isBlank()) {
                File workingDir = new File(cwd);
                if (workingDir.isDirectory()) {
                    processBuilder.directory(workingDir);
                } else {
                    log.warn("cwd '{}' is not a valid directory, ignoring", cwd);
                }
            }

            Process process = processBuilder.start();
            ClientSessionOperations sessionOperations = null;
            if (context.getContext().get("_AGENT_CONFIG_") instanceof RunnableConfig config) {
                if (config.context().get(CLIENT_SESSION_CONTEXT_KEY) instanceof ClientSessionOperations clientSessionOperations) {
                    sessionOperations = clientSessionOperations;

                }

            }

            // Create shell session with stdin support
            ShellSession session = new ShellSession(process, sessionOperations, shellId, command);
            shellSessions.put(shellId, session);

            if (interactive) {
                // Interactive mode: shell stays alive. Send initial command over the pipe, then return.
                if (command != null && !command.isBlank()) {
                    session.sendInput(command);
                }
                return handleInteractiveSession(session, shellId, command);
            }

            // Once mode: wait for completion or timeout
            boolean completed = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);

            if (completed) {
                // Command completed within timeout - return result and clean up session
                return handleCompletedProcess(session, shellId, command);
            } else {
                // Command timed out - return interactive shell session info
                return handleTimeoutSession(session, shellId, command, timeoutMs);
            }

        } catch (IOException e) {
            return ToolResult.builder().error("Error executing command: " + e.getMessage()).build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResult.builder().error("Command execution interrupted: " + e.getMessage()).build();
        }
    }

    /**
     * Validate and normalize timeout value
     */
    private long validateTimeout(Long timeout) {
        if (timeout == null) {
            return DEFAULT_TIMEOUT_MS;
        }
        if (timeout < MIN_TIMEOUT_MS) {
            log.warn("Timeout {}ms is less than minimum {}ms, using minimum", timeout, MIN_TIMEOUT_MS);
            return MIN_TIMEOUT_MS;
        }
        if (timeout > MAX_TIMEOUT_MS) {
            log.warn("Timeout {}ms is greater than maximum {}ms, using maximum", timeout, MAX_TIMEOUT_MS);
            return MAX_TIMEOUT_MS;
        }
        return timeout;
    }

    /**
     * Handle completed process - return result and clean up session
     */
    private Map<String, Object> handleCompletedProcess(ShellSession session, String shellId, String command) {
        // Wait for output readers to finish
        try {
            session.waitForOutputReaders(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Get the output
        String stdout = session.getStdout();
        String stderr = session.getStderr();
        int exitCode = session.getExitCode();

        // Clean up the session
        session.destroy();
        shellSessions.remove(shellId);

        // Build result
        StringBuilder result = new StringBuilder();
        result.append("bash_id: ").append(shellId).append("\n\n");

        if (stdout != null && !stdout.isEmpty()) {
            result.append(stdout);
        }

        if (stderr != null && !stderr.isEmpty()) {
            if (result.length() > result.indexOf("\n\n") + 2) result.append("\n");
            result.append("STDERR:\n").append(stderr);
        }

        if (exitCode != 0) {
            if (result.length() > result.indexOf("\n\n") + 2) result.append("\n");
            result.append("Exit code: ").append(exitCode);
        }

        // Truncate if too long - keep the tail, since errors/stack traces/failures are usually at the end
        String output = result.toString();
        if (output.length() > MAX_RETURN_CHARS) {
            String header = output.substring(0, output.indexOf("\n\n") + 2);
            String content = output.substring(output.indexOf("\n\n") + 2);
            int keep = MAX_RETURN_CHARS - header.length() - "\n... (output truncated)\n".length();
            if (keep <= 0) {
                keep = Math.min(1000, content.length());
            }
            String tail = content.substring(Math.max(0, content.length() - keep));
            output = header + "\n... (output truncated)\n" + tail;
        }

        return ToolResult.builder()
                .success(exitCode == 0)
                .content(output)
                .toolCallContent(ToolResult.createTerminalContent(shellId))
                .put("bash_id", shellId)
                .put("command", command)
                .put("exitCode", exitCode)
                .put("stdout", stdout)
                .put("stderr", stderr)
                .build();
    }

    /**
     * Handle timeout - return interactive shell session info
     */
    private Map<String, Object> handleTimeoutSession(ShellSession session, String shellId, String command, long timeoutMs) {
        String output = String.format(
                "bash_id: %s\n\n" +
                        "Command timed out after %dms, but shell session is still running.\n\n" +
                        "Interactive shell session started with ID: %s\n" +
                        "Use ShellInput tool with shell_id='%s' and input='your command' to send commands.\n" +
                        "Use BashOutput tool with bash_id='%s' to read the output.\n" +
                        "Use KillShell tool with bash_id='%s' to terminate the session.\n\n" +
                        "Note: The command is still running in the background. You can continue to interact with it.",
                shellId, timeoutMs, shellId, shellId, shellId, shellId);

        return ToolResult.builder()
                .success(true)
                .content(output)
                .toolCallContent(ToolResult.createTerminalContent(shellId))
                .put("bash_id", shellId)
                .put("command", command)
                .put("timedOut", true)
                .put("timeout", timeoutMs)
                .put("interactive", true)
                .build();
    }

    /**
     * Handle interactive background shell - shell stays alive, return immediately.
     * Commands are sent later via ShellInput, output read via BashOutput.
     */
    private Map<String, Object> handleInteractiveSession(ShellSession session, String shellId, String command) {
        String output = String.format(
                "bash_id: %s\n\n" +
                        "Interactive background shell started with ID: %s\n" +
                        "Initial command has been sent over the pipe: %s\n" +
                        "This is a persistent interactive shell that stays alive until you kill it.\n" +
                        "Use ShellInput tool with shell_id='%s' and input='your command' to send commands.\n" +
                        "Use BashOutput tool with bash_id='%s' to read the output.\n" +
                        "Use KillShell tool with bash_id='%s' to terminate the session.\n\n" +
                        "Note: Send commands via ShellInput to keep state (env vars, working directory) across commands.",
                shellId, shellId, command, shellId, shellId, shellId);

        return ToolResult.builder()
                .success(true)
                .content(output)
                .toolCallContent(ToolResult.createTerminalContent(shellId))
                .put("bash_id", shellId)
                .put("command", session.getCommand())
                .put("interactive", true)
                .put("mode", "interactive")
                .build();
    }

    // @formatter:off
	@Tool(name = "BashOutput", description = """
		- Retrieves output from a running or completed interactive bash shell
		- Takes a shell_id parameter identifying the shell
		- Always returns only new output since the last check
		- Returns stdout and stderr output along with shell status
		- Supports optional regex filtering to show only lines matching a pattern
		- Use this tool to monitor or check the output of a shell session
		- Shell IDs can be found using the ShellSessions tool
		""")
	public Map<String, Object> bashOutput(
			@JsonProperty(value = "bash_id", required = true)
					@JsonPropertyDescription("The ID of the shell to retrieve output from")
					String bash_id,
			@JsonProperty(value = "filter")
					@JsonPropertyDescription("Optional regular expression to filter the output lines. Only lines matching this regex will be included in the result.")
					String filter) { // @formatter:on

        ShellSession session = shellSessions.get(bash_id);

        if (session == null) {
            return ToolResult.builder().error("Error: No shell session found with ID: " + bash_id).build();
        }

        String newOutput;
        try {
            newOutput = session.getNewOutput(filter);
        } catch (PatternSyntaxException e) {
            return ToolResult.builder().error("Error: Invalid regex filter: " + e.getMessage()).build();
        }
        // Cap returned output to avoid bloating the agent's context
        if (newOutput != null && newOutput.length() > MAX_RETURN_CHARS) {
            newOutput = "... (output truncated)\n" + newOutput.substring(newOutput.length() - MAX_RETURN_CHARS);
        }

        StringBuilder result = new StringBuilder();
        result.append("Shell ID: ").append(bash_id).append("\n");
        result.append("Type: Interactive Session\n");
        result.append("Status: ").append(session.isAlive() ? "Running" : "Completed").append("\n");

        if (!session.isAlive()) {
            try {
                result.append("Exit code: ").append(session.getExitCode()).append("\n");
                // Clean up completed session
                session.destroy();
                shellSessions.remove(bash_id);
                result.append("\nNote: Shell session has completed and been cleaned up.");
            } catch (IllegalThreadStateException e) {
                // Process not yet terminated
            }
        }

        if (newOutput != null && !newOutput.isEmpty()) {
            result.append("\nNew output:\n").append(newOutput);
        } else {
            result.append("\nNo new output since last check.");
        }

        return ToolResult.builder()
                .success(true)
                .content(result.toString())
                .toolCallContent(ToolResult.createTerminalContent(bash_id))
                .put("bash_id", bash_id)
                .put("isAlive", session.isAlive())
                .put("newOutput", newOutput)
                .put("interactive", true)
                .build();
    }

    // @formatter:off
	@Tool(name = "KillShell", description = """
		- Kills a running bash shell by its ID
		- Takes a shell_id parameter identifying the shell to kill
		- Returns a success or failure status
		- Use this tool to terminate a long-running shell session
		- Shell IDs can be found using the ShellSessions tool
		""")
	public Map<String, Object> killShell(
			@JsonProperty(value = "bash_id", required = true)
					@JsonPropertyDescription("The ID of the shell to kill")
					String bash_id) { // @formatter:on

        ShellSession session = shellSessions.get(bash_id);

        if (session == null) {
            return ToolResult.builder().error("Error: No shell session found with ID: " + bash_id).build();
        }

        if (!session.isAlive()) {
            shellSessions.remove(bash_id);
            String message = "Shell " + bash_id + " was already terminated. Removed from active shells.";
            return ToolResult.builder().success(true).content(message).put("bash_id", bash_id).build();
        }

        session.destroy();
        shellSessions.remove(bash_id);

        String message = "Successfully killed shell: " + bash_id;
        return ToolResult.builder()
                .success(true)
                .content(message)
                .toolCallContent(ToolResult.createTerminalContent(bash_id))
                .put("bash_id", bash_id)
                .build();
    }

    // @formatter:off
	@Tool(name = "ShellInput", description = """
		- Sends input (commands) to an interactive shell session
		- Takes a shell_id parameter identifying the shell to send input to
		- Takes an input parameter containing the command to send
		- Use this tool to interact with a running shell session
		- After sending input, use BashOutput to read the response
		- Shell IDs can be found using the ShellSessions tool
		""")
	public Map<String, Object> shellInput(
			@JsonProperty(value = "shell_id", required = true)
					@JsonPropertyDescription("The ID of the shell session to send input to")
					String shell_id,
			@JsonProperty(value = "input", required = true)
					@JsonPropertyDescription("The command/input to send to the shell")
					String input) { // @formatter:on

        ShellSession session = shellSessions.get(shell_id);

        if (session == null) {
            return ToolResult.builder().error("Error: No shell session found with ID: " + shell_id).build();
        }

        if (!session.isAlive()) {
            return ToolResult.builder().error("Error: Shell session " + shell_id + " is no longer running.").build();
        }

        // Send input to the shell
        boolean success = session.sendInput(input);

        if (!success) {
            return ToolResult.builder().error("Error: Failed to send input to shell " + shell_id).build();
        }

        String message = "Input sent to shell " + shell_id + ":\n" + input + "\n\nUse BashOutput to read the response.";
        return ToolResult.builder()
                .success(true)
                .content(message)
                .toolCallContent(ToolResult.createTerminalContent(shell_id))
                .put("shell_id", shell_id)
                .put("input", input)
                .build();
    }

    // @formatter:off
	@Tool(name = "ShellSessions", description = """
		- Lists all active shell sessions
		- Returns information about each shell including ID, status, and command
		- Use this to find shell IDs for BashOutput, KillShell, or ShellInput operations
		""")
	public Map<String, Object> shellSessions() { // @formatter:on

        cleanupIdleSessions();

        StringBuilder result = new StringBuilder();
        result.append("Active Shell Sessions:\n\n");

        if (shellSessions.isEmpty()) {
            result.append("No active shell sessions.");
        } else {
            for (Map.Entry<String, ShellSession> entry : shellSessions.entrySet()) {
                ShellSession session = entry.getValue();
                result.append("  - ID: ").append(entry.getKey()).append("\n");
                result.append("    Status: ").append(session.isAlive() ? "Running" : "Completed").append("\n");
                result.append("    Command: ").append(session.getCommand()).append("\n");
            }
        }

        return ToolResult.builder()
                .success(true)
                .content(result.toString())
                .put("sessionCount", shellSessions.size())
                .build();
    }

    public static class Builder {
        public ShellTools build() {
            return new ShellTools();
        }
    }

    /**
     * Inner class to manage interactive shell sessions with stdin support.
     * This allows sending commands to a running shell and reading responses.
     */
    public static class ShellSession {

        final Process process;
        final StringBuilder stdout;
        final StringBuilder stderr;
        final Thread stdoutReader;
        final Thread stderrReader;
        final OutputStream stdin;
        final String command;
        final ClientSessionOperations clientSessionOperations;
        final String charsetName;

        final AtomicBoolean stdoutFinished = new AtomicBoolean(false);
        final AtomicBoolean stderrFinished = new AtomicBoolean(false);

        // Monotonic counters for append/trim tracking so delta reads stay correct even when buffers are bounded
        final AtomicLong stdoutAppended = new AtomicLong();
        final AtomicLong stderrAppended = new AtomicLong();
        final AtomicLong stdoutDropped = new AtomicLong();
        final AtomicLong stderrDropped = new AtomicLong();
        long lastStdoutRead = 0;
        long lastStderrRead = 0;

        volatile long lastActivity = System.currentTimeMillis();

        ShellSession(Process process, ClientSessionOperations clientSessionOperations, String shellId, String command) {
            this.process = process;
            this.stdout = new StringBuilder();
            this.stderr = new StringBuilder();
            this.command = command;
            this.clientSessionOperations = clientSessionOperations;

            // Get the stdin stream for sending commands
            this.stdin = process.getOutputStream();
            String osName = System.getProperty("os.name").toLowerCase();
            String charsetName = osName.contains("win") ? "GBK" : Charset.defaultCharset().name();
            this.charsetName = charsetName;
            // Start thread to read stdout
            this.stdoutReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), charsetName))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        appendLine(stdout, stdoutDropped, stdoutAppended, line);
                        if (clientSessionOperations != null) {
                            String finalLine = line + "\n";
                            SuspendKt.runSuspend((completion) -> {
                                clientSessionOperations.notify(BridgeKt.buildAgentThoughtChunk(new ContentBlock.Text(finalLine, null, null)), null, completion);
                                return null;
                            });
                        }
                    }
                } catch (IOException e) {
                    // Process terminated or stream closed
                } finally {
                    stdoutFinished.set(true);
                }
            });
            this.stdoutReader.setDaemon(true);
            this.stdoutReader.start();

            // Start thread to read stderr
            this.stderrReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), charsetName))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        appendLine(stderr, stderrDropped, stderrAppended, line);
                        if (clientSessionOperations != null) {
                            String finalLine = line + "\n";
                            SuspendKt.runSuspend((completion) -> {
                                clientSessionOperations.notify(BridgeKt.buildAgentThoughtChunk(new ContentBlock.Text(finalLine, null, null)), null, completion);
                                return null;
                            });
                        }
                    }
                } catch (IOException e) {
                    // Process terminated or stream closed
                } finally {
                    stderrFinished.set(true);
                }
            });
            this.stderrReader.setDaemon(true);
            this.stderrReader.start();
        }

        /**
         * Send input (command) to the shell's stdin
         */
        public synchronized boolean sendInput(String input) {
            try {
                // Add newline if not present (use CRLF on Windows for interactive shells)
                String cmd = input;
                boolean isWindows = charsetName.equalsIgnoreCase("GBK");
                String lineSep = isWindows ? "\r\n" : "\n";
                if (!cmd.endsWith("\r\n") && !cmd.endsWith("\n")) {
                    cmd = cmd + lineSep;
                }
                // Encode input with the same charset used for reading output, so Chinese chars are not garbled
                stdin.write(cmd.getBytes(Charset.forName(charsetName)));
                stdin.flush();
                this.lastActivity = System.currentTimeMillis();
                return true;
            } catch (IOException e) {
                log.error("Failed to send input to shell: {}", e.getMessage());
                return false;
            }
        }

        /**
         * Append a line to a bounded buffer, dropping the oldest chars when the cap is exceeded.
         * Keeps monotonic appended/dropped counters so getNewOutput can still return the correct delta.
         */
        private void appendLine(StringBuilder sb, AtomicLong dropped, AtomicLong appended, String line) {
            String text = line + "\n";
            int len = text.length();
            synchronized (sb) {
                if (sb.length() + len > MAX_OUTPUT_CHARS) {
                    int over = sb.length() + len - MAX_OUTPUT_CHARS;
                    int remove = Math.min(sb.length(), over);
                    sb.delete(0, remove);
                    dropped.addAndGet(remove);
                }
                sb.append(text);
                appended.addAndGet(len);
                this.lastActivity = System.currentTimeMillis();
            }
        }

        /**
         * Get new output since last check
         */
        String getNewOutput(String filter) throws PatternSyntaxException {
            this.lastActivity = System.currentTimeMillis();
            StringBuilder result = new StringBuilder();

            synchronized (stdout) {
                String delta = readDelta(stdout, stdoutDropped, stdoutAppended, lastStdoutRead, "STDOUT", filter);
                if (!delta.isEmpty()) {
                    result.append(delta);
                }
                lastStdoutRead = stdoutAppended.get();
            }

            synchronized (stderr) {
                String delta = readDelta(stderr, stderrDropped, stderrAppended, lastStderrRead, "STDERR", filter);
                if (!delta.isEmpty()) {
                    if (result.length() > 0) result.append("\n");
                    result.append(delta);
                }
                lastStderrRead = stderrAppended.get();
            }

            return result.toString();
        }

        /**
         * Read the delta (chars appended since lastRead) from a bounded buffer, adjusting for trimmed head.
         */
        private String readDelta(StringBuilder sb, AtomicLong dropped, AtomicLong appended, long lastRead, String label, String filter) {
            long appendedTotal = appended.get();
            long droppedTotal = dropped.get();
            if (appendedTotal <= droppedTotal) {
                return "";
            }
            long start = Math.max(0, lastRead - droppedTotal);
            long end = appendedTotal - droppedTotal;
            if (end <= start) {
                return "";
            }
            String content = sb.substring((int) start, (int) end);
            if (filter != null && !filter.isEmpty()) {
                content = filterOutput(content, Pattern.compile(filter));
            }
            return content.isEmpty() ? "" : label + ":\n" + content;
        }

        private String filterOutput(String output, Pattern pattern) {
            String[] lines = output.split("\n");
            StringBuilder filtered = new StringBuilder();
            for (String line : lines) {
                if (pattern.matcher(line).find()) {
                    filtered.append(line).append("\n");
                }
            }
            return filtered.toString();
        }

        boolean isAlive() {
            return process.isAlive();
        }

        /**
         * Get full stdout content
         */
        synchronized String getStdout() {
            return stdout.toString();
        }

        /**
         * Get full stderr content
         */
        synchronized String getStderr() {
            return stderr.toString();
        }

        /**
         * Wait for output readers to finish, polling the finished flags so remaining buffered output is not lost.
         */
        public void waitForOutputReaders(long timeoutMs) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (!stdoutFinished.get() || !stderrFinished.get()) {
                if (System.currentTimeMillis() >= deadline) {
                    break;
                }
                Thread.sleep(20);
            }
            stdoutReader.join(200);
            stderrReader.join(200);
        }

        public void destroy() {
            try {
                stdin.close();
            } catch (IOException e) {
                // Ignore
            }
            // On Windows, kill the whole process tree to avoid orphan processes (cmd.exe may spawn children)
            if (charsetName.equalsIgnoreCase("GBK")) {
                try {
                    new ProcessBuilder("taskkill", "/F", "/T", "/PID", String.valueOf(process.pid()))
                            .redirectErrorStream(true)
                            .start()
                            .waitFor(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    log.warn("Failed to taskkill process tree for shell: {}", e.getMessage());
                }
            }
            process.destroy();
            try {
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }

        int getExitCode() {
            return process.exitValue();
        }

        String getCommand() {
            return command;
        }

        long getLastActivity() {
            return lastActivity;
        }
    }

}