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
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.xr21.ai.agent.agent.LocalAgent.WORKSPACE_ROOT;

/**
 * Git patch application tool for efficient file editing.
 * Applies unified diff patches using git apply with proper line ending handling.
 *
 * @author Endless
 */
public final class EditFileWithGitPatchTool {

    private static final Logger logger = LoggerFactory.getLogger(EditFileWithGitPatchTool.class);
    private static final Pattern NEW_FILE_PATTERN = Pattern.compile("^\\+\\+\\+ (?:[ab]/(.+)|(.+))");
    private static final Pattern HUNK_HEADER_PATTERN = Pattern.compile("^@@ -(\\d+)(?:,\\d+)? \\+(\\d+)(?:,(\\d+))? @@.*");
    private static final String LINE_SEPARATOR = System.lineSeparator();
    private static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");

    private final List<String> allowedPrefixes;

    public EditFileWithGitPatchTool() {
        this.allowedPrefixes = List.of();
    }

    public EditFileWithGitPatchTool(List<String> allowedPrefixes) {
        this.allowedPrefixes = allowedPrefixes != null ? List.copyOf(allowedPrefixes) : List.of();
    }

    @Tool(name = "edit_file_with_git_patch", description = """
        Apply git patch for efficient file editing. Uses git apply with proper line ending handling.

        IMPORTANT: Line Ending Requirements
        ====================================
        Current platform: %s
        Platform default line separator: %s
        Git configuration used: core.autocrlf=false, core.eol=lf

        CRITICAL: Generate patches with the platform's default line separator (%s).
        On Windows, use CRLF (\\r\\n). On Unix/Linux/macOS, use LF (\\n).
        The tool does NOT perform automatic line ending normalization.

        Standard Patch Format:
        ======================
        diff --git a/file.txt b/file.txt
        --- a/file.txt
        +++ b/file.txt
        @@ -1,3 +1,3 @@
         line1
        -old line
        +new line
         line3

        Parameters:
        - patchContent: Unified diff format patch content (required)
        - strip: Path prefix levels to strip (default: 1, removes a/ and b/ prefixes)

        Best Practices:
        - Keep patches under 500 lines for reliability
        - Use cross-file patches for multiple edits
        - Ensure context lines match target file exactly
        - Always end patch with newline character
        - Use platform-appropriate line endings in patch content
        - Be sure to pay attention when multiple patches occur simultaneously on the same file
            Except for the first patch, the source files were changed when subsequent patches were executed, especially the line numbers
            You should generate patches based on files after the last patch; Otherwise, the patch will not succeed
        """)
    public Map<String, Object> editFileWithGitPatch(
            @JsonProperty(value = "patchContent", required = true)
            @JsonPropertyDescription("The git patch content to apply (unified diff format with platform-appropriate line endings)")
            String patchContent,
            @JsonProperty(value = "strip")
            @JsonPropertyDescription("Number of path components to strip (default: 1)")
            Integer strip) {

        long startTime = System.currentTimeMillis();
        StringBuilder warnings = new StringBuilder();

        // Validate patch content
        if (patchContent == null || patchContent.trim().isEmpty()) {
            return ToolResult.builder().error("Patch content cannot be null or empty").build();
        }

        // Validate patch format
        if (!patchContent.contains("--- ") || !patchContent.contains("+++ ")) {
            return ToolResult.builder()
                    .error("Invalid patch format: missing '---' or '+++' headers")
                    .build();
        }
        if (!patchContent.contains("@@ -")) {
            return ToolResult.builder()
                    .error("Invalid patch format: missing hunk header (@@ -...)")
                    .build();
        }

        // Validate strip parameter
        int stripLevel = strip != null ? strip : 1;
        if (stripLevel < 0) {
            return ToolResult.builder().error("strip level must be >= 0, got: " + stripLevel).build();
        }

        // Validate working directory
        Path workPath = Paths.get(WORKSPACE_ROOT);
        if (!Files.exists(workPath)) {
            return ToolResult.builder().error("Working directory does not exist: " + WORKSPACE_ROOT).build();
        }

        // Create temporary patch file
        Path tempPatchFile;
        try {
            tempPatchFile = Files.createTempFile("patch_", ".patch");
            Files.write(tempPatchFile, patchContent.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            return ToolResult.builder().error("Failed to create temp patch file: " + e.getMessage()).build();
        }

        try {
            // Parse patch files for result reporting
            List<PatchFileInfo> patchFiles = parsePatchFiles(patchContent, WORKSPACE_ROOT, stripLevel);

            // Execute git apply with check first
            ProcessResult checkResult = executeGitApply(tempPatchFile, stripLevel, true, workPath);
            if (checkResult.exitCode != 0) {
                return ToolResult.builder()
                        .success(false)
                        .error("Patch validation failed: " + checkResult.stderr)
                        .put("checkStderr", checkResult.stderr)
                        .build();
            }
            // Apply patch
            ProcessResult result = executeGitApply(tempPatchFile, stripLevel, false, workPath);

            // Read modified lines on success
            Map<String, List<ModifiedLine>> modifiedLines = Map.of();
            if (result.exitCode == 0) {
                modifiedLines = readModifiedLines(patchFiles);
            }
            return buildResult(result, modifiedLines, warnings, startTime, WORKSPACE_ROOT);

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResult.builder().error("Patch application failed: " + e.getMessage()).build();
        } finally {
            try {
                Files.deleteIfExists(tempPatchFile);
            } catch (IOException e) {
                logger.warn("Failed to delete temp patch file: {}", tempPatchFile);
            }
        }
    }

    private ProcessResult executeGitApply(Path patchFile, int strip, boolean checkOnly, Path workDir)
            throws IOException, InterruptedException {
        List<String> command = buildGitApplyCommand(patchFile.toString(), strip, checkOnly);
        ProcessBuilder pb = new ProcessBuilder(command)
                .directory(workDir.toFile())
                .redirectErrorStream(false);
        Process process = pb.start();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> stdoutFuture = executor.submit(() -> readStream(process.getInputStream()));
            Future<String> stderrFuture = executor.submit(() -> readStream(process.getErrorStream()));
            int exitCode = process.waitFor();
            String stdout = stdoutFuture.get(5, TimeUnit.SECONDS);
            String stderr = stderrFuture.get(5, TimeUnit.SECONDS);
            return new ProcessResult(exitCode, stdout, stderr);
        } catch (Exception e) {
            process.destroyForcibly();
            return new ProcessResult(-1, "", "Process execution error: " + e.getMessage());
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private String readStream(java.io.InputStream stream) {
        String osName = System.getProperty("os.name").toLowerCase();
        String charsetName = osName.contains("win") ? "GBK" : Charset.defaultCharset().name();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, charsetName))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            logger.warn("Failed to read stream: {}", e.getMessage());
            return "";
        }
    }

    private List<String> buildGitApplyCommand(String patchFile, int strip, boolean checkOnly) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("-c");
        command.add("core.autocrlf=false");
        command.add("-c");
        command.add("core.eol=lf");
        command.add("apply");
        if (checkOnly) {
            command.add("--check");
        }
        command.add("--ignore-whitespace");
        command.add("-p" + strip);
        command.add(patchFile);
        return command;
    }

    private List<PatchFileInfo> parsePatchFiles(String patchContent, String workDir, int stripLevel) {
        List<PatchFileInfo> files = new ArrayList<>();
        String[] lines = patchContent.split("\\R");

        PatchFileInfo current = null;
        for (String line : lines) {
            Matcher m = NEW_FILE_PATTERN.matcher(line);
            if (m.matches()) {
                String relPath = m.group(1) != null ? m.group(1) : m.group(2);
                if ("/dev/null".equals(relPath)) {
                    current = null;
                    continue;
                }
                String filePath = resolveFilePath(workDir, relPath, stripLevel);
                current = new PatchFileInfo(filePath, relPath);
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

    private String resolveFilePath(String workDir, String relativePath, int stripLevel) {
        if (relativePath == null || relativePath.isEmpty()) {
            return workDir;
        }

        String normalized = relativePath.replace('/', java.io.File.separatorChar);
        String[] parts = normalized.split(java.util.regex.Pattern.quote(java.io.File.separator));

        if (parts.length > stripLevel) {
            normalized = String.join(java.io.File.separator,
                    Arrays.copyOfRange(parts, stripLevel, parts.length));
        }

        Path resolved = Paths.get(workDir, normalized).normalize();
        if (!resolved.startsWith(Paths.get(workDir).normalize())) {
            logger.warn("Path traversal detected: {}", relativePath);
            return Paths.get(workDir).resolve("INVALID_PATH").toString();
        }
        return resolved.toString();
    }

    private Map<String, List<ModifiedLine>> readModifiedLines(List<PatchFileInfo> patchFiles) {
        Map<String, List<ModifiedLine>> result = new java.util.LinkedHashMap<>();
        for (PatchFileInfo pf : patchFiles) {
            Path filePath = Paths.get(pf.filePath);
            if (!Files.exists(filePath)) {
                continue;
            }
            try {
                List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
                List<ModifiedLine> modifiedLines = new ArrayList<>();
                for (HunkRange hunk : pf.hunks) {
                    int end = Math.min(hunk.startLine + hunk.lineCount - 1, lines.size());
                    for (int i = hunk.startLine; i <= end; i++) {
                        modifiedLines.add(new ModifiedLine(pf.filePath, i, lines.get(i - 1)));
                    }
                }
                if (!modifiedLines.isEmpty()) {
                    result.put(pf.filePath, modifiedLines);
                }
            } catch (IOException e) {
                logger.warn("Failed to read modified file: {}", pf.filePath, e);
            }
        }
        return result;
    }

    private Map<String, Object> buildResult(ProcessResult result,
                                            Map<String, List<ModifiedLine>> modifiedLines,
                                            StringBuilder warnings,
                                            long startTime,
                                            String workDir) {

        boolean success = result.exitCode == 0;
        long duration = System.currentTimeMillis() - startTime;

        ToolResult builder = ToolResult.builder()
                .success(success)
                .put("exitCode", result.exitCode)
                .put("stdout", result.stdout)
                .put("stderr", result.stderr)
                .put("processingTimeMs", duration)
                .put("modifiedFileCount", modifiedLines.size());

        StringBuilder content = new StringBuilder();
        content.append("Patch apply ").append(success ? "succeeded" : "failed").append("\n");
        content.append("Exit code: ").append(result.exitCode).append("\n");
        content.append("Processing time: ").append(duration).append("ms\n");

        if (!warnings.isEmpty()) {
            content.append("\nWarnings:\n").append(warnings);
        }
        if (!result.stdout.isEmpty()) {
            content.append("\nStdout:\n").append(result.stdout).append("\n");
        }
        if (!result.stderr.isEmpty()) {
            content.append("\nStderr:\n").append(result.stderr).append("\n");
        }

        content.append("\nModified files: ").append(modifiedLines.size()).append("\n");
        int totalLines = 0;
        for (Map.Entry<String, List<ModifiedLine>> entry : modifiedLines.entrySet()) {
            content.append("\n  ").append(entry.getKey()).append(":\n");
            for (ModifiedLine line : entry.getValue()) {
                content.append(String.format("    %d: %s\n", line.lineNumber, line.content));
                builder.location(line.filePath, line.lineNumber);
                totalLines++;
            }
        }
        content.append("\nTotal modified lines: ").append(totalLines);

        builder.content(content.toString());
        builder.metadata("totalModifiedLines", totalLines);

        return builder.build();
    }

    private record ProcessResult(int exitCode, String stdout, String stderr) {}
    private record HunkRange(int startLine, int lineCount) {}
    private record PatchFileInfo(String filePath, String relativePath, List<HunkRange> hunks) {
        PatchFileInfo(String filePath, String relativePath) {
            this(filePath, relativePath, new ArrayList<>());
        }
    }
    private record ModifiedLine(String filePath, int lineNumber, String content) {}
}
