package com.xr21.ai.agent.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Groovy 脚本工具单元测试：验证 tools 对象可编排调用所有 MCP 工具。
 */
public class GroovyScriptToolTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public static class DummyTools {
        @Tool(name = "echo", description = "echo the message")
        public Map<String, Object> echo(
                @JsonProperty(value = "message", required = true) @JsonPropertyDescription("msg") String message) {
            return Map.of("success", true, "received", message);
        }

        @Tool(name = "add", description = "add two ints")
        public Map<String, Object> add(
                @JsonProperty(value = "a", required = true) @JsonPropertyDescription("a") int a,
                @JsonProperty(value = "b", required = true) @JsonPropertyDescription("b") int b) {
            return Map.of("success", true, "sum", a + b);
        }
    }

    private List<ToolCallback> dummyTools() {
        List<ToolCallback> out = new ArrayList<>();
        for (ToolCallback cb : MethodToolCallbackProvider.builder().toolObjects(new DummyTools()).build().getToolCallbacks()) {
            out.add(cb);
        }
        return out;
    }

    private JsonNode runScript(String script) throws Exception {
        ToolCallback groovyCb = MethodToolCallbackProvider.builder()
                .toolObjects(new GroovyScriptTool(dummyTools())).build().getToolCallbacks()[0];
        String escaped = script.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
        String json = "{\"script\":\"" + escaped + "\"}";
        String raw = groovyCb.call(json, new ToolContext(Map.of("session", "test")));
        return objectMapper.readTree(raw);
    }

    private String content(JsonNode result) {
        return result.path("content").asText();
    }

    @Test
    public void testToolsNames() throws Exception {
        JsonNode r = runScript("return tools.names");
        assertTrue(r.path("success").asBoolean());
        assertTrue(content(r).contains("echo"));
        assertTrue(content(r).contains("add"));
    }

    @Test
    public void testMapInvocation() throws Exception {
        JsonNode r = runScript("return tools.echo([message: 'hello'])");
        assertTrue(content(r).contains("hello"));
        assertTrue(content(r).contains("\"success\":true"));
    }

    @Test
    public void testPositionalInvocation() throws Exception {
        JsonNode r = runScript("return tools.add(3, 4)");
        assertTrue(content(r).contains("\"sum\":7"));
    }

    @Test
    public void testSingleValueWrap() throws Exception {
        JsonNode r = runScript("return tools.echo('hi')");
        assertTrue(content(r).contains("hi"));
    }

    @Test
    public void testOrchestration() throws Exception {
        JsonNode r = runScript("""
                def a = tools.add(1, 2)
                def b = tools.add(a.sum as int, 10)
                def msg = tools.echo([message: 'total=' + b.sum])
                return [step1: a.sum, step2: b.sum, echoed: msg.received]
                """);
        String c = content(r);
        assertTrue("content=" + c, c.contains("\"step1\":3"));
        assertTrue(c.contains("\"step2\":13"));
        assertTrue(c.contains("\"echoed\":\"total=13\""));
    }

    @Test
    public void testErrorOnUnknownTool() throws Exception {
        JsonNode r = runScript("return tools.nonexistent()");
        assertTrue(r.path("error").asText().contains("Groovy 脚本执行失败"));
    }
}
