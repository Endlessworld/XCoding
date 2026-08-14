package com.xr21.ai.agent.plugins;

import groovy.lang.Closure;
import lombok.Builder;
import lombok.Data;

import java.nio.file.Path;
import java.util.List;

/**
 * 已加载插件的描述：清单元数据 + 三类能力（tools/hooks/interceptors）+ 生命周期（init/close）+ 来源信息。
 */
@Data
@Builder
public class GroovyPlugin {
    private final String name;
    private final String version;
    private final String description;
    /** 客户端扩展命名空间（默认 com.xr21.agent） */
    private final String namespace;
    /** 插件根目录（PLUGIN_ROOT） */
    private final Path root;
    /** 客户端管理的可写持久目录（PLUGIN_DATA） */
    private final Path dataDir;
    /** 是否 legacy 合成清单（松散 .groovy 文件） */
    private final boolean legacy;
    private final List<GroovyToolSpec> tools;
    private final List<GroovyHookSpec> hooks;
    private final List<GroovyInterceptorSpec> interceptors;
    /** 初始化闭包：加载时执行一次 init(PluginContext ctx)（设计文档 §5.7） */
    private final Closure<?> init;
    /** 清理闭包：卸载/热重载时执行 close()（设计文档 §5.7） */
    private final Closure<?> close;
}
