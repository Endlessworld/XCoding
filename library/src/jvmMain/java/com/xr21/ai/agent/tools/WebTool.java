/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.xr21.ai.agent.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.xr21.ai.agent.utils.AcpNotifyHelper.sendProgress;

/**
 * 网络搜索工具 - 使用 Bing 免费搜索引擎（无需 API Key，国内可正常访问）
 *
 * @author Endless
 */
public class WebTool {

    private static final String SEARCH_URL = "https://www.bing.com/search";
    private static final Logger log = LoggerFactory.getLogger(WebTool.class);

    // @formatter:off
    @Tool(name = "web_search", description = "使用Bing搜索引擎检索网络信息,该工具返回摘要和网址 你需要搭配web_fetch进一步获取网页详情")
    public Map<String, Object> webSearch(
            @JsonProperty(value = "queryList", required = true)
            @JsonPropertyDescription("Search query List (required) Up to 5 queries")
            List<String> queryList,
            ToolContext toolContext,
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
        int resultCount = (count != null && count > 0) ? Math.min(count, 10) : 3;
        String timeRange = freshness != null ? freshness : "noLimit";

        sendProgress(toolContext, "🔎 Starting web search for %d query(ies)...<br/>".formatted(queryList.size()));

        List<Map<String, Object>> allResults = new ArrayList<>();
        int queryIndex = 0;
        for (String query : queryList) {
            queryIndex++;
            sendProgress(toolContext, "🔎 Searching (%d/%d): \"%s\"...<br/>".formatted(queryIndex, queryList.size(), query));
            try {
                List<Map<String, Object>> searchResults = bingSearch(query, resultCount, timeRange);
                allResults.addAll(searchResults);
                sendProgress(toolContext, "✅ Found %d results for \"%s\"<br/>".formatted(searchResults.size(), query));
                for (Map<String, Object> searchResult : searchResults) {
                    if (searchResult.get("name") instanceof String name) {
                        sendProgress(toolContext, "✅ " + StringUtils.abbreviate(name,10) + "<br/>");
                    }
                }

            } catch (Exception e) {
                log.error("Bing search failed for query: {}", query, e);
                sendProgress(toolContext, "❌ Search failed for \"%s\": %s<br/>".formatted(query, e.getMessage()));
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("title", "搜索失败");
                errorResult.put("error", e.getMessage());
                allResults.add(errorResult);
            }
        }

        log.info("WebSearch total results: {}", allResults.size());
        result.put("results", allResults);
        sendProgress(toolContext, "📊 Web search completed, total: %d results<br/>".formatted(allResults.size()));
        return result;
    }

    private List<Map<String, Object>> bingSearch(String query, int count, String timeRange) throws Exception {
        long startTime = System.currentTimeMillis();
        log.info("Bing searching: {} (count={}, timeRange={})", query, count, timeRange);

        // 构建搜索 URL 参数
        StringBuilder urlBuilder = new StringBuilder(SEARCH_URL)
                .append("?q=").append(URLEncoder.encode(query, StandardCharsets.UTF_8))
                .append("&count=").append(count);
        if (!"noLimit".equals(timeRange)) {
            urlBuilder.append("&qft=").append(convertTimeRange(timeRange));
        }
        String searchUrl = urlBuilder.toString();

        // 使用 Jsoup 发送 GET 请求
        Document doc = Jsoup.connect(searchUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .timeout(15000)
                .get();

        // 解析搜索结果
        List<Map<String, Object>> results = new ArrayList<>();
        Elements resultElements = doc.select("#b_results > li.b_algo");

        int maxResults = Math.min(count, resultElements.size());
        for (int i = 0; i < maxResults; i++) {
            Element resultEl = resultElements.get(i);
            Map<String, Object> item = new HashMap<>();

            // 标题和链接
            Element titleEl = resultEl.selectFirst("h2 a");
            if (titleEl != null) {
                item.put("name", titleEl.text());
                item.put("url", titleEl.attr("href"));
            }

            // 摘要
            Element snippetEl = resultEl.selectFirst(".b_caption p");
            if (snippetEl != null) {
                item.put("summary", snippetEl.text());
            }

            if (!item.isEmpty()) {
                results.add(item);
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("Bing search completed, results: {}, duration: {}ms", results.size(), duration);
        return results;
    }

    private String convertTimeRange(String freshness) {
        // Bing 时间筛选参数：qft 参数格式
        return switch (freshness) {
            case "oneDay" -> "interval=7";
            case "oneWeek" -> "interval=14";
            case "oneMonth" -> "interval=30";
            case "oneYear" -> "interval=365";
            default -> null;
        };
    }


    // @formatter:off
    @Tool(name = "web_fetch", description = """
        请求指定网页并返回清洗之后的网页 innerText 内容（最大 1000 字符）
        功能：抓取指定URL的网页内容，去除HTML标签、样式、脚本等，提取纯文本内容。

        使用场景：
        1. 获取实时天气、新闻等动态信息
        2. 查看网页正文内容
        3. 抓取 API 文档或帮助页面
        4. 获取搜索结果详情页内容
        """)
    public Map<String, Object> fetchWeb(
            @JsonProperty(value = "url", required = true)
            @JsonPropertyDescription("要抓取的网页 URL（必须是以 http:// 或 https:// 开头的完整 URL）")
            String url,
            ToolContext toolContext,
            @JsonProperty(value = "maxLength")
            @JsonPropertyDescription("返回内容的最大字符数（默认 1000，最大 5000）")
            Integer maxLength
    ) { // @formatter:on
        Map<String, Object> result = new HashMap<>();
        int maxLen = (maxLength != null && maxLength > 0) ? Math.min(maxLength, 5000) : 1000;

        try {
            log.info("Fetching web page: {} (maxLength={})", url, maxLen);
            sendProgress(toolContext, "🌐 Fetching web page: %s...<br/>".formatted(url));

            // 使用 Jsoup 发送 GET 请求获取网页
            Document doc = Jsoup.connect(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                            + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .timeout(15000)
                    .get();

            // 提取网页 title
            String title = doc.title();
            result.put("title", title);

            // 获取清洗后的纯文本内容（去除 HTML 标签、样式、脚本等）
            String text = doc.body().text();

            // 截取最大字符数
            String trimmedText = text.length() > maxLen ? "%s...".formatted(text.substring(0, maxLen)) : text;
            result.put("content", trimmedText);
            result.put("totalLength", text.length());
            result.put("returnedLength", trimmedText.length());
            result.put("url", url);

            log.info("Fetch completed: title='{}', totalChars={}, returnedChars={}",
                    title, text.length(), trimmedText.length());
            sendProgress(toolContext, "✅ Fetched: <a href=\"%s\">%s</a> (%d chars)<br/>".formatted(url, StringUtils.abbreviate(title, 10), trimmedText.length()));

        } catch (Exception e) {
            log.error("Failed to fetch web page: {}", url, e);
            sendProgress(toolContext, "❌ Failed to fetch: %s - %s<br/>".formatted(url, e.getMessage()));
            result.put("error", "抓取失败: %s".formatted(e.getMessage()));
            result.put("url", url);
        }

        return result;
    }
    // @formatter:on
}
