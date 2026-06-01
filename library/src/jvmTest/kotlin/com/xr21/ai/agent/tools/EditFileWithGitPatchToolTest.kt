package com.xr21.ai.agent.tools

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
        originalWorkspaceRoot = LocalAgent.WORKSPACE_ROOT
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
        runGit("init")
        runGit("config", "user.email", "test@example.com")
        runGit("config", "user.name", "Test User")
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
        createAndCommitFile("test.txt", "line1\nline2\nline3\n")
        val patch = makePatch(
            "diff --git a/test.txt b/test.txt",
            "--- a/test.txt",
            "+++ b/test.txt",
            "@@ -1,3 +1,3 @@",
            " line1",
            "-line2",
            "+line2-modified",
            " line3"
        )
        val result = tool.editFileWithGitPatch(patch, 1)
        assertTrue(result["success"] as Boolean, "Patch should succeed")
        // Normalize line endings for Windows compatibility
        val content = readTestFile("test.txt").replace("\r\n", "\n").trimEnd('\n')
        assertEquals("line1\nline2-modified\nline3", content)
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
        assertNotNull(result["stderr"])
        // 即使失败也应包含诊断信息
        val stderr = result["stderr"] as String
        assertTrue(stderr.contains("DIAGNOSTIC") || stderr.contains("error"),
            "Error output should contain diagnostic info")
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
        assertNotNull(result["stderr"], "Should have stderr")
        assertNotNull(result["workDir"], "Should have workDir")
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
        assertTrue(result["success"] as Boolean, "Patch without trailing newline should be auto-fixed")
        val content = readTestFile("notrail.txt").replace("\r\n", "\n").trimEnd('\n')
        assertEquals("aaa\nBBB\nccc", content)
        // 验证有警告信息
        val warnings = result["warnings"]
        assertNotNull(warnings, "Should have warning about missing trailing newline")
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
        // 验证 toolCallContents 存在
        assertTrue(result.containsKey("toolCallContents"),
            "Result should contain toolCallContents for diff display")
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
}