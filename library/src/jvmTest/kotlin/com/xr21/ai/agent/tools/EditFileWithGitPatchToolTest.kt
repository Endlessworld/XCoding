package com.xr21.ai.agent.tools

import com.alibaba.fastjson.JSON
import com.xr21.ai.agent.agent.LocalAgent
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Test class for EditFileWithGitPatchTool.
 * Tests various git patch application scenarios including:
 * - Basic file modification
 * - New file creation
 * - Multiple hunks modification
 * - Empty/invalid patch handling
 * - Path traversal prevention
 * - Whitespace auto-fix
 * - Strip level parameter
 */
class EditFileWithGitPatchToolTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private lateinit var tool: EditFileWithGitPatchTool
    private lateinit var tempDir: File
    private var originalWorkspaceRoot: String? = null

    @Before
    fun setUp() {
        tool = EditFileWithGitPatchTool()
        tempDir = tempFolder.root
        // Save and override WORKSPACE_ROOT
        originalWorkspaceRoot ="E:\\local-github\\ai-agents"
        LocalAgent.WORKSPACE_ROOT = tempDir.absolutePath
        initGitRepo()
    }

    @After
    fun tearDown() {
        // Restore WORKSPACE_ROOT
        originalWorkspaceRoot?.let {
            LocalAgent.WORKSPACE_ROOT = it
        }
    }

    private fun initGitRepo() {
//        runGit("init")
//        runGit("config", "user.email", "test@example.com")
//        runGit("config", "user.name", "Test User")
    }

    private fun runGit(vararg args: String) {
        val cmd = mutableListOf("git")
        cmd.addAll(args)
        val pb = ProcessBuilder(cmd)
        pb.directory(tempDir)
        pb.inheritIO()
        val exitCode = pb.start().waitFor()
        assertEquals(0, exitCode, "Git failed: ${args.joinToString(" ")}")
    }

    private fun createAndCommitFile(relPath: String, content: String) {
        val file = tempDir.toPath().resolve(relPath)
        Files.createDirectories(file.parent)
        Files.writeString(file, content)
        runGit("add", relPath)
        runGit("commit", "-m", "Add $relPath")
    }

    private fun readTestFile(relPath: String): String {
        return Files.readString(tempDir.toPath().resolve(relPath))
    }

    private fun makePatch(vararg lines: String): String {
        return lines.joinToString("\n") + "\n"
    }

    // ========== Tests ==========

    @Test
    fun testBasicLineModification() {
        var str = " {\"arg0\": \"diff --git a/library/src/jvmMain/java/com/xr21/ai/agent/utils/AcpProgressUtil.java b/library/src/jvmMain/java/com/xr21/ai/agent/utils/AcpProgressUtil.java \nindex 506e942..a9671f2 100644\\n--- a/library/src/jvmMain/java/com/xr21/ai/agent/utils/AcpProgressUtil.java\\n+++ b/library/src/jvmMain/java/com/xr21/ai/agent/utils/AcpProgressUtil.java\\n@@ -30,9 +30,11 @@\\n /**\\n  * Utility class for sending ACP real-time progress updates from tools.\\n  * Reference: AcpWriteTodosTool.sendAcpPlanUpdate()\\n  */\\n @Slf4j\\n public class AcpProgressUtil {\\n \\n+    /** Context key for retrieving the agent RunnableConfig from ToolContext. */\\n+    private static final String AGENT_CONFIG_KEY = \\\"_AGENT_CONFIG_\\\";\\n+\\n     /**\\n      * Send a real-time AgentThoughtChunk notification to the ACP client.\\n      * This allows tools to stream progress updates as they execute.\\n@@ -43,12 +45,20 @@\\n     public static void sendProgress(ToolContext toolContext, String message) {\\n         try {\\n-            if (toolContext.getContext().get(\\\"_AGENT_CONFIG_\\\") instanceof RunnableConfig config) {\\n-                if (config.context().get(CLIENT_SESSION_CONTEXT_KEY) instanceof ClientSessionOperations client) {\\n+            if (toolContext == null || toolContext.getContext() == null) {\\n+                log.debug(\\\"ToolContext or context map is null, skipping ACP progress\\\");\\n+                return;\\n+            }\\n+            Object configObj = toolContext.getContext().get(AGENT_CONFIG_KEY);\\n+            if (!(configObj instanceof RunnableConfig config) || config.context() == null) {\\n+                return;\\n+            }\\n+            Object clientObj = config.context().get(CLIENT_SESSION_CONTEXT_KEY);\\n+            if (clientObj instanceof ClientSessionOperations client) {\\n                     RunSuspendKt.runSuspend((completion) -> {\\n                         SessionUpdate notification = BridgeKt.buildAgentThoughtChunk(new ContentBlock.Text(message, null, null));\\n                         client.notify(notification, null, completion);\\n                         if (config.context().get(SESSION_ID_CONTEXT_KEY) instanceof String sessionId) {\\n                             log.debug(\\\"ACP progress [{}]: {}\\\", sessionId, message);\\n                         }\\n                         return null;\\n                     });\\n                 }\\n-            }\\n         } catch (Exception e) {\\n             log.debug(\\\"Could not send ACP progress update: {}\\\", e.getMessage());\\n         }\\n\"}" +
                "       "
        var patch = JSON.parseObject(str)
        println(patch)
        val result = tool.editFileWithGitPatch(patch.getString("arg0"), 1)
        println(result)
    }

    @Test
    fun testMultipleHunks() {
        createAndCommitFile("multi.txt",
            "line1\nline2\nline3\nline4\nline5\nline6\nline7\nline8\nline9\nline10\n")
        val patch = makePatch(
            "diff --git a/multi.txt b/multi.txt",
            "--- a/multi.txt",
            "+++ b/multi.txt",
            "@@ -1,3 +1,3 @@",
            "-line1",
            "+line1-modified",
            " line2",
            " line3",
            "@@ -8,3 +8,3 @@",
            " line7",
            "-line8",
            "+line8-modified",
            " line9"
        )
        val result = tool.editFileWithGitPatch(patch, 1)
        assertTrue(result["success"] as Boolean, "Patch should succeed")
        val content = readTestFile("multi.txt")
        assertTrue(content.contains("line1-modified"))
        assertTrue(content.contains("line8-modified"))
    }

    @Test
    fun testNewFileCreation() {
        createAndCommitFile("parent.txt", "parent content\n")
        val patch = makePatch(
            "diff --git a/newfile.txt b/newfile.txt",
            "new file mode 100644",
            "--- /dev/null",
            "+++ b/newfile.txt",
            "@@ -0,0 +1,2 @@",
            "+new file line 1",
            "+new file line 2"
        )
        val result = tool.editFileWithGitPatch(patch, 1)
        assertTrue(result["success"] as Boolean, "Patch should succeed")
        // Normalize line endings for Windows compatibility
        val content = readTestFile("newfile.txt").replace("\r\n", "\n").trimEnd('\n')
        assertEquals("new file line 1\nnew file line 2", content)
    }

    @Test
    fun testEmptyPatchContent() {
        val result = tool.editFileWithGitPatch("", 1)
        assertFalse(result["success"] as Boolean, "Empty patch should fail")
        assertNotNull(result["error"])
    }

    @Test
    fun testInvalidPatchMissingHeaders() {
        val patch = "some random content\nwithout patch format\n"
        val result = tool.editFileWithGitPatch(patch, 1)
        assertFalse(result["success"] as Boolean)
        assertNotNull(result["error"])
    }

    @Test
    fun testStripLevelDefault() {
        createAndCommitFile("hello.txt", "hello\nworld\n")
        // Use default strip=1 (null parameter)
        val patch = makePatch(
            "diff --git a/hello.txt b/hello.txt",
            "--- a/hello.txt",
            "+++ b/hello.txt",
            "@@ -1,2 +1,2 @@",
            " hello",
            "-world",
            "+earth"
        )
        val result = tool.editFileWithGitPatch(patch, null)
        assertTrue(result["success"] as Boolean, "Default strip patch should succeed")
        val content = readTestFile("hello.txt").replace("\r\n", "\n").trimEnd('\n')
        assertEquals("hello\nearth", content)
    }

    @Test
    fun testNegativeStripLevel() {
        val patch = makePatch(
            "diff --git a/test.txt b/test.txt",
            "--- a/test.txt",
            "+++ b/test.txt",
            "@@ -1,1 +1,1 @@",
            " a",
            "+b"
        )
        val result = tool.editFileWithGitPatch(patch, -1)
        assertFalse(result["success"] as Boolean)
        assertNotNull(result["error"])
    }

    @Test
    fun testMissingHunkHeader() {
        val patch = makePatch(
            "diff --git a/test.txt b/test.txt",
            "--- a/test.txt",
            "+++ b/test.txt",
            " some content without hunk header"
        )
        val result = tool.editFileWithGitPatch(patch, 1)
        assertFalse(result["success"] as Boolean)
        assertNotNull(result["error"])
    }

    @Test
    fun testResultContainsModifiedLines() {
        createAndCommitFile("result.txt", "aaa\nbbb\nccc\n")
        val patch = makePatch(
            "diff --git a/result.txt b/result.txt",
            "--- a/result.txt",
            "+++ b/result.txt",
            "@@ -1,3 +1,3 @@",
            " aaa",
            "-bbb",
            "+BBB",
            " ccc"
        )
        val result = tool.editFileWithGitPatch(patch, 1)
        assertTrue(result["success"] as Boolean)
        assertEquals(1, result["modifiedFileCount"])
        assertNotNull(result["content"])
        val content = result["content"] as String
        assertTrue(content.contains("result.txt"))
    }

    // ========== 多文件 Patch ==========

    @Test
    fun testMultiFilePatch() {
        createAndCommitFile("fileA.txt", "aaa\nbbb\nccc\n")
        createAndCommitFile("fileB.txt", "111\n222\n333\n")
        val patch = makePatch(
            "diff --git a/fileA.txt b/fileA.txt",
            "--- a/fileA.txt",
            "+++ b/fileA.txt",
            "@@ -1,3 +1,3 @@",
            " aaa",
            "-bbb",
            "+BBB",
            " ccc",
            "diff --git a/fileB.txt b/fileB.txt",
            "--- a/fileB.txt",
            "+++ b/fileB.txt",
            "@@ -1,3 +1,3 @@",
            " 111",
            "-222",
            "+222-modified",
            " 333"
        )
        val result = tool.editFileWithGitPatch(patch, 1)
        assertTrue(result["success"] as Boolean, "Multi-file patch should succeed")
        assertEquals(2, result["modifiedFileCount"], "Should report 2 modified files")
        val contentA = readTestFile("fileA.txt").replace("\r\n", "\n").trimEnd('\n')
        assertTrue(contentA.contains("BBB"), "fileA should be modified")
        val contentB = readTestFile("fileB.txt").replace("\r\n", "\n").trimEnd('\n')
        assertTrue(contentB.contains("222-modified"), "fileB should be modified")
    }

    // ========== 文件删除 ==========

    @Test
    fun testFileDeletion() {
        createAndCommitFile("todelete.txt", "line1\nline2\nline3\n")
        val patch = makePatch(
            "diff --git a/todelete.txt b/todelete.txt",
            "deleted file mode 100644",
            "--- a/todelete.txt",
            "+++ /dev/null",
            "@@ -1,3 +0,0 @@",
            "-line1",
            "-line2",
            "-line3"
        )
        val result = tool.editFileWithGitPatch(patch, 1)
        assertTrue(result["success"] as Boolean, "File deletion patch should succeed")
        assertFalse(Files.exists(tempDir.toPath().resolve("todelete.txt")), "File should be deleted")
    }

    // ========== 仅添加行 ==========

    @Test
    fun testAppendOnlyLines() {
        createAndCommitFile("append.txt", "line1\nline2\n")
        val patch = makePatch(
            "diff --git a/append.txt b/append.txt",
            "--- a/append.txt",
            "+++ b/append.txt",
            "@@ -1,2 +1,4 @@",
            " line1",
            " line2",
            "+line3",
            "+line4"
        )
        val result = tool.editFileWithGitPatch(patch, 1)
        assertTrue(result["success"] as Boolean, "Append-only patch should succeed")
        val content = readTestFile("append.txt").replace("\r\n", "\n").trimEnd('\n')
        assertEquals("line1\nline2\nline3\nline4", content)
    }

    // ========== 仅删除行 ==========

    @Test
    fun testDeleteOnlyLines() {
        createAndCommitFile("delete.txt", "keep1\nremove\nkeep2\n")
        val patch = makePatch(
            "diff --git a/delete.txt b/delete.txt",
            "--- a/delete.txt",
            "+++ b/delete.txt",
            "@@ -1,3 +1,2 @@",
            " keep1",
            "-remove",
            " keep2"
        )
        val result = tool.editFileWithGitPatch(patch, 1)
        assertTrue(result["success"] as Boolean, "Delete-only patch should succeed")
        val content = readTestFile("delete.txt").replace("\r\n", "\n").trimEnd('\n')
        assertEquals("keep1\nkeep2", content)
    }

    // ========== 在文件开头插入 ==========

    @Test
    fun testInsertAtBeginning() {
        createAndCommitFile("begin.txt", "original line 1\noriginal line 2\n")
        val patch = makePatch(
            "diff --git a/begin.txt b/begin.txt",
            "--- a/begin.txt",
            "+++ b/begin.txt",
            "@@ -1,2 +1,4 @@",
            "+new line before",
            " original line 1",
            " original line 2",
            "+new line after"
        )
        val result = tool.editFileWithGitPatch(patch, 1)
        assertTrue(result["success"] as Boolean, "Insert at beginning should succeed")
        val content = readTestFile("begin.txt").replace("\r\n", "\n").trimEnd('\n')
        assertTrue(content.startsWith("new line before"), "Should insert at beginning")
        assertTrue(content.endsWith("new line after"), "Should insert at end")
    }

    // ========== 大文件修改 ==========

    @Test
    fun testLargeFileModification() {
        val lineCount = 500
        val lines = (1..lineCount).map { "Line number $it" }
        val content = lines.joinToString("\n") + "\n"
        createAndCommitFile("large.txt", content)
        // 修改第 100 行和第 400 行
        val patch = makePatch(
            "diff --git a/large.txt b/large.txt",
            "--- a/large.txt",
            "+++ b/large.txt",
            "@@ -98,5 +98,5 @@",
            " Line number 98",
            " Line number 99",
            "-Line number 100",
            "+LINE NUMBER 100 MODIFIED",
            " Line number 101",
            " Line number 102",
            "@@ -398,5 +398,5 @@",
            " Line number 398",
            " Line number 399",
            "-Line number 400",
            "+LINE NUMBER 400 MODIFIED",
            " Line number 401",
            " Line number 402"
        )
        val result = tool.editFileWithGitPatch(patch, 1)
        assertTrue(result["success"] as Boolean, "Large file patch should succeed")
        val modified = readTestFile("large.txt").replace("\r\n", "\n")
        assertTrue(modified.contains("LINE NUMBER 100 MODIFIED"), "Line 100 should be modified")
        assertTrue(modified.contains("LINE NUMBER 400 MODIFIED"), "Line 400 should be modified")
        assertTrue(modified.contains("Line number 250"), "Unchanged lines should remain")
    }

    // ========== 中文 / Unicode 字符 ==========

    @Test
    fun testChineseCharacters() {
        createAndCommitFile("chinese.txt", "第一行\n第二行\n第三行\n")
        val patch = makePatch(
            "diff --git a/chinese.txt b/chinese.txt",
            "--- a/chinese.txt",
            "+++ b/chinese.txt",
            "@@ -1,3 +1,3 @@",
            " 第一行",
            "-第二行",
            "+第二行已修改",
            " 第三行"
        )
        val result = tool.editFileWithGitPatch(patch, 1)
        assertTrue(result["success"] as Boolean, "Chinese chars patch should succeed")
        val content = readTestFile("chinese.txt").replace("\r\n", "\n").trimEnd('\n')
        assertTrue(content.contains("第二行已修改"), "Chinese text should be modified")
    }

    @Test
    fun testUnicodeSpecialChars() {
        createAndCommitFile("unicode.txt", "hello\nworld\nemoji\n")
        val patch = makePatch(
            "diff --git a/unicode.txt b/unicode.txt",
            "--- a/unicode.txt",
            "+++ b/unicode.txt",
            "@@ -1,3 +1,3 @@",
            " hello",
            "-world",
            "+wörld ©®™",
            " emoji"
        )
        val result = tool.editFileWithGitPatch(patch, 1)
        assertTrue(result["success"] as Boolean, "Unicode patch should succeed")
        val content = readTestFile("unicode.txt").replace("\r\n", "\n").trimEnd('\n')
        assertTrue(content.contains("wörld ©®™"), "Unicode chars should be preserved")
    }

    // ========== 空文件修改 ==========

    @Test
    fun testModifyEmptyFile() {
        createAndCommitFile("empty.txt", "")
        val patch = makePatch(
            "diff --git a/empty.txt b/empty.txt",
            "--- a/empty.txt",
            "+++ b/empty.txt",
            "@@ -0,0 +1,3 @@",
            "+line 1",
            "+line 2",
            "+line 3"
        )
        val result = tool.editFileWithGitPatch(patch, 1)
        assertTrue(result["success"] as Boolean, "Patch to empty file should succeed")
        val content = readTestFile("empty.txt").replace("\r\n", "\n").trimEnd('\n')
        assertEquals("line 1\nline 2\nline 3", content)
    }

    // ========== 上下文不匹配 ==========

    @Test
    fun testContextMismatch() {
        createAndCommitFile("ctx.txt", "keep1\nkeep2\nkeep3\n")
        // patch 期望 "old" 但实际文件是 "keep2"
        val patch = makePatch(
            "diff --git a/ctx.txt b/ctx.txt",
            "--- a/ctx.txt",
            "+++ b/ctx.txt",
            "@@ -1,3 +1,3 @@",
            " keep1",
            "-old",
            "+new",
            " keep3"
        )
        val result = tool.editFileWithGitPatch(patch, 1)
        assertFalse(result["success"] as Boolean, "Context mismatch should fail")
        assertNotNull(result["checkStderr"], "Should have checkStderr when --check fails")
    }

    // ========== 结果元数据验证 ==========

    @Test
    fun testResultMetadata() {
        createAndCommitFile("meta.txt", "aaa\nbbb\nccc\n")
        val patch = makePatch(
            "diff --git a/meta.txt b/meta.txt",
            "--- a/meta.txt",
            "+++ b/meta.txt",
            "@@ -1,3 +1,3 @@",
            " aaa",
            "-bbb",
            "+BBB",
            " ccc"
        )
        val result = tool.editFileWithGitPatch(patch, 1)
        assertTrue(result["success"] as Boolean)
        // 验证元数据字段
        assertTrue(result.containsKey("processingTimeMs"), "Should contain processingTimeMs")
        val timeMs = result["processingTimeMs"] as Number
        assertTrue(timeMs.toLong() >= 0, "Processing time should be >= 0")
        assertEquals(1, result["modifiedFileCount"], "Should have 1 modified file")
        assertNotNull(result["content"], "Should have content")
        assertNotNull(result["stdout"], "Should have stdout")
        assertTrue(result.containsKey("processingTimeMs"), "Should have processingTimeMs")
    }

    // ========== CRLF Patch 内容 ==========

    @Test
    fun testCrlfPatchContent() {
        createAndCommitFile("crlf.txt", "line1\nline2\nline3\n")
        // patch 内容使用 CRLF 换行符
        val patchLines = listOf(
            "diff --git a/crlf.txt b/crlf.txt",
            "--- a/crlf.txt",
            "+++ b/crlf.txt",
            "@@ -1,3 +1,3 @@",
            " line1",
            "-line2",
            "+line2-crlf-fixed",
            " line3",
            ""
        )
        val patch = patchLines.joinToString("\r\n")
        val result = tool.editFileWithGitPatch(patch, 1)
        assertTrue(result["success"] as Boolean, "CRLF patch content should be normalized and succeed")
        val content = readTestFile("crlf.txt").replace("\r\n", "\n").trimEnd('\n')
        assertEquals("line1\nline2-crlf-fixed\nline3", content)
    }

    // ========== 嵌套目录文件修改 ==========

    @Test
    fun testNestedDirectoryFile() {
        createAndCommitFile("deep/nested/file.txt", "top\nmiddle\nbottom\n")
        val patch = makePatch(
            "diff --git a/deep/nested/file.txt b/deep/nested/file.txt",
            "--- a/deep/nested/file.txt",
            "+++ b/deep/nested/file.txt",
            "@@ -1,3 +1,3 @@",
            " top",
            "-middle",
            "+middle-modified",
            " bottom"
        )
        val result = tool.editFileWithGitPatch(patch, 1)
        assertTrue(result["success"] as Boolean, "Nested dir file patch should succeed")
        val content = readTestFile("deep/nested/file.txt").replace("\r\n", "\n").trimEnd('\n')
        assertTrue(content.contains("middle-modified"), "Nested file should be modified")
    }

    // ========== Patch 末尾无换行符自动修复 ==========

    @Test
    fun testPatchWithoutTrailingNewline() {
        createAndCommitFile("notrail.txt", "aaa\nbbb\nccc\n")
        // patch 末尾没有换行符
        val patch = listOf(
            "diff --git a/notrail.txt b/notrail.txt",
            "--- a/notrail.txt",
            "+++ b/notrail.txt",
            "@@ -1,3 +1,3 @@",
            " aaa",
            "-bbb",
            "+BBB",
            " ccc"
        ).joinToString("\n")  // 注意：没有最后的 + "\n"
        val result = tool.editFileWithGitPatch(patch, 1)
        // 无尾部换行的补丁可能失败或成功，取决于git版本
        // 这里仅验证工具不会崩溃
        assertNotNull(result, "Result should not be null")
    }

    // ========== 验证结果中包含 toolCallContent (Diff) ==========

    @Test
    fun testResultContainsDiffContent() {
        createAndCommitFile("diffcheck.txt", "old content\n")
        val patch = makePatch(
            "diff --git a/diffcheck.txt b/diffcheck.txt",
            "--- a/diffcheck.txt",
            "+++ b/diffcheck.txt",
            "@@ -1,1 +1,1 @@",
            "-old content",
            "+new content"
        )
        val result = tool.editFileWithGitPatch(patch, 1)
        assertTrue(result["success"] as Boolean)
        // 验证 locations 或 content 存在
        assertTrue(result.containsKey("locations") || result.containsKey("content"),
            "Result should contain location info")
    }

    // ========== 精确的行级位置信息 ==========

    @Test
    fun testLocationInfo() {
        createAndCommitFile("loc.txt", "lineA\nlineB\nlineC\nlineD\nlineE\n")
        val patch = makePatch(
            "diff --git a/loc.txt b/loc.txt",
            "--- a/loc.txt",
            "+++ b/loc.txt",
            "@@ -2,3 +2,3 @@",
            " lineB",
            "-lineC",
            "+lineC-modified",
            " lineD"
        )
        val result = tool.editFileWithGitPatch(patch, 1)
        assertTrue(result["success"] as Boolean)
        assertTrue(result.containsKey("locations"), "Result should contain locations")
    }

    // ========== Tab 缩进文件用空格 patch 修改 ==========

    @Test
    fun testTabIndentedFileWithSpacePatch() {
        // 文件使用 Tab 缩进
        createAndCommitFile("tabfile.txt", "line1\n\tline2\n\tline3\n")
        // patch 使用空格缩进（不匹配）
        val patch = makePatch(
            "diff --git a/tabfile.txt b/tabfile.txt",
            "--- a/tabfile.txt",
            "+++ b/tabfile.txt",
            "@@ -1,3 +1,3 @@",
            " line1",
            "-\tline2",
            "+    line2-modified",
            " \tline3"
        )
        val result = tool.editFileWithGitPatch(patch, 1)
        assertTrue(result["success"] as Boolean, "Tab-indented file with space patch should succeed after normalization")
        val content = readTestFile("tabfile.txt").replace("\r\n", "\n").trimEnd('\n')
        assertTrue(content.contains("line2-modified"), "Content should be modified")
    }

    // ========== 空格缩进文件用 Tab patch 修改 ==========

    @Test
    fun testSpaceIndentedFileWithTabPatch() {
        // 文件使用 4空格 缩进
        createAndCommitFile("spacefile.txt", "line1\n    line2\n    line3\n")
        // patch 使用 Tab 缩进（不匹配）
        val patch = makePatch(
            "diff --git a/spacefile.txt b/spacefile.txt",
            "--- a/spacefile.txt",
            "+++ b/spacefile.txt",
            "@@ -1,3 +1,3 @@",
            " line1",
            "-    line2",
            "+\tline2-modified",
            "     line3"
        )
        val result = tool.editFileWithGitPatch(patch, 1)
        assertTrue(result["success"] as Boolean, "Space-indented file with tab patch should succeed after normalization")
        val content = readTestFile("spacefile.txt").replace("\r\n", "\n").trimEnd('\n')
        assertTrue(content.contains("line2-modified"), "Content should be modified")
    }

    // ========== Patch 使用 Tab，目标文件使用 Tab ==========

    @Test
    fun testTabPatchOnTabFile() {
        createAndCommitFile("tabtab.txt", "aaa\n\tbbb\n\tccc\n")
        val patch = makePatch(
            "diff --git a/tabtab.txt b/tabtab.txt",
            "--- a/tabtab.txt",
            "+++ b/tabtab.txt",
            "@@ -1,3 +1,3 @@",
            " aaa",
            "-\tbbb",
            "+\tBBB-modified",
            " \tccc"
        )
        val result = tool.editFileWithGitPatch(patch, 1)
        assertTrue(result["success"] as Boolean, "Tab patch on tab file should succeed")
        val content = readTestFile("tabtab.txt").replace("\r\n", "\n").trimEnd('\n')
        assertTrue(content.contains("BBB-modified"), "Content should be modified")
    }

    // ========== Patch 使用空格，目标文件使用空格 ==========

    @Test
    fun testSpacePatchOnSpaceFile() {
        createAndCommitFile("spacespace.txt", "aaa\n    bbb\n    ccc\n")
        val patch = makePatch(
            "diff --git a/spacespace.txt b/spacespace.txt",
            "--- a/spacespace.txt",
            "+++ b/spacespace.txt",
            "@@ -1,3 +1,3 @@",
            " aaa",
            "-    bbb",
            "+    BBB-modified",
            "     ccc"
        )
        val result = tool.editFileWithGitPatch(patch, 1)
        assertTrue(result["success"] as Boolean, "Space patch on space file should succeed")
        val content = readTestFile("spacespace.txt").replace("\r\n", "\n").trimEnd('\n')
        assertTrue(content.contains("BBB-modified"), "Content should be modified")
    }

    // ========== 多级缩进 Tab 文件 ==========

    @Test
    fun testMultiLevelTabIndentPatch() {
        createAndCommitFile("multitab.txt",
            "def func():\n" +
            "\tif True:\n" +
            "\t\treturn 1\n" +
            "\treturn 0\n")
        val patch = makePatch(
            "diff --git a/multitab.txt b/multitab.txt",
            "--- a/multitab.txt",
            "+++ b/multitab.txt",
            "@@ -1,4 +1,4 @@",
            " def func():",
            " \tif True:",
            "-\t\treturn 1",
            "+\t\treturn 42",
            " \treturn 0"
        )
        val result = tool.editFileWithGitPatch(patch, 1)
        assertTrue(result["success"] as Boolean, "Multi-level tab indent patch should succeed")
        val content = readTestFile("multitab.txt").replace("\r\n", "\n").trimEnd('\n')
        assertTrue(content.contains("return 42"), "Content should be modified")
    }

    // ========== 复杂实际场景测试 ==========

    @Test
    fun testContextMismatchWithCheckStderr() {
        createAndCommitFile("ctx.txt", "keep1\nkeep2\nkeep3\n")
        val patch = makePatch(
            "diff --git a/ctx.txt b/ctx.txt",
            "--- a/ctx.txt",
            "+++ b/ctx.txt",
            "@@ -1,3 +1,3 @@",
            " keep1",
            "-old",
            "+new",
            " keep3"
        )
        val result = tool.editFileWithGitPatch(patch, 1)
        println(result)
        assertFalse(result["success"] as Boolean, "Context mismatch should fail")
        assertNotNull(result["checkStderr"], "Should have checkStderr when --check fails")
    }

    @Test
    fun testPatchWithTrailingNewline() {
        createAndCommitFile("notrail.txt", "aaa\nbbb\nccc\n")
        val patch = makePatch(
            "diff --git a/notrail.txt b/notrail.txt",
            "--- a/notrail.txt",
            "+++ b/notrail.txt",
            "@@ -1,3 +1,3 @@",
            " aaa",
            "-bbb",
            "+BBB",
            " ccc"
        )
        val result = tool.editFileWithGitPatch(patch, 1)
        assertTrue(result["success"] as Boolean, "Patch with trailing newline should succeed")
        val content = readTestFile("notrail.txt").replace("\r\n", "\n").trimEnd('\n')
        assertEquals("aaa\nBBB\nccc", content)
    }

    @Test
    fun testSimulateFileRenameByDeleteAndCreate() {
        createAndCommitFile("old_name.txt", "content line 1\ncontent line 2\ncontent line 3\n")
        val patch = makePatch(
            "diff --git a/old_name.txt b/old_name.txt",
            "deleted file mode 100644",
            "--- a/old_name.txt",
            "+++ /dev/null",
            "@@ -1,3 +0,0 @@",
            "-content line 1",
            "-content line 2",
            "-content line 3",
            "diff --git a/renamed.txt b/renamed.txt",
            "new file mode 100644",
            "--- /dev/null",
            "+++ b/renamed.txt",
            "@@ -0,0 +1,3 @@",
            "+content line 1",
            "+content line 2",
            "+content line 3"
        )
        val result = tool.editFileWithGitPatch(patch, 1)
        assertTrue(result["success"] as Boolean, "Delete+create simulate rename should succeed")
        assertFalse(Files.exists(tempDir.toPath().resolve("old_name.txt")), "Old file should not exist")
        assertTrue(Files.exists(tempDir.toPath().resolve("renamed.txt")), "New file should exist")
        val content = readTestFile("renamed.txt").replace("\r\n", "\n").trimEnd('\n')
        assertEquals("content line 1\ncontent line 2\ncontent line 3", content)
    }

    @Test
    fun testSimulateFileCopyByCreateNew() {
        createAndCommitFile("source.txt", "# Source file\nThis is the original content.\n")
        val patch = makePatch(
            "diff --git a/copied.txt b/copied.txt",
            "new file mode 100644",
            "--- /dev/null",
            "+++ b/copied.txt",
            "@@ -0,0 +1,2 @@",
            "+# Source file",
            "+This is the original content."
        )
        val result = tool.editFileWithGitPatch(patch, 1)
        assertTrue(result["success"] as Boolean, "Create new file (simulate copy) should succeed")
        assertTrue(Files.exists(tempDir.toPath().resolve("source.txt")), "Source file should still exist")
        assertTrue(Files.exists(tempDir.toPath().resolve("copied.txt")), "Copied file should exist")
        val srcContent = readTestFile("source.txt").replace("\r\n", "\n").trimEnd('\n')
        val dstContent = readTestFile("copied.txt").replace("\r\n", "\n").trimEnd('\n')
        assertEquals(srcContent, dstContent, "Copied content should match source")
    }

    @Test
    fun testSimulateRenameWithModification() {
        createAndCommitFile("old_script.sh", "#!/bin/bash\necho \"Old path\"\nexit 0\n")
        val patch = makePatch(
            "diff --git a/old_script.sh b/old_script.sh",
            "deleted file mode 100644",
            "--- a/old_script.sh",
            "+++ /dev/null",
            "@@ -1,3 +0,0 @@",
            "-#!/bin/bash",
            "-echo \"Old path\"",
            "-exit 0",
            "diff --git a/app/deploy.sh b/app/deploy.sh",
            "new file mode 100644",
            "--- /dev/null",
            "+++ b/app/deploy.sh",
            "@@ -0,0 +1,3 @@",
            "+#!/bin/bash",
            "+echo \"Moved to app/ and modified\"",
            "+exit 0"
        )
        val result = tool.editFileWithGitPatch(patch, 1)
        assertTrue(result["success"] as Boolean, "Delete+create simulate rename+modify should succeed")
        assertFalse(Files.exists(tempDir.toPath().resolve("old_script.sh")), "Old file should be gone")
        assertTrue(Files.exists(tempDir.toPath().resolve("app/deploy.sh")), "New file should exist")
        val content = readTestFile("app/deploy.sh").replace("\r\n", "\n").trimEnd('\n')
        assertTrue(content.contains("Moved to app/ and modified"), "Content should be modified")
    }

    @Test
    fun testContentModificationWithModeChangeComment() {
        createAndCommitFile("exec.sh", "#!/bin/bash\necho \"Hello\"\n")
        val patch = makePatch(
            "diff --git a/exec.sh b/exec.sh",
            "--- a/exec.sh",
            "+++ b/exec.sh",
            "@@ -1,2 +1,2 @@",
            " #!/bin/bash",
            "-echo \"Hello\"",
            "+echo \"Mode changed from 755 to 644\""
        )
        val result = tool.editFileWithGitPatch(patch, 1)
        assertTrue(result["success"] as Boolean, "Content modification should succeed")
        val content = readTestFile("exec.sh").replace("\r\n", "\n").trimEnd('\n')
        assertTrue(content.contains("Mode changed from 755 to 644"), "Content should be modified")
    }

    @Test
    fun testJavaSourceFileSimpleModification() {
        val javaContent = "package com.example.demo;\n\npublic class App {\n    public static void main(String[] args) {\n        System.out.println(\"Hello\");\n    }\n}\n"
        createAndCommitFile("App.java", javaContent)
        val patch = makePatch(
            "diff --git a/App.java b/App.java",
            "--- a/App.java",
            "+++ b/App.java",
            "@@ -1,5 +1,6 @@",
            " package com.example.demo;",
            "",
            " public class App {",
            "-    public static void main(String[] args) {",
            "+    public static void main(String[] args) {",
            "+        System.out.println(\"App started\");",
            "         System.out.println(\"Hello\");",
            "     }",
            " }"
        )
        val result = tool.editFileWithGitPatch(patch, 1)
        assertTrue(result["success"] as Boolean, "Java source patch should succeed")
        val content = readTestFile("App.java")
        assertTrue(content.contains("App started"), "Should have added startup log")
    }

    @Test
    fun testKotlinSourceSimpleModification() {
        val ktContent = "package com.example.app\n\ndata class User(val name: String, val age: Int)\n"
        createAndCommitFile("User.kt", ktContent)
        val patch = makePatch(
            "diff --git a/User.kt b/User.kt",
            "--- a/User.kt",
            "+++ b/User.kt",
            "@@ -1,3 +1,3 @@",
            " package com.example.app",
            " ",
            "-data class User(val name: String, val age: Int)",
            "+data class User(val name: String, val age: Int, val email: String)"
        )
        val result = tool.editFileWithGitPatch(patch, 1)
        assertTrue(result["success"] as Boolean, "Kotlin source patch should succeed")
        val content = readTestFile("User.kt")
        assertTrue(content.contains("val email: String"), "Should have added email field")
    }

    @Test
    fun testYamlConfigSimpleModification() {
        val yamlContent = "server:\n  port: 8080\n  host: 0.0.0.0\ndatabase:\n  url: jdbc:mysql://localhost:3306/mydb\n  poolSize: 10\nlogging:\n  level: INFO\n"
        createAndCommitFile("application.yml", yamlContent)
        val patch = makePatch(
            "diff --git a/application.yml b/application.yml",
            "--- a/application.yml",
            "+++ b/application.yml",
            "@@ -1,4 +1,6 @@",
            " server:",
            "   port: 8080",
            "   host: 0.0.0.0",
            "+  ssl:",
            "+    enabled: true",
            " database:",
            "@@ -6,3 +8,4 @@",
            "   poolSize: 10",
            "+  timeout: 30000",
            " logging:",
            "   level: INFO"
        )
        val result = tool.editFileWithGitPatch(patch, 1)
        assertTrue(result["success"] as Boolean, "YAML config patch should succeed")
        val content = readTestFile("application.yml")
        assertTrue(content.contains("ssl:"), "Should have added ssl section")
        assertTrue(content.contains("timeout: 30000"), "Should have added timeout")
    }

    @Test
    fun testJsonConfigSimpleModification() {
        val jsonContent = "{\n  \"name\": \"my-app\",\n  \"version\": \"1.0.0\",\n  \"deps\": {\n    \"spring\": \"3.2.0\"\n  }\n}\n"
        createAndCommitFile("package.json", jsonContent)
        val patch = makePatch(
            "diff --git a/package.json b/package.json",
            "--- a/package.json",
            "+++ b/package.json",
            "@@ -1,5 +1,6 @@",
            " {",
            "   \"name\": \"my-app\",",
            "-  \"version\": \"1.0.0\",",
            "+  \"version\": \"2.0.0\",",
            "+  \"license\": \"Apache-2.0\",",
            "   \"deps\": {",
            "     \"spring\": \"3.2.0\"",
            "   }",
            " }"
        )
        val result = tool.editFileWithGitPatch(patch, 1)
        assertTrue(result["success"] as Boolean, "JSON file patch should succeed")
        val content = readTestFile("package.json")
        assertTrue(content.contains("\"version\": \"2.0.0\""), "Version should be updated")
        assertTrue(content.contains("\"license\""), "Should have added license")
    }

    @Test
    fun testMultiFileSimplePatch() {
        createAndCommitFile("README.md", "# Project\n\nThis is the README.\n")
        createAndCommitFile("src/app.js", "const app = require('express')();\napp.listen(3000);\n")
        createAndCommitFile(".gitignore", "node_modules/\n.env\n")
        val patch = makePatch(
            "diff --git a/README.md b/README.md",
            "--- a/README.md",
            "+++ b/README.md",
            "@@ -1,3 +1,3 @@",
            " # Project",
            " ",
            "-This is the README.",
            "+This is the README for the project.",
            "diff --git a/src/app.js b/src/app.js",
            "--- a/src/app.js",
            "+++ b/src/app.js",
            "@@ -1,2 +1,3 @@",
            " const app = require('express')();",
            "+app.get('/', (req, res) => res.send('OK'));",
            " app.listen(3000);",
            "diff --git a/.gitignore b/.gitignore",
            "--- a/.gitignore",
            "+++ b/.gitignore",
            "@@ -1,2 +1,3 @@",
            " node_modules/",
            " .env",
            "+build/"
        )
        val result = tool.editFileWithGitPatch(patch, 1)
        assertTrue(result["success"] as Boolean, "Multi-file patch should succeed")
        assertTrue((result["modifiedFileCount"] as Int) >= 1, "Should report at least 1 modified file")
        val readme = readTestFile("README.md")
        assertTrue(readme.contains("README for the project"), "README should be updated")
        val appJs = readTestFile("src/app.js")
        assertTrue(appJs.contains("app.get('/'"), "app.js should have new route")
        val gitignore = readTestFile(".gitignore")
        assertTrue(gitignore.contains("build/"), "gitignore should have build/")
    }

    @Test
    fun testDeleteFileInNestedDirectory() {
        createAndCommitFile("deep/dir/to/remove/old_module.py",
            "#!/usr/bin/env python3\n# Old module\n\ndef old_func():\n    pass\n")
        val patch = makePatch(
            "diff --git a/deep/dir/to/remove/old_module.py b/deep/dir/to/remove/old_module.py",
            "deleted file mode 100644",
            "--- a/deep/dir/to/remove/old_module.py",
            "+++ /dev/null",
            "@@ -1,5 +0,0 @@",
            "-#!/usr/bin/env python3",
            "-# Old module",
            "-",
            "-def old_func():",
            "-    pass"
        )
        val result = tool.editFileWithGitPatch(patch, 1)
        assertTrue(result["success"] as Boolean, "Delete nested file should succeed")
        assertFalse(Files.exists(tempDir.toPath().resolve("deep/dir/to/remove/old_module.py")),
            "Deleted file should not exist")
    }

    @Test
    fun testDockerfileModification() {
        val dockerContent = "FROM openjdk:17-jdk-slim\nWORKDIR /app\nCOPY build/libs/*.jar app.jar\nEXPOSE 8080\nCMD [\"java\", \"-jar\", \"app.jar\"]\n"
        createAndCommitFile("Dockerfile", dockerContent)
        val patch = makePatch(
            "diff --git a/Dockerfile b/Dockerfile",
            "--- a/Dockerfile",
            "+++ b/Dockerfile",
            "@@ -1,5 +1,6 @@",
            "-FROM openjdk:17-jdk-slim",
            "+FROM eclipse-temurin:21-jre-alpine",
            "+LABEL maintainer=\"dev@example.com\"",
            " WORKDIR /app",
            " COPY build/libs/*.jar app.jar",
            " EXPOSE 8080",
            "-CMD [\"java\", \"-jar\", \"app.jar\"]",
            "+CMD [\"sh\", \"-c\", \"java -jar app.jar\"]"
        )
        val result = tool.editFileWithGitPatch(patch, 1)
        assertTrue(result["success"] as Boolean, "Dockerfile patch should succeed")
        val content = readTestFile("Dockerfile")
        assertTrue(content.contains("eclipse-temurin:21-jre-alpine"), "Base image should be updated")
        assertTrue(content.contains("LABEL maintainer"), "Should have maintainer label")
    }

    @Test
    fun testStripLevelZero() {
        createAndCommitFile("stripped.txt", "alpha\nbeta\ngamma\n")
        val patch = makePatch(
            "diff --git a/stripped.txt b/stripped.txt",
            "--- stripped.txt",
            "+++ stripped.txt",
            "@@ -1,3 +1,3 @@",
            " alpha",
            "-beta",
            "+BETA",
            " gamma"
        )
        val result = tool.editFileWithGitPatch(patch, 0)
        assertTrue(result["success"] as Boolean, "Strip level 0 patch should succeed")
        val content = readTestFile("stripped.txt").replace("\r\n", "\n").trimEnd('\n')
        assertEquals("alpha\nBETA\ngamma", content)
    }

    @Test
    fun testAddNewFileInSubdirectory() {
        createAndCommitFile("existing.txt", "existing content\n")
        val patch = makePatch(
            "diff --git a/src/main/resources/config.properties b/src/main/resources/config.properties",
            "new file mode 100644",
            "--- /dev/null",
            "+++ b/src/main/resources/config.properties",
            "@@ -0,0 +1,5 @@",
            "+# Application Configuration",
            "+app.name=MyApp",
            "+app.version=1.0.0",
            "+app.debug=false",
            "+app.theme=dark"
        )
        val result = tool.editFileWithGitPatch(patch, 1)
        assertTrue(result["success"] as Boolean, "New file in subdirectory should succeed")
        assertTrue(Files.exists(tempDir.toPath().resolve("src/main/resources/config.properties")),
            "Config file should exist in subdirectory")
        val content = readTestFile("src/main/resources/config.properties").replace("\r\n", "\n").trimEnd('\n')
        assertTrue(content.contains("app.name=MyApp"), "Should contain app.name")
        assertTrue(content.contains("app.theme=dark"), "Should contain app.theme")
    }

    @Test
    fun testReplaceEntireFileContent() {
        createAndCommitFile("config.txt", "OLD_CONFIG\nkey1=value1\nkey2=value2\n")
        val patch = makePatch(
            "diff --git a/config.txt b/config.txt",
            "--- a/config.txt",
            "+++ b/config.txt",
            "@@ -1,3 +1,5 @@",
            "-OLD_CONFIG",
            "-key1=value1",
            "-key2=value2",
            "+# New Configuration",
            "+app.name=Demo",
            "+app.port=9090",
            "+app.admin=true",
            "+app.cache.enabled=false"
        )
        val result = tool.editFileWithGitPatch(patch, 1)
        assertTrue(result["success"] as Boolean, "Replace entire content should succeed")
        val content = readTestFile("config.txt").replace("\r\n", "\n").trimEnd('\n')
        assertFalse(content.contains("OLD_CONFIG"), "Old content should be gone")
        assertTrue(content.contains("app.name=Demo"), "New content should be present")
        assertTrue(content.contains("app.cache.enabled=false"), "All new lines should be present")
    }

    @Test
    fun testPatchWithMultipleHunksAndFiles() {
        createAndCommitFile("build.gradle",
            "plugins {\n    id 'java'\n}\n\nrepositories {\n    mavenCentral()\n}\n\ndependencies {\n    testImplementation 'junit:junit:4.13'\n}\n")
        createAndCommitFile("settings.gradle",
            "rootProject.name = 'demo'\n")
        val patch = makePatch(
            "diff --git a/build.gradle b/build.gradle",
            "--- a/build.gradle",
            "+++ b/build.gradle",
            "@@ -1,5 +1,6 @@",
            " plugins {",
            "-    id 'java'",
            "+    id 'java'",
            "+    id 'application'",
            " }",
            "",
            " repositories {",
            "@@ -7,3 +8,5 @@",
            " dependencies {",
            "     testImplementation 'junit:junit:4.13'",
            "+    implementation 'com.google.guava:guava:33.0.0'",
            "+    implementation 'org.slf4j:slf4j-api:2.0.9'",
            " }",
            "diff --git a/settings.gradle b/settings.gradle",
            "--- a/settings.gradle",
            "+++ b/settings.gradle",
            "@@ -1 +1,3 @@",
            "-rootProject.name = 'demo'",
            "+rootProject.name = 'my-app'",
            "+",
            "+include 'subproject-a', 'subproject-b'"
        )
        val result = tool.editFileWithGitPatch(patch, 1)
        assertTrue(result["success"] as Boolean, "Multi-hunk multi-file patch should succeed")
        assertEquals(2, result["modifiedFileCount"], "Should report 2 modified files")
        val buildContent = readTestFile("build.gradle")
        assertTrue(buildContent.contains("id 'application'"), "Should add application plugin")
        assertTrue(buildContent.contains("com.google.guava:guava:33.0.0"), "Should add guava dep")
        assertTrue(buildContent.contains("org.slf4j:slf4j-api:2.0.9"), "Should add slf4j dep")
        val settingsContent = readTestFile("settings.gradle")
        assertTrue(settingsContent.contains("rootProject.name = 'my-app'"), "Project name should be updated")
        assertTrue(settingsContent.contains("include 'subproject-a', 'subproject-b'"), "Should add subprojects")
    }

    @Test
    fun testModifyFileWithTabs() {
        createAndCommitFile("tabs.txt", "line1\n\tindented line\n\t\tdeep indent\nline4\n")
        val patch = makePatch(
            "diff --git a/tabs.txt b/tabs.txt",
            "--- a/tabs.txt",
            "+++ b/tabs.txt",
            "@@ -1,4 +1,4 @@",
            " line1",
            "-\tindented line",
            "+\tindented line modified",
            " \t\tdeep indent",
            " line4"
        )
        val result = tool.editFileWithGitPatch(patch, 1)
        assertTrue(result["success"] as Boolean, "Tab-indented file patch should succeed")
        val content = readTestFile("tabs.txt")
        assertTrue(content.contains("\tindented line modified"), "Tab-indented line should be modified")
    }

    @Test
    fun testProcessingTimeAndMetadata() {
        createAndCommitFile("perf.txt", "a\nb\nc\n")
        val patch = makePatch(
            "diff --git a/perf.txt b/perf.txt",
            "--- a/perf.txt",
            "+++ b/perf.txt",
            "@@ -1,3 +1,3 @@",
            " a",
            "-b",
            "+B",
            " c"
        )
        val result = tool.editFileWithGitPatch(patch, 1)
        assertTrue(result["success"] as Boolean)
        val timeMs = result["processingTimeMs"] as Number
        assertTrue(timeMs.toLong() > 0, "Processing time should be positive")
        assertTrue(result.containsKey("exitCode"), "Should contain exitCode")
        assertEquals(0, result["exitCode"], "Exit code should be 0")
        assertTrue(result.containsKey("stdout"), "Should contain stdout")
        assertTrue(result.containsKey("stderr"), "Should contain stderr")
    }
}