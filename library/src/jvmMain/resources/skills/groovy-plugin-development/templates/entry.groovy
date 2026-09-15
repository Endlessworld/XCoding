// <plugin-name> 插件入口：返回插件描述 Map（契约参考 doc/GROOVY_PLUGIN_DESIGN.md §4.1）
// 可用变量：tools、PLUGIN_ROOT、PLUGIN_DATA、cwd、conversation、pluginContext
// 本模板演示三类能力的完整写法：tools / hooks / interceptors + 生命周期

def pluginState = [initialized: false, counter: 0]

return [
    name: "<plugin-name>",
    description: "插件功能说明",
    version: "1.0.0",

    // ===== 生命周期（可选）=====
    // init：加载时执行一次，初始化状态/注册能力
    init: { ctx ->
        pluginState.initialized = true
        pluginState.counter = 0
        // 白名单注入宿主能力（仅以下 key 可用）：
        // ctx.inject('client') / ctx.inject('chatModel') / ctx.inject('conversation')
        // ctx.inject('workers') / ctx.inject('sharedCache')
        println "[<plugin-name>] init() executed"
    },
    // close：卸载/热重载时执行，释放资源（框架 try/finally 保证执行）
    close: { ->
        pluginState.initialized = false
        println "[<plugin-name>] close() executed"
    },

    // ===== 1) tools：注册工具，进模型工具集 =====
    // 模型按 description 决定是否调用，务必写清功能、参数与返回
    tools: [
        // 示例 A：无状态工具（含入参 schema）
        [
            name: "example_echo",
            description: "回显输入文本。演示工具注册与入参 schema",
            inputSchema: [type: "object",
                          properties: [text: [type: "string", description: "要回显的文本"]],
                          required: ["text"]],
            run: { Map args ->
                // args = 模型传入的入参 Map；可访问 pluginState / PLUGIN_ROOT / PLUGIN_DATA / tools
                pluginState.counter++
                return [echo: args.text, counter: pluginState.counter]
            }
        ],
        // 示例 B：读取插件状态（演示每插件状态跨调用保留 + PLUGIN_ROOT 使用）
        [
            name: "example_status",
            description: "读取插件运行状态（初始化标志、调用计数、插件根路径）",
            inputSchema: [type: "object", properties: [:], required: []],
            run: { Map args ->
                return [initialized: pluginState.initialized,
                        counter: pluginState.counter,
                        pluginRoot: PLUGIN_ROOT]
            }
        ]
    ],

    // ===== 2) hooks：监听/改写模型请求（在 agent 执行各阶段触发）=====
    // run 返回的 Map 合并进 agent 状态；返回空 Map = 不改写
    hooks: [
        [
            name: "pluginStatusHook",
            position: "BEFORE_AGENT",        // 位置枚举见宿主 Hook 定义（如 BEFORE_AGENT）
            run: { state, config ->
                // 演示：注入插件状态供 agent 上下文使用；不需要则返回 [:]
                return [pluginInitialized: pluginState.initialized]
            }
        ]
    ],

    // ===== 3) interceptors：拦截/改写模型调用（服务/状态/透传）=====
    // 顺序安全：插件拦截器只能追加（默认在内置之后），不能移除/重排内置安全与重试拦截器
    interceptors: [
        [
            name: "pluginPassthrough",
            type: "model",                   // 拦截器类型（如 model）
            apply: { request, handler ->
                // 透传示例：不改变模型调用；可在此记录日志或改写 request
                return handler.call(request)  // 必须调用 handler.call 放行，否则模型调用被截断
            }
        ]
    ]
]
