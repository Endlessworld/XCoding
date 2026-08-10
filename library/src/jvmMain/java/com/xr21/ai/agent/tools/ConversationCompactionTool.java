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

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.tools.ToolContextHelper;
import com.alibaba.cloud.ai.graph.state.ReplaceAllWith;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.xr21.ai.agent.entity.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;

import java.util.*;

/**
 * 会话压缩工具（纯工具实现），让智能体自主进行对话摘要与上下文管理。
 * <p>
 * 当上下文过大（token/消息数超阈值）、或智能体进入了错误的分支/探索了某条无法
 * 解决问题的路径时，可调用本工具回退到某个决策分叉点：把被截断/压缩掉的这段消息的
 * 摘要作为工具参数传入。本工具直接通过 {@link ToolContext} 获取工作流的
 * {@link OverAllState}，并将新的消息列表以 {@code ReplaceAllWith} 写回
 * {@code _AGENT_STATE_FOR_UPDATE_}，从而在下一个模型调用前真正替换对话记录。
 * <p>
 * 本工具调用记录（AssistantMessage 工具调用）位于原始消息列表末尾，随截断保留最近消息时
 * 一并作为正常对话记录保留；其返回值（ToolResponseMessage）因 {@code ReplaceAllWith} 会覆盖
 * 框架自动追加的记录，故由本工具在截断后手动补回，保证 AI 调用与响应配对完整。
 * 因此模型可自主进行多路径探索、忽略失败的探索过程、保留有意义的探索结果，并在超大上下文中持续对话且节省 token。
 *
 * @author Endless
 */
@Slf4j
public class ConversationCompactionTool {

    private static final String TOOL_NAME = "compact_conversation";
    private static final int DEFAULT_KEEP_LAST = 3;
    private static final int MAX_KEEP_LAST = 20;

    // @formatter:off
    @Tool(name = TOOL_NAME, description = """
            会话压缩/回退工具。当你判断当前上下文过大（token 或消息数超过阈值）、
            或者你进入了错误的分支、或者你探索了某条解决问题的路径但无法解决时，
            可调用本工具回退到某个决策分叉点，并主动丢弃/压缩掉此前的一批消息以节省上下文。

            Usage:
                - summary（必填）：对被压缩/丢弃掉的这段消息的摘要，将作为上下文保留下来，
                  使你在回退后仍不丢失关键信息（探索结论、失败原因、已确认事实、关键标识符等）。
                - keep_last（可选，默认 3）：回退后保留的最近消息条数（不含 system 前缀）。
                - checkpoint（可选）：对本决策分叉点的简要描述，说明当前正在放弃的分支。

            效果：
                - 本工具调用记录与返回结果会作为正常对话记录保留，不会消失。
                - 从下一个模型调用起，被压缩掉的历史消息将被截断，仅保留固定前缀（含系统提示词）
                  + 最近的 keep_last 条消息，从而显著节省 token。
                - 这样你可以放心地探索多种解决路径：失败的路径被压缩为摘要保留，再继续尝试新路径。
            """)
    public Map<String, Object> compactConversation(
            @JsonProperty(value = "summary", required = true)
            @JsonPropertyDescription("对被压缩/丢弃掉的这段消息的摘要，回退后作为上下文保留，需保留关键信息（结论、失败原因、已确认事实、关键标识符、路径等）")
            String summary,
            @JsonProperty(value = "keep_last")
            @JsonPropertyDescription("回退后保留的最近消息条数（不含 system 前缀），默认 3")
            Integer keepLast,
            @JsonProperty(value = "checkpoint")
            @JsonPropertyDescription("对本决策分叉点的简要描述，说明当前正在放弃的分支/路径")
            String checkpoint,
            ToolContext toolContext) { // @formatter:on

        if (summary == null || summary.isBlank()) {
            return ToolResult.builder()
                    .error("summary 不能为空：请提供对被压缩消息的摘要")
                    .build();
        }

        int keep = (keepLast == null || keepLast <= 0) ? DEFAULT_KEEP_LAST : Math.min(keepLast, MAX_KEEP_LAST);

        // 通过 ToolContext 获取工作流状态与待提交的状态更新 map。
        OverAllState state = ToolContextHelper.getState(toolContext).orElse(null);
        Map<String, Object> stateForUpdate = ToolContextHelper.getStateForUpdate(toolContext).orElse(null);
        if (state == null || stateForUpdate == null) {
            log.warn("compact_conversation: OverAllState/stateForUpdate not available in ToolContext");
            return ToolResult.builder()
                    .error("无法获取工作流状态，压缩指令未生效（当前运行环境不支持）")
                    .build();
        }

        @SuppressWarnings("unchecked")
        List<Message> messages = state.value("messages", List.class).orElse(List.of());
        if (messages.isEmpty()) {
            return ToolResult.builder()
                    .success(true)
                    .content("会话为空，无需压缩。")
                    .build();
        }

        // 构造新的消息列表：直接截断 = 固定前缀 + 最近 keep 条消息（含本次工具调用的 AssistantMessage）。
        List<Message> newMessages = buildCompactedMessages(messages, keep);

        // 通过 ReplaceAllWith 整体替换 messages，AppendStrategy 会识别该包装并整体替换。
        stateForUpdate.put("messages", ReplaceAllWith.of(newMessages));
        log.info("compact_conversation applied: {} messages -> {} (keepLast={}, checkpoint={})",
                messages.size(), newMessages.size(), keep, checkpoint);
        return ToolResult.builder()
                .success(true)
                .content("会话压缩已完成：历史消息已截断，仅保留最近 " + keep
                        + " 条消息（含本次工具调用）。被压缩的历史摘要为：\n" + summary)
                .metadata("keepLast", keep)
                .metadata("checkpoint", checkpoint == null ? "" : checkpoint)
                .metadata("messagesBefore", messages.size())
                .metadata("messagesAfter", newMessages.size())
                .build();
    }

    /**
     * 构建压缩后的消息列表：直接截断原始消息列表。
     * <ol>
     *   <li>固定前缀 = 开头到最后一个 SystemMessage（含真实系统提示词与旧摘要），原样保留以命中缓存。</li>
     *   <li>保留最近 {@code keep} 条消息（安全截止，不拆散 AI/工具调用配对），其中包含本次工具调用。</li>
     *   <li>手动补回本次 {@code compact_conversation} 调用的 ToolResponseMessage（因 ReplaceAllWith 会覆盖框架自动追加的响应）。</li>
     * </ol>
     */
    private List<Message> buildCompactedMessages(List<Message> messages, int keep) {
        int lastSystemIndex = -1;
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i) instanceof SystemMessage) {
                lastSystemIndex = i;
            }
        }
        // 直接截断原始消息列表：固定前缀（开头到最后一个 SystemMessage，含真实系统提示词与旧摘要，
        // 原样保留以命中提供商缓存）+ 最近 keep 条消息（安全截止，不拆散 AI/工具调用配对）。
        // 本次 compact_conversation 工具调用的 AssistantMessage 位于原始消息列表末尾，
        // 只要最近消息被保留，它即作为正常对话记录随截断一并保留下来。
        // 由于 ReplaceAllWith 会覆盖框架自动追加的 ToolResponseMessage，此处需手动补回本次调用响应。
        List<Message> newMessages = new ArrayList<>(messages.subList(0, lastSystemIndex + 1));
        newMessages.addAll(selectRecentKeep(messages, lastSystemIndex + 1, keep));
        newMessages.add(buildSelfResponseMessage(messages));
        return newMessages;
    }

    /**
     * 为本次 {@code compact_conversation} 工具调用构建对应的 ToolResponseMessage，
     * 使其作为正常对话记录被保留。本次调用位于原始消息列表末尾（最新一条 AssistantMessage），
     * 从后向前扫描以定位其 toolCallId。若找不到本次调用，则返回空响应。
     */
    private ToolResponseMessage buildSelfResponseMessage(List<Message> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message m = messages.get(i);
            if (m instanceof AssistantMessage am) {
                for (AssistantMessage.ToolCall tc : am.getToolCalls()) {
                    if (TOOL_NAME.equals(tc.name())) {
                        ToolResponseMessage.ToolResponse response =
                                new ToolResponseMessage.ToolResponse(tc.id(), tc.name(),
                                        "[会话压缩已生效] 历史消息已截断，仅保留固定前缀与最近若干条消息。");
                        return ToolResponseMessage.builder().responses(List.of(response)).build();
                    }
                }
            }
        }
        return ToolResponseMessage.builder().responses(List.of()).build();
    }

    /**
     * 从 startIndex（含）起的消息中，从后向前挑选最多 keep 条，保证不拆散 AI 消息与其工具响应。
     */
    private List<Message> selectRecentKeep(List<Message> messages, int startIndex, int keep) {
        List<Message> tail = new ArrayList<>(messages.subList(startIndex, messages.size()));
        if (tail.isEmpty()) {
            return List.of();
        }
        int targetCutoff = Math.max(0, tail.size() - keep);
        int cutoff = findSafeCutoff(tail, targetCutoff);
        return new ArrayList<>(tail.subList(cutoff, tail.size()));
    }

    /**
     * 从 targetCutoff 起向后搜索第一个安全切割点（优先保留最近消息）；找不到则返回 0。
     */
    private int findSafeCutoff(List<Message> tail, int targetCutoff) {
        for (int i = targetCutoff; i < tail.size(); i++) {
            if (isSafeCutoff(tail, i)) {
                return i;
            }
        }
        return 0;
    }

    /**
     * 检查在 tail 中从 index 处切断是否会拆散 AI 消息与其工具响应。
     */
    private boolean isSafeCutoff(List<Message> tail, int cutoffIndex) {
        if (cutoffIndex >= tail.size()) {
            return true;
        }
        Set<String> toolResponseIdsAfter = new HashSet<>();
        for (int i = cutoffIndex; i < tail.size(); i++) {
            if (tail.get(i) instanceof ToolResponseMessage trm) {
                for (ToolResponseMessage.ToolResponse r : trm.getResponses()) {
                    toolResponseIdsAfter.add(r.id());
                }
            }
        }
        for (int i = 0; i < cutoffIndex; i++) {
            Message m = tail.get(i);
            if (m instanceof AssistantMessage am && !am.getToolCalls().isEmpty()) {
                for (AssistantMessage.ToolCall tc : am.getToolCalls()) {
                    if (toolResponseIdsAfter.contains(tc.id())) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
