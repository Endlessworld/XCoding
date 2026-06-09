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

import dev.tamboui.style.Color;

/**
 * TUI 主题系统 - Tamboui 版本
 */
public class TuiTheme {
    public final Color borderNormal;
    public final Color borderFocused;
    public final Color panelTitle;
    public final Color panelTitleFocused;
    public final Color textPrimary;
    public final Color textSecondary;
    public final Color textMuted;
    public final Color accent;
    public final Color success;
    public final Color warning;
    public final Color error;
    public final Color info;
    public final Color userMessage;
    public final Color assistantMessage;
    public final Color systemMessage;
    public final Color toolMessage;
    public final Color errorMessage;
    public final Color statusBarText;
    public final Color statusConnected;
    public final Color statusConnecting;
    public final Color statusDisconnected;
    public final Color statusError;
    public final Color inputPrompt;
    public final Color inputText;
    public final Color selectedText;
    public final Color currentIndicator;
    public final Color scrollHint;
    public final Color keyHint;

    public TuiTheme(
            Color borderNormal, Color borderFocused,
            Color panelTitle, Color panelTitleFocused,
            Color textPrimary, Color textSecondary, Color textMuted,
            Color accent, Color success, Color warning, Color error, Color info,
            Color userMessage, Color assistantMessage, Color systemMessage, Color toolMessage, Color errorMessage,
            Color statusBarText, Color statusConnected, Color statusConnecting, Color statusDisconnected, Color statusError,
            Color inputPrompt, Color inputText,
            Color selectedText, Color currentIndicator,
            Color scrollHint, Color keyHint) {
        this.borderNormal = borderNormal;
        this.borderFocused = borderFocused;
        this.panelTitle = panelTitle;
        this.panelTitleFocused = panelTitleFocused;
        this.textPrimary = textPrimary;
        this.textSecondary = textSecondary;
        this.textMuted = textMuted;
        this.accent = accent;
        this.success = success;
        this.warning = warning;
        this.error = error;
        this.info = info;
        this.userMessage = userMessage;
        this.assistantMessage = assistantMessage;
        this.systemMessage = systemMessage;
        this.toolMessage = toolMessage;
        this.errorMessage = errorMessage;
        this.statusBarText = statusBarText;
        this.statusConnected = statusConnected;
        this.statusConnecting = statusConnecting;
        this.statusDisconnected = statusDisconnected;
        this.statusError = statusError;
        this.inputPrompt = inputPrompt;
        this.inputText = inputText;
        this.selectedText = selectedText;
        this.currentIndicator = currentIndicator;
        this.scrollHint = scrollHint;
        this.keyHint = keyHint;
    }

    public static TuiTheme modernDark() {
        return new TuiTheme(
                Color.GRAY, Color.LIGHT_CYAN,
                Color.BRIGHT_WHITE, Color.LIGHT_CYAN,
                Color.BRIGHT_WHITE, Color.WHITE, Color.GRAY,
                Color.LIGHT_CYAN, Color.LIGHT_GREEN, Color.LIGHT_YELLOW, Color.LIGHT_RED, Color.LIGHT_BLUE,
                Color.LIGHT_BLUE, Color.LIGHT_GREEN, Color.LIGHT_YELLOW, Color.LIGHT_MAGENTA, Color.LIGHT_RED,
                Color.GRAY, Color.LIGHT_GREEN, Color.LIGHT_YELLOW, Color.GRAY, Color.LIGHT_RED,
                Color.GRAY, Color.BRIGHT_WHITE,
                Color.LIGHT_CYAN, Color.LIGHT_GREEN,
                Color.LIGHT_CYAN, Color.GRAY
        );
    }
}
