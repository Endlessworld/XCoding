package com.xr21.ai.agent.tools;

import com.xr21.ai.agent.agent.LocalAgent;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


/**
 * GlobTool 性能测试 — 基于真实项目 spring-ai (2878 文件, 1885 .java)
 * <p>
 * 10 个测试用例覆盖：
 * 1. 精确深层路径 — 只遍历少量目录
 * 2. 全树通配 — 遍历所有非跳过目录
 * 3. 前导 / 模式 — 验证修复
 * 4. 多模式 — 同时匹配多种扩展名
 * 5. 无匹配 — 全树遍历后空结果
 * 6. 单模块精确匹配 — 只命中一个子模块
 * 7. 跨模块匹配 — 命中多个子模块
 * 8. 根目录浅层匹配 — 只匹配一级
 * 9. 字符类匹配 — [abc] 语法
 * 10. walkFileTree vs 分段逐级对比
 *
 * @author Endless
 */
public class GlobToolPerfTest {

    /** spring-ai 项目根目录 */
    private static final String SPRING_AI_ROOT = "E:/local-github/spring-ai";

    private static String originalWorkspaceRoot;
    private static int totalFiles;

    @BeforeClass
    public static void setUp() throws IOException {
        originalWorkspaceRoot = LocalAgent.WORKSPACE_ROOT;
        LocalAgent.WORKSPACE_ROOT = SPRING_AI_ROOT;

        Path root = Paths.get(SPRING_AI_ROOT);
        totalFiles = countFiles(root);

        System.out.println("=== GlobTool 性能测试 (spring-ai 真实项目) ===");
        System.out.println("项目路径: " + SPRING_AI_ROOT);
        System.out.println("总文件数: " + totalFiles);
        System.out.println();
    }

    @AfterClass
    public static void tearDown() {
        LocalAgent.WORKSPACE_ROOT = originalWorkspaceRoot;
    }

    // ==================== 10 个测试用例 ====================

    /**
     * 用例 1: 深层精确路径 — 只匹配 spring-ai-openai 模块下的 Java 文件
     * 预期：只遍历 models/spring-ai-openai 子树，跳过其他 20 个 models 子模块
     */
    @Test
    public void test01_deepExactPath() {
        runBenchmark("01-深层精确 [models/spring-ai-openai/**/*.java]",
                "models/spring-ai-openai/**/*.java", 1);
    }

    /**
     * 用例 2: 全树通配 — 所有 .java 文件
     * 预期：遍历所有非跳过目录，返回最多 25 条
     */
    @Test
    public void test02_allJavaFiles() {
        runBenchmark("02-全树通配 [**/*.java]", "**/*.java", 25);
    }

    /**
     * 用例 3: 前导 / 模式 — 验证修复后能正确匹配
     */
    @Test
    public void test03_leadingSlash() {
        runBenchmark("03-前导斜杠 [/models/spring-ai-openai/**/*.java]",
                "/models/spring-ai-openai/**/*.java", 1);
    }

    /**
     * 用例 4: 多模式 — 同时匹配 .java + .xml + .md
     */
    @Test
    public void test04_multiPattern() {
        runBenchmarkMulti("04-多模式 [java,xml,md]",
                new String[]{"**/*.java", "**/*.xml", "**/*.md"}, 25);
    }

    /**
     * 用例 5: 无匹配 — 全树遍历后返回空
     */
    @Test
    public void test05_noMatch() {
        runBenchmark("05-无匹配 [**/*.nonexistent]", "**/*.nonexistent", 0);
    }

    /**
     * 用例 6: 单模块精确 — 只匹配 models/spring-ai-openai 下的 Java 文件
     * 预期：跳过其他 20 个 models 子模块
     */
    @Test
    public void test06_singleModule() {
        runBenchmark("06-单模块 [models/spring-ai-openai/**/*.java]",
                "models/spring-ai-openai/**/*.java", 1);
    }

    /**
     * 用例 7: 跨模块匹配 — 匹配所有 models 子模块下的 pom.xml
     */
    @Test
    public void test07_crossModule() {
        runBenchmark("07-跨模块 [models/*/pom.xml]", "models/*/pom.xml", 10);
    }

    /**
     * 用例 8: 根目录浅层 — 只匹配根目录下的 .md 文件
     */
    @Test
    public void test08_rootShallow() {
        runBenchmark("08-根目录浅层 [*.md]", "*.md", 1);
    }

    /**
     * 用例 9: 字符类匹配 — 匹配 .xml 和 .yml 文件
     */
    @Test
    public void test09_charClass() {
        runBenchmark("09-字符类 [**/*.[xy][ml]*]", "**/*.[xy][ml]*", 1);
    }

    /**
     * 用例 10: walkFileTree vs 分段逐级对比
     */
    @Test
    public void test10_compareSegmented() {
        String pattern = "models/spring-ai-openai/**/*.java";
        int warmup = 5;
        int runs = 20;

        for (int i = 0; i < warmup; i++) {
            globWithWalkFileTree(pattern);
            globWithSegmentedMatching(pattern);
        }

        long walkTotal = 0;
        for (int i = 0; i < runs; i++) {
            long start = System.nanoTime();
            globWithWalkFileTree(pattern);
            walkTotal += System.nanoTime() - start;
        }
        double walkAvgMs = walkTotal / (double) runs / 1_000_000;

        long segTotal = 0;
        for (int i = 0; i < runs; i++) {
            long start = System.nanoTime();
            globWithSegmentedMatching(pattern);
            segTotal += System.nanoTime() - start;
        }
        double segAvgMs = segTotal / (double) runs / 1_000_000;

        System.out.printf("  [对比] walkFileTree: %.3f ms | 分段逐级: %.3f ms | 倍率: %.2fx%n",
                walkAvgMs, segAvgMs, walkAvgMs / segAvgMs);
        System.out.println();
    }

    // ==================== 基准测试工具方法 ====================

    private void runBenchmark(String label, String pattern, int expectedMinResults) {
        GlobTool tool = new GlobTool();
        int warmup = 5;
        int runs = 20;

        for (int i = 0; i < warmup; i++) {
            tool.glob(List.of(pattern), null);
        }

        long totalNanos = 0;
        int resultCount = 0;
        for (int i = 0; i < runs; i++) {
            long start = System.nanoTime();
            Map<String, Object> result = tool.glob(List.of(pattern), null);
            totalNanos += System.nanoTime() - start;
            Object meta = result.get("metadata");
            if (meta instanceof Map<?, ?> m && m.get("fileCount") instanceof Integer c) {
                resultCount = c;
            }
        }

        double avgMs = totalNanos / (double) runs / 1_000_000;
        System.out.printf("  %-50s %7.3f ms | 结果: %d%n", label, avgMs, resultCount);

        if (expectedMinResults > 0) {
            assertTrue(label + " 期望>=" + expectedMinResults + ", 实际=" + resultCount,
                    resultCount >= expectedMinResults);
        } else {
            assertEquals(label + " 期望 0", 0, resultCount);
        }
    }

    private void runBenchmarkMulti(String label, String[] patterns, int expectedMinResults) {
        GlobTool tool = new GlobTool();
        int warmup = 5;
        int runs = 20;

        for (int i = 0; i < warmup; i++) {
            tool.glob(Arrays.asList(patterns), null);
        }

        long totalNanos = 0;
        int resultCount = 0;
        for (int i = 0; i < runs; i++) {
            long start = System.nanoTime();
            Map<String, Object> result = tool.glob(Arrays.asList(patterns), null);
            totalNanos += System.nanoTime() - start;
            Object meta = result.get("metadata");
            if (meta instanceof Map<?, ?> m && m.get("fileCount") instanceof Integer c) {
                resultCount = c;
            }
        }

        double avgMs = totalNanos / (double) runs / 1_000_000;
        System.out.printf("  %-50s %7.3f ms | 结果: %d%n", label, avgMs, resultCount);

        assertTrue(label + " 期望>=" + expectedMinResults + ", 实际=" + resultCount,
                resultCount >= expectedMinResults);
    }

    // ==================== walkFileTree (当前实现) ====================

    private List<String> globWithWalkFileTree(String pattern) {
        GlobTool tool = new GlobTool();
        Map<String, Object> result = tool.glob(List.of(pattern), null);
        String files = (String) result.get("files");
        if (files == null || files.startsWith("No files")) return List.of();
        return Arrays.asList(files.split("\n"));
    }

    // ==================== 分段逐级匹配 (对照组) ====================

    private List<String> globWithSegmentedMatching(String pattern) {
        String p = pattern.startsWith("/") ? pattern.substring(1) : pattern;
        String[] segments = p.split("/", -1);
        Path basePath = Paths.get(SPRING_AI_ROOT);

        List<Path> current = new ArrayList<>(List.of(basePath));

        for (String seg : segments) {
            if (seg.isEmpty()) continue;
            List<Path> next = new ArrayList<>();

            if (seg.equals("**")) {
                for (Path dir : current) {
                    collectAllPaths(dir, next);
                }
            } else if (seg.contains("*") || seg.contains("?") || seg.contains("[")) {
                PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + seg);
                for (Path dir : current) {
                    if (Files.isDirectory(dir)) {
                        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
                            for (Path entry : ds) {
                                if (matcher.matches(entry.getFileName())) {
                                    next.add(entry);
                                }
                            }
                        } catch (IOException ignored) {}
                    }
                }
            } else {
                for (Path dir : current) {
                    Path target = dir.resolve(seg);
                    if (Files.exists(target)) {
                        next.add(target);
                    }
                }
            }
            current = next;
        }

        List<String> result = new ArrayList<>();
        for (Path path : current) {
            if (Files.isRegularFile(path)) {
                result.add(path.toAbsolutePath().toString());
            }
        }
        return result;
    }

    private static void collectAllPaths(Path dir, List<Path> out) {
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path entry : ds) {
                out.add(entry);
                if (Files.isDirectory(entry)) {
                    collectAllPaths(entry, out);
                }
            }
        } catch (IOException ignored) {}
    }

    // ==================== 辅助方法 ====================

    private static int countFiles(Path root) throws IOException {
        int[] count = {0};
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                count[0]++;
                return FileVisitResult.CONTINUE;
            }
        });
        return count[0];
    }
}
