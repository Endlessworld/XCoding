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
import dev.tamboui.style.Color;
import dev.tamboui.terminal.Frame;
import dev.tamboui.tfx.CellFilter;
import dev.tamboui.tfx.Fx;
import dev.tamboui.tfx.Interpolation;
import dev.tamboui.tfx.Motion;
import dev.tamboui.tfx.tui.TfxIntegration;
import dev.tamboui.toolkit.Toolkit;
import dev.tamboui.toolkit.app.ToolkitRunner;
import dev.tamboui.tui.EventHandler;
import dev.tamboui.tui.Renderer;
import dev.tamboui.tui.TuiConfig;
import dev.tamboui.tui.TuiRunner;
import dev.tamboui.tui.bindings.BindingSets;
import dev.tamboui.tui.error.RenderErrorHandlers;
import dev.tamboui.tui.event.*;
import jakarta.annotation.Nullable;
import lombok.Getter;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.ScheduledExecutorService;

import static dev.tamboui.tui.TuiConfig.*;

/**
 * Tamboui 版 TUI 应用主入口
 * <p>
 * 管理应用生命周期：初始化、运行、清理。
 * 实现 EventHandler 和 Renderer 与 Tamboui TuiRunner 集成。
 */
public class TambouiTuiApp implements EventHandler, Renderer {

    @Getter
    public final AppState appState = new AppState();
    private final ThemeManager themeManager = new ThemeManager();
    private InputPanelWidget inputPanel;
    private StatusBarWidget statusBar;
    private final TfxIntegration tfx = new TfxIntegration();
    public AcpBridge acpBridge;
    private ScheduledExecutorService scheduler;
    private ToolkitRunner runner;
    private boolean firstMessageAnimationAdded = false;

    public static void main(String[] args) throws Exception {
        TambouiTuiApp app = new TambouiTuiApp();
        // 这里可以通过反射或 ServiceLoader 注入 AcpBridge
        // 实际使用时由 Kotlin 桥接层调用 setAcpBridge
        app.start();
    }

    public void start() throws Exception {
        TuiConfig tuiConfig = new TuiConfig(true,                        // rawMode
                true,                        // alternateScreen
                true,                        // hideCursor
                true,                        // mouseCapture
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

        // 创建 Widget 实例并注册事件回调
        this.inputPanel = createInputPanel();
        this.statusBar = createStatusBar();

        try (ToolkitRunner toolkitRunner = ToolkitRunner.builder().config(tuiConfig).styleEngine(themeManager.styleEngine()).build()) {
            this.runner = toolkitRunner;
            // 启动状态栏定时刷新（每秒更新系统时间）
            runner.scheduleRepeating(this::forceRender, Duration.ofMillis(100));
            runner.runOnRenderThread(() -> {
                // 自动感知 OS 主题模式（夜间/白天）
                Boolean isDark = null;
                try {
                    isDark = OsThemeDetector.isDarkMode(tuiConfig.backend(), 500);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                if (isDark != null) {
                    appState.isDarkMode = isDark;
                } else {
                    appState.isDarkMode = false;
                }
                themeManager.setDarkMode(appState.isDarkMode);
            });
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
                            forceRender();
                        });
                    }

                    @Override
                    public void onDisconnected() {
                        runner.runOnRenderThread(() -> {
                            appState.connectionState = com.xr21.ai.agent.tui.acp.ConnectionState.DISCONNECTED;
                            forceRender();
                        });
                    }

                    @Override
                    public void onEvent(AcpEvent event) {
                        runner.runOnRenderThread(() -> {
                            event.apply(appState);
                            forceRender();
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runner.runOnRenderThread(() -> {
                            appState.connectionState = com.xr21.ai.agent.tui.acp.ConnectionState.DISCONNECTED_ERROR;
                            appState.errorMessage = message;
                            forceRender();
                        });
                    }
                });
            }

            // Add TFX logo animation effect for initial chat load
            Rect termArea = runner.tuiRunner().terminal().area();
            int chatW = (int) (termArea.width() * 0.75);
            int statusH = 1;
            int inputH = Math.min(5, termArea.height() / 5);
            int mainH = termArea.height() - statusH - inputH;
            Rect chatArea = new Rect(0, 0, chatW, mainH);
            tfx.addEffect(Fx.slideIn(Motion.LEFT_TO_RIGHT, 15, 0, Color.BLACK, 2500, Interpolation.SineInOut).withFilter(CellFilter.text()), chatArea);

            tfx.runWith(runner.tuiRunner(), this, this);
        } finally {
            cleanup();
        }
    }

    @Override
    public boolean handle(Event event, TuiRunner runner) {
        if (event instanceof KeyEvent key) {
            return handleKeyEvent(key);
        }
        if (event instanceof MouseEvent mouse) {
            return handleMouseEvent(mouse);
        }
        return false;
    }

    private boolean handleKeyEvent(KeyEvent key) {
        // 弹框可见时的按键拦截
        if (appState.isSessionListPopupVisible || appState.isHelpPopupVisible || appState.isModelPopupVisible) {
            if (key.code() == KeyCode.ESCAPE) {
                if (appState.isSessionListPopupVisible) {
                    appState.closeSessionListPopup();
                }
                if (appState.isHelpPopupVisible) {
                    appState.closeHelpPopup();
                }
                if (appState.isModelPopupVisible) {
                    appState.closeModelPopup();
                }
                return true;
            }
            // 模型选择弹框特有按键
            if (appState.isModelPopupVisible) {
                if (key.code() == KeyCode.UP) {
                    appState.modelSelectUp();
                    return true;
                } else if (key.code() == KeyCode.DOWN) {
                    appState.modelSelectDown();
                    return true;
                } else if (key.code() == KeyCode.ENTER) {
                    String modelId = appState.confirmModelSelection();
                    if (modelId != null && acpBridge != null) {
                        acpBridge.setModel(modelId);
                    }
                    return true;
                }
            }

            // 会话列表弹框特有按键
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
                }
            }
            return false;
        }

        // 上下文敏感按键（ChatPanel 滚动）
        if (key.code() == KeyCode.UP) {
            if (appState.focusPanel == PanelType.CENTER) appState.scrollUp();
            return true;
        } else if (key.code() == KeyCode.DOWN) {
            if (appState.focusPanel == PanelType.CENTER) appState.scrollDown();
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
            appState.autoScroll = true;
            return true;
        }

        // 委托 InputPanelWidget 处理输入按键
        if (inputPanel.handleKeyEvent(key)) {
            return true;
        }

        // 快捷键（全局）
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
                    firstMessageAnimationAdded = false;
                    return true;
                case 'w': // Ctrl+W: 关闭会话
                    appState.closeCurrentSession();
                    firstMessageAnimationAdded = false;
                    return true;
                case 'q': // Ctrl+Q: 退出
                    runner.quit();
                    return true;
                case 'p': // Ctrl+P: 会话列表
                    appState.toggleSessionListPopup();
                    return true;
                case 'l': // Ctrl+L: 帮助
                    appState.toggleHelpPopup();
                    return true;
                case 'k': // Ctrl+K: 清空对话
                    appState.clearConversation();
                    firstMessageAnimationAdded = false;
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

        // Space 在 ChatPanel 焦点时展开工具消息
        if (key.code() == KeyCode.CHAR && " ".equals(key.string()) && appState.focusPanel == PanelType.CENTER) {
            appState.toggleLastToolMessage();
            return true;
        }

        // Ctrl+H (ASCII 0x08) sent as CHAR by some terminals
        if (key.code() == KeyCode.CHAR && key.modifiers().ctrl() && key.string().length() == 1 && key.string().charAt(0) == '\b') {
            appState.toggleHelpPopup();
            return true;
        }

        return false;
    }


    /**
     * 处理鼠标事件。
     * <ul>
     *   <li>点击 ChatPanel 区域 → 聚焦 CENTER</li>
     *   <li>点击 InputPanel 区域 → 聚焦 INPUT</li>
     *   <li>点击状态栏交互区域 → 委托 StatusBarWidget 处理</li>
     * </ul>
     */
    private boolean handleMouseEvent(MouseEvent mouse) {
        if (mouse.kind() != MouseEventKind.RELEASE) return false;

        int mx = mouse.x();
        int my = mouse.y();

        // 获取当前布局尺寸
        Rect area = runner.tuiRunner().terminal().area();
        int width = area.width();
        int height = area.height();
        int statusHeight = 1;
        int inputHeight = Math.min(5, height / 5);
        int mainHeight = height - statusHeight - inputHeight;
        int chatWidth = (int) (width * 0.75);

        // 点击 ChatPanel 区域 (0,0) ~ (chatWidth, mainHeight)
        if (mx >= 0 && mx < chatWidth && my >= 0 && my < mainHeight) {
            appState.focusPanel = PanelType.CENTER;
            return true;
        }

        // 点击 InputPanel 区域 (0, mainHeight) ~ (width, mainHeight + inputHeight)
        if (mx >= 0 && mx < width && my >= mainHeight && my < mainHeight + inputHeight) {
            appState.focusPanel = PanelType.INPUT;
            inputPanel.setFocused(true);
            return true;
        }

        // 点击状态栏区域 → 委托 StatusBarWidget 处理
        if (my == mainHeight + inputHeight) {
            return statusBar.handleMouseClick(mx, my);
        }

        return false;
    }

    private void sendMessage() {
        String message = appState.inputState.text().trim();
        if (message.isEmpty()) return;

        // Add TFX animation on first message load
        if (!firstMessageAnimationAdded && appState.currentSession().messages.isEmpty()) {
            tfx.addEffect(Fx.slideIn(Motion.LEFT_TO_RIGHT, 10, 0, Color.BLACK, 1500, Interpolation.SineInOut).withFilter(CellFilter.text()));
            firstMessageAnimationAdded = true;
        }

        appState.autoScroll = true;
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
        int chatWidth = (int) (width * 0.75);
        int infoWidth = width - chatWidth;

        Rect chatArea = new Rect(0, 0, chatWidth, mainHeight);
        Rect infoArea = new Rect(chatWidth, 0, infoWidth, mainHeight);
        Rect inputArea = new Rect(0, mainHeight, width, inputHeight);
        Rect statusArea = new Rect(0, mainHeight + inputHeight, width, statusHeight);

        // 渲染各面板
        boolean chatFocused = appState.focusPanel == PanelType.CENTER;
        boolean inputFocused = appState.focusPanel == PanelType.INPUT;

        inputPanel.setFocused(inputFocused);

        frame.renderWidget(new ChatPanelWidget(appState, themeManager.currentTheme(), chatFocused), chatArea);
        frame.renderWidget(new InfoPanelWidget(appState, themeManager.currentTheme()), infoArea);
        frame.renderWidget(inputPanel, inputArea);
        frame.renderWidget(statusBar, statusArea);

        // 弹框覆盖
        if (appState.isSessionListPopupVisible) {
            int popupW = Math.min(40, width - 4);
            int popupH = Math.min(appState.sessionCount() + 4, height - 4);
            int popupX = (width - popupW) / 2;
            int popupY = (height - popupH) / 2;
            frame.renderWidget(new SessionListPopupWidget(appState, themeManager.currentTheme()), new Rect(popupX, popupY, popupW, popupH));
        }
        if (appState.isModelPopupVisible) {
            int popupW = Math.min(40, width - 4);
            int popupH = Math.min(appState.availableModels.size() + 4, height - 4);
            int popupX = (width - popupW) / 2;
            int popupY = (height - popupH) / 2;
            frame.renderWidget(new ModelSelectPopupWidget(appState, themeManager.currentTheme()), new Rect(popupX, popupY, popupW, popupH));
        }
        if (appState.isHelpPopupVisible) {
            int popupW = Math.min(50, width - 4);
            int popupH = Math.min(24, height - 4);
            int popupX = (width - popupW) / 2;
            int popupY = (height - popupH) / 2;
            frame.renderWidget(new HelpPopupWidget(appState, themeManager.currentTheme()), new Rect(popupX, popupY, popupW, popupH));
        }
    }


    /**
     * 强制触发界面刷新：通过 dispatch ResizeEvent 让事件循环调用 safeRender()
     * 因为 handle() 对 UiRunnable 返回 false，不会触发渲染，
     * 而 ResizeEvent 在事件循环中直接触发 safeRender()（不经过 handle()）。
     */
    private void forceRender() {
        if (runner != null && runner.isRunning()) {
            runner.tuiRunner().dispatch(ResizeEvent.of(runner.tuiRunner().terminal().area().width(), runner.tuiRunner().terminal().area().height()));
        }
    }

    // ========== Widget 工厂方法 ==========

    /**
     * 创建 InputPanelWidget 并注册事件回调。
     * 基于 ToolkitRunner 调研最佳实践：
     * - 应用层业务逻辑（发送消息、取消）→ 回调注册
     * - 元素固定逻辑（字符输入、光标移动）→ 内置 handleKeyEvent
     */
    private InputPanelWidget createInputPanel() {
        return new InputPanelWidget(appState, themeManager.currentTheme()).onSubmit(this::sendMessage).onCancel(() -> {
            if (appState.isStreaming) {
                appState.finishStreaming();
                if (acpBridge != null) acpBridge.cancel();
            }
        });
    }

    /**
     * 创建 StatusBarWidget 并注册事件回调。
     * 基于 ToolkitRunner 调研最佳实践：
     * - 鼠标点击区域（模型名称、主题切换）→ 回调注册
     * - 全局快捷键 → 回调注册
     */
    private StatusBarWidget createStatusBar() {
        return new StatusBarWidget(appState, themeManager.currentTheme()).onModelClick(() -> {
            appState.toggleModelPopup();
        }).onThemeToggle(() -> {
            appState.toggleTheme();
            themeManager.toggle();
        }).onGlobalShortcut(action -> {
            switch (action) {
                case "help" -> appState.toggleHelpPopup();
                case "quit" -> runner.quit();
                case "session-list" -> appState.toggleSessionListPopup();
                case "new-session" -> {
                    appState.newSession();
                    firstMessageAnimationAdded = false;
                }
                case "close-session" -> {
                    appState.closeCurrentSession();
                    firstMessageAnimationAdded = false;
                }
                case "clear-conversation" -> {
                    appState.clearConversation();
                    firstMessageAnimationAdded = false;
                }
                case "cancel-or-quit" -> {
                    if (appState.isStreaming) {
                        appState.finishStreaming();
                        if (acpBridge != null) acpBridge.cancel();
                    } else {
                        runner.quit();
                    }
                }
            }
        });
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

        void setModel(String modelId);

        void setMode(String modeId);

        void setConfigOption(String configId, String value);

        /**
         * 设置重连策略
         */
        default void setReconnectStrategy(Object strategy) {
        }

        /**
         * 启动重连
         */
        default void startReconnect() {
        }

        /**
         * 停止重连
         */
        default void stopReconnect() {
        }

        /**
         * 认证
         */
        default void authenticate(String provider, String token) {
        }

        /**
         * 登出
         */
        default void logout() {
        }

        /**
         * 关闭当前会话（不关闭连接）
         */
        default void closeSession() {
        }

        /**
         * 加载已存在的会话
         */
        default void loadSession(String sessionId) {
        }

        /**
         * 分支（fork）已有会话
         */
        default void forkSession(String sourceSessionId) {
        }

        /**
         * 恢复已存在的会话
         */
        default void resumeSession(String sessionId) {
        }

        /**
         * 切换当前活动会话
         */
        default void switchSession(String sessionId) {
        }

        /**
         * 按 ID 关闭指定会话
         */
        default void closeSessionById(String sessionId) {
        }

        /**
         * 获取活跃会话 ID 列表
         */
        default String[] getActiveSessionIds() {
            return new String[0];
        }

        /**
         * 获取当前活动会话 ID
         */
        @Nullable
        default String getActiveSessionId() {
            return null;
        }

        /**
         * 销毁客户端
         */
        default void destroy() {
        }
    }

    public interface ConnectionCallback {
        void onConnected(String agentName, String agentVersion, String modelName);

        void onDisconnected();

        void onEvent(AcpEvent event);

        void onError(String message);

        /**
         * 重连中
         */
        default void onReconnecting(int attempt, long delayMs) {
        }

        /**
         * 重连成功
         */
        default void onReconnected() {
        }
    }

    // ========== 静态入口 ==========

    public interface AcpEvent {
        void apply(AppState state);
    }
}
