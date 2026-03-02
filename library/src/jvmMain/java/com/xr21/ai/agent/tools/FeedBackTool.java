package com.xr21.ai.agent.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

/**
 * 用户反馈工具，用于收集用户输入、确认操作或显示消息
 */
public class FeedBackTool {

    private static final Logger log = LoggerFactory.getLogger(FeedBackTool.class);
    private static final Scanner SCANNER = new Scanner(System.in);

    /**
     * 创建需要用户确认的请求
     */
    public static Map<String, Object> createConfirmationRequest(String prompt) {
        Map<String, Object> request = new HashMap<>();
        request.put("prompt", prompt);
        request.put("requireConfirmation", true);
        return request;
    }

    /**
     * 创建需要用户输入的请求
     */
    public static Map<String, Object> createInputRequest(String prompt) {
        Map<String, Object> request = new HashMap<>();
        request.put("prompt", prompt);
        request.put("requireInput", true);
        return request;
    }

    /**
     * 创建只显示消息的请求
     */
    public static Map<String, Object> createMessageRequest(String message) {
        Map<String, Object> request = new HashMap<>();
        request.put("message", message);
        return request;
    }

    // @formatter:off
    @Tool(name = "feedback", description = """
        FeedBackTool 本工具用于收集用户信息，支持多轮对话和用户确认。
        可以用于获取用户输入、确认操作或收集反馈。
        """)
    public Map<String, Object> feedback(
            @JsonProperty(value = "prompt")
            @JsonPropertyDescription("提示信息，向用户显示的消息")
            String prompt,
            @JsonProperty(value = "message")
            @JsonPropertyDescription("直接显示的消息，不需要用户输入")
            String message,
            @JsonProperty(value = "requireConfirmation")
            @JsonPropertyDescription("是否需要用户确认 (y/n)")
            Boolean requireConfirmation,
            @JsonProperty(value = "requireInput")
            @JsonPropertyDescription("是否需要用户输入文本")
            Boolean requireInput
    ) { // @formatter:on
        Map<String, Object> result = new HashMap<>();

        // 显示提示信息
        if (prompt != null && !prompt.isEmpty()) {
            log.info("\n[系统提示] {}", prompt);
        }

        // 如果只是显示消息
        if (message != null && !message.isEmpty()) {
            log.info("[消息] {}", message);
            result.put("message", "已显示消息给用户: " + message);
            return result;
        }

        // 如果需要用户确认
        if (Boolean.TRUE.equals(requireConfirmation)) {
            log.info("是否确认执行? (y/n): ");
            String confirmation = SCANNER.nextLine().trim().toLowerCase();
            if (!confirmation.equals("y") && !confirmation.equals("yes")) {
                result.put("message", "用户取消了操作");
                result.put("confirmed", false);
                return result;
            }
            result.put("confirmed", true);
        }

        // 如果需要用户输入
        if (Boolean.TRUE.equals(requireInput)) {
            log.info("请输入: ");
            String userInput = SCANNER.nextLine().trim();
            result.put("userInput", userInput);
            return result;
        }

        result.put("message", "操作完成");
        return result;
    }
}
