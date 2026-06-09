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

import com.xr21.ai.agent.tui.acp.ConnectionState;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 应用状态
 */
public class AppState {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    public final List<Session> sessions = new ArrayList<>();
    public final List<String> inputHistory = new ArrayList<>();
    public final List<TodoItem> todos = new ArrayList<>();
    public int currentSessionIndex = 0;
    public String inputBuffer = "";
    public int inputCursorPos = 0;
    public int inputHistoryIndex = -1;
    public int scrollOffset = 0;
    public int inputScrollOffset = 0;
    public PanelType focusPanel = PanelType.CENTER;
    public boolean isSessionListPopupVisible = false;
    public int sidebarSelectedIndex = 0;
    public ConnectionState connectionState = ConnectionState.DISCONNECTED;
    public String agentName = "ai-agent";
    public String agentVersion = "";
    public String modelName = "";
    public int totalSessions = 1;
    public TokenUsage tokenUsage = new TokenUsage();
    public boolean isStreaming = false;
    public String errorMessage = null;

    public AppState() {
        sessions.add(new Session());
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
     * 设置总 Token 用量
     */
    public void setTotalTokens(long totalTokens) {
        this.tokenUsage.totalTokens = totalTokens;
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

    public void sendMessage(String content) {
        if (content == null || content.isBlank()) return;
        currentSession().messages.add(new ChatMessage(MessageRole.USER, content));
        inputHistory.add(content);
        inputHistoryIndex = inputHistory.size();
        inputBuffer = "";
        inputCursorPos = 0;
        inputScrollOffset = 0;
        currentSession().updatedAt = LocalDateTime.now();
        isStreaming = true;
    }

    public void appendStreamingContent(String content) {
        List<ChatMessage> msgs = currentSession().messages;
        if (!msgs.isEmpty()) {
            ChatMessage last = msgs.get(msgs.size() - 1);
            if (last.isStreaming && last.role == MessageRole.ASSISTANT) {
                last.content += content;
                return;
            }
        }
        msgs.add(new ChatMessage(MessageRole.ASSISTANT, content, true));
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
                return;
            }
        }
        msgs.add(new ChatMessage(MessageRole.SYSTEM, "\uD83D\uDCAD " + content, true));
    }

    public void addToolCall(String toolName, String args) {
        finishStreaming();
        currentSession().messages.add(new ChatMessage(MessageRole.TOOL_CALL,
                "\uD83D\uDD27 " + toolName + "\n参数: " + args, true));
    }

    public void appendToolCallUpdate(String content) {
        List<ChatMessage> msgs = currentSession().messages;
        if (!msgs.isEmpty()) {
            ChatMessage last = msgs.get(msgs.size() - 1);
            if (last.role == MessageRole.TOOL_CALL && last.isStreaming) {
                last.content += content;
            }
        }
    }

    public void addToolResult(String content) {
        List<ChatMessage> msgs = currentSession().messages;
        if (!msgs.isEmpty()) {
            ChatMessage last = msgs.get(msgs.size() - 1);
            if (last.isStreaming) last.isStreaming = false;
        }
        String truncated = content.length() > 500 ? content.substring(0, 500) + "\n\n... (结果过长，已截断)" : content;
        currentSession().messages.add(new ChatMessage(MessageRole.TOOL_RESULT, "\uD83D\uDCCE " + truncated));
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
        scrollOffset = Math.max(0, scrollOffset - 1);
    }

    public void scrollDown() {
        scrollOffset++;
    }

    public void scrollPageUp() {
        scrollOffset = Math.max(0, scrollOffset - 20);
    }

    public void scrollPageDown() {
        scrollOffset += 20;
    }

    public void inputHistoryPrev() {
        if (inputHistory.isEmpty()) return;
        if (inputHistoryIndex > 0) {
            inputHistoryIndex--;
            inputBuffer = inputHistory.get(inputHistoryIndex);
            inputCursorPos = inputBuffer.length();
        }
    }

    public void inputHistoryNext() {
        if (inputHistoryIndex < inputHistory.size() - 1) {
            inputHistoryIndex++;
            inputBuffer = inputHistory.get(inputHistoryIndex);
            inputCursorPos = inputBuffer.length();
        } else {
            inputHistoryIndex = inputHistory.size();
            inputBuffer = "";
            inputCursorPos = 0;
        }
    }
}
