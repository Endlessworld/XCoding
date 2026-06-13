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
package com.xr21.ai.agent.tui;

import com.agentclientprotocol.model.AvailableCommand;
import com.xr21.ai.agent.tui.acp.ConnectionState;
import dev.tamboui.widgets.input.TextAreaState;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 应用状态
 */
public class AppState {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    public final List<Session> sessions = new ArrayList<>();
    public final List<String> inputHistory = new ArrayList<>();
    public final List<TodoItem> todos = new ArrayList<>();
    public int currentSessionIndex = 0;
    public final TextAreaState inputState = new TextAreaState();
    public int inputHistoryIndex = -1;
    public volatile int scrollOffset = 0;
    public volatile boolean autoScroll = true;
    private static final int MAX_SESSION_NAME_LEN = 20;
    public boolean isSessionListPopupVisible = false;
    public boolean isHelpPopupVisible = false;
    public int sidebarSelectedIndex = 0;
    public boolean isModelPopupVisible = false;
    public int modelSelectIndex = 0;
    public ConnectionState connectionState = ConnectionState.DISCONNECTED;
    public String agentName = "ai-agent";
    public String agentVersion = "";
    public String modelName = "";
    public int totalSessions = 1;
    public TokenUsage tokenUsage = new TokenUsage();
    public boolean isStreaming = false;
    public String errorMessage = null;
    private static final int MAX_SESSIONS = 50;
    public final List<ConfigOption> configOptions = new ArrayList<>();
    public final List<ModelInfo> availableModels = new ArrayList<>();
    public final List<ModeInfo> availableModes = new ArrayList<>();
    public final List<AvailableCommand> availableCommands = new ArrayList<>();
    public boolean isProviderPopupVisible = false;
    public int providerSelectIndex = 0;
    public final List<ProviderInfo> providers = new ArrayList<>();
    public String currentProviderId = "";
    /**
     * 最近一次 SessionUpdate 的 messageId（Unstable；当前仅记录不用于消息合并）。
     * 来源：SessionUpdate 各子类型中的 messageId 字段。
     */
    public String lastMessageId = "";
    /**
     * 最近一次 PromptResponse 中的 userMessageId（Unstable；与 lastMessageId 区分：后者来自 SessionUpdate）。
     * 来源：PromptResponse.userMessageId。
     */
    public String lastUserMessageId = "";
    /**
     * 最近一次未知 SessionUpdate 子类型名（兜底；用于日志/调试，不渲染到 UI）。
     * 来源：SessionUpdate.UnknownSessionUpdate.sessionUpdateType。
     */
    public String lastUnknownUpdateType = "";
    public PanelType focusPanel = PanelType.INPUT;

    public boolean isDarkMode = true;

    /**
     * 状态变更回调（由 TuiApp 设置，用于触发界面刷新）
     */
    public transient Runnable onStateChanged;

    public AppState() {
        sessions.add(new Session());
    }

    private void notifyStateChanged() {
        if (onStateChanged != null) {
            onStateChanged.run();
        }
    }

    /**
     * 添加 Todo 项（供 Kotlin 桥接层调用）
     */
    public void addTodo(String content, String statusName, String priorityName) {
        TodoStatus s = TodoStatus.valueOf(statusName);
        TodoPriority p = TodoPriority.valueOf(priorityName);
        todos.add(new TodoItem(content, s, p));
    }

    /**
     * 清空 Todo 列表
     */
    public void clearTodos() {
        todos.clear();
    }

    /**
     * 设置总 Token 用量（仅写 totalTokens；保留以兼容已有调用方）。
     * 推荐使用 {@link #setTokenUsage(TokenUsage)} 一站式写入全量字段。
     */
    public void setTotalTokens(long totalTokens) {
        this.tokenUsage.totalTokens = totalTokens;
    }

    /**
     * 一站式写入 Token 用量（来自 SDK Usage / UsageUpdate；Unstable 字段可能为 null/缺省）
     *
     * <p>写入字段：promptTokens、completionTokens、totalTokens、thoughtTokens、
     * cachedReadTokens、cachedWriteTokens、contextWindowSize、costUsd、costCurrency。
     *
     * @param usage SDK 返回的 TokenUsage；为 null 时不修改状态
     */
    public void setTokenUsage(TokenUsage usage) {
        if (usage == null) return;
        this.tokenUsage.promptTokens = usage.promptTokens;
        this.tokenUsage.completionTokens = usage.completionTokens;
        this.tokenUsage.totalTokens = usage.totalTokens;
        this.tokenUsage.thoughtTokens = usage.thoughtTokens;
        this.tokenUsage.cachedReadTokens = usage.cachedReadTokens;
        this.tokenUsage.cachedWriteTokens = usage.cachedWriteTokens;
        this.tokenUsage.contextWindowSize = usage.contextWindowSize;
        this.tokenUsage.costUsd = usage.costUsd;
        this.tokenUsage.costCurrency = usage.costCurrency == null ? "" : usage.costCurrency;
    }

    public void setStopReason(String stopReason) {
        this.stopReason = stopReason;
    }
    // ACP model/mode/config state
    public String stopReason = "";
    public String currentModelId = "";
    public String currentModeId = "";

    /**
     * 更新当前模型 ID
     */
    public void setCurrentModelId(String modelId) {
        this.currentModelId = modelId;
    }

    /**
     * 更新当前模式 ID
     */
    public void setCurrentModeId(String modeId) {
        this.currentModeId = modeId;
    }

    /**
     * 清空并设置配置选项
     */
    public void setConfigOptions(List<ConfigOption> options) {
        this.configOptions.clear();
        if (options != null) {
            this.configOptions.addAll(options);
        }
    }

    /**
     * 清空并设置可用命令列表（来自 SessionUpdate.AvailableCommandsUpdate）
     */
    public void setAvailableCommands(List<AvailableCommand> commands) {
        this.availableCommands.clear();
        if (commands != null) {
            this.availableCommands.addAll(commands);
        }
    }

    /**
     * 记录最近一次 SessionUpdate 的 messageId（Unstable 字段，仅记录）
     *
     * @param messageId SDK 返回的 MessageId；为 null 时记为空串
     */
    public void setLastMessageId(String messageId) {
        this.lastMessageId = messageId == null ? "" : messageId;
    }

    /**
     * 记录最近一次 PromptResponse 中的 userMessageId（Unstable 字段，仅记录）
     *
     * @param userMessageId SDK 返回的 MessageId；为 null 时记为空串
     */
    public void setLastUserMessageId(String userMessageId) {
        this.lastUserMessageId = userMessageId == null ? "" : userMessageId;
    }

    /**
     * 记录最近一次未知 SessionUpdate 子类型名（兜底；不渲染到 UI）
     */
    public void setLastUnknownUpdateType(String typeName) {
        this.lastUnknownUpdateType = typeName == null ? "" : typeName;
    }

    public Session currentSession() {
        return sessions.get(currentSessionIndex);
    }

    public int sessionCount() {
        return sessions.size();
    }

    public void focusNext() {
        focusPanel = switch (focusPanel) {
            case LEFT -> PanelType.CENTER;
            case CENTER -> PanelType.INPUT;
            case INPUT -> PanelType.CENTER;
        };
    }

    public void focusPrevious() {
        focusNext(); // same cycle for 3 panels
    }

    public void toggleSessionListPopup() {
        isSessionListPopupVisible = !isSessionListPopupVisible;
        if (isSessionListPopupVisible) {
            sidebarSelectedIndex = currentSessionIndex;
            focusPanel = PanelType.LEFT;
        }
    }

    public void closeSessionListPopup() {
        isSessionListPopupVisible = false;
    }

    public void toggleModelPopup() {
        isModelPopupVisible = !isModelPopupVisible;
        if (isModelPopupVisible) {
            modelSelectIndex = 0;
            for (int i = 0; i < availableModels.size(); i++) {
                if (availableModels.get(i).id.equals(currentModelId)) {
                    modelSelectIndex = i;
                    break;
                }
            }
        }
    }

    public void toggleProviderPopup() {
        isProviderPopupVisible = !isProviderPopupVisible;
        if (isProviderPopupVisible) {
            providerSelectIndex = 0;
        }
    }

    public void closeProviderPopup() {
        isProviderPopupVisible = false;
        notifyStateChanged();
    }

    /**
     * 清空并设置 Provider 列表
     */
    public void setProviders(List<ProviderInfo> providerList) {
        this.providers.clear();
        if (providerList != null) {
            this.providers.addAll(providerList);
        }
    }

    public void closeModelPopup() {
        isModelPopupVisible = false;
        notifyStateChanged();
    }

    public boolean isConfigPopupVisible = false;
    public ConfigOption currentConfigOption = null;
    public int configSelectIndex = 0;

    public void toggleConfigPopup(ConfigOption option) {
        if (isConfigPopupVisible && currentConfigOption == option) {
            isConfigPopupVisible = false;
            currentConfigOption = null;
        } else {
            isConfigPopupVisible = true;
            currentConfigOption = option;
            // 初始化选中索引
            if ("boolean".equals(option.type)) {
                configSelectIndex = Boolean.parseBoolean(option.currentValue) ? 0 : 1;
            } else if (option.options != null && !option.options.isEmpty()) {
                int idx = option.options.indexOf(option.currentValue);
                configSelectIndex = idx >= 0 ? idx : 0;
            } else {
                configSelectIndex = 0;
            }
        }
    }

    public void closeConfigPopup() {
        isConfigPopupVisible = false;
        currentConfigOption = null;
        configSelectIndex = 0;
        notifyStateChanged();
    }

    public void setConfigOptionValue(String configId, String value) {
        int index = -1;
        ConfigOption old = null;
        for (int i = 0; i < configOptions.size(); i++) {
            if (configOptions.get(i).id.equals(configId)) {
                index = i;
                old = configOptions.get(i);
                break;
            }
        }
        if (old != null) {
            configOptions.set(index, new ConfigOption(old.id, old.name, old.type, value, old.options));
        }
    }

    public void toggleHelpPopup() {
        isHelpPopupVisible = !isHelpPopupVisible;
    }

    public void closeHelpPopup() {
        isHelpPopupVisible = false;
    }


    /**
     * 切换 dark/light 主题模式
     */
    public void toggleTheme() {
        isDarkMode = !isDarkMode;
    }
    public void popupConfirmSelection() {
        if (sidebarSelectedIndex >= 0 && sidebarSelectedIndex < sessions.size()) {
            currentSessionIndex = sidebarSelectedIndex;
            scrollOffset = 0;
        }
        isSessionListPopupVisible = false;
    }

    public void selectUp() {
        if (sidebarSelectedIndex > 0) sidebarSelectedIndex--;
    }

    public void selectDown() {
        if (sidebarSelectedIndex < sessions.size() - 1) sidebarSelectedIndex++;
    }

    public void confirmSelection() {
        if (sidebarSelectedIndex >= 0 && sidebarSelectedIndex < sessions.size()) {
            currentSessionIndex = sidebarSelectedIndex;
            scrollOffset = 0;
        }
    }

    public void toggleLastToolMessage() {
        List<ChatMessage> msgs = currentSession().messages;
        for (int i = msgs.size() - 1; i >= 0; i--) {
            ChatMessage m = msgs.get(i);
            if (m.role == MessageRole.TOOL_CALL || m.role == MessageRole.TOOL_RESULT) {
                m.isExpanded = !m.isExpanded;
                break;
            }
        }
    }

    /**
     * 清空并设置可用模型列表
     */
    public void setAvailableModels(List<ModelInfo> models) {
        this.availableModels.clear();
        if (models != null) {
            this.availableModels.addAll(models);
        }
    }

    /**
     * 清空并设置可用模式列表
     */
    public void setAvailableModes(List<ModeInfo> modes) {
        this.availableModes.clear();
        if (modes != null) {
            this.availableModes.addAll(modes);
        }
    }

    public void sendMessage(String content) {
        if (content == null || content.isBlank()) return;
        currentSession().messages.add(new ChatMessage(MessageRole.USER, content));
        inputHistory.add(content);
        inputHistoryIndex = inputHistory.size();
        inputState.clear();
        currentSession().updatedAt = LocalDateTime.now();
        isStreaming = true;

        // Auto-name session on first user message
        Session session = currentSession();
        if ("New Session".equals(session.name)) {
            String trimmed = content.trim();
            if (trimmed.length() > MAX_SESSION_NAME_LEN) {
                session.name = trimmed.substring(0, MAX_SESSION_NAME_LEN) + "…";
            } else {
                session.name = trimmed;
            }
        }
    }

    public boolean canCreateNewSession() {
        return sessions.size() < MAX_SESSIONS;
    }

    public void appendStreamingContent(String content) {
        List<ChatMessage> msgs = currentSession().messages;
        if (!msgs.isEmpty()) {
            ChatMessage last = msgs.get(msgs.size() - 1);
            if (last.isStreaming && last.role == MessageRole.ASSISTANT) {
                last.content += content;
                scrollToBottom();
                return;
            }
        }
        msgs.add(new ChatMessage(MessageRole.ASSISTANT, content, true));
        scrollToBottom();
    }

    public void appendThoughtContent(String content) {
        List<ChatMessage> msgs = currentSession().messages;
        if (!msgs.isEmpty()) {
            ChatMessage last = msgs.get(msgs.size() - 1);
            if (last.role == MessageRole.ASSISTANT && last.isStreaming) {
                last.isStreaming = false;
            }
        }
        if (!msgs.isEmpty()) {
            ChatMessage last = msgs.get(msgs.size() - 1);
            if (last.role == MessageRole.SYSTEM && last.isStreaming) {
                last.content += content;
                scrollToBottom();
                return;
            }
        }
        msgs.add(new ChatMessage(MessageRole.SYSTEM, "\uD83D\uDCAD " + content, true));
        scrollToBottom();
    }

    public void addToolCall(String toolName, String args, String toolCallId) {
        finishStreaming();
        ChatMessage msg = new ChatMessage(MessageRole.TOOL_CALL,
                "\uD83D\uDD27 " + toolName, true);
        msg.toolCallId = toolCallId;
        msg.toolStatus = "IN_PROGRESS";
        msg.toolName = toolName;
        msg.toolInput = args;
        currentSession().messages.add(msg);
        scrollToBottom();
    }

    public void appendToolCallUpdate(String content, String toolCallId) {
        List<ChatMessage> msgs = currentSession().messages;
        for (int i = msgs.size() - 1; i >= 0; i--) {
            ChatMessage msg = msgs.get(i);
            if (msg.role == MessageRole.TOOL_CALL && toolCallId.equals(msg.toolCallId)) {
                if (msg.toolInput == null || msg.toolInput.isEmpty()) {
                    msg.toolInput = content;
                } else {
                    msg.toolInput += content;
                }
                scrollToBottom();
                return;
            }
        }
        // Fallback: append to last tool call if no match
        if (!msgs.isEmpty()) {
            ChatMessage last = msgs.get(msgs.size() - 1);
            if (last.role == MessageRole.TOOL_CALL && last.isStreaming) {
                last.toolInput = (last.toolInput == null ? "" : last.toolInput) + content;
                scrollToBottom();
            }
        }
    }

    public void updateToolCall(String toolCallId, String status, String output) {
        List<ChatMessage> msgs = currentSession().messages;
        for (int i = msgs.size() - 1; i >= 0; i--) {
            ChatMessage msg = msgs.get(i);
            if (toolCallId.equals(msg.toolCallId)) {
                msg.toolStatus = status;
                msg.isStreaming = false;
                if (output != null && !output.isEmpty()) {
                    msg.toolOutput = output;
                }
                scrollToBottom();
                return;
            }
        }
    }

    public void addToolResult(String content, String toolCallId) {
        List<ChatMessage> msgs = currentSession().messages;
        if (!msgs.isEmpty()) {
            ChatMessage last = msgs.get(msgs.size() - 1);
            if (last.isStreaming) last.isStreaming = false;
        }
        // Try to match existing tool call by toolCallId
        for (int i = msgs.size() - 1; i >= 0; i--) {
            ChatMessage msg = msgs.get(i);
            if (toolCallId.equals(msg.toolCallId)) {
                msg.toolStatus = "COMPLETED";
                msg.isStreaming = false;
                String truncated = content.length() > 500 ? content.substring(0, 500) + "\n\n... (结果过长，已截断)" : content;
                msg.toolOutput = truncated;
                scrollToBottom();
                return;
            }
        }
        // Fallback: add as standalone result
        String truncated = content.length() > 500 ? content.substring(0, 500) + "\n\n... (结果过长，已截断)" : content;
        ChatMessage result = new ChatMessage(MessageRole.TOOL_RESULT, "\uD83D\uDCCE " + truncated);
        result.toolCallId = toolCallId;
        result.toolStatus = "COMPLETED";
        currentSession().messages.add(result);
        scrollToBottom();
    }

    /**
     * 检查当前滚动位置是否在底部附近（3行以内）
     */
    public boolean isNearBottom(int totalHeight, int availableHeight) {
        int maxOffset = Math.max(0, totalHeight - availableHeight);
        int currentOffset = scrollOffset == Integer.MAX_VALUE ? maxOffset : scrollOffset;
        return currentOffset >= maxOffset - 3;
    }

    /**
     * 尝试恢复自动滚动：如果当前在底部附近则重新启用 autoScroll
     */
    public void tryRestoreAutoScroll(int totalHeight, int availableHeight) {
        if (!autoScroll && isNearBottom(totalHeight, availableHeight)) {
            autoScroll = true;
        }
    }

    public void scrollToBottom() {
        if (autoScroll) {
            scrollOffset = Integer.MAX_VALUE;
        }
    }

    public void finishStreaming() {
        List<ChatMessage> msgs = currentSession().messages;
        if (!msgs.isEmpty()) {
            ChatMessage last = msgs.get(msgs.size() - 1);
            if (last.isStreaming) last.isStreaming = false;
        }
        isStreaming = false;
    }

    public void newSession() {
        if (sessions.size() >= MAX_SESSIONS) return;
        sessions.add(new Session());
        currentSessionIndex = sessions.size() - 1;
        totalSessions++;
        scrollOffset = 0;
    }

    public void closeCurrentSession() {
        if (sessions.size() <= 1) {
            sessions.set(0, new Session());
            currentSessionIndex = 0;
        } else {
            sessions.remove(currentSessionIndex);
            if (currentSessionIndex >= sessions.size()) {
                currentSessionIndex = sessions.size() - 1;
            }
        }
        scrollOffset = 0;
    }

    public void clearConversation() {
        currentSession().messages.clear();
        scrollOffset = 0;
    }

    public void scrollUp() {
        autoScroll = false;
        scrollOffset = Math.max(0, scrollOffset - 1);
    }

    public void scrollDown() {
        autoScroll = false;
        scrollOffset++;
    }

    public void scrollPageUp() {
        autoScroll = false;
        scrollOffset = Math.max(0, scrollOffset - 20);
    }

    public void scrollPageDown() {
        autoScroll = false;
        scrollOffset += 20;
    }

    public void inputHistoryPrev() {
        if (inputHistory.isEmpty()) return;
        if (inputHistoryIndex > 0) {
            inputHistoryIndex--;
            inputState.setText(inputHistory.get(inputHistoryIndex));
        }
    }

    public void inputHistoryNext() {
        if (inputHistoryIndex < inputHistory.size() - 1) {
            inputHistoryIndex++;
            inputState.setText(inputHistory.get(inputHistoryIndex));
        } else {
            inputHistoryIndex = inputHistory.size();
            inputState.clear();
        }
    }
}
