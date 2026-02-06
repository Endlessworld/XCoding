package com.xr21.ai.agent.interceptors;

import com.alibaba.cloud.ai.graph.agent.extension.file.FilesystemBackend;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import com.xr21.ai.agent.tools.*;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.tool.ToolCallback;

import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

public class FilesystemInterceptor extends ModelInterceptor {
    private static final Pattern TRAVERSAL_PATTERN = Pattern.compile("\\.\\.|~");
    private final List<ToolCallback> tools;
    private final String systemPrompt;
    private final boolean readOnly;
    private final Map<String, String> customToolDescriptions;

    private FilesystemInterceptor(Builder builder) {
        this.readOnly = builder.readOnly;
        this.systemPrompt = builder.systemPrompt != null ? builder.systemPrompt : "## Filesystem Tools `ls`, `read_file`, `write_file`, `edit_file`, `glob`, `grep`\n\nYou have access to a filesystem which you can interact with using these tools.\nAll file paths must start with a /.\nAvoid using the root path because you might not have permission to read/write there.\n\n- ls: list files in a directory (requires absolute path)\n- read_file: read a file from the filesystem\n- write_file: write to a file in the filesystem\n- edit_file: edit a file in the filesystem\n- glob: find files matching a pattern (e.g., \"**/*.py\")\n- grep: search for text within files\n";
        this.customToolDescriptions = builder.customToolDescriptions != null ? new HashMap(builder.customToolDescriptions) : new HashMap();
        List<ToolCallback> toolList = new ArrayList();
        toolList.add(ListFilesTool.createListFilesToolCallback(this.customToolDescriptions.getOrDefault("ls", "Lists all files in the filesystem, filtering by directory.\n\nUsage:\n- The path parameter must be an absolute path, not a relative path\n- The list_files tool will return a list of all files in the specified directory.\n- This is very useful for exploring the file system and finding the right file to read or edit.\n- You should almost ALWAYS use this tool before using the Read or Edit tools.\n")));
        toolList.add(ReadFileTool.createReadFileToolCallback(null));
        if (!this.readOnly) {
            toolList.add(WriteFileTool.createWriteFileToolCallback());
            toolList.add(EditFileTool.createEditFileToolCallback(this.customToolDescriptions.getOrDefault("edit_file", "在文件中批量执行精确的字符串替换。\n\n使用法：\n- 编辑输出文本时，请保持精确缩进，- 始终优先编辑现有文件。 \n- 如果“old_string”在文件中不唯一，编辑将失败。\n- new_string 不得超过1000字符，超过1000字符使用新的工具调用\n")));
        }

        toolList.add(GlobTool.createGlobToolCallback(this.customToolDescriptions.getOrDefault("glob", "Find files matching a glob pattern.\n\nUsage:\n- Supports standard glob patterns: `*` (any characters), `**` (any directories), `?` (single character)\n- Returns a list of absolute file paths that match the pattern\n\nExamples:\n- `**/*.java` - Find all Java files\n- `*.txt` - Find all text files in root\n- `/src/**/*.xml` - Find all XML files under /src\n")));
        toolList.add(GrepTool.createGrepToolCallback(this.customToolDescriptions.getOrDefault("grep", "Search for a pattern in files.\n\nUsage:\n- The pattern parameter is the text to search for (literal string, not regex)\n- The path parameter filters which directory to search in\n- The glob parameter accepts a glob pattern to filter which files to search\n\nExamples:\n- Search all files: `grep(pattern=\"TODO\")`\n- The search is case-sensitive by default.\n")));
        this.tools = Collections.unmodifiableList(toolList);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static String validatePath(String path, List<String> allowedPrefixes) {
        if (TRAVERSAL_PATTERN.matcher(path).find()) {
            throw new IllegalArgumentException("Path traversal not allowed: " + path);
        } else {
            String normalized = path.replace("\\", "/");
            normalized = Paths.get(normalized).normalize().toString().replace("\\", "/");
            if (!normalized.startsWith("/")) {
                normalized = "/" + normalized;
            }

            if (allowedPrefixes != null && !allowedPrefixes.isEmpty()) {
                boolean hasValidPrefix = false;

                for (String prefix : allowedPrefixes) {
                    if (normalized.startsWith(prefix)) {
                        hasValidPrefix = true;
                        break;
                    }
                }

                if (!hasValidPrefix) {
                    throw new IllegalArgumentException("Path must start with one of " + allowedPrefixes + ": " + path);
                }
            }

            return normalized;
        }
    }

    public List<ToolCallback> getTools() {
        return this.tools;
    }

    public String getName() {
        return "Filesystem";
    }

    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
        SystemMessage enhancedSystemMessage;
        if (request.getSystemMessage() == null) {
            enhancedSystemMessage = new SystemMessage(this.systemPrompt);
        } else {
            String var10002 = request.getSystemMessage().getText();
            enhancedSystemMessage = new SystemMessage(var10002 + "\n\n" + this.systemPrompt);
        }

        ModelRequest enhancedRequest = ModelRequest.builder(request).systemMessage(enhancedSystemMessage).build();
        return handler.call(enhancedRequest);
    }

    public static class Builder {
        private String systemPrompt;
        private boolean readOnly = false;
        private Map<String, String> customToolDescriptions;
        private FilesystemBackend backend;

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder readOnly(boolean readOnly) {
            this.readOnly = readOnly;
            return this;
        }

        public Builder customToolDescriptions(Map<String, String> customToolDescriptions) {
            this.customToolDescriptions = customToolDescriptions;
            return this;
        }

        public Builder addCustomToolDescription(String toolName, String description) {
            if (this.customToolDescriptions == null) {
                this.customToolDescriptions = new HashMap();
            }

            this.customToolDescriptions.put(toolName, description);
            return this;
        }

        public FilesystemInterceptor build() {
            return new FilesystemInterceptor(this);
        }
    }
}
