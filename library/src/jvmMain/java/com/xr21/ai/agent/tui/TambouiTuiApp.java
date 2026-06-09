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

import com.xr21.ai.agent.tui.layout.*;
import dev.tamboui.backend.jline3.JLineBackend;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.EventHandler;
import dev.tamboui.tui.Renderer;
import dev.tamboui.tui.TuiConfig;
import dev.tamboui.tui.TuiRunner;
import dev.tamboui.tui.bindings.BindingSets;
import dev.tamboui.tui.error.RenderErrorHandlers;
import dev.tamboui.tui.event.Event;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static dev.tamboui.tui.TuiConfig.*;

/**
 * Tamboui 版 TUI 应用主入口
 * <p>
 * 管理应用生命周期：初始化、运行、清理。
 * 实现 EventHandler 和 Renderer 与 Tamboui TuiRunner 集成。
 */
public class TambouiTuiApp implements EventHandler, Renderer {

    private final AppState appState = new AppState();
    private final TuiTheme theme = TuiTheme.modernDark();
    private final StatusBarWidget statusBar = new StatusBarWidget(appState, theme);
    private TuiRunner runner;
    private ScheduledExecutorService scheduler;
    private volatile boolean needsRender = true;
    private AcpBridge acpBridge;

    public static void main(String[] args) throws Exception {
        TambouiTuiApp app = new TambouiTuiApp();
        // 这里可以通过反射或 ServiceLoader 注入 AcpBridge
        // 实际使用时由 Kotlin 桥接层调用 setAcpBridge
        app.start();
    }

    public void setAcpBridge(AcpBridge bridge) {
        this.acpBridge = bridge;
    }

    public AppState getAppState() {
        return appState;
    }

    public void start() throws Exception {
        TuiConfig tuiConfig = new TuiConfig(
                true,                        // rawMode
                true,                        // alternateScreen
                true,                        // hideCursor
                false,                       // mouseCapture
                false,                       // bracketedPaste
                Duration.ofMillis(DEFAULT_POLL_TIMEOUT),      // pollTimeout
                Duration.ofMillis(DEFAULT_TICK_TIMEOUT),      // tickRate
                Duration.ofMillis(DEFAULT_RESIZE_GRACE_PERIOD),  // resizeGracePeriod
                true,                        // shutdownHook
                BindingSets.defaults(),      // bindings
                RenderErrorHandlers.displayAndQuit(),  // errorHandler
                System.err,                  // errorOutput
                false,                       // fpsOverlayEnabled
                Collections.emptyList(),     // postRenderProcesssssors
                new JLineBackend(),                          // backend (allows for lazy backend creation)
                null                         // scheduler
        );
        try (var tui = TuiRunner.create(tuiConfig)) {
            this.runner = tui;
            // 启动状态栏定时刷新（每秒更新系统时间）
            startStatusBarTimer();

            // 连接 ACP Agent
            if (acpBridge != null) {
                acpBridge.connect(new String[0], new ConnectionCallback() {
                    @Override
                    public void onConnected(String agentName, String agentVersion, String modelName) {
                        runner.runOnRenderThread(() -> {
                            appState.connectionState = com.xr21.ai.agent.tui.acp.ConnectionState.CONNECTED;
                            appState.agentName = agentName;
                            appState.agentVersion = agentVersion;
                            appState.modelName = modelName;
                            requestRender();
                        });
                    }

                    @Override
                    public void onDisconnected() {
                        runner.runOnRenderThread(() -> {
                            appState.connectionState = com.xr21.ai.agent.tui.acp.ConnectionState.DISCONNECTED;
                            requestRender();
                        });
                    }

                    @Override
                    public void onEvent(AcpEvent event) {
                        runner.runOnRenderThread(() -> {
                            event.apply(appState);
                            requestRender();
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runner.runOnRenderThread(() -> {
                            appState.connectionState = com.xr21.ai.agent.tui.acp.ConnectionState.DISCONNECTED_ERROR;
                            appState.errorMessage = message;
                            requestRender();
                        });
                    }
                });
            }

            tui.run(this, this);
        } finally {
            cleanup();
        }
    }

    @Override
    public boolean handle(Event event, TuiRunner runner) {
        if (event instanceof KeyEvent key) {
            return handleKeyEvent(key);
        }
        return false;
    }

    private boolean handleKeyEvent(KeyEvent key) {
        // 会话列表弹框可见时的按键拦截
        if (appState.isSessionListPopupVisible) {
            if (key.code() == KeyCode.UP) {
                appState.selectUp();
                return true;
            } else if (key.code() == KeyCode.DOWN) {
                appState.selectDown();
                return true;
            } else if (key.code() == KeyCode.ENTER) {
                appState.popupConfirmSelection();
                return true;
            } else if (key.code() == KeyCode.ESCAPE) {
                appState.closeSessionListPopup();
                return true;
            }
            return false;
        }

        // 上下文敏感按键
        if (key.code() == KeyCode.UP) {
            if (appState.focusPanel == PanelType.CENTER) appState.scrollUp();
            else if (appState.focusPanel == PanelType.INPUT) appState.inputHistoryPrev();
            return true;
        } else if (key.code() == KeyCode.DOWN) {
            if (appState.focusPanel == PanelType.CENTER) appState.scrollDown();
            else if (appState.focusPanel == PanelType.INPUT) appState.inputHistoryNext();
            return true;
        } else if (key.code() == KeyCode.PAGE_UP) {
            appState.scrollPageUp();
            return true;
        } else if (key.code() == KeyCode.PAGE_DOWN) {
            appState.scrollPageDown();
            return true;
        } else if (key.code() == KeyCode.HOME) {
            appState.scrollOffset = 0;
            return true;
        } else if (key.code() == KeyCode.END) {
            appState.scrollOffset = Integer.MAX_VALUE;
            return true;
        } else if (key.code() == KeyCode.LEFT) {
            if (appState.inputCursorPos > 0) appState.inputCursorPos--;
            return true;
        } else if (key.code() == KeyCode.RIGHT) {
            if (appState.inputCursorPos < appState.inputBuffer.length()) appState.inputCursorPos++;
            return true;
        }

        // 快捷键
        if (key.code() == KeyCode.CHAR && key.modifiers().ctrl()) {
            char c = Character.toLowerCase(key.string().charAt(0));
            switch (c) {
                case 'c': // Ctrl+C: 取消/退出
                    if (appState.isStreaming) {
                        appState.finishStreaming();
                        if (acpBridge != null) acpBridge.cancel();
                    } else {
                        runner.quit();
                    }
                    return true;
                case 'n': // Ctrl+N: 新会话
                    appState.newSession();
                    return true;
                case 'w': // Ctrl+W: 关闭会话
                    appState.closeCurrentSession();
                    return true;
                case 'q': // Ctrl+Q: 退出
                    runner.quit();
                    return true;
                case 'p': // Ctrl+P: 会话列表
                    appState.toggleSessionListPopup();
                    return true;
                case 'k': // Ctrl+K: 清空对话
                    appState.clearConversation();
                    return true;
            }
        }

        if (key.code() == KeyCode.TAB) {
            if (key.modifiers().shift()) {
                appState.focusPrevious();
            } else {
                appState.focusNext();
            }
            return true;
        }

        if (key.code() == KeyCode.ESCAPE) {
            if (appState.isSessionListPopupVisible) {
                appState.closeSessionListPopup();
                return true;
            }
            return false;
        }

        if (key.code() == KeyCode.ENTER) {
            if (appState.focusPanel == PanelType.INPUT && !appState.inputBuffer.isBlank()) {
                sendMessage();
                return true;
            }
            return false;
        }

        if (key.code() == KeyCode.BACKSPACE) {
            if (!appState.inputBuffer.isEmpty() && appState.inputCursorPos > 0) {
                int pos = appState.inputCursorPos - 1;
                appState.inputBuffer = appState.inputBuffer.substring(0, pos) + appState.inputBuffer.substring(pos + 1);
                appState.inputCursorPos = pos;
            }
            return true;
        }

        if (key.code() == KeyCode.DELETE) {
            if (appState.inputCursorPos < appState.inputBuffer.length()) {
                appState.inputBuffer = appState.inputBuffer.substring(0, appState.inputCursorPos) + appState.inputBuffer.substring(appState.inputCursorPos + 1);
            }
            return true;
        }

        if (key.code() == KeyCode.CHAR) {
            // Alt+Enter 插入换行
            if (key.modifiers().alt() && key.string().equals("\n")) {
                int pos = appState.inputCursorPos;
                appState.inputBuffer = appState.inputBuffer.substring(0, pos) + "\n" + appState.inputBuffer.substring(pos);
                appState.inputCursorPos = pos + 1;
                return true;
            }
            // Space 在 ChatPanel 焦点时展开工具消息
            if (key.string().equals(" ") && appState.focusPanel == PanelType.CENTER) {
                appState.toggleLastToolMessage();
                return true;
            }
            // 普通字符输入
            String s = key.string();
            if (!s.isEmpty() && !key.modifiers().ctrl() && !key.modifiers().alt()) {
                int pos = appState.inputCursorPos;
                appState.inputBuffer = appState.inputBuffer.substring(0, pos) + s + appState.inputBuffer.substring(pos);
                appState.inputCursorPos = pos + s.length();
                return true;
            }
        }

        return false;
    }

    private void sendMessage() {
        String message = appState.inputBuffer.trim();
        if (message.isEmpty()) return;
        appState.sendMessage(message);
        if (acpBridge != null) {
            acpBridge.sendMessage(message);
        }
    }

    @Override
    public void render(Frame frame) {
        Rect area = frame.area();
        int width = area.width();
        int height = area.height();

        int statusHeight = 1;
        int inputHeight = Math.min(5, height / 5);
        int mainHeight = height - statusHeight - inputHeight;

        if (mainHeight < 3 || inputHeight < 2) return;

        // 主区域两栏布局
        int chatWidth = (int) (width * 0.65);
        int infoWidth = width - chatWidth;

        Rect chatArea = new Rect(0, 0, chatWidth, mainHeight);
        Rect infoArea = new Rect(chatWidth, 0, infoWidth, mainHeight);
        Rect inputArea = new Rect(0, mainHeight, width, inputHeight);
        Rect statusArea = new Rect(0, mainHeight + inputHeight, width, statusHeight);

        // 渲染各面板
        boolean chatFocused = appState.focusPanel == PanelType.CENTER;
        boolean inputFocused = appState.focusPanel == PanelType.INPUT;

        frame.renderWidget(new ChatPanelWidget(appState, theme, chatFocused), chatArea);
        frame.renderWidget(new InfoPanelWidget(appState, theme), infoArea);
        frame.renderWidget(new InputPanelWidget(appState, theme, inputFocused), inputArea);
        frame.renderWidget(statusBar, statusArea);

        // 弹框覆盖
        if (appState.isSessionListPopupVisible) {
            int popupW = Math.min(40, width - 4);
            int popupH = Math.min(appState.sessionCount() + 4, height - 4);
            int popupX = (width - popupW) / 2;
            int popupY = (height - popupH) / 2;
            frame.renderWidget(new SessionListPopupWidget(appState, theme), new Rect(popupX, popupY, popupW, popupH));
        }
    }

    private void startStatusBarTimer() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "tui-statusbar-timer");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(() -> {
            if (runner != null && runner.isRunning()) {
                runner.runOnRenderThread(this::requestRender);
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void requestRender() {
        needsRender = true;
    }

    private void cleanup() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        if (acpBridge != null) {
            acpBridge.disconnect();
        }
    }

    /**
     * ACP 事件回调接口（由 Kotlin 桥接层注入）
     */
    public interface AcpBridge {
        void connect(String[] args, ConnectionCallback callback);

        void sendMessage(String message);

        void cancel();

        void disconnect();
    }

    public interface ConnectionCallback {
        void onConnected(String agentName, String agentVersion, String modelName);

        void onDisconnected();

        void onEvent(AcpEvent event);

        void onError(String message);
    }

    // ========== 静态入口 ==========

    public interface AcpEvent {
        void apply(AppState state);
    }
}
