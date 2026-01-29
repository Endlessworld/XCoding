package com.xr21.ai.agent.utils;

import com.alibaba.cloud.ai.graph.agent.hook.TokenCounter;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;

import java.util.List;

public class DefaultTokenCounter implements TokenCounter {
    private final TokenCountEstimator tokenCountEstimator = new JTokkitTokenCountEstimator();
    int DEFAULT_CHARS_PER_TOKEN = 4;

    @Override
    public int countTokens(List<Message> messages) {
        int total = 0;

        for (Message msg : messages) {
            if (msg instanceof ToolResponseMessage toolResponseMessage) {
                for (ToolResponseMessage.ToolResponse response : toolResponseMessage.getResponses()) {
                    if (response.responseData() != null) {
                        total += tokenCountEstimator.estimate(response.responseData());
                    }
                }
            } else if (msg instanceof AssistantMessage assistantMessage) {
                if (msg.getText() != null) {
                    total += tokenCountEstimator.estimate(msg.getText());
                }
                for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {
                    if (toolCall.arguments() != null) {
                        total += tokenCountEstimator.estimate(toolCall.arguments());
                    }
                }
            } else if (msg.getText() != null) {
                total += tokenCountEstimator.estimate(msg.getText());
            }
        }

        return total;

    }
}
