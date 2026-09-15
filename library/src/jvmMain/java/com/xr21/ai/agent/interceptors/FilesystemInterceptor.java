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
package com.xr21.ai.agent.interceptors;

import com.alibaba.cloud.ai.graph.agent.extension.file.FilesystemBackend;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import com.xr21.ai.agent.tools.*;
import com.xr21.ai.agent.utils.Prompts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

/**
 *
 * @author Endless
 */
public class FilesystemInterceptor extends ModelInterceptor {
    private static final Logger log = LoggerFactory.getLogger(FilesystemInterceptor.class);
    private static final Pattern TRAVERSAL_PATTERN = Pattern.compile("\\.\\.|~|\\|");
    private static final Pattern DANGEROUS_PATHS = Pattern.compile("(/etc/|/proc/|/sys/|/boot/|/root/)");

    private final List<ToolCallback> tools;
    private final String systemPrompt;
    private final boolean readOnly;
    private final Map<String, String> customToolDescriptions;
    private final FilesystemBackend backend;
    private final List<String> allowedPrefixes;

    private FilesystemInterceptor(Builder builder) {
        this.readOnly = builder.readOnly;
        this.backend = builder.backend;
        this.allowedPrefixes = builder.allowedPrefixes != null ? new ArrayList<>(builder.allowedPrefixes) : Collections.emptyList();

        this.systemPrompt = builder.systemPrompt != null ? builder.systemPrompt : createDefaultSystemPrompt(readOnly);
        this.customToolDescriptions = builder.customToolDescriptions != null ? new HashMap<>(builder.customToolDescriptions) : new HashMap<>();

        List<ToolCallback> toolList = createTools();
        this.tools = Collections.unmodifiableList(toolList);

        log.info("FilesystemInterceptor initialized with {} tools, readOnly: {}, allowedPrefixes: {}", toolList.size(), readOnly, allowedPrefixes);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static String validatePath(String path, List<String> allowedPrefixes) {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }

        // Check for path traversal
        if (TRAVERSAL_PATTERN.matcher(path).find()) {
            throw new IllegalArgumentException("Path traversal not allowed: " + path);
        }

        // Check for dangerous system paths
        if (DANGEROUS_PATHS.matcher(path).find()) {
            throw new IllegalArgumentException("Access to dangerous system path denied: " + path);
        }

        // Normalize path
        String normalized = path.replace("\\", "/");
        normalized = Paths.get(normalized).normalize().toString().replace("\\", "/");

        // Ensure absolute path
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }

        // Validate against allowed prefixes
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

        log.debug("Path validated: {} -> {}", path, normalized);
        return normalized;
    }

    public static String sanitizeFilename(String filename) {
        if (filename == null) {
            return "";
        }

        // Remove dangerous characters
        String sanitized = filename.replaceAll("[\\\\/:*?\"<>|]", "_");

        // Limit length
        if (sanitized.length() > 255) {
            sanitized = sanitized.substring(0, 255);
        }

        return sanitized;
    }

    private String createDefaultSystemPrompt(boolean readOnly) {
        return Prompts.FILESYSTEM_SYSTEM_PROMPT.formatted(readOnly ? "只读" : "读写");
    }

    private List<ToolCallback> createTools() {
        List<Object> toolObjects = new ArrayList<>();

        // Add read operations (always available)
        toolObjects.add(new ListFilesTool());
        toolObjects.add(new ReadFileTool());
        toolObjects.add(new GlobTool());
        toolObjects.add(new GrepTool());

        // Add write operations only if not read-only
        if (!readOnly) {
            toolObjects.add(new WriteFileTool());
            toolObjects.add(new SmartEditTool());
//            toolObjects.add(new EditFileTool());
//            toolObjects.add(new EditFileWithGitPatchTool(allowedPrefixes));
        }

        // Create provider with all tool objects
        MethodToolCallbackProvider provider = MethodToolCallbackProvider.builder().toolObjects(toolObjects.toArray()).build();

        // Get all tool callbacks from provider
        List<ToolCallback> toolCallbacks = List.of(provider.getToolCallbacks());
        log.info("Created {} filesystem tools (readOnly: {})", toolCallbacks.size(), readOnly);

        return toolCallbacks;
    }

    public String validatePath(String path) {
        return validatePath(path, this.allowedPrefixes);
    }

    public boolean isPathAllowed(String path) {
        try {
            validatePath(path, this.allowedPrefixes);
            return true;
        } catch (IllegalArgumentException e) {
            log.warn("Path not allowed: {} - {}", path, e.getMessage());
            return false;
        }
    }

    public String getSafePath(String path) {
        return validatePath(path, this.allowedPrefixes);
    }

    public void checkWritePermission(String path) {
        if (readOnly) {
            throw new SecurityException("Filesystem is in read-only mode. Cannot write to: " + path);
        }

        if (!isPathAllowed(path)) {
            throw new SecurityException("Path not allowed for write operations: " + path);
        }
    }

    public List<ToolCallback> getTools() {
        return this.tools;
    }

    public String getName() {
        return "Filesystem";
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public List<String> getAllowedPrefixes() {
        return Collections.unmodifiableList(allowedPrefixes);
    }

    public FilesystemBackend getBackend() {
        return backend;
    }

    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
        log.debug("Intercepting model request for filesystem access");

        try {
            SystemMessage enhancedSystemMessage;
            if (request.getSystemMessage() == null) {
                enhancedSystemMessage = new SystemMessage(this.systemPrompt);
                log.debug("Added filesystem system prompt to request");
            } else {
                String existingPrompt = request.getSystemMessage().getText();
                enhancedSystemMessage = new SystemMessage(existingPrompt + "\n\n" + this.systemPrompt);
                log.debug("Appended filesystem system prompt to existing prompt");
            }

            ModelRequest enhancedRequest = ModelRequest.builder(request).systemMessage(enhancedSystemMessage).build();

            log.info("FilesystemInterceptor processing request with {} tools", tools.size());
            return handler.call(enhancedRequest);

        } catch (Exception e) {
            log.error("Error in FilesystemInterceptor", e);
            throw e;
        }
    }

    public static class Builder {
        private String systemPrompt;
        private boolean readOnly = false;
        private Map<String, String> customToolDescriptions;
        private FilesystemBackend backend;
        private List<String> allowedPrefixes;

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder readOnly(boolean readOnly) {
            this.readOnly = readOnly;
            return this;
        }

        public Builder backend(FilesystemBackend backend) {
            this.backend = backend;
            return this;
        }

        public Builder allowedPrefixes(List<String> allowedPrefixes) {
            this.allowedPrefixes = allowedPrefixes;
            return this;
        }

        public Builder addAllowedPrefix(String prefix) {
            if (this.allowedPrefixes == null) {
                this.allowedPrefixes = new ArrayList<>();
            }
            this.allowedPrefixes.add(prefix);
            return this;
        }

        public Builder customToolDescriptions(Map<String, String> customToolDescriptions) {
            this.customToolDescriptions = customToolDescriptions;
            return this;
        }

        public Builder addCustomToolDescription(String toolName, String description) {
            if (this.customToolDescriptions == null) {
                this.customToolDescriptions = new HashMap<>();
            }

            this.customToolDescriptions.put(toolName, description);
            return this;
        }

        public Builder withWorkspaceRoot(String workspaceRoot) {
            if (workspaceRoot != null && !workspaceRoot.trim().isEmpty()) {
                String normalizedRoot = workspaceRoot.replace("\\", "/");
                if (!normalizedRoot.endsWith("/")) {
                    normalizedRoot = normalizedRoot + "/";
                }
                this.addAllowedPrefix(normalizedRoot);
                this.addAllowedPrefix(workspaceRoot);
            }
            return this;
        }

        public Builder withDefaultSecurity() {
            // Add common safe prefixes
            this.addAllowedPrefix("/workspace/");
            this.addAllowedPrefix("/home/");
            this.addAllowedPrefix("/tmp/");
            return this;
        }

        public FilesystemInterceptor build() {
            // Apply default security if no prefixes specified
            if (this.allowedPrefixes == null || this.allowedPrefixes.isEmpty()) {
                this.withDefaultSecurity();
            }

            // Note: backend may remain null if not provided
            // This is acceptable as it's optional for basic functionality

            return new FilesystemInterceptor(this);
        }
    }
}
