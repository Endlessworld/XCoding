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

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.xr21.ai.agent.entity.ToolResult;
import com.xr21.ai.agent.event.AcpEventBus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

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

    // Default timeout value (2 minutes)
    public static final long DEFAULT_TIMEOUT_MS = 120000;

    // Storage for interactive shell sessions (for re-entrant shell support)
    public static final Map<String, ShellSession> shellSessions = new ConcurrentHashMap<>();

    public static Builder builder() {
        return new Builder();
    }

    //
    // Shell comnmands
    //

    // @formatter:off
	@Tool(name = "Bash", description = """
		Executes a given bash command in a persistent shell session with timeout support.

		IMPORTANT: This tool is for terminal operations like git, npm, docker, etc. DO NOT use it for file operations (reading, writing, editing, searching, finding files) - use the specialized tools for this instead.

		Behavior:
		- If the command completes within the timeout period, the result is returned immediately and the session is closed.
		- If the command does NOT complete within the timeout period, an interactive shell session is returned, allowing you to continue interacting with it using ShellInput and BashOutput tools.

		Before executing the command, please follow these steps:

		1. Directory Verification:
		- If the command will create new directories or files, first use `ls` to verify the parent directory exists and is the correct location
		- For example, before running "mkdir foo/bar", first use `ls foo` to check that "foo" exists and is the intended parent directory

		2. Command Execution:
		- Always quote file paths that contain spaces with double quotes (e.g., cd "path with spaces/file.txt")
		- Examples of proper quoting:
			- cd "/Users/[REDACTED]/My Documents" (correct)
			- cd /Users/[REDACTED]/My Documents (incorrect - will fail)
			- python "/path/with spaces/script.py" (correct)
			- python /path/with spaces/script.py" (incorrect - will fail)
		- After ensuring proper quoting, execute the command.
		- Capture the output of the command.

		Usage notes:
		- The command argument is required.
		- The timeout argument is required and must be at least 30000ms (30 seconds) and at most 600000ms (10 minutes).
		- It is very helpful if you write a clear, concise description of what this command does in 5-10 words.
		- If the output exceeds 30000 characters, output will be truncated before being returned to you.

		- Avoid using Bash with the `find`, `grep`, `cat`, `head`, `tail`, `sed`, `awk`, or `echo` commands, unless explicitly instructed or when these commands are truly necessary for the task. Instead, always prefer using the dedicated tools for these commands:
			- File search: Use Glob (NOT find or ls)
			- Content search: Use Grep (NOT grep or rg)
			- Read files: Use Read (NOT cat/head/tail)
			- Edit files: Use Edit (NOT sed/awk)
			- Write files: Use Write (NOT echo >/cat <<EOF)
			- Communication: Output text directly (NOT echo/printf)
		- When issuing multiple commands:
			- If the commands are independent and can run in parallel, make multiple Bash tool calls in a single message. For example, if you need to run "git status" and "git diff", send a single message with two Bash tool calls in parallel.
			- If the commands depend on each other and must run sequentially, use a single Bash call with '&&' to chain them together (e.g., `git add . && git commit -m "message" && git push`). For instance, if one operation must complete before another starts (like mkdir before cp, Write before Bash for git operations, or git add before git commit), run them sequentially instead.
			- Use ';' only when you need to run commands sequentially but don't care if earlier commands fail
			- DO NOT use newlines to separate commands (newlines are ok in quoted strings)
		- Try to maintain your current working directory throughout the session by using absolute paths and avoiding usage of `cd`. You may use `cd` if the User explicitly requests it.
			<good-example>
			pytest /foo/bar/tests
			</good-example>
			<bad_example>
			cd /foo/bar && pytest tests
			</bad_example>

		# Committing changes with git

		Only create commits when requested by the user. If unclear, ask first. When the user asks you to create a new git commit, follow these steps carefully:

		Git Safety Protocol:
		- NEVER update the git config
		- NEVER run destructive/irreversible git commands (like push --force, hard reset, etc) unless the user explicitly requests it
		- NEVER skip hooks (--no-verify, --no-gpg-sign, etc) unless the user explicitly requests it
		- NEVER run force push to main/master, warn the user if they request it
		- Avoid git commit --amend.  ONLY use --amend when either (1) user explicitly requested amend OR (2) adding edits from pre-commit hook (additional instructions below)
		- Before amending: ALWAYS check authorship (git log -1 --format='%an %ae')
		- NEVER commit changes unless the user explicitly asks you to. It is VERY IMPORTANT to only commit when explicitly asked, otherwise the user will feel that you are being too proactive.

		1. You can call multiple tools in a single response when all commands are likely to succeed, run multiple Bash tool calls in parallel for optimal performance. run the following bash commands in parallel, each using the Bash tool:
		- Run a git status command to see all untracked files.
		- Run a git diff command to see both staged and unstaged changes that will be committed.
		- Run a git log command to see recent commit messages, so that you can follow this repository's commit message style.
		2. Analyze all staged changes (both previously staged and newly added) and draft a commit message:
		- Summarize the nature of the changes (eg. new feature, enhancement to an existing feature, bug fix, refactoring, docs, etc.). Ensure the message accurately reflects the changes and their purpose (i.e. "add" means a wholly new feature, "update" means an enhancement to an existing feature, "fix" means a bug fix, etc.).
		- Do not commit files that likely contain secrets (.env, credentials.json, etc). Warn the user if they specifically request to commit those files
		- Draft a concise (1-2 sentences) commit message that focuses on the "why" rather than the "what"
		- Ensure it accurately reflects the changes and their purpose
		3. You can call multiple tools in a single response when all commands are likely to succeed, run the following bash commands in parallel:
		- Add relevant untracked files to the staging area.
		- Create the commit with a message ending with:
		🤖 Generated with [Claude Code](https://claude.com/claude-code)

		Co-Authored-By: Claude <noreply@anthropic.com>
		- Run git status after the commit completes to verify success.
		Note: git status depends on the commit completing, so run it sequentially after the commit.
		4. If the commit fails due to pre-commit hook changes, retry ONCE. If it succeeds but files were modified by the hook, verify it's safe to amend:
		- Check authorship: git log -1 --format='%an %ae'
		- Check not pushed: git status shows "Your branch is ahead"
		- If both true: amend your commit. Otherwise: create NEW commit (never amend other developers' commits)

		Important notes:
		- NEVER run additional commands to read or explore code, besides git bash commands
		- NEVER use the TodoWrite or Task tools
		- DO NOT push to the remote repository unless the user explicitly asks you to do so
		- IMPORTANT: Never use git commands with the -i flag (like git rebase -i) since they require interactive input which is not supported.
		- If there are no changes to commit (i.e., no untracked files and no modifications), do not create an empty commit

		# Creating pull requests
		Use the gh command via the Bash tool for ALL GitHub-related tasks including working with issues, pull requests, checks, and releases. If given a Github URL use the gh command to get the information needed.

		IMPORTANT: When the user asks you to create a pull request, follow these steps carefully:

		1. You can call multiple tools in a single response to understand the current state of the branch since it diverged from the main branch:
		- Run a git status command to see all untracked files
		- Run a git diff command to see both staged and unstaged changes that will be committed
		- Check if the current branch tracks a remote branch and is up to date with the remote, so you know if you need to push to the remote
		- Run a git log command and `git diff [base-branch]...HEAD` to understand the full commit history for the current branch
		2. Analyze all changes that will be included in the pull request, and draft a pull request summary
		3. You can call multiple tools in a single response when all commands are likely to succeed, run the following bash commands in parallel:
		- Create new branch if needed
		- Push to remote with -u flag if needed
		- Create PR using gh pr create with the format below:
		<example>
		gh pr create --title "the pr title" --body "$(cat <<'EOF'
		## Summary
		<1-3 bullet points>

		## Test plan
		[Bulleted markdown checklist of TODOs for testing the pull request...]

		🤖 Generated with [Claude Code](https://claude.com/claude-code)
		EOF
		)"
		</example>

		Important:
		- DO NOT use the TodoWrite or Task tools
		- Return the PR URL when you're done, so the user can see it

		# Other common operations
		- View comments on a Github PR: gh api repos/foo/bar/pulls/123/comments
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

            // Get AcpEventBus if available
            AcpEventBus eventBus = null;
            if (context.getContext().get("_AGENT_CONFIG_") instanceof RunnableConfig config) {
                if (config.context().get(AcpEventBus.CONTEXT_KEY) instanceof AcpEventBus bus) {
                    eventBus = bus;
                }
            }

            // Create interactive shell session with stdin support
            ShellSession session = new ShellSession(process, eventBus, shellId, command);
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
        final AcpEventBus eventBus;

        final AtomicBoolean stdoutFinished = new AtomicBoolean(false);
        final AtomicBoolean stderrFinished = new AtomicBoolean(false);

        int lastStdoutPosition = 0;
        int lastStderrPosition = 0;

        ShellSession(Process process, AcpEventBus eventBus, String shellId, String command) {
            this.process = process;
            this.stdout = new StringBuilder();
            this.stderr = new StringBuilder();
            this.command = command;
            this.eventBus = eventBus;

            // Get the stdin stream for sending commands
            this.stdin = process.getOutputStream();

            // Start thread to read stdout
            this.stdoutReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        synchronized (stdout) {
                            stdout.append(line).append("\n");
                            if (eventBus != null) {
                                eventBus.emitText(line + "\n");
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
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        synchronized (stderr) {
                            stderr.append(line).append("\n");
                            if (eventBus != null) {
                                eventBus.emitText(line + "\n");
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