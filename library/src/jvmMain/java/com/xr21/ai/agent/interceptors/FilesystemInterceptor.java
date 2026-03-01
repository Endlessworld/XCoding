package com.xr21.ai.agent.interceptors;

import com.alibaba.cloud.ai.graph.agent.extension.file.FilesystemBackend;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import com.xr21.ai.agent.tools.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

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

        this.systemPrompt = builder.systemPrompt != null ? builder.systemPrompt : createDefaultSystemPrompt();
        this.customToolDescriptions = builder.customToolDescriptions != null ? new HashMap<>(builder.customToolDescriptions) : new HashMap<>();

        List<ToolCallback> toolList = createTools();
        this.tools = Collections.unmodifiableList(toolList);

        log.info("FilesystemInterceptor initialized with {} tools, readOnly: {}, allowedPrefixes: {}", toolList.size(), readOnly, allowedPrefixes);
    }

    public static Builder builder() {
        return new Builder();
    }

    private String createDefaultSystemPrompt() {
        return """
                ## Filesystem Access Tools
                
                You have access to a filesystem which you can interact with using these tools. 
                All file paths must be absolute paths starting with `/`.
                
                ### Security Guidelines:
                1. Avoid using the root (`/`) directory - use specific workspace paths
                2. Never attempt path traversal using `..` or `~`
                3. Be cautious when editing system files
                4. Always validate paths before operations
                
                ### Available Tools:
                - `ls`: List files in a directory with depth control
                - `read_file`: Read file contents (supports pagination)
                - `write_file`: Create or overwrite files (use with caution)
                - `edit_file`: Edit existing files with precise string replacement
                - `glob`: Find files matching patterns (e.g., `**/*.java`)
                - `grep`: Search for text within files
                
                ### Best Practices:
                1. Always use `ls` to explore directories before reading/editing
                2. Use `read_file` with offset/limit for large files
                3. Create backups before major edits
                4. Use descriptive paths and avoid ambiguous names
                
                ### Path Validation:
                - All paths are validated for security
                - Path traversal attempts are blocked
                - Dangerous system paths are restricted
                - Paths are normalized automatically
                
                Remember: You are working in a ${readOnly ? "READ-ONLY" : "READ-WRITE"} mode.
                """;
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
            toolObjects.add(new EditFileTool());
        }

        // Create provider with all tool objects
        MethodToolCallbackProvider provider = MethodToolCallbackProvider.builder().toolObjects(toolObjects.toArray()).build();

        // Get all tool callbacks from provider
        List<ToolCallback> toolCallbacks = List.of(provider.getToolCallbacks());
        log.info("Created {} filesystem tools (readOnly: {})", toolCallbacks.size(), readOnly);

        return toolCallbacks;
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
