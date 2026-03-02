package com.xr21.ai.agent.tools;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 网络搜索工具
 */
public class WebSearchTool {

    private static final String BASE_URL = "https://api.bochaai.com/v1/web-search";
    private static final Logger log = LoggerFactory.getLogger(WebSearchTool.class);

    // @formatter:off
    @Tool(name = "webSearch", description = "从搜索引擎检索网络信息")
    public Map<String, Object> webSearch(
            @JsonProperty(value = "queryList", required = true)
            @JsonPropertyDescription("Search query List (required) Up to 5 queries")
            List<String> queryList,
            @JsonProperty(value = "freshness")
            @JsonPropertyDescription("The time range for the search results. (Available options noLimit, oneYear, oneMonth, oneWeek, oneDay. Default is noLimit)")
            String freshness,
            @JsonProperty(value = "summary")
            @JsonPropertyDescription("Whether to return a summary. default true")
            Boolean summary,
            @JsonProperty(value = "count")
            @JsonPropertyDescription("Number of results (1-10, default 3)")
            Integer count
    ) { // @formatter:on
        Map<String, Object> result = new HashMap<>();

        List<Map<String, Object>> body = new ArrayList<>();
        for (String query : queryList) {
            var params = new Params(query, freshness, summary, count);
            body.add(webSearchApi(params));
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
        result.put("results", data);
        return result;
    }

    private Map<String, Object> webSearchApi(Params searchParams) {
        long startTime = System.currentTimeMillis();
        log.info("WebSearch params {}", searchParams);
        var apiKey = System.getenv("SEARCH_BOCHA_KEY");
        try {
            // 从数据库获取配置信息 如果后面引入mybaits这里优化下
            log.info("baseUrl {},apiKey {}", BASE_URL, apiKey);
            if (!StringUtils.hasText(apiKey)) {
                return Map.of("error", "apiKey 配置为空,请提醒用户配置检索apikey!");
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

    public record Params(String query, String freshness, Boolean summary, Integer count) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SearchRequest(
            @JsonProperty(value = "queryList", required = true)
            @JsonPropertyDescription("queryList: Search query List (required) Up to 5 queries") List<String> queryList,
            @JsonProperty(value = "freshness")
            @JsonPropertyDescription("freshness: The time range for the search results. (Available options noLimit, oneYear, oneMonth, oneWeek, oneDay. Default is noLimit)") String freshness,
            @JsonProperty(value = "summary")
            @JsonPropertyDescription("summary: Whether to return a summary. default true") Boolean summary,
            @JsonProperty(value = "count")
            @JsonPropertyDescription("count: Number of results (1-10, default 3)") Integer count) {
    }
}
