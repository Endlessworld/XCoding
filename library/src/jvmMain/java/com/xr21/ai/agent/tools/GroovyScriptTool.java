package com.xr21.ai.agent.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.xr21.ai.agent.entity.ToolResult;
import com.xr21.ai.agent.utils.GroovyToolBindings;
import com.xr21.ai.agent.utils.Prompts;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Groovy 脚本执行工具。脚本内绑定一个 tools 对象，可通过 tools.xxx(...) 调用
 * 当前已注册的所有 MCP 工具，从而实现多工具编排执行。
 */
@Slf4j
public class GroovyScriptTool {

    private final List<ToolCallback> availableTools;
    /**
     * 共享线程池：脚本在独立线程中执行，以便支持超时中断，防止死循环/无限递归卡死工具。
     */
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /** 默认脚本执行超时（秒）。 */
    private static final long DEFAULT_TIMEOUT_SECONDS = 600;
    /** 脚本 stdout 输出上限（字符），防止无限打印耗尽内存。 */
    private static final int MAX_OUTPUT_CHARS = 200_000;

    public GroovyScriptTool(List<ToolCallback> availableTools) {
        this.availableTools = availableTools;
    }

    // @formatter:off
    @Tool(name = "run_groovy_script", description = Prompts.TOOL_GROOVY_SCRIPT_DESCRIPTION)
    public Map<String, Object> runGroovyScript(
            @JsonProperty(value = "script", required = true)
            @JsonPropertyDescription("要执行的 Groovy 脚本源码")
            String script,
            @JsonProperty(value = "cwd")
            @JsonPropertyDescription("脚本工作目录，可通过绑定变量 cwd 在脚本内访问")
            String cwd,
            @JsonProperty(value = "timeout_seconds")
            @JsonPropertyDescription("脚本执行超时（秒），默认 600。超时后自动终止，防止死循环/无限递归卡死工具")
            Integer timeoutSeconds,
            ToolContext toolContext) { // @formatter:on
        if (script == null || script.isBlank()) {
            return ToolResult.builder().error("script 参数不能为空").build();
        }
        long timeout = (timeoutSeconds != null && timeoutSeconds > 0)
                ? timeoutSeconds : DEFAULT_TIMEOUT_SECONDS;
        // 在独立线程执行脚本，支持超时中断，避免死循环/无限递归永久占用调用线程。
        var future = executor.submit(() -> execute(script, cwd, toolContext));
        try {
            return future.get(timeout, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("Groovy script execution timed out after {}s", timeout);
            return ToolResult.builder()
                    .error("Groovy 脚本执行超时（" + timeout + "s），已自动终止。请检查脚本是否存在死循环或无限递归。")
                    .build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResult.builder().error("Groovy 脚本执行被中断").build();
        } catch (Exception e) {
            Throwable cause = (e.getCause() != null) ? e.getCause() : e;
            log.error("Groovy script execution failed: {}", cause.getMessage());
            return ToolResult.builder()
                    .error("Groovy 脚本执行失败: " + cause.getClass().getSimpleName() + ": " + cause.getMessage())
                    .build();
        }
    }

    /**
     * 在独立线程中实际执行脚本：绑定 tools/cwd/out 变量、捕获 stdout、限制输出大小并解析返回值。
     */
    private Map<String, Object> execute(String script, String cwd, ToolContext toolContext) {
        LimitedByteArrayOutputStream stdout = new LimitedByteArrayOutputStream(MAX_OUTPUT_CHARS);
        try (PrintStream ps = new PrintStream(stdout, true, StandardCharsets.UTF_8)) {
            Binding binding = new Binding();
            binding.setVariable("tools", new GroovyToolBindings(availableTools, toolContext));
            binding.setVariable("cwd", cwd != null ? cwd : System.getProperty("user.dir"));
            // 将脚本的 out 属性指向捕获流，使脚本内 println(...) 输出被捕获。
            binding.setVariable("out", ps);
            CompilerConfiguration cc = new CompilerConfiguration();
            cc.addCompilationCustomizers(
                    new ImportCustomizer() {{
                        addStarImports("groovy.json");
                    }}
            );
            GroovyShell shell = new GroovyShell(binding, cc);
            Object value = shell.evaluate(script);
            String printed = stdout.toString(StandardCharsets.UTF_8);
            // 脚本最后表达式的返回值（非 null 时）作为结构化结果保留。
            ToolResult result = ToolResult.builder().success(true);
            if (printed != null && !printed.isBlank()) {
                result.content(printed);
            }
            if (value != null) {
                result.put("returnValue", value);
            }
            return result.build();
        } catch (Exception e) {
            log.error("Groovy script execution failed", e);
            return ToolResult.builder()
                    .error("Groovy 脚本执行失败: " + e.getClass().getSimpleName() + ": " + e.getMessage())
                    .build();
        }
    }

    /**
     * 带容量上限的字节输出流：写入达到上限后丢弃多余数据，防止脚本无限打印耗尽内存。
     */
    private static class LimitedByteArrayOutputStream extends ByteArrayOutputStream {
        private final int max;

        LimitedByteArrayOutputStream(int max) {
            this.max = max;
        }

        @Override
        public synchronized void write(int b) {
            if (size() < max) {
                super.write(b);
            }
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) {
            if (size() < max) {
                super.write(b, off, Math.min(len, max - size()));
            }
        }
    }

}
