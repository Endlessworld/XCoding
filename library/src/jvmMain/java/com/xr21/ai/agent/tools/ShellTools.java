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
import kotlin.coroutines.jvm.internal.RunSuspendKt;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import static com.xr21.ai.agent.acp.AgiAgentKt.CLIENT_SESSION_CONTEXT_KEY;

/**
 * @author Christian Tzolov
 * @author Endless
 */
@Slf4j
public class ShellTools {

    // Minimum timeout value in milliseconds (30 seconds)
    public static final long MIN_TIMEOUT_MS = 30000;

    // Maximum timeout value in milliseconds (10 minutes)
    public static final long MAX_TIMEOUT_MS = 600000;

    public static final long DEFAULT_TIMEOUT_MS = 120000;

    public static final Map<String, ShellSession> shellSessions = new ConcurrentHashMap<>();

    public static Builder builder() {
        return new Builder();
    }

    // @formatter:off
	@Tool(name = "Bash", description = """
		在支持超时的持久壳会话中执行给定的bash命令。
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
		- 需要超时参数，且至少30000毫秒（30秒），最多60000毫秒（10分钟）。
		- 如果你能用5到10个单词清晰简洁地描述这个命令的作用，会非常有帮助。
		- 如果输出超过30000字符，输出会被截断后再返回给你。
        - 使用 Bash 命令时，Windows 平台支持 CRLF，但建议生成文件内容时使用 LF，以确保跨平台兼容性
        - 在用 Bash 编译项目时，只输出编译错误或成功消息
        - 务必注意多个命令之间的串行/并行顺序和依赖关系
        - 不要用换行来分隔命令（引号字符串中换行是可以的）
        - 如果一定要是用Bash写入或读取文件 务必在任何读取或写入文件的命令中指定编码为UTF-8,且写入文件只能使用无BOM UTF-8 其它一切编码或者BOM头都将损坏文件导致无法编译
        <如果当前是Windows系统>
            先看当前环境是否存在 GNU coreutils 如果存在优先使用GNU coreutils
            否则使用PowerShell的替代GNU coreutils完成对应功能
        </如果当前是Windows系统>
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
			@JsonProperty(value = "command", required = true)
					@JsonPropertyDescription("The command to execute")
					String command,
			@JsonProperty(value = "timeout", required = true)
					@JsonPropertyDescription("Timeout in milliseconds. Must be at least 30000ms (30 seconds) and at most 600000ms (10 minutes).")
					Long timeout,
			@JsonProperty(value = "description")
					@JsonPropertyDescription("Clear, concise description of what this command does in 5-10 words, in active voice. Examples:\nInput: ls\nOutput: List files in current directory\n\nInput: git status\nOutput: Show working tree status\n\nInput: npm install\nOutput: Install package dependencies\n\nInput: mkdir foo\nOutput: Create directory 'foo'")
					String description,
			ToolContext context) { // @formatter:on

        // Generate unique shell ID for all executions
        String shellId = "shell_" + System.currentTimeMillis();

        try {
            log.info("Bash tool called - command: {}, timeout: {}", command, timeout);
            log.debug("Tool context: {}", context.getContext());

            // Validate and normalize timeout
            long timeoutMs = validateTimeout(timeout);

            // Determine the shell to use based on OS
            String[] shellCommand;
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                shellCommand = new String[]{"cmd.exe", "/c", command};
            } else {
                shellCommand = new String[]{"/bin/bash", "-c", command};
            }

            ProcessBuilder processBuilder = new ProcessBuilder(shellCommand);
            processBuilder.redirectErrorStream(false);

            Process process = processBuilder.start();
            ClientSessionOperations sessionOperations = null;
            if (context.getContext().get("_AGENT_CONFIG_") instanceof RunnableConfig config) {
                if (config.context().get(CLIENT_SESSION_CONTEXT_KEY) instanceof ClientSessionOperations clientSessionOperations) {
                    sessionOperations = clientSessionOperations;

                }

            }

            // Create interactive shell session with stdin support
            ShellSession session = new ShellSession(process, sessionOperations, shellId, command);
            shellSessions.put(shellId, session);

            // Wait for completion or timeout
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
            session.waitForOutputReaders(1000);
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

        // Truncate if too long
        String output = result.toString();
        if (output.length() > 30000) {
            String header = output.substring(0, output.indexOf("\n\n") + 2);
            String content = output.substring(output.indexOf("\n\n") + 2);
            output = header + content.substring(0, Math.min(content.length(), 30000 - header.length())) + "\n... (output truncated)";
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

        String newOutput = session.getNewOutput(filter);

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

        final AtomicBoolean stdoutFinished = new AtomicBoolean(false);
        final AtomicBoolean stderrFinished = new AtomicBoolean(false);

        int lastStdoutPosition = 0;
        int lastStderrPosition = 0;

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
            // Start thread to read stdout
            this.stdoutReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), charsetName))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        synchronized (stdout) {
                            stdout.append(line).append("\n");
                            if (clientSessionOperations != null) {
                                String finalLine = line + "\n";
                                RunSuspendKt.runSuspend((completion) -> {
                                    clientSessionOperations.notify(BridgeKt.buildAgentThoughtChunk(new ContentBlock.Text(finalLine, null, null)), null, completion);
                                    return null;
                                });
                            }
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
                        synchronized (stderr) {
                            stderr.append(line).append("\n");
                            if (clientSessionOperations != null) {
                                String finalLine = line + "\n";
                                RunSuspendKt.runSuspend((completion) -> {
                                    clientSessionOperations.notify(BridgeKt.buildAgentThoughtChunk(new ContentBlock.Text(finalLine, null, null)), null, completion);
                                    return null;
                                });
                            }
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
                // Add newline if not present
                String cmd = input;
                if (!cmd.endsWith("\n")) {
                    cmd = cmd + "\n";
                }
                stdin.write(cmd.getBytes());
                stdin.flush();
                return true;
            } catch (IOException e) {
                log.error("Failed to send input to shell: {}", e.getMessage());
                return false;
            }
        }

        /**
         * Get new output since last check
         */
        String getNewOutput(String filter) {
            StringBuilder result = new StringBuilder();

            synchronized (stdout) {
                String newStdout = stdout.substring(lastStdoutPosition);
                if (filter != null && !filter.isEmpty()) {
                    Pattern pattern = Pattern.compile(filter);
                    newStdout = filterOutput(newStdout, pattern);
                }
                if (!newStdout.isEmpty()) {
                    result.append("STDOUT:\n").append(newStdout);
                }
                lastStdoutPosition = stdout.length();
            }

            synchronized (stderr) {
                String newStderr = stderr.substring(lastStderrPosition);
                if (filter != null && !filter.isEmpty()) {
                    Pattern pattern = Pattern.compile(filter);
                    newStderr = filterOutput(newStderr, pattern);
                }
                if (!newStderr.isEmpty()) {
                    if (result.length() > 0) result.append("\n");
                    result.append("STDERR:\n").append(newStderr);
                }
                lastStderrPosition = stderr.length();
            }

            return result.toString();
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
         * Wait for output readers to finish
         */
        public void waitForOutputReaders(long timeoutMs) throws InterruptedException {
            stdoutReader.join(timeoutMs);
            stderrReader.join(timeoutMs);
        }

        public void destroy() {
            try {
                stdin.close();
            } catch (IOException e) {
                // Ignore
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
    }

}