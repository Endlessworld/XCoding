package com.xr21.ai.agent.commands;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;

/**
 * 文件操作命令
 */
@Slf4j
@ShellComponent
@RequiredArgsConstructor
public class FileCommands {

    private static final String WORKSPACE_ROOT = "D:\\local-github\\ai-agents";

    @ShellMethod(key = "ls", value = "列出目录内容")
    public String listFiles(@ShellOption(defaultValue = ".") String path) {
        try {
            Path targetPath = Paths.get(WORKSPACE_ROOT, path);
            File dir = targetPath.toFile();
            
            if (!dir.exists()) {
                return "目录不存在: " + targetPath;
            }
            
            if (!dir.isDirectory()) {
                return "不是目录: " + targetPath;
            }

            File[] files = dir.listFiles();
            if (files == null) {
                return "无法读取目录内容: " + targetPath;
            }

            StringBuilder result = new StringBuilder();
            result.append("目录内容: ").append(targetPath).append("\n");
            result.append("=".repeat(60)).append("\n");

            Arrays.stream(files)
                    .sorted(Comparator.comparing(File::isDirectory).reversed()
                            .thenComparing(File::getName))
                    .forEach(file -> {
                        String type = file.isDirectory() ? "[DIR]" : "[FILE]";
                        String size = file.isDirectory() ? "" : String.format(" (%d bytes)", file.length());
                        result.append(String.format("%s %-30s %s%n", type, file.getName(), size));
                    });

            return result.toString();
        } catch (Exception e) {
            log.error("列出目录时发生错误", e);
            return "列出目录失败: " + e.getMessage();
        }
    }

    @ShellMethod(key = "read", value = "读取文件内容")
    public String readFile(@ShellOption String path) {
        try {
            Path targetPath = Paths.get(WORKSPACE_ROOT, path);
            File file = targetPath.toFile();
            
            if (!file.exists()) {
                return "文件不存在: " + targetPath;
            }
            
            if (!file.isFile()) {
                return "不是文件: " + targetPath;
            }

            String content = Files.readString(targetPath);
            
            // 如果文件太大，只显示前1000行
            String[] lines = content.split("\n");
            if (lines.length > 1000) {
                StringBuilder result = new StringBuilder();
                result.append("文件内容 (前1000行，共").append(lines.length).append("行):\n");
                result.append("=".repeat(60)).append("\n");
                
                for (int i = 0; i < 1000; i++) {
                    result.append(String.format("%4d: %s%n", i + 1, lines[i]));
                }
                
                result.append("\n... 还有 ").append(lines.length - 1000).append(" 行未显示");
                return result.toString();
            }
            
            StringBuilder result = new StringBuilder();
            result.append("文件内容 (共").append(lines.length).append("行):\n");
            result.append("=".repeat(60)).append("\n");
            
            for (int i = 0; i < lines.length; i++) {
                result.append(String.format("%4d: %s%n", i + 1, lines[i]));
            }
            
            return result.toString();
        } catch (Exception e) {
            log.error("读取文件时发生错误", e);
            return "读取文件失败: " + e.getMessage();
        }
    }

    @ShellMethod(key = "write", value = "写入文件内容")
    public String writeFile(@ShellOption String path, @ShellOption String content) {
        try {
            Path targetPath = Paths.get(WORKSPACE_ROOT, path);
            
            // 创建父目录
            Files.createDirectories(targetPath.getParent());
            
            Files.writeString(targetPath, content);
            
            return "文件写入成功: " + targetPath;
        } catch (Exception e) {
            log.error("写入文件时发生错误", e);
            return "写入文件失败: " + e.getMessage();
        }
    }

    @ShellMethod(key = "grep", value = "在文件中搜索内容")
    public String grep(@ShellOption String pattern, @ShellOption(defaultValue = ".") String path) {
        try {
            Path targetPath = Paths.get(WORKSPACE_ROOT, path);
            File file = targetPath.toFile();
            
            if (!file.exists()) {
                return "文件不存在: " + targetPath;
            }
            
            if (file.isDirectory()) {
                // 如果是目录，递归搜索
                return searchInDirectory(file, pattern);
            }
            
            // 搜索单个文件
            String content = Files.readString(targetPath);
            String[] lines = content.split("\n");
            
            StringBuilder result = new StringBuilder();
            result.append("搜索结果 (模式: '").append(pattern).append("')\n");
            result.append("文件: ").append(targetPath).append("\n");
            result.append("=".repeat(60)).append("\n");
            
            boolean found = false;
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].contains(pattern)) {
                    result.append(String.format("%4d: %s%n", i + 1, lines[i]));
                    found = true;
                }
            }
            
            if (!found) {
                return "未找到匹配的内容";
            }
            
            return result.toString();
        } catch (Exception e) {
            log.error("搜索文件时发生错误", e);
            return "搜索文件失败: " + e.getMessage();
        }
    }

    @ShellMethod(key = "mkdir", value = "创建目录")
    public String makeDirectory(@ShellOption String path) {
        try {
            Path targetPath = Paths.get(WORKSPACE_ROOT, path);
            Files.createDirectories(targetPath);
            return "目录创建成功: " + targetPath;
        } catch (Exception e) {
            log.error("创建目录时发生错误", e);
            return "创建目录失败: " + e.getMessage();
        }
    }

    @ShellMethod(key = "rm", value = "删除文件或目录")
    public String remove(@ShellOption String path) {
        try {
            Path targetPath = Paths.get(WORKSPACE_ROOT, path);
            File file = targetPath.toFile();
            
            if (!file.exists()) {
                return "文件或目录不存在: " + targetPath;
            }
            
            if (file.isDirectory()) {
                // 递归删除目录
                deleteDirectory(file);
            } else {
                // 删除文件
                Files.delete(targetPath);
            }
            
            return "删除成功: " + targetPath;
        } catch (Exception e) {
            log.error("删除时发生错误", e);
            return "删除失败: " + e.getMessage();
        }
    }

    @ShellMethod(key = "pwd", value = "显示当前工作目录")
    public String printWorkingDirectory() {
        return "当前工作目录: " + WORKSPACE_ROOT;
    }

    @ShellMethod(key = "find", value = "查找文件")
    public String findFiles(@ShellOption String pattern, @ShellOption(defaultValue = ".") String path) {
        try {
            Path targetPath = Paths.get(WORKSPACE_ROOT, path);
            File dir = targetPath.toFile();
            
            if (!dir.exists()) {
                return "目录不存在: " + targetPath;
            }
            
            if (!dir.isDirectory()) {
                return "不是目录: " + targetPath;
            }

            StringBuilder result = new StringBuilder();
            result.append("查找结果 (模式: '").append(pattern).append("')\n");
            result.append("搜索目录: ").append(targetPath).append("\n");
            result.append("=".repeat(60)).append("\n");
            
            boolean found = findFilesRecursive(dir, pattern, targetPath.toString(), result);
            
            if (!found) {
                return "未找到匹配的文件";
            }
            
            return result.toString();
        } catch (Exception e) {
            log.error("查找文件时发生错误", e);
            return "查找文件失败: " + e.getMessage();
        }
    }

    private String searchInDirectory(File dir, String pattern) {
        StringBuilder result = new StringBuilder();
        result.append("搜索结果 (模式: '").append(pattern).append("')\n");
        result.append("搜索目录: ").append(dir.getPath()).append("\n");
        result.append("=".repeat(60)).append("\n");
        
        boolean found = searchInDirectoryRecursive(dir, pattern, result);
        
        if (!found) {
            return "未找到匹配的内容";
        }
        
        return result.toString();
    }

    private boolean searchInDirectoryRecursive(File dir, String pattern, StringBuilder result) {
        boolean found = false;
        
        File[] files = dir.listFiles();
        if (files == null) {
            return false;
        }
        
        for (File file : files) {
            if (file.isDirectory()) {
                found |= searchInDirectoryRecursive(file, pattern, result);
            } else if (file.isFile()) {
                try {
                    String content = Files.readString(file.toPath());
                    String[] lines = content.split("\n");
                    
                    for (int i = 0; i < lines.length; i++) {
                        if (lines[i].contains(pattern)) {
                            result.append(String.format("%s:%d: %s%n", file.getPath(), i + 1, lines[i]));
                            found = true;
                        }
                    }
                } catch (Exception e) {
                    log.warn("无法读取文件: " + file.getPath(), e);
                }
            }
        }
        
        return found;
    }

    private boolean findFilesRecursive(File dir, String pattern, String basePath, StringBuilder result) {
        boolean found = false;
        
        File[] files = dir.listFiles();
        if (files == null) {
            return false;
        }
        
        for (File file : files) {
            if (file.getName().contains(pattern)) {
                String relativePath = file.getPath().substring(basePath.length());
                if (relativePath.startsWith("\\")) {
                    relativePath = relativePath.substring(1);
                }
                result.append(String.format("%s %s%n", 
                    file.isDirectory() ? "[DIR]" : "[FILE]", relativePath));
                found = true;
            }
            
            if (file.isDirectory()) {
                found |= findFilesRecursive(file, pattern, basePath, result);
            }
        }
        
        return found;
    }

    private void deleteDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        directory.delete();
    }
}
