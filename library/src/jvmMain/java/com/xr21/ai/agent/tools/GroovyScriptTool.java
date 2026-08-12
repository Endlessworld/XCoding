package com.xr21.ai.agent.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.xr21.ai.agent.entity.ToolResult;
import com.xr21.ai.agent.utils.GroovyToolBindings;
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
    @Tool(name = "run_groovy_script", description = """
            groovy脚本引擎，内部注入了tools对象，tools包含了一套coding agent专属工具，
            可通过tools系列方法简化脚本编写，优先使用tools系列工具，无法满足的再使用自定义脚本
            tools中的可用工具是动态注入的 你需要先探索一下可用工具
            【重要·返回值类型】tools.xxx(...) 的返回值都是【已解析的 Java 对象】，不是 JSON 字符串：
            - 能解析为 JSON 的工具返回 LinkedHashMap/List/基本类型；否则原样返回字符串。
            - 切勿再对返回值调用 JsonSlurper.parseText(...) / objectMapper.readTree(...) 二次解析（会抛 MissingMethodException）。
            - worker 返回 Map：{success, worker_type, result_type, content 或 filePath}，直接用 .content / .success 访问。
            - content 字段类型不确定（可能 String/Integer/Boolean/Map/List）。统一安全转换：
                数字：Integer.parseInt(String.valueOf(r.content))
                布尔：boolean b = (r.content as boolean)   // 兼容 Boolean 与字符串 "true"/"false"
                文本：String.valueOf(r.content)
            - 工具失败时返回 {success:false, error:...}，用 r.success==false 判断，不依赖抛异常。
            用法：
            - 查看可用工具: tools.names 返回工具名称列表
            - 查看工具信息: tools.inspect('read_file') 返回 工具名称/描述/入参schema JSON
            - 调用工具传参：
                - Map命名参数 : tools.read_file([filePaths: ['/a.txt']])
                - 位置参数(按工具 schema 属性顺序): tools.read_file(['/a.txt'], 0, 100)
                - 单参数工具: tools.Sleep([seconds: 3]) 或 tools.Sleep(3)
            - 探测类是否可加载/调用: tools.canLoad('java.lang.String')
              返回 Map：{className, success, classLoader, isInterface, isAbstract, instantiable, hasStaticCallable}。
              可加载则 success=true；否则返回 {success:false, error:...}。用于在脚本中动态判断某类在当前环境是否可见、可实例化、可调用静态方法。
            - tools 调用的每个工具失败时会返回 {success:false, error:...} 而不是抛出异常，便于脚本内继续编排处理。
            - 输出捕获：脚本内所有 显式 return / println(...) 的输出都会被返回
            # 工具调用编排（多工具 / 子智能体动态编排）
                在本工具中可通过 tools.xxx(...) 同时调用多个工具，并用 Groovy 语法（变量、循环、
                条件、List/Map 运算）将它们组合成复杂工作流。tools.worker 用于启动隔离子智能体，
                可像编排 graph 工作流一样进行并发、分支、级联、循环批量编排。

            ① 并发编排（并行启动多个 worker，各自返回后取结果）
                def r1 = tools.worker([worker_type:'worker', task_id:'t1', title:'任务1',
                    description:'处理任务1', result_type:'text'])
                def r2 = tools.worker([worker_type:'worker', task_id:'t2', title:'任务2',
                    description:'处理任务2', result_type:'text'])
                println(r1.content); println(r2.content)

            ② 分支判断编排（依赖 worker 返回值做条件分支）
                // 必须指定 result_type='boolean'；content 可能是 Boolean 或字符串
                def r = tools.worker([worker_type:'worker', task_id:'t3', title:'判断',
                    description:'返回 true 或 false', result_type:'boolean'])
                boolean ok = (r.content as boolean)   // 统一用 as boolean 安全转换
                if (ok) { println('分支A: 满足条件') } else { println('分支B: 不满足') }

            ③ 级联编排（worker A 的输出作为 worker B 的输入，形成流水线）
                def rA = tools.worker([worker_type:'worker', task_id:'A', title:'产出数据',
                    description:'返回文件列表', result_type:'json'])
                def target = (rA.content as List)[0]   // content 已是解析后的对象
                def rB = tools.worker([worker_type:'worker', task_id:'B', title:'消费数据',
                    description:'基于 '+target+' 继续分析', result_type:'text'])
                println(rB.content)

            ④ 循环批量编排（遍历多个目标，每个启动一个 worker 并收集结果）
                def results = [:]
                ['/a','/b','/c'].eachWithIndex { f, i ->
                    def rr = tools.worker([worker_type:'worker', task_id:'loop-'+(i+1),
                        title:'处理'+f, description:'分析 '+f, result_type:'text'])
                    results[f] = (rr.success ? String.valueOf(rr.content) : 'FAILED')
                }
                println(results)

            # 注意事项
                - 返回值类型：tools.xxx(...) 返回【已解析的 Java 对象】而非 JSON 字符串，切勿二次
                  解析（JsonSlurper/objectMapper.readTree 会抛 MissingMethodException）。
                - worker 返回 Map {success, worker_type, result_type, content 或 filePath}，直接
                  .content/.success 访问；content 类型不确定，用安全转换：数字 parseInt、
                  布尔 as boolean、文本 String.valueOf。
                - 工具失败返回 {success:false, error:...}，用 r.success==false 判断，不依赖抛异常。
                - 脚本默认 60s 超时自动终止，避免死循环/无限递归；长任务可传 timeout_seconds 调大。
                - 输出上限 200_000 字符，避免无限 println 耗尽内存。
                - println(...) 输出进 content；显式 return 的值进 returnValue 字段。
                - cwd 为脚本工作目录，可通过绑定变量 cwd 访问。

            基础示例
                def r = tools.read_file([filePaths: ['/a.txt']])
                println(r)
                tools.write_todos([entries: [
                    [content: '步骤1', status: 'IN_PROGRESS', priority: 'HIGH']
                ]])
            """)
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
