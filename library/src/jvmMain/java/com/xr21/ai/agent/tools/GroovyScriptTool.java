package com.xr21.ai.agent.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xr21.ai.agent.entity.ToolResult;
import com.xr21.ai.agent.utils.GroovyToolBindings;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;

import java.util.List;
import java.util.Map;

/**
 * Groovy 脚本执行工具。脚本内绑定一个 tools 对象，可通过 tools.xxx(...) 调用
 * 当前已注册的所有 MCP 工具，从而实现多工具编排执行。
 */
@Slf4j
public class GroovyScriptTool {

    private final List<ToolCallback> availableTools;

    public GroovyScriptTool(List<ToolCallback> availableTools) {
        this.availableTools = availableTools;
    }

    // @formatter:off
    @Tool(name = "run_groovy_script", description = """
            执行一段 Groovy 脚本，用于多工具编排。脚本内会绑定一个 tools 对象，
            可通过 tools.xxx(...) 动态调用当前已注册的所有 MCP 工具，并把每个工具
            的返回结果（优先解析为结构化 Map/List）作为表达式的值返回，供脚本编排使用。

            用法：
            - 查看可用工具: tools.names
            - 调用工具(推荐传入 Map 命名参数): tools.read_file([filePaths: ['/a.txt']])
            - 位置参数(按工具 schema 属性顺序): tools.read_file(['/a.txt'], 0, 100)
            - 单参数工具: tools.Sleep([seconds: 3]) 或 tools.Sleep(3)

            示例编排：
                def r = tools.read_file([filePaths: ['/a.txt']])
                println(r)
                tools.write_todos([entries: [
                    [content: '步骤1', status: 'IN_PROGRESS', priority: 'HIGH']
                ]])

            tools 调用的每个工具失败时会返回 {success:false, error:...} 而不是抛出异常，
            便于脚本内继续编排处理。
            """)
    public Map<String, Object> runGroovyScript(
            @JsonProperty(value = "script", required = true)
            @JsonPropertyDescription("要执行的 Groovy 脚本源码")
            String script,
            @JsonProperty(value = "cwd")
            @JsonPropertyDescription("脚本工作目录，可通过绑定变量 cwd 在脚本内访问")
            String cwd,
            ToolContext toolContext) { // @formatter:on
        if (script == null || script.isBlank()) {
            return ToolResult.builder().error("script 参数不能为空").build();
        }
        try {
            Binding binding = new Binding();
            binding.setVariable("tools", new GroovyToolBindings(availableTools, toolContext));
            binding.setVariable("cwd", cwd != null ? cwd : System.getProperty("user.dir"));
            GroovyShell shell = new GroovyShell(binding);
            Object value = shell.evaluate(script);
            return ToolResult.builder()
                    .success(true)
                    .content(serialize(value))
                    .build();
        } catch (Exception e) {
            log.error("Groovy script execution failed", e);
            return ToolResult.builder()
                    .error("Groovy 脚本执行失败: " + e.getMessage())
                    .build();
        }
    }

    private String serialize(Object value) {
        try {
            return new ObjectMapper().writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }
}
