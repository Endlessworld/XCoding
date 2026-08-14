package com.xr21.ai.agent.plugins;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Groovy 插件机制单元测试：验证目录包发现、plugin.json 校验、工具注册与闭包执行。
 */
public class GroovyPluginLoaderTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private static final String PLUGIN_JSON = """
            {
              "$schema": "https://agent-plugins.org/schemas/1.0.0/plugin.schema.json",
              "name": "my-tools",
              "version": "1.0.0",
              "description": "测试插件",
              "extensions": {
                "com.xr21.agent": {
                  "groovy": {
                    "entrypoints": ["./entry.groovy"]
                  }
                }
              }
            }
            """;

    private static final String ENTRY_GROOVY = """
            return [
                name: "my-tools",
                version: "1.0.0",
                tools: [
                    [
                        name: "weather",
                        description: "查询城市天气",
                        inputSchema: [type: "object",
                                      properties: [city: [type: "string", description: "城市名"]],
                                      required: ["city"]],
                        run: { Map args ->
                            def city = (args.city ?: "北京").toString()
                            return [temperature: 25, condition: "晴", city: city]
                        }
                    ]
                ],
                hooks: [
                    [name: "pluginStatus", position: "BEFORE_AGENT", run: { state, config -> [:] }]
                ],
                interceptors: [
                    [name: "pluginPassthrough", type: "model", apply: { request, handler -> handler.call(request) }]
                ]
            ]
            """;

    private Path createPluginPackage() throws Exception {
        Path pkg = tempFolder.newFolder(".agents", "plugins", "my-tools").toPath();
        Files.writeString(pkg.resolve("plugin.json"), PLUGIN_JSON);
        Files.writeString(pkg.resolve("entry.groovy"), ENTRY_GROOVY);
        return pkg;
    }

    @Test
    public void loadDirectoryPluginAndInvokeTool() throws Exception {
        createPluginPackage();
        GroovyPluginLoader.reload(List.of(), tempFolder.getRoot().getAbsolutePath());

        GroovyPlugin plugin = GroovyPluginRegistry.get().plugin("my-tools");
        assertNotNull("插件应已加载", plugin);
        assertEquals("1.0.0", plugin.getVersion());
        assertEquals(1, plugin.getTools().size());

        ToolCallback weather = GroovyPluginRegistry.get().toolCallbacks().stream()
                .filter(cb -> cb.getToolDefinition().name().equals("weather"))
                .findFirst()
                .orElse(null);
        assertNotNull("weather 工具应注册", weather);

        String raw = weather.call("{\"city\":\"上海\"}", new ToolContext(Map.of("session", "test")));
        assertTrue("返回应包含城市名: " + raw, raw.contains("上海"));
        assertTrue("返回应包含天气: " + raw, raw.contains("晴"));

        assertEquals("应注册 1 个 hook", 1, GroovyPluginRegistry.get().hooks().size());
        assertEquals("应注册 1 个拦截器", 1, GroovyPluginRegistry.get().interceptors().size());
        assertEquals("pluginStatus", GroovyPluginRegistry.get().hooks().get(0).getName());
        assertEquals("pluginPassthrough", GroovyPluginRegistry.get().interceptors().get(0).getName());
    }

    @Test
    public void rejectInvalidSchemaAndName() throws Exception {
        Path pkg = tempFolder.newFolder(".agents", "plugins", "bad-plugin").toPath();
        Files.writeString(pkg.resolve("plugin.json"), """
                {
                  "$schema": "https://agent-plugins.org/schemas/9.9.9/plugin.schema.json",
                  "name": "Bad-Plugin"
                }
                """);
        Files.writeString(pkg.resolve("entry.groovy"), "return [name: 'bad-plugin']");
        GroovyPluginLoader.reload(List.of(), tempFolder.getRoot().getAbsolutePath());

        assertTrue("非法 $schema 插件不应被注册", GroovyPluginRegistry.get().plugin("bad-plugin") == null);
    }
}
