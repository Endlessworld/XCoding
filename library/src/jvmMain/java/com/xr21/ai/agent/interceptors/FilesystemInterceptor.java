package com.xr21.ai.agent.interceptors;

import com.alibaba.cloud.ai.graph.agent.extension.file.FilesystemBackend;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
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
        List<ToolCallback> toolList = new ArrayList<>();

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
