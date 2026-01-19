package com.xr21.ai.agent.tools;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Stream;

public class WebSearchTool implements BiFunction<WebSearchTool.SearchRequest, ToolContext, List<Map<String, Object>>> {
    public static final String DESCRIPTION = "从搜索引擎检索网络信息";
    private static final String BASE_URL = "https://api.bochaai.com/v1/web-search";
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(WebSearchTool.class);

    public static ToolCallback createWebSearchToolCallback() {
        return FunctionToolCallback.builder("webSearch", new WebSearchTool())
                .description(DESCRIPTION)
                .inputType(SearchRequest.class)
                .build();
    }

    public static Map<String, Object> webSearch(Params searchParams) {
        long startTime = System.currentTimeMillis();
        log.info("WebSearch params {}", searchParams);
        var apiKey = System.getenv("SEARCH_BOCHA_KEY");
        try {
            // 从数据库获取配置信息 如果后面引入mybaits这里优化下
            log.info("baseUrl {},apiKey {}", BASE_URL, apiKey);
            if (!StringUtils.hasText(apiKey)) {
                return Map.of("msg", "apiKey 配置为空,请提醒用户配置检索apikey!");
            }
            RestClient restClient = RestClient.builder()
                    .baseUrl(BASE_URL)
                    .defaultHeader("Authorization", "Bearer " + apiKey)
                    .build();
            var body = restClient.post().body(searchParams).retrieve().body(Map.class);
            // todo 1、根据检索结果 重新爬取对应网页  2、文本清理、分段、向量化、入向量库 3、向量检索相关内容 4、返回
            log.info("WebSearch result {}", body);
            return body;
        } finally {
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            log.info("WebSearch completed, duration: {}ms", duration);
        }
    }

    public List<Map<String, Object>> apply(SearchRequest request, ToolContext toolContext) {
        List<Map<String, Object>> body = new ArrayList<>();
        for (String query : request.queryList) {
            var params = new Params(query, request.freshness, request.summary, request.count);
            body.add(webSearch(params));
        }
        List<String> filterKeys = List.of("summary", "data", "url", "webPages", "value", "name", "datePublished", "siteName", "siteIcon");
        var mapper = JsonMapper.builder().build();
        List<Map<String, Object>> data = body.stream()
                .map(mapper::valueToTree)
                .map(node -> ((JsonNode) node).path("data").path("webPages").path("value"))
                .filter(JsonNode::isArray)
                .flatMap(node -> Stream.of(mapper.convertValue(node, new TypeReference<List<Map<String, Object>>>() {
                })))
                .flatMap(List::stream)
                .map(e -> {
                    // 创建一个新的Map，只保留filterKeys中指定的键
                    Map<String, Object> filtered = new HashMap<>();
                    for (String key : filterKeys) {
                        if (e.containsKey(key)) {
                            filtered.put(key, e.get(key));
                        }
                    }
                    return filtered;
                })
                .toList();
        log.info("WebSearch result {}", data);
        return data;
    }

    public record Params(String query, String freshness, Boolean summary, Integer count) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SearchRequest(
            @JsonPropertyDescription("query: Search query List (required) Up to 5 queries") List<String> queryList,
            @JsonPropertyDescription("freshness: The time range for the search results. (Available options noLimit, oneYear, oneMonth, oneWeek, oneDay. Default is noLimit)") String freshness,
            @JsonPropertyDescription("summary: Whether to return a summary. default true") Boolean summary,
            @ToolParam(description = "count: Number of results (1-10, default 3)") Integer count) {
    }

}
