package com.xr21.ai.agent.plugins;

import com.alibaba.cloud.ai.graph.agent.hook.Hook;
import com.alibaba.cloud.ai.graph.agent.interceptor.Interceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 进程级常驻插件注册表（cordis registry + fiber 生命周期在 XAgent 的对应物）。
 * <p>
 * 持有已加载插件及其三类能力（工具/hook/interceptor），供 {@code LocalAgent} 装配链三处并入；
 * 额外登记宿主工具快照（供插件闭包 tools.xxx 编排）与内置/已注册工具名（去重）。
 * v0.3 登记插件来源（包路径/legacy）供审计与覆盖规则使用。
 */
@Slf4j
public final class GroovyPluginRegistry {

    private static final GroovyPluginRegistry INSTANCE = new GroovyPluginRegistry();

    private final Map<String, GroovyPlugin> plugins = new ConcurrentHashMap<>();
    private final Map<String, ToolCallback> toolCallbacks = new ConcurrentHashMap<>();
    private final List<Hook> hooks = new CopyOnWriteArrayList<>();
    private final List<Interceptor> interceptors = new CopyOnWriteArrayList<>();
    /** 宿主工具快照：注入插件闭包 bindings（tools.xxx 编排宿主工具） */
    private volatile List<ToolCallback> hostTools = List.of();
    /** 工具名去重：内置工具名 + 已注册插件工具名 */
    private final Set<String> reservedToolNames = ConcurrentHashMap.newKeySet();

    private GroovyPluginRegistry() {
    }

    public static GroovyPluginRegistry get() {
        return INSTANCE;
    }

    /** 登记宿主工具快照（须在插件注册前调用），并记入保留工具名。 */
    public void setHostTools(List<ToolCallback> tools) {
        this.hostTools = tools == null ? List.of() : List.copyOf(tools);
        for (ToolCallback cb : hostTools) {
            reservedToolNames.add(cb.getToolDefinition().name());
        }
        log.info("GroovyPluginRegistry: host tools snapshot set ({} tools)", hostTools.size());
    }

    public List<ToolCallback> hostTools() {
        return hostTools;
    }

    /** 清空全部注册（热重载/测试用）。 */
    public void reset() {
        plugins.clear();
        toolCallbacks.clear();
        hooks.clear();
        interceptors.clear();
        reservedToolNames.clear();
        hostTools = List.of();
        log.info("GroovyPluginRegistry: reset all registrations");
    }

    /** 登记插件及其三类能力。同名插件先卸载旧注册（项目级覆盖全局级）。 */
    public void register(GroovyPlugin plugin) {
        if (plugins.containsKey(plugin.getName())) {
            unregister(plugin.getName());
        }
        // 工具注册：与内置/已注册工具重名则拒绝该条并告警（失败隔离）。
        int toolCount = 0;
        if (plugin.getTools() != null) {
            for (GroovyToolSpec spec : plugin.getTools()) {
                if (reservedToolNames.contains(spec.getName()) || toolCallbacks.containsKey(spec.getName())) {
                    log.warn("GroovyPluginRegistry: tool '{}' from plugin '{}' conflicts with existing tool, skipped",
                            spec.getName(), plugin.getName());
                    continue;
                }
                ClosureToolCallback cb = new ClosureToolCallback(spec);
                toolCallbacks.put(spec.getName(), cb);
                reservedToolNames.add(spec.getName());
                toolCount++;
            }
        }
        // 钩子注册：同名拒绝该条。
        int hookCount = 0;
        if (plugin.getHooks() != null) {
            for (GroovyHookSpec spec : plugin.getHooks()) {
                if (hasHookName(spec.getName())) {
                    log.warn("GroovyPluginRegistry: hook '{}' from plugin '{}' conflicts with existing hook, skipped",
                            spec.getName(), plugin.getName());
                    continue;
                }
                try {
                    hooks.add(GroovyPluginHook.create(spec));
                    hookCount++;
                } catch (Exception e) {
                    log.warn("GroovyPluginRegistry: hook '{}' from plugin '{}' rejected: {}", spec.getName(), plugin.getName(), e.getMessage());
                }
            }
        }
        // 拦截器注册：同名拒绝该条。
        int interceptorCount = 0;
        if (plugin.getInterceptors() != null) {
            for (GroovyInterceptorSpec spec : plugin.getInterceptors()) {
                if (hasInterceptorName(spec.getName())) {
                    log.warn("GroovyPluginRegistry: interceptor '{}' from plugin '{}' conflicts with existing interceptor, skipped",
                            spec.getName(), plugin.getName());
                    continue;
                }
                try {
                    interceptors.add(GroovyPluginInterceptor.create(spec));
                    interceptorCount++;
                } catch (Exception e) {
                    log.warn("GroovyPluginRegistry: interceptor '{}' from plugin '{}' rejected: {}", spec.getName(), plugin.getName(), e.getMessage());
                }
            }
        }
        plugins.put(plugin.getName(), plugin);
        log.info("GroovyPluginRegistry: registered plugin '{}' v{} with {} tools, {} hooks, {} interceptors (root={}, legacy={})",
                plugin.getName(), plugin.getVersion(), toolCount, hookCount, interceptorCount, plugin.getRoot(), plugin.isLegacy());
    }

    /** 卸载插件：移除其工具/hook/interceptor 注册（阶段三热重载用）。 */
    public void unregister(String name) {
        GroovyPlugin plugin = plugins.remove(name);
        if (plugin == null) {
            return;
        }
        if (plugin.getTools() != null) {
            for (GroovyToolSpec spec : plugin.getTools()) {
                toolCallbacks.remove(spec.getName());
                reservedToolNames.remove(spec.getName());
            }
        }
        if (plugin.getHooks() != null) {
            for (GroovyHookSpec spec : plugin.getHooks()) {
                hooks.removeIf(h -> spec.getName().equals(h.getName()));
            }
        }
        if (plugin.getInterceptors() != null) {
            for (GroovyInterceptorSpec spec : plugin.getInterceptors()) {
                interceptors.removeIf(i -> spec.getName().equals(i.getName()));
            }
        }
        log.info("GroovyPluginRegistry: unregistered plugin '{}'", name);
    }

    public GroovyPlugin plugin(String name) {
        return plugins.get(name);
    }

    public Collection<GroovyPlugin> plugins() {
        return plugins.values();
    }

    public List<ToolCallback> toolCallbacks() {
        return List.copyOf(toolCallbacks.values());
    }

    public List<Hook> hooks() {
        return List.copyOf(hooks);
    }

    public List<Interceptor> interceptors() {
        return List.copyOf(interceptors);
    }

    private boolean hasHookName(String name) {
        return hooks.stream().anyMatch(h -> name.equals(h.getName()));
    }

    private boolean hasInterceptorName(String name) {
        return interceptors.stream().anyMatch(i -> name.equals(i.getName()));
    }
}
