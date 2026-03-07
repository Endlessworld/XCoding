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
package com.xr21.ai.agent.utils;

import com.alibaba.cloud.ai.graph.agent.hook.TokenCounter;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;

import java.util.List;

/**
 *
 * @author Endless
 */
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
