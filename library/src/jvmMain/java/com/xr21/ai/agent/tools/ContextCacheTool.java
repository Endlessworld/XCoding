package com.xr21.ai.agent.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 上下文缓存工具，用于存储和检索工具调用的参数和响应引用
 */
public class ContextCacheTool {

    private static final Logger log = LoggerFactory.getLogger(ContextCacheTool.class);

    /**
     * 使用ConcurrentHashMap保证线程安全
     */
    public static final ConcurrentHashMap<String, String> argumentsRef = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, String> responsesRef = new ConcurrentHashMap<>();

    /**
     * 最大缓存容量，防止内存溢出
     */
    private static final int MAX_CACHE_SIZE = 10000;

    /**
     * 缓存过期时间（毫秒），默认1小时
     */
    private static final long CACHE_EXPIRE_TIME = 60 * 60 * 1000L;

    /**
     * 使用双端队列记录访问顺序，支持LRU淘汰
     */
    private static final ConcurrentLinkedDeque<String> accessOrder = new ConcurrentLinkedDeque<>();

    /**
     * 记录每个条目的创建时间，用于过期清理
     */
    private static final ConcurrentHashMap<String, Long> creationTime = new ConcurrentHashMap<>();

    /**
     * 添加参数引用（线程安全）
     */
    public static void addArgumentsRef(String refId, String content) {
        if (!StringUtils.hasText(refId) || content == null) {
            log.warn("尝试添加无效的引用: refId={}, content={}", refId, content);
            return;
        }

        // 检查缓存容量
        if (argumentsRef.size() >= MAX_CACHE_SIZE) {
            evictOldEntries();
        }

        argumentsRef.put(refId, content);
        creationTime.put(refId, System.currentTimeMillis());
        accessOrder.addLast(refId);

        log.debug("添加参数引用 {}，当前缓存大小: {}", refId, argumentsRef.size());
    }

    /**
     * 添加响应引用（线程安全）
     */
    public static void addResponsesRef(String refId, String content) {
        if (!StringUtils.hasText(refId) || content == null) {
            log.warn("尝试添加无效的引用: refId={}, content={}", refId, content);
            return;
        }

        // 检查缓存容量
        if (responsesRef.size() >= MAX_CACHE_SIZE) {
            evictOldEntries();
        }

        responsesRef.put(refId, content);
        creationTime.put(refId, System.currentTimeMillis());
        accessOrder.addLast(refId);

        log.debug("添加响应引用 {}，当前缓存大小: {}", refId, responsesRef.size());
    }

    /**
     * 清理过期或最旧的条目（LRU策略）
     */
    private static void evictOldEntries() {
        int targetSize = (int) (MAX_CACHE_SIZE * 0.8); // 保留80%容量

        while (argumentsRef.size() + responsesRef.size() >= MAX_CACHE_SIZE && !accessOrder.isEmpty()) {
            String oldestRef = accessOrder.pollFirst();
            if (oldestRef != null) {
                // 从所有缓存中移除
                argumentsRef.remove(oldestRef);
                responsesRef.remove(oldestRef);
                creationTime.remove(oldestRef);
                log.debug("清理过期缓存: {}", oldestRef);
            }
        }

        // 清理过期条目（超过过期时间）
        long currentTime = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> iterator = creationTime.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (currentTime - entry.getValue() > CACHE_EXPIRE_TIME) {
                String refId = entry.getKey();
                argumentsRef.remove(refId);
                responsesRef.remove(refId);
                accessOrder.remove(refId);
                iterator.remove();
                log.debug("清理超时缓存: {}", refId);
            }
        }
    }

    // @formatter:off
    @Tool(name = "contextCacheTool", description = """
        指针数据读取器，上下文编辑器会将你超长的工具调用参数或工具调用执行结果转换成指针,
        指针地址格式：$ref+工具调用id，你可以在需要的时候重新根据指针地址重新获取具体内容
        """)
    public static Map<String, Object> retrieveRef(
        @ToolParam(description = "指针地址列表，指针格式：$ref+工具调用id，根据指针地址重新获取具体内容") List<String> refs
    ) { // @formatter:on
        // 输入验证
        if (refs == null || refs.isEmpty()) {
            log.warn("ContextCacheTool接收到空的请求");
            return Map.of("error", "错误：未提供有效的指针地址");
        }

        log.debug("ContextCacheTool收到请求: {}", refs);

        Map<String, Object> result = new HashMap<>();
        Set<String> processedRefs = new HashSet<>();

        for (String ref : refs) {
            // 跳过重复的ref
            if (!processedRefs.add(ref)) {
                continue;
            }

            if (!StringUtils.hasText(ref)) {
                result.put(ref, "错误：无效的指针地址");
                continue;
            }

            // 尝试从参数引用中获取
            String content = argumentsRef.get(ref);
            if (content == null) {
                // 如果参数引用中不存在，则从响应引用中获取
                content = responsesRef.get(ref);
            }

            if (content != null) {
                result.put(ref, content);
                // 更新访问时间
                creationTime.put(ref, System.currentTimeMillis());
                // 移动到队列尾部（LRU策略）
                accessOrder.remove(ref);
                accessOrder.addLast(ref);
            } else {
                result.put(ref, "错误：未找到对应的引用内容");
                log.debug("未找到引用: {}", ref);
            }
        }

        return result;
    }

    /**
     * 手动清理指定引用
     */
    public static void removeRef(String refId) {
        if (!StringUtils.hasText(refId)) {
            return;
        }

        argumentsRef.remove(refId);
        responsesRef.remove(refId);
        creationTime.remove(refId);
        accessOrder.remove(refId);
        log.info("手动清理引用: {}", refId);
    }

    /**
     * 清空所有缓存
     */
    public static void clearAll() {
        argumentsRef.clear();
        responsesRef.clear();
        creationTime.clear();
        accessOrder.clear();
        log.info("已清空所有上下文缓存");
    }

    /**
     * 获取缓存统计信息
     */
    public static Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("argumentsRefSize", argumentsRef.size());
        stats.put("responsesRefSize", responsesRef.size());
        stats.put("totalSize", argumentsRef.size() + responsesRef.size());
        stats.put("maxCacheSize", MAX_CACHE_SIZE);
        stats.put("cacheExpireTimeMs", CACHE_EXPIRE_TIME);
        return stats;
    }
}
