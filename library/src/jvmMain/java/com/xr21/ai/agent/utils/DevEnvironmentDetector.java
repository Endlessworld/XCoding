package com.xr21.ai.agent.utils;

import com.xr21.ai.agent.tools.ShellTools;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class DevEnvironmentDetector {

    private DevEnvironmentDetector() {
    }

    private static volatile String cached;

    /**
     * 待探测的开发工具：[可执行文件名, 版本参数...]
     */
    private static final String[][] CANDIDATE_TOOLS = {
            {"git", "--version"}, {"node", "--version"}, {"npm", "--version"},
            {"pnpm", "--version"}, {"yarn", "--version"}, {"bun", "--version"},
            {"deno", "--version"}, {"java", "--version"}, {"javac", "--version"},
            {"mvn", "--version"}, {"gradle", "--version"}, {"python", "--version"},
            {"python3", "--version"}, {"uv", "--version"}, {"pip", "--version"},
            {"go", "version"}, {"rustc", "--version"}, {"cargo", "--version"},
            {"docker", "--version"}, {"kubectl", "version", "--client"},
            {"gh", "--version"}, {"gcc", "--version"}, {"make", "--version"},
            {"cmake", "--version"}, {"sqlite3", "--version"}, {"nu", "--version"},
            {"pwsh", "--version"}
    };

    private static final long PROBE_TIMEOUT_SECONDS = 4;

    /**
     * 返回运行环境描述（Markdown 片段），首次调用时探测并缓存。
     */
    public static String describe() {
        String v = cached;
        if (v == null) {
            synchronized (DevEnvironmentDetector.class) {
                if (cached == null) {
                    cached = build();
                }
                v = cached;
            }
        }
        return v;
    }

    private static String build() {
        StringBuilder sb = new StringBuilder();
        sb.append("### 运行环境\n");
        sb.append("- 操作系统: ").append(System.getProperty("os.name")).append(' ')
                .append(System.getProperty("os.version")).append(" (")
                .append(System.getProperty("os.arch")).append(")\n");
        sb.append("- Shell: ").append(ShellTools.getShellExecutable()).append(" (请严格遵循该shell的语法\n");
        sb.append("- JVM: ").append(System.getProperty("java.version")).append('\n');
        sb.append("- user.home: ").append(System.getProperty("user.home")).append('\n');
        sb.append("- user.dir: ").append(System.getProperty("user.dir")).append("\n\n");
        sb.append("### 已安装开发工具\n");
        boolean any = false;
        for (String[] tool : CANDIDATE_TOOLS) {
            String exe = tool[0];
            String version = firstLine(probe(exe, Arrays.copyOfRange(tool, 1, tool.length)));
            if (version == null) {
                continue;
            }
            any = true;
            sb.append("- ").append(exe).append(": ").append(version);
            String location = firstLine(probe(locateCommand(), exe));
            if (location != null) {
                sb.append("  @ ").append(location);
            }
            sb.append('\n');
        }
        if (!any) {
            sb.append("- (未探测到常见开发工具)\n");
        }
        return sb.toString();
    }

    private static String locateCommand() {
        return System.getProperty("os.name").toLowerCase().contains("win") ? "where" : "which";
    }

    /**
     * 执行探测命令并返回原始输出；未安装或超时返回 null。
     */
    private static String probe(String exe, String... args) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add(exe);
            cmd.addAll(List.of(args));
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            if (!p.waitFor(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                p.destroyForcibly();
            }
            return readAll(p.getInputStream());
        } catch (Exception e) {
            return null;
        }
    }

    private static String readAll(InputStream in) {
        try (in) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private static String firstLine(String text) {
        if (text == null) {
            return null;
        }
        for (String line : text.split("\\r?\\n")) {
            String t = line.trim();
            if (!t.isEmpty()) {
                return t;
            }
        }
        return null;
    }
}
