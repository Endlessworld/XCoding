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
package com.xr21.ai.agent.utils;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 将 classpath 内置 skills 资源释放到工作目录下的 .agents/skills。
 * <p>
 * 由于 hooks 无法同时挂载两个 {@code SkillsAgentHook}，{@code ClasspathSkillRegistry}
 * 与 {@code FileSystemSkillRegistry} 不能共存。这里在应用启动（构建 Agent）时把打包在
 * classpath 的 {@code skills/} 资源物化到 {@code <cwd>/.agents/skills}，
 * 使其统一由 FileSystemSkillRegistry 加载。
 * </p>
 *
 * @author Endless
 */
@Slf4j
public final class SkillResourceReleaser {

    /** classpath 中内置技能资源根目录 */
    private static final String CLASSPATH_ROOT = "skills";

    /** 已释放的目标目录，避免重复释放 */
    private static final Set<String> RELEASED_DIRS = ConcurrentHashMap.newKeySet();

    private SkillResourceReleaser() {
    }

    /**
     * 将 classpath 内置 skills 释放到指定目录（仅写入不存在的文件，不覆盖用户已有内容）。
     *
     * @param targetSkillsDir 目标技能目录（通常为 {@code <cwd>/.agents/skills}）
     */
    public static void release(Path targetSkillsDir) {
        if (targetSkillsDir == null) {
            return;
        }
        Path target = targetSkillsDir.toAbsolutePath().normalize();
        String key = target.toString();
        if (RELEASED_DIRS.contains(key)) {
            return;
        }
        try {
            int count = copyClasspathDir(CLASSPATH_ROOT, target);
            RELEASED_DIRS.add(key);
            if (count > 0) {
                log.info("SkillResourceReleaser: released {} built-in skill file(s) to {}", count, target);
            }
        } catch (Exception e) {
            log.warn("SkillResourceReleaser: failed to release built-in skills to {}: {}", target, e.getMessage());
        }
    }

    /** 枚举 classpath 中 root 目录的所有资源（支持目录与 jar 两种形态）并落盘。 */
    private static int copyClasspathDir(String root, Path targetDir) throws Exception {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = SkillResourceReleaser.class.getClassLoader();
        }
        int count = 0;
        Enumeration<URL> urls = loader.getResources(root);
        while (urls.hasMoreElements()) {
            URL url = urls.nextElement();
            String protocol = url.getProtocol();
            if ("file".equals(protocol)) {
                Path dir = Path.of(url.toURI());
                if (Files.isDirectory(dir)) {
                    count += copyFromDirectory(dir, dir, targetDir);
                }
            } else if ("jar".equals(protocol)) {
                JarURLConnection conn = (JarURLConnection) url.openConnection();
                conn.setUseCaches(false);
                try (JarFile jar = conn.getJarFile()) {
                    count += copyFromJar(jar, root, targetDir);
                }
            }
        }
        return count;
    }

    /** 递归复制本地目录（开发态 classpath 为展开目录）。 */
    private static int copyFromDirectory(Path base, Path current, Path targetDir) throws IOException {
        int count = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(current)) {
            for (Path path : stream) {
                if (Files.isDirectory(path)) {
                    count += copyFromDirectory(base, path, targetDir);
                } else {
                    Path dest = targetDir.resolve(base.relativize(path).toString());
                    if (writeIfAbsent(dest, path)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /** 复制 jar 中 root 前缀下的所有条目。 */
    private static int copyFromJar(JarFile jar, String root, Path targetDir) throws IOException {
        String prefix = root.endsWith("/") ? root : root + "/";
        int count = 0;
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (entry.isDirectory() || !name.startsWith(prefix)) {
                continue;
            }
            Path dest = targetDir.resolve(name.substring(prefix.length()));
            try (InputStream in = jar.getInputStream(entry)) {
                if (writeIfAbsent(dest, in)) {
                    count++;
                }
            }
        }
        return count;
    }

    /** 目标不存在时写入源文件，返回是否实际写入。 */
    private static boolean writeIfAbsent(Path dest, Path source) throws IOException {
        if (Files.exists(dest)) {
            return false;
        }
        Files.createDirectories(dest.getParent());
        Files.copy(source, dest);
        return true;
    }

    /** 目标不存在时写入字节流，返回是否实际写入。 */
    private static boolean writeIfAbsent(Path dest, InputStream source) throws IOException {
        if (Files.exists(dest)) {
            return false;
        }
        Files.createDirectories(dest.getParent());
        Files.copy(source, dest);
        return true;
    }
}
