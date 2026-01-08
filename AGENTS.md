# AGENTS.md - AI Agents Project Documentation

This document provides essential information for agents working on this codebase.

## Project Overview

This is a **Spring Boot 3.5.0** application with **Spring Shell 3.3.2** that provides a local AI agent interface. The agent uses the **Alibaba AI Graph** framework for orchestrating multiple sub-agents and supports various LLM providers through OpenAI-compatible APIs.

## Essential Commands

### Build and Run

```bash
# Compile the project
mvn clean compile

# Run the Spring Boot application
mvn spring-boot:run

# Package the application
mvn package

# Clean build artifacts
mvn clean
```

### Development

The application runs as a non-interactive Spring Shell application. When running via `mvn spring-boot:run`, it starts in interactive mode where you can use the shell commands documented below.

## Shell Commands

Once the application is running, the following commands are available:

### Chat Commands
- `chat <message>` - Send a message to the AI assistant
- `session list` - List all sessions
- `session create` - Create a new session
- `session switch <id>` - Switch to a specific session
- `session current` - Show current session
- `history` - View current session history
- `clear` - Clear current session history
- `save [filename]` - Save conversation to file
- `load <filename>` - Load conversation from file
- `feedback <message>` - Send feedback to the AI assistant
- `help-commands` - Show help information

### File Operation Commands
- `ls [path]` - List directory contents (defaults to workspace root)
- `read <path>` - Read file content with line numbers
- `write <path> <content>` - Write content to a file
- `grep <pattern> [path]` - Search for pattern in files
- `mkdir <path>` - Create directory
- `rm <path>` - Delete file or directory
- `pwd` - Show current working directory
- `find <pattern> [path]` - Find files matching pattern

## Code Organization

```
src/main/java/com/xr21/ai/agent/
├── AiAgentApplication.java          # Spring Boot application entry point
├── LocalAgent.java                   # Core agent implementation and AI graph setup
├── config/
│   ├── ShellConfig.java             # Spring Shell bean configuration
│   ├── AiModels.java                # AI model enum with configuration
│   └── SpringAiExcludeConfig.java   # Spring AI auto-configuration exclusions
├── commands/
│   ├── InteractiveCommands.java     # Shell commands for chat/session management
│   └── FileCommands.java            # Shell commands for file operations
├── session/
│   └── ConversationSessionManager.java # Session persistence and management
├── entity/
│   ├── AgentOutput.java             # AI output entity with ServerSentEvent
│   └── ConversationMessage.java     # Message entity with types
├── interceptors/
│   ├── ContextEditingInterceptor.java # Token context management
│   ├── FilesystemInterceptor.java   # Filesystem access control
│   └── ToolRetryInterceptor.java    # Tool execution retry logic
├── tools/
│   ├── GlobTool.java               # File glob pattern matching
│   ├── GrepTool.java               # Content search tool
│   ├── ReadFileTool.java           # File reading with pagination
│   ├── EditFileTool.java           # File editing with exact matching
│   ├── WriteFileTool.java          # File writing
│   ├── ListFilesTool.java          # Directory listing
│   ├── FeedBackTool.java           # User feedback collection
│   ├── ContextCacheTool.java       # Context caching
│   ├── DefaultTokenCounter.java    # Token counting
│   └── Json.java                   # JSON utilities
└── utils/
    ├── SinksUtil.java              # Reactor Flux utilities for streaming
    └── SinksUtil.java              # (Duplicate - unused)
```

## Configuration

### Environment Variables

The application requires the following environment variables for AI API access:

| Variable | Description | Default |
|----------|-------------|---------|
| `AI_MINIMAX_BASE_URL` | MiniMax API base URL | Required |
| `AI_MINIMAX_API_KEY` | MiniMax API key | Required |
| `AI_OPENAPI_BASE_URL` | OpenAI-compatible API base URL | Required |
| `AI_OPENAPI_API_KEY` | OpenAI-compatible API key | Required |
| `AI_XIAOMI_BASE_URL` | Xiaomi API base URL | Optional |
| `AI_XIAOMI_API_KEY` | Xiaomi API key | Optional |

### application.properties

Key configuration values:

```properties
spring.main.web-application-type=none          # Non-web CLI mode
spring.shell.interactive.enabled=true         # Enable interactive shell
spring.shell.script.enabled=false             # Disable script mode
spring.shell.history.enabled=true             # Enable command history
jline.terminal=org.jline.terminal.impl.ExecPty
spring.ai.chat.client.enabled=false           # Disable default chat client
spring.ai.model.chat=false                    # Disable chat model auto-config
spring.ai.model.embedding=ollama              # Use Ollama for embeddings
spring.ai.model.image=false                   # Disable image model
spring.ai.model.audio.speech=false            # Disable audio speech
spring.ai.model.audio.transcription=false     # Disable audio transcription
logging.level.root=warn                       # Minimize logging
```

### AI Models

The `AiModels` enum in `config/AiModels.java` defines available models:

- `DEEPSEEK_V3_2_TERMINUS` - DeepSeek V3.2 (OpenAI-compatible)
- `GLM_4_7` - ChatGLM-4.7 (OpenAI-compatible)
- `MINIMAX_M2_1` - MiniMax M2.1
- `MINIMAX_M2_1_LIGHTNING` - MiniMax M2.1 Lightning
- `MIMO_V2_FLASH` - Xiaomi Mimo V2 Flash
- `DEEPSEEK_FUNCTION_CALL` - DeepSeek function calling variant

Default model: `MINIMAX_M2_1` (configured in `ShellConfig.java`)

## Workspace Path

The workspace root is hardcoded in multiple locations:

- `LocalAgent.java:57` - `WORKSPACE_ROOT = "D:\\local-github\\ai-agents"`
- `FileCommands.java:24` - `WORKSPACE_ROOT = "D:\\local-github\\ai-agents"`

All file operations should use absolute paths or paths relative to this directory.

## Session Management

Sessions are stored in the `./conversations` directory as JSON files with the naming pattern: `{sessionId}_{date}.json`

- **Auto-save**: Sessions are auto-saved every 30 seconds
- **Format**: JSON with `ConversationSession` structure containing messages array
- **Message types**: USER, ASSISTANT, SYSTEM, TOOL_CALL, TOOL_RESPONSE, ERROR

## Agent Architecture

### Sub-Agents

The system uses a `SupervisorAgent` with three sub-agents:

1. **writer_agent** - File operations agent (read, write, edit, grep, ls)
2. **check_agent** - Task completion checker (outputs FINISH or NOT_DONE)
3. **fallback_agent** - General-purpose AI assistant for non-file tasks

### Interceptors

- **ContextEditingInterceptor**: Manages token context by clearing old tool messages when threshold exceeded (default: 500,500 tokens)
- **ToolRetryInterceptor**: Retries failed tool calls with exponential backoff (max 2 retries, 1s initial delay, 1.5x backoff)
- **FilesystemInterceptor**: Controls filesystem access boundaries
- **LargeResultEvictionInterceptor**: Stores large tool results to filesystem when exceeding 10,000 tokens

### Tool Filtering

Only specific tools are exposed to the agent (defined in `LocalAgent.java:63`):

- `grep` - Content search
- `glob` - File pattern matching
- `edit_file` - File editing
- `write_file` - File creation/writing
- `read_file` - File reading
- `ls` - Directory listing
- `execute_terminal_command` - Shell command execution

## Key Patterns and Conventions

### File Editing Pattern

When editing files, use exact text matching with surrounding context:

```java
// The EditFileTool requires exact string matching including whitespace
// It handles CRLF/LF normalization automatically
// If text appears multiple times, provide more context or use replaceAll=true
```

### Agent Instruction Pattern

Agent instructions in `LocalAgent.java` specify:
- **writer_agent**: Explicitly prohibits `**/*` glob patterns, requires specific keywords
- **edit_file**: Limited to 3 lines per edit operation
- **Context management**: Keep recent 8 tool messages, clear oldest when threshold exceeded

### Shell Command Pattern

Spring Shell commands use annotations:

```java
@ShellComponent
@RequiredArgsConstructor
@Slf4j
public class InteractiveCommands {
    @ShellMethod(key = "chat", value = "Send message to AI")
    public void chat(@ShellOption(defaultValue = "") String message, CommandContext ctx) {
        // Implementation
    }
}
```

### Session Message Pattern

Messages are categorized by type with factory methods in `ConversationMessage`:

```java
ConversationMessage.createUserMessage(sessionId, content, round)
ConversationMessage.createAssistantMessage(sessionId, content, round)
ConversationMessage.createToolCallMessage(sessionId, toolName, arguments, callId, round)
ConversationMessage.createToolResponseMessage(sessionId, toolName, response, success, round)
```

## Gotchas and Important Notes

1. **Hardcoded Windows Paths**: Workspace and Java paths are Windows-specific (e.g., `D:\local-github\ai-agents`, `D:\JetBrains\...\java.exe`)

2. **Case Sensitivity**: Grep and file searches are case-sensitive by default

3. **Line Ending Differences**: EditFileTool normalizes CRLF to LF, but always use exact text from file

4. **Tool Call Limits**: Only 8 recent tool messages are kept; older ones are cleared

5. **Session Auto-Save**: Changes are saved every 30 seconds, not immediately

6. **Maven Wrapper**: The project uses `mvnw` (Maven wrapper) - prefer over system Maven

7. **Spring AI Version**: Uses snapshot version `2.0.0.20251117-SNAPSHOT`

8. **No Database**: Sessions are stored as JSON files, not in a database

9. **Blocking Operations**: Agent processing uses `.blockLast()` which blocks the current thread

10. **Tool Filtering**: Unlisted tools (like MCP tools) are filtered out in `LocalAgent.getTools()`

## Dependencies

Key dependencies from `pom.xml`:

- **Spring Boot 3.5.0** - Application framework
- **Spring Shell 3.3.2** - CLI framework
- **Spring AI OpenAI** - OpenAI-compatible API client
- **Alibaba AI Graph 1.1.0.0** - Agent graph framework
- **Hutool 5.8.33** - Utility library
- **Lombok** - Code generation
- **Java 21** - Runtime requirement

## Testing

No test framework is currently configured. Tests would be located in `src/test/java/` following Maven conventions.

## Additional Resources

- Prompts are stored in `src/main/resources/prompt/` directory
- Banner is configured in `src/main/resources/banner.txt`
- Large tool results are stored in `large_tool_results/` directory
- Conversation history is stored in `conversations/` directory
