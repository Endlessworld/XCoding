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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.xr21.ai.agent.entity.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.xr21.ai.agent.agent.LocalAgent.WORKSPACE_ROOT;

/**
 * PatchApply工具 - 接收git patch补丁文件内容并执行bash命令应用补丁
 * 返回所有修改后的行，每行前加行号
 * <p>
 * 增强功能：自动检测目标文件缩进风格（tab/空格），在应用patch前进行缩进归一化，
 * 解决因tab/空格混用导致的patch上下文匹配失败问题。
 * @author Endless
 */
public class EditFileWithGitPatchTool {

    private static final Logger logger = LoggerFactory.getLogger(EditFileWithGitPatchTool.class);
    private static final Pattern NEW_FILE_PATTERN =
            Pattern.compile("^\\+\\+\\+ (?:[ab]/(.+)|(.+))");

    /** Windows 系统判断缓存 */
    private static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");
    private static final Pattern HUNK_HEADER_PATTERN =
            Pattern.compile("^@@ -(\\d+)(?:,\\d+)? \\+(\\d+)(?:,(\\d+))? @@.*");

    /** 检测 patch 中上下文行的缩进风格 */
    private static final Pattern CONTEXT_LINE_PATTERN =
            Pattern.compile("^ (\\t+| +)");

    /** 重试条件配置：错误关键词 -> 修复参数 */
    private static final RetryRule[] RETRY_RULES = {
        new RetryRule("trailing whitespace", "--whitespace=fix", "whitespace issues"),
        new RetryRule("patch failed", "--whitespace=fix", "patch partially failed"),
        new RetryRule("does not match", "--whitespace=fix", "context mismatch"),
        new RetryRule("patch does not apply", "--whitespace=fix", "patch does not apply"),
        new RetryRule("indent", "--whitespace=fix", "indentation mismatch")
    };

    private final List<String> allowedPrefixes;

    public EditFileWithGitPatchTool() {
        this.allowedPrefixes = Collections.emptyList();
    }

    public EditFileWithGitPatchTool(List<String> allowedPrefixes) {
        this.allowedPrefixes = allowedPrefixes != null
                ? new ArrayList<>(allowedPrefixes)
                : Collections.emptyList();
    }

    private void validatePathAllowed(String path) {
        if (allowedPrefixes.isEmpty()) return;
        String norm = path.replace("\\", "/");
        boolean ok = allowedPrefixes.stream().anyMatch(p -> norm.startsWith(p.replace("\\", "/")));
        if (!ok) throw new SecurityException("Path not allowed: " + path + ". Allowed: " + allowedPrefixes);
    }

    /**
     * 归一化换行符和缩进空白字符。
     * 将 CRLF/LF 统一，并根据目标文件的缩进风格转换 patch 中的缩进。
     */
    private String normalizeWhitespace(String s) {
        if (s == null || s.isEmpty()) return "";
        // 1. 统一换行符：CRLF -> LF, 单独的 CR -> LF
        String result = s.replace("\r\n", "\n").replace('\r', '\n');
        // 2. 去除所有残留的 \r 字符
        result = result.replace("\r", "");
        // 3. 确保以 LF 换行符结尾
        if (!result.endsWith("\n")) result += "\n";
        return result;
    }

    /**
     * 归一化 patch 内容中的缩进，使其与目标文件匹配。
     * 检测目标文件的缩进风格，将 patch 中的上下文行和新增行的缩进进行转换。
     */
    private String normalizePatchIndentation(String patchContent, String workDir, int stripLevel) {
        // 按行分割前先确保没有残留的 \r 字符，避免缩进检测和匹配失败
        String cleanContent = patchContent.replace("\r", "");
        String[] lines = cleanContent.split("\n", -1);
        // 如果内容没有变化，复用原始引用
        if (cleanContent.equals(patchContent)) cleanContent = patchContent;
        String currentTargetFile = null;
        boolean[] hasTabIndent = {false};
        boolean[] hasSpaceIndent = {false};

        // 第一遍扫描：找出所有目标文件并检测其缩进风格
        for (String line : lines) {
            Matcher m = NEW_FILE_PATTERN.matcher(line);
            if (m.matches()) {
                String rel = m.group(1) != null ? m.group(1) : m.group(2);
                if ("/dev/null".equals(rel)) {
                    currentTargetFile = null;
                    continue;
                }
                currentTargetFile = resolveFilePath(workDir, rel, stripLevel);
                // 检测目标文件的缩进风格
                detectFileIndentStyle(currentTargetFile, hasTabIndent, hasSpaceIndent);
            }
        }

        // 如果目标文件没有明确的缩进风格，或 patch 和目标文件风格一致，则无需转换
        if (!hasTabIndent[0] || !hasSpaceIndent[0]) {
            return cleanContent;
        }

        // 目标文件使用 tab，但 patch 使用空格 -> 将 patch 中的空格缩进转为 tab
        // 目标文件使用空格，但 patch 使用 tab -> 将 patch 中的 tab 缩进转为空格
        boolean targetUsesTab = hasTabIndent[0];
        boolean targetUsesSpace = hasSpaceIndent[0];

        // 检测 patch 本身的缩进风格
        boolean patchUsesTab = false;
        boolean patchUsesSpace = false;
        for (String line : lines) {
            Matcher cm = CONTEXT_LINE_PATTERN.matcher(line);
            if (cm.matches()) {
                String indent = cm.group(1);
                if (indent.contains("\t")) patchUsesTab = true;
                if (indent.contains(" ")) patchUsesSpace = true;
                if (patchUsesTab && patchUsesSpace) break;
            }
        }

        // 如果两者缩进风格一致，无需转换
        if ((targetUsesTab && patchUsesTab) || (targetUsesSpace && patchUsesSpace)) {
            return cleanContent;
        }

        logger.info("Normalizing patch indentation: target={}, patch={}",
                targetUsesTab ? "Tab" : "Spaces", patchUsesTab ? "Tab" : "Spaces");

        return convertPatchIndentation(cleanContent, targetUsesTab);
    }

    /** 构建 git apply 命令，注入 -c core.autocrlf=false 和 core.eol=lf 解决 Windows CRLF 问题 */
    private List<String> buildGitApplyCommand(String flag, String patchFile, int strip, boolean checkOnly) {
        List<String> cmd = new ArrayList<>();
        // 强制禁用 autocrlf 并设置 eol=lf，确保 git apply 不会自动转换换行符
        cmd.add("git");
        cmd.add("-c"); cmd.add("core.autocrlf=false");
        cmd.add("-c"); cmd.add("core.eol=lf");
        cmd.add("apply");
        if (checkOnly) cmd.add("--check");
        cmd.add("--ignore-whitespace");
        cmd.add("--recount");
        if (flag != null && !flag.trim().isEmpty() && !flag.contains("--ignore-whitespace")) {
            cmd.add(flag.trim());
        }
        cmd.add("-p" + strip);
        cmd.add(patchFile);
        return cmd;
    }

    /**
     * 并发读取进程 stdout/stderr，统一管理 ExecutorService 生命周期。
     * try-finally + awaitTermination 确保线程池安全关闭。
     */
    private ProcessResult executeProcess(List<String> command, Path workDir)
            throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder();
        pb.command(command).directory(workDir.toFile()).redirectErrorStream(false);
        Process process = pb.start();

        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> f1 = executor.submit(() -> {
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String l; while ((l = r.readLine()) != null) stdout.append(l).append("\n");
                } catch (IOException e) { logger.warn("stdout read error: {}", e.getMessage()); }
            });
            Future<?> f2 = executor.submit(() -> {
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                    String l; while ((l = r.readLine()) != null) stderr.append(l).append("\n");
                } catch (IOException e) { logger.warn("stderr read error: {}", e.getMessage()); }
            });
            int exitCode = process.waitFor();
            f1.get(5, TimeUnit.SECONDS);
            f2.get(5, TimeUnit.SECONDS);
            return new ProcessResult(exitCode, stdout.toString(), stderr.toString());
        } catch (TimeoutException e) {
            process.destroyForcibly();
            return new ProcessResult(-1, stdout.toString(), stderr + "\n[TIMEOUT]\n");
        } catch (ExecutionException e) {
            return new ProcessResult(-1, stdout.toString(), stderr + "\n[ERROR] " + e.getMessage() + "\n");
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(3, TimeUnit.SECONDS)) executor.shutdownNow();
            } catch (InterruptedException ie) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    // @formatter:off
    @Tool(name = "edit_file_with_git_patch", description = """
        通过标准 diff/unified 格式的 git patch 补丁进行纯文本类型的文件编辑，
        本工具会将 patchContent 写入临时 .patch 文件，在工作空间根目录执行 git apply 以应用补丁，
        从而进行跨文件的高效编辑。

        标准 Patch 格式说明（必须严格遵守，否则将应用失败）：
        ============================================================

        一、基本结构
        ---
        合法的 patch 由以下部分组成：
          1. 文件头：--- 和 +++ 行，标识原文件和新文件路径
          2. hunk 块：@@ -旧行号,行数 +新行号,行数 @@ 开头，包含具体修改内容
          3. 上下文行：以空格开头，表示未修改的上下文
          4. 删除行：以减号 - 开头，表示要删除的行
          5. 新增行：以加号 + 开头，表示要新增的行

        二、完整示例
        ---
        <patch_examples>
From 9f7a8c3d1b2e4f5a6c7d8e9f0a1b2c3d4e5f6a7b Mon Sep 17 00:00:00 2001
From: Your Name <your.email@example.com>
Date: Mon, 1 Jun 2026 10:00:00 +0800
Subject: [PATCH] Comprehensive demo: all file operations in one patch
<+>UTF-8
===================================================================
diff --git a/demo.txt b/demo.txt
index 3b18e52..d00491f 100644
--- a/demo.txt
+++ b/demo.txt
@@ -1,2 +1,3 @@
 Hello, world!
 This file is modified.
+Added line to demonstrate modification.

diff --git a/newfile.txt b/newfile.txt
new file mode 100644
index 0000000..257cc56
--- /dev/null
+++ b/newfile.txt
@@ -0,0 +1 @@
+This is a brand new file.

diff --git a/oldfile.txt b/oldfile.txt
deleted file mode 100644
index e69de29..0000000
--- a/oldfile.txt
+++ /dev/null
@@ -1 +0,0 @@
-This file will be deleted.

diff --git a/script.sh b/app/script.sh
similarity index 61%
rename from script.sh
rename to app/script.sh
index 8be128f..b2f7e6c 100755
--- a/script.sh
+++ b/app/script.sh
@@ -1,3 +1,3 @@
 #!/bin/bash
-echo "Old path"
+echo "Moved to app/ and modified"

diff --git a/README.md b/README.txt
similarity index 100%
copy from README.md
copy to README.txt
index d03e242..d03e242 100644
--- a/README.md
+++ b/README.txt
@@ -1 +1 @@
-# README (copied)
+# README (copied)

diff --git a/binary.png b/binary.png
new file mode 100644
index 0000000..4a88ef4
--- /dev/null
+++ b/binary.png
@@ -0,0 +1 @@
+GIT binary patch
+literal 123
+zcmZ2X%!T>2_s0aJH8Gmt<2Xj#tB`rT1#3Ld4KJ9QPz~sxV`vBPt;{Wsw8zKw
+PK^r(2Lwx|p6Fp1k
+
+literal 0
+HcmV?d00001
+

diff --git a/link.lnk b/link.lnk
new file mode 120000
index 0000000..9f7a8c3
--- /dev/null
+++ b/link.lnk
@@ -0,0 +1 @@
+target_file.txt
\\ No newline at end of file

diff --git a/script.sh b/script.sh
old mode 100755
new mode 100644
index 8be128f..b2f7e6c
--- a/script.sh
+++ b/script.sh
@@ -1,3 +1,3 @@
 #!/bin/bash
-echo "Old path"
+echo "Mode changed from 755 to 644
</patch_examples>

        三、注意事项
        ---
        1. 换行符：必须统一使用 LF（\\n），禁止使用 CRLF（\\r\\n）
        2. 路径前缀：默认使用 a/ 和 b/ 前缀（strip=1 时自动去除）
        3. 行号准确性：旧文件行号必须与当前文件完全匹配，否则 git apply 会失败
        4. 上下文匹配：hunk 中上下文行（空格开头）必须与目标文件完全一致
        5. 新文件创建：--- /dev/null 和 +++ b/path/to/NewFile.java
        6. 文件删除：--- a/path/to/File.java 和 +++ /dev/null
        7. 末尾换行：patch 文件必须以换行符结尾，不可遗漏
        8. 多文件修改：可在同一个 patch 中包含多个文件，用文件头分隔
        9. 参考patch_examples中的样例
        四、常见失败原因
        ---
        - 上下文行与目标文件不匹配（最常见）
        - 换行符混用（CRLF 混入 LF 中）
        - 行号错误（文件已被修改，行号偏移）
        - patch 末尾缺少换行符
        - 路径前缀层数（strip 参数）不正确
        - 文件包含特殊字符或二进制内容

        Usage:
            - patchContent: git patch 补丁文件内容（标准 diff/unified format）
            - strip: 去除路径前缀的层数，相当于 git apply -p 参数（可选，默认1），设为 1 去除 a/ 和 b/ 前缀
            - 换行符：Windows 环境下要注意 CRLF/LF 问题，统一使用 LF 换行符
            - 禁止使用 Bash 命令替代该工具
            - 积极使用跨文件的patch实现更高效编辑
            - patch内容不得过大500行以内最佳 更多内容修复改分成多个patch
        """)
    public Map<String, Object> editFileWithGitPatch(
            @JsonProperty(value = "patchContent", required = true)
            @JsonPropertyDescription("The git patch content to apply (standard diff/unified format)")
            String patchContent,
            @JsonProperty(value = "strip")
            @JsonPropertyDescription("Number of path components to strip (like git apply -p, default: 1)")
            Integer strip
    ) { // @formatter:on
        long startTime = System.currentTimeMillis();
        StringBuilder warnings = new StringBuilder();

        if (patchContent == null || patchContent.trim().isEmpty()) {
            return ToolResult.builder().error("Patch content cannot be null or empty").build();
        }
        patchContent = normalizeWhitespace(patchContent);

        if (!patchContent.endsWith("\n")) {
            patchContent += "\n";
            warnings.append("Warning: patch content was missing trailing newline, auto-fixed.\n");
        }
        patchContent = patchContent.replaceAll("\\\\ No newline at end of file\n", "");

        if (!patchContent.contains("--- ") || !patchContent.contains("+++ ")) {
            return ToolResult.builder()
                    .error("Invalid patch format: missing '---' or '+++' headers. Content starts: "
                            + patchContent.substring(0, Math.min(200, patchContent.length())))
                    .build();
        }
        if (!patchContent.contains("@@ -")) {
            return ToolResult.builder()
                    .error("Invalid patch format: missing hunk header (@@ -...). Patch must contain at least one hunk.")
                    .build();
        }

        String workDir = WORKSPACE_ROOT;
        int stripLevel = (strip != null) ? strip : 1;
        if (stripLevel < 0) {
            return ToolResult.builder().error("strip level must be >= 0, got: " + stripLevel).build();
        }
        try { validatePathAllowed(workDir); }
        catch (SecurityException e) { return ToolResult.builder().error(e.getMessage()).build(); }

        Path workPath = Paths.get(workDir);
        if (!Files.exists(workPath)) {
            return ToolResult.builder().error("Working directory does not exist: " + workDir).build();
        }

        // 缩进归一化：让 patch 的缩进风格与目标文件一致
        patchContent = normalizePatchIndentation(patchContent, workDir, stripLevel);

        Path tempPatchFile = null;
        try {
            tempPatchFile = Files.createTempFile("patch_", ".patch");
            // 始终使用 byte[] 写入，避免 Windows 上 Files.writeString 自动将 \n 转为 \r\n
            // 导致 .patch 文件包含 CRLF 而目标文件为 LF，造成 git apply 上下文不匹配
            // 跨平台统一使用字节写入，确保 patch 文件内容与 patchContent 完全一致
            byte[] patchBytes = patchContent.getBytes(StandardCharsets.UTF_8);
            Files.write(tempPatchFile, patchBytes);
        } catch (IOException e) {
            return ToolResult.builder().error("Failed to create temp patch file: " + e.getMessage()).build();
        }

        try {
            // Step 1: 先执行 git apply --check 验证 patch 是否可应用，不实际修改文件
            List<String> checkCommand = buildGitApplyCommand("",
                    tempPatchFile.toAbsolutePath().toString(), stripLevel, true);
            ProcessResult checkResult = executeProcess(checkCommand, workPath);
            if (checkResult.exitCode != 0) {
                String checkStderr = checkResult.stderr;
                // 收集诊断信息帮助用户定位问题
                String diagInfo = collectDiagnosticInfo(workPath, patchContent, checkStderr,
                        "git apply --check 检查失败，patch 无法应用。请根据以下错误信息修改 patch 后重试。\n");
                String errorMsg = "git apply --check 检查失败，patch 无法应用。\n\n"
                        + "错误信息:\n" + checkStderr + "\n"
                        + diagInfo;
                logger.warn("git apply --check failed for patch, returning error to user");
                return ToolResult.builder()
                        .success(false)
                        .error(errorMsg)
                        .put("checkFailed", true)
                        .put("checkStderr", checkStderr)
                        .put("patchContent", patchContent)
                        .build();
            }

            // Step 2: --check 通过后，执行实际的 git apply 应用 patch
            List<String> command = buildGitApplyCommand("",
                    tempPatchFile.toAbsolutePath().toString(), stripLevel, false);
            ProcessResult result = executeProcess(command, workPath);
            int exitCode = result.exitCode;
            StringBuilder stdout = new StringBuilder(result.stdout);
            StringBuilder stderr = new StringBuilder(result.stderr);

            String rejectContent = handleRejectFiles(workPath);

            // 先解析 patch 文件信息（重试逻辑中 tryConvertTargetFileIndentation 需要用到 patchFiles）
            List<PatchFileInfo> patchFiles = parsePatchFiles(patchContent, workDir, stripLevel);

            if (exitCode != 0) {
                String stderrLow = stderr.toString().toLowerCase();
                String matchedDesc = null;
                for (RetryRule rule : RETRY_RULES) {
                    if (stderrLow.contains(rule.keyword)) {
                        matchedDesc = rule.desc;
                        logger.warn("git apply failed ({}), retrying with {}...", rule.desc, rule.flag);

                    // 如果重试仍因缩进问题失败，尝试转换目标文件缩进
                    if (rule.keyword.equals("patch does not apply") || rule.keyword.equals("does not match")) {
                        boolean converted = tryConvertTargetFileIndentation(patchFiles);
                        if (converted) logger.info("Converted target file indentation, retrying git apply...");
                    }

                        warnings.append("Note: Retried with ").append(rule.flag)
                                .append(" due to ").append(rule.desc).append(".\n");
                        exitCode = retryGitApply(workPath, tempPatchFile, stripLevel, stdout, stderr, rule.flag);
                        break;
                    }
                }
                if (exitCode != 0) {
                    stderr.append("\n===== DIAGNOSTIC INFO =====\n")
                          .append(collectDiagnosticInfo(workPath, patchContent, stderr.toString(),
                                  matchedDesc != null ? "Retry still failed. " : ""));
                }
            }

            // 在 git apply 成功（或重试成功）后，重新读取修改行的内容
            // 确保 patch 已实际应用到文件上，避免第一次失败但重试成功后读取到未修改的内容
            Map<String, List<ModifiedLine>> allModifiedLines = new LinkedHashMap<>();
            if (exitCode == 0) {
                for (PatchFileInfo pf : patchFiles) {
                    Path fp = Paths.get(pf.filePath);
                    if (Files.exists(fp)) {
                        List<ModifiedLine> lines = readModifiedLines(fp, pf);
                        if (!lines.isEmpty()) allModifiedLines.put(pf.filePath, lines);
                    }
                }
            }

            return buildResult(exitCode, stdout.toString(), stderr.toString(), rejectContent,
                    allModifiedLines, workDir, startTime, System.currentTimeMillis(), warnings, patchContent);

        } catch (IOException e) {
            return ToolResult.builder().error("Error executing git apply: " + e.getMessage()).build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResult.builder().error("Patch application was interrupted: " + e.getMessage()).build();
        } finally {
            if (tempPatchFile != null) {
                try { Files.deleteIfExists(tempPatchFile); }
                catch (IOException e) { logger.warn("Failed to delete temp patch file: {}", tempPatchFile); }
            }
        }
    }

    private String collectDiagnosticInfo(Path workPath, String patchContent, String stderr, String prefix) {
        StringBuilder diag = new StringBuilder(prefix).append("[Diagnostic Info]\n");
        diag.append("Patch preview (first 200 chars):\n");
        diag.append(patchContent.substring(0, Math.min(200, patchContent.length()))).append("\n\n");
        diag.append("Working directory file listing (top 2 levels):\n");
        try (Stream<Path> stream = Files.walk(workPath, 2)) {
            List<String> files = stream.filter(p -> !p.equals(workPath))
                    .map(p -> workPath.relativize(p).toString().replace('\\', '/'))
                    .sorted().collect(Collectors.toList());
            if (files.isEmpty()) diag.append("  (empty directory)\n");
            else files.forEach(f -> diag.append("  ").append(f).append("\n"));
        } catch (IOException e) {
            diag.append("  Failed to list files: ").append(e.getMessage()).append("\n");
        }
        diag.append("\nStderr (last 300 chars):\n");
        if (stderr.length() > 300) diag.append("...").append(stderr.substring(stderr.length() - 300));
        else diag.append(stderr);
        if (!diag.toString().endsWith("\n")) diag.append("\n");
        return diag.toString();
    }

    private int retryGitApply(Path workPath, Path tempPatchFile, int stripLevel,
                               StringBuilder stdout, StringBuilder stderr, String flag)
            throws IOException, InterruptedException {
        // 重试时组合 flag：始终包含 --ignore-whitespace，再加上额外的 flag（如 --whitespace=fix）
        String combinedFlag;
        if (flag != null && !flag.isEmpty() && !flag.contains("--ignore-whitespace")) {
            combinedFlag = "--ignore-whitespace " + flag;
        } else {
            combinedFlag = (flag != null && !flag.isEmpty()) ? flag : "--ignore-whitespace";
        }
        ProcessResult result = executeProcess(
                buildGitApplyCommand(combinedFlag, tempPatchFile.toAbsolutePath().toString(), stripLevel, false), workPath);
        stdout.setLength(0); stdout.append(result.stdout);
        stderr.setLength(0); stderr.append(result.stderr);
        return result.exitCode;
    }

    private Map<String, Object> buildResult(int exitCode, String stdout, String stderr,
            String rejectContent, Map<String, List<ModifiedLine>> allModifiedLines,
            String workDir, long startTime, long endTime, StringBuilder warnings, String patchContent) {
        boolean success = exitCode == 0;
        StringBuilder cb = new StringBuilder();
        cb.append("Patch apply ").append(success ? "succeeded" : "failed").append("\n");
        cb.append("Exit code: ").append(exitCode).append("\n\n");
        if (warnings != null && !warnings.isEmpty()) cb.append("WARNINGS:\n").append(warnings).append("\n");
        if (!stdout.isEmpty()) cb.append("STDOUT:\n").append(stdout).append("\n");
        if (!stderr.isEmpty()) cb.append("STDERR:\n").append(stderr).append("\n");
        if (!rejectContent.isEmpty()) cb.append("REJECTS:\n").append(rejectContent).append("\n");
        cb.append("Modified files:\n");

        ToolResult result = ToolResult.builder().success(success)
                .put("exitCode", exitCode).put("stdout", stdout).put("stderr", stderr)
                .put("rejectContent", rejectContent).put("workDir", workDir)
                .put("processingTimeMs", endTime - startTime)
                .put("modifiedFileCount", allModifiedLines.size());

        int totalLines = 0;
        for (Map.Entry<String, List<ModifiedLine>> entry : allModifiedLines.entrySet()) {
            List<ModifiedLine> lines = entry.getValue();
            String displayPath = lines.isEmpty() ? entry.getKey() : lines.get(0).filePath;
            cb.append("\n  File: ").append(displayPath).append("\n");
            for (ModifiedLine ml : lines) {
                cb.append(String.format("    %d: %s", ml.lineNumber, ml.content)).append("\n");
                totalLines++;
                result.location(ml.filePath, ml.lineNumber);
            }
        }
        cb.append("\nTotal modified lines: ").append(totalLines);
        result.metadata("totalModifiedLines", totalLines);
        result.content(cb.toString());
        if (warnings != null && !warnings.isEmpty()) result.put("warnings", warnings.toString());

        if (success && patchContent != null) {
            Map<String, FileDiffContent> fileDiffs = parseFileDiffContents(patchContent, workDir);
            for (Map.Entry<String, List<ModifiedLine>> entry : allModifiedLines.entrySet()) {
                String fp = entry.getValue().isEmpty() ? entry.getKey() : entry.getValue().get(0).filePath;
                FileDiffContent dc = fileDiffs.get(fp);
                if (dc != null) result.toolCallContent(ToolResult.createDiffContent(fp, dc.oldText, dc.newText));
            }
        }
        return result.build();
    }

    private List<PatchFileInfo> parsePatchFiles(String patchContent, String workDir, int stripLevel) {
        List<PatchFileInfo> files = new ArrayList<>();
        String[] lines = patchContent.split("\n");
        long hunkCount = Arrays.stream(lines).filter(l -> l.startsWith("@@ -")).count();
        if (hunkCount == 0) return files;

        PatchFileInfo current = null;
        for (String line : lines) {
            Matcher m = NEW_FILE_PATTERN.matcher(line);
            if (m.matches()) {
                String rel = m.group(1) != null ? m.group(1) : m.group(2);
                if ("/dev/null".equals(rel)) continue;
                current = new PatchFileInfo(resolveFilePath(workDir, rel, stripLevel), rel);
                files.add(current);
                continue;
            }
            Matcher hm = HUNK_HEADER_PATTERN.matcher(line);
            if (hm.matches() && current != null) {
                int startLine = Integer.parseInt(hm.group(2));
                int lineCount = hm.group(3) != null ? Integer.parseInt(hm.group(3)) : 1;
                current.hunks.add(new HunkRange(startLine, lineCount));
            }
        }
        return files;
    }

    /**
     * 解析路径，去除stripLevel层前缀，并校验防止路径穿越。
     * 增强：parts.length <= stripLevel 时直接使用 relativePath 作为完整路径。
     */
    private String resolveFilePath(String workDir, String relativePath, int stripLevel) {
        if (relativePath == null || relativePath.isEmpty()) return workDir;
        String norm = relativePath.replace('/', File.separatorChar);
        String[] parts = norm.split(Pattern.quote(File.separator));

        if (parts.length > stripLevel) {
            norm = String.join(File.separator, Arrays.copyOfRange(parts, stripLevel, parts.length));
        }
        // parts.length <= stripLevel 时保持 norm = relativePath 不变

        Path resolved = Paths.get(workDir, norm).normalize();
        if (!resolved.startsWith(Paths.get(workDir).normalize())) {
            logger.warn("Path traversal detected: {} -> {} outside {}", relativePath, resolved, workDir);
            return Paths.get(workDir).resolve("INVALID_PATH_TRAVERSAL").toString();
        }
        return resolved.toString();
    }

    private List<ModifiedLine> readModifiedLines(Path filePath, PatchFileInfo patchFile) {
        List<ModifiedLine> result = new ArrayList<>();
        try {
            List<String> fileLines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
            for (HunkRange hunk : patchFile.hunks) {
                int end = Math.min(hunk.startLine + hunk.lineCount - 1, fileLines.size());
                for (int i = hunk.startLine; i <= end; i++) {
                    result.add(new ModifiedLine(filePath.toAbsolutePath().toString(), i, fileLines.get(i - 1)));
                }
            }
        } catch (IOException e) { logger.warn("Failed to read modified file: {}", filePath, e); }
        return result;
    }

    private String handleRejectFiles(Path workPath) {
        StringBuilder content = new StringBuilder();
        try {
            Files.walk(workPath).filter(p -> p.toString().endsWith(".rej")).forEach(p -> {
                try {
                    content.append("Reject file: ").append(p.getFileName()).append("\n");
                    content.append(Files.readString(p, StandardCharsets.UTF_8)).append("\n");
                    Files.delete(p);
                } catch (IOException e) { logger.warn("Failed to process reject file: {}", p); }
            });
        } catch (IOException e) { logger.warn("Failed to walk for .rej files: {}", e.getMessage()); }
        return content.toString();
    }

    private Map<String, FileDiffContent> parseFileDiffContents(String patchContent, String workDir) {
        Map<String, FileDiffContent> result = new LinkedHashMap<>();
        String[] lines = patchContent.split("\n");
        String currentFilePath = null;
        StringBuilder oldText = new StringBuilder();
        StringBuilder newText = new StringBuilder();
        boolean inHunk = false;
        int stripLevel = 1;

        for (String line : lines) {
            Matcher m = NEW_FILE_PATTERN.matcher(line);
            if (m.matches()) {
                if (currentFilePath != null && (oldText.length() > 0 || newText.length() > 0)) {
                    result.put(currentFilePath, new FileDiffContent(oldText.toString(), newText.toString()));
                }
                String rel = m.group(1) != null ? m.group(1) : m.group(2);
                currentFilePath = "/dev/null".equals(rel) ? null : resolveFilePath(workDir, rel, stripLevel);
                oldText = new StringBuilder();
                newText = new StringBuilder();
                inHunk = false;
                continue;
            }
            if (HUNK_HEADER_PATTERN.matcher(line).matches()) { inHunk = true; continue; }
            if (inHunk && currentFilePath != null) {
                if (line.startsWith("-") && !line.startsWith("---")) oldText.append(line.substring(1)).append("\n");
                else if (line.startsWith("+") && !line.startsWith("+++")) newText.append(line.substring(1)).append("\n");
            }
        }
        if (currentFilePath != null && (oldText.length() > 0 || newText.length() > 0)) {
            result.put(currentFilePath, new FileDiffContent(oldText.toString(), newText.toString()));
        }
        return result;
    }

    /**
     * 检测文件的缩进风格（tab vs 空格）
     */
    private void detectFileIndentStyle(String filePath, boolean[] hasTab, boolean[] hasSpace) {
        if (filePath == null || filePath.isEmpty()) return;
        Path path = Paths.get(filePath);
        if (!Files.exists(path) || !Files.isRegularFile(path)) return;
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line.isEmpty() || line.isBlank()) continue;
                char first = line.charAt(0);
                if (first == '\t') hasTab[0] = true;
                else if (first == ' ') hasSpace[0] = true;
                if (hasTab[0] && hasSpace[0]) break;
            }
        } catch (IOException e) {
            logger.warn("Failed to detect indent style for {}: {}", filePath, e.getMessage());
        }
    }

    /**
     * 转换 patch 内容中的缩进。
     * 将空格缩进转为 tab（或反之），保持缩进层级不变。
     */
    private String convertPatchIndentation(String patchContent, boolean toTab) {
        StringBuilder result = new StringBuilder();
        String[] lines = patchContent.split("\n", -1);
        for (String line : lines) {
            if (line.isEmpty()) {
                result.append('\n');
                continue;
            }
            char first = line.charAt(0);
            // 只转换上下文行（空格开头）、删除行（-开头）和新增行（+开头）
            if (first == ' ' || first == '-' || first == '+') {
                String indent = extractLeadingWhitespace(line.substring(1));
                String rest = line.substring(1 + indent.length());
                String convertedIndent = convertIndent(indent, toTab);
                result.append(first).append(convertedIndent).append(rest).append('\n');
            } else {
                result.append(line).append('\n');
            }
        }
        return result.toString();
    }

    /** 提取行首空白（空格和tab） */
    private String extractLeadingWhitespace(String s) {
        int i = 0;
        while (i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == '\t')) {
            i++;
        }
        return s.substring(0, i);
    }

    /**
     * 转换缩进：空格<->tab互转。
     * toTab=true: 每4个空格转1个tab
     * toTab=false: 每个tab转4个空格
     */
    private String convertIndent(String indent, boolean toTab) {
        if (indent.isEmpty()) return indent;
        if (toTab) {
            // 空格 -> tab：每4个空格转1个tab
            int spaceCount = 0;
            for (char c : indent.toCharArray()) {
                if (c == ' ') spaceCount++;
                else if (c == '\t') spaceCount += 4;
            }
            int tabs = spaceCount / 4;
            return "\t".repeat(Math.max(1, tabs));
        } else {
            // tab -> 空格：每个tab转4个空格
            return indent.replace("\t", "    ");
        }
    }

    /**
     * 尝试转换目标文件的缩进（tab<->空格），以便 patch 能正确应用。
     */
    private boolean tryConvertTargetFileIndentation(List<PatchFileInfo> patchFiles) {
        boolean converted = false;
        for (PatchFileInfo pf : patchFiles) {
            Path fp = Paths.get(pf.filePath);
            if (!Files.exists(fp)) continue;
            try {
                String content = Files.readString(fp, StandardCharsets.UTF_8);
                String[] lines = content.split("\n", -1);
                boolean hasTab = false;
                boolean hasSpace = false;
                for (String line : lines) {
                    if (line.isEmpty() || line.isBlank()) continue;
                    char first = line.charAt(0);
                    if (first == '\t') hasTab = true;
                    else if (first == ' ') hasSpace = true;
                    if (hasTab && hasSpace) break;
                }
                // 如果文件同时有 tab 和空格，不做自动转换（风险太大）
                if (hasTab && hasSpace) continue;

                String newContent;
                if (hasTab && !hasSpace) {
                    // 文件全是 tab -> 转为空格
                    newContent = convertFileIndentation(content, false);
                    Files.writeString(fp, newContent, StandardCharsets.UTF_8);
                    converted = true;
                    logger.info("Converted {} from Tab to Spaces for patch compatibility", pf.filePath);
                } else if (hasSpace && !hasTab) {
                    // 文件全是空格 -> 转为 tab
                    newContent = convertFileIndentation(content, true);
                    Files.writeString(fp, newContent, StandardCharsets.UTF_8);
                    converted = true;
                    logger.info("Converted {} from Spaces to Tab for patch compatibility", pf.filePath);
                }
            } catch (IOException e) {
                logger.warn("Failed to convert indentation for {}: {}", pf.filePath, e.getMessage());
            }
        }
        return converted;
    }

    /**
     * 转换整个文件的缩进
     */
    private String convertFileIndentation(String content, boolean toTab) {
        StringBuilder result = new StringBuilder();
        String[] lines = content.split("\n", -1);
        for (String line : lines) {
            String indent = extractLeadingWhitespace(line);
            String rest = line.substring(indent.length());
            result.append(convertIndent(indent, toTab)).append(rest).append('\n');
        }
        return result.toString();
    }

    private static class RetryRule {
        final String keyword;
        final String flag;
        final String desc;
        RetryRule(String keyword, String flag, String desc) {
            this.keyword = keyword;
            this.flag = flag;
            this.desc = desc;
        }
    }

    private static class ProcessResult {
        final int exitCode;
        final String stdout;
        final String stderr;
        ProcessResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }

    private static class HunkRange {
        final int startLine;
        final int lineCount;
        HunkRange(int startLine, int lineCount) { this.startLine = startLine; this.lineCount = lineCount; }
    }

    private static class PatchFileInfo {
        final String filePath;
        final String relativePath;
        final List<HunkRange> hunks = new ArrayList<>();
        PatchFileInfo(String filePath, String relativePath) { this.filePath = filePath; this.relativePath = relativePath; }
    }

    private static class ModifiedLine {
        final String filePath;
        final int lineNumber;
        final String content;
        ModifiedLine(String filePath, int lineNumber, String content) {
            this.filePath = filePath; this.lineNumber = lineNumber; this.content = content;
        }
    }

    private static class FileDiffContent {
        final String oldText;
        final String newText;
        FileDiffContent(String oldText, String newText) { this.oldText = oldText; this.newText = newText; }
    }
}
