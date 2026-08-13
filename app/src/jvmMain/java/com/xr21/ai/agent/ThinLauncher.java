package com.xr21.ai.agent;

import java.io.*;
import java.lang.reflect.Method;
import java.net.*;
import java.nio.file.*;
import java.util.*;

/**
 * 瘦 jar 启动器（零三方依赖，纯 JDK）。
 * 运行时读取 jar 内 /thin-deps.properties 依赖清单，
 * 依次从配置的仓库自动下载缺失依赖到本地缓存，再加载真实主类。
 *
 * 仓库来源（按优先级）：
 *   1. -Dxagent.repos=url1;url2   （分号分隔，可覆盖默认）
 *   2. jar 内 /thin-repos.properties（每行一个 url，可选）
 *   3. 默认 Maven Central
 *
 * 支持 http(s):// 与 file:// 仓库。缓存根目录可用 -Dxagent.home 覆盖。
 * 用法: java -jar XAgent-thin.jar [args]
 */
public class ThinLauncher {
    private static final String MAIN_CLASS = "com.xr21.ai.agent.AgentApplication";
    private static final String CENTRAL = "https://repo1.maven.org/maven2/";
    private static final String DEPS_RESOURCE = "/thin-deps.properties";
    private static final String REPOS_RESOURCE = "/thin-repos.properties";
    private static final String CACHE_SUBDIR = ".xagent/lib";
    private static final String CACHE_PROP = "xagent.home";
    private static final String REPOS_PROP = "xagent.repos";

    public static void main(String[] args) throws Exception {
        File libDir = libDir();
        List<File> cp = new ArrayList<>();
        cp.add(appJar());
        for (String dep : readLines(DEPS_RESOURCE)) {
            if (dep.isBlank() || dep.startsWith("#")) continue;
            cp.add(ensure(dep.trim(), libDir));
        }
        URL[] urls = new URL[cp.size()];
        for (int i = 0; i < cp.size(); i++) urls[i] = cp.get(i).toURI().toURL();
        URLClassLoader loader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader());
        Thread.currentThread().setContextClassLoader(loader);
        Class<?> mainClass = Class.forName(MAIN_CLASS, true, loader);
        Method main = mainClass.getMethod("main", String[].class);
        main.invoke(null, (Object) args);
    }

    private static File appJar() throws Exception {
        return new File(ThinLauncher.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
    }

    private static File libDir() {
        String base = System.getProperty(CACHE_PROP, System.getProperty("user.home"));
        File dir = new File(base, CACHE_SUBDIR);
        dir.mkdirs();
        return dir;
    }

    /** 读取 jar 内资源，每行 trim 后返回。 */
    private static List<String> readLines(String resource) throws IOException {
        List<String> lines = new ArrayList<>();
        try (InputStream in = ThinLauncher.class.getResourceAsStream(resource)) {
            if (in == null) return lines; // 可选资源
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
                String line;
                while ((line = r.readLine()) != null) lines.add(line.trim());
            }
        }
        return lines;
    }

    /** 计算仓库列表：-Dxagent.repos > jar 内 thin-repos.properties > Maven Central。 */
    private static List<String> repos() throws IOException {
        List<String> list = new ArrayList<>();
        String sys = System.getProperty(REPOS_PROP);
        if (sys != null && !sys.isBlank()) {
            for (String s : sys.split(";")) {
                if (!s.isBlank()) list.add(normalizeRepo(s));
            }
        } else {
            List<String> file = readLines(REPOS_RESOURCE);
            if (!file.isEmpty()) {
                for (String s : file) {
                    if (!s.isBlank() && !s.startsWith("#")) list.add(normalizeRepo(s));
                }
            } else {
                list.add(CENTRAL);
            }
        }
        return list;
    }

    private static String normalizeRepo(String repo) {
        return repo.endsWith("/") ? repo : repo + "/";
    }

    /** 检查本地缓存，缺失则依次从各仓库下载。 */
    private static File ensure(String dep, File libDir) throws Exception {
        String[] parts = dep.split(":");
        if (parts.length < 3) throw new IOException("Bad dep entry: " + dep);
        String group = parts[0], artifact = parts[1], version = parts[2];
        String fileName = group + "." + artifact + "-" + version + ".jar";
        File target = new File(libDir, fileName);
        if (target.exists() && target.length() > 0) return target;
        String relPath = group.replace('.', '/') + "/" + artifact + "/"
                + version + "/" + artifact + "-" + version + ".jar";
        IOException lastErr = null;
        for (String repo : repos()) {
            try {
                download(repo + relPath, target, libDir, fileName);
                System.out.println("[ThinLauncher] Downloaded " + fileName);
                return target;
            } catch (IOException e) {
                lastErr = e;
            }
        }
        throw new IOException("Cannot download " + fileName + " from any repo", lastErr);
    }

    private static void download(String urlPath, File target, File libDir,
                                 String fileName) throws IOException {
        File tmp = new File(libDir, fileName + ".part");
        try (InputStream in = open(urlPath);
             OutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private static InputStream open(String urlPath) throws IOException {
        if (urlPath.startsWith("file:")) {
            return new FileInputStream(new File(URI.create(urlPath)));
        }
        HttpURLConnection conn = (HttpURLConnection) new URL(urlPath).openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "XAgent-ThinLauncher/1.0");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        int code = conn.getResponseCode();
        if (code != 200) throw new IOException("HTTP " + code + " for " + urlPath);
        return conn.getInputStream();
    }
}
