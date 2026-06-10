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
import dev.tamboui.tui.EventHandler;
import dev.tamboui.tui.Renderer;
import dev.tamboui.tui.TuiConfig;
import dev.tamboui.tui.TuiRunner;
import dev.tamboui.tui.bindings.BindingSets;
import dev.tamboui.tui.error.RenderErrorHandlers;
import dev.tamboui.tui.event.*;

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
    private TuiTheme theme = TuiTheme.modernDark();
    private final StatusBarWidget statusBar = new StatusBarWidget(appState, theme);
    private final TfxIntegration tfx = new TfxIntegration();
    private TuiRunner runner;
    private ScheduledExecutorService scheduler;
    private volatile boolean needsRender = true;
    private AcpBridge acpBridge;
    private boolean firstMessageAnimationAdded = false;

    public static void main(String[] args) throws Exception {
        TambouiTuiApp app = new TambouiTuiApp();
        // 这里可以通过反射或 ServiceLoader 注入 AcpBridge
        // 实际使用时由 Kotlin 桥接层调用 setAcpBridge
        app.start();
    }

    public void setAcpBridge(AcpBridge bridge) {
        this.acpBridge = bridge;
    }


    /**
     * 设置主题模式。
     *
     * @param isDark true = 暗色模式, false = 亮色模式
     */
    public void setThemeMode(boolean isDark) {
        this.theme = isDark ? TuiTheme.modernDark() : TuiTheme.modernLight();
    }

    public AppState getAppState() {
        return appState;
    }

    public void start() throws Exception {
        TuiConfig tuiConfig = new TuiConfig(
                true,                        // rawMode
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
            Rect termArea = tui.terminal().area();
            int chatW = (int) (termArea.width() * 0.75);
            int statusH = 1;
            int inputH = Math.min(5, termArea.height() / 5);
            int mainH = termArea.height() - statusH - inputH;
            Rect chatArea = new Rect(0, 0, chatW, mainH);
            tfx.addEffect(
                    Fx.slideIn(Motion.LEFT_TO_RIGHT, 15, 0, Color.BLACK, 2500, Interpolation.SineInOut)
                            .withFilter(CellFilter.text()),
                    chatArea
            );

            tfx.runWith(tui, this, this);
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
        if (appState.isSessionListPopupVisible || appState.isHelpPopupVisible) {
            if (key.code() == KeyCode.ESCAPE) {
                if (appState.isSessionListPopupVisible) {
                    appState.closeSessionListPopup();
                }
                if (appState.isHelpPopupVisible) {
                    appState.closeHelpPopup();
                }
                return true;
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
            appState.autoScroll = true;
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
                case 'l': // Ctrl+/: 帮助
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

        // ESC 已在弹框拦截器中处理（见上方弹框按键拦截逻辑）
        // 此处不再重复处理

        if (key.code() == KeyCode.ENTER) {
            if (appState.focusPanel == PanelType.INPUT && !appState.inputBuffer.isBlank())  {
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
            String s = key.string();
            // Enter 发送消息（某些终端将 Enter 发送为 CHAR + \r 而非 ENTER keycode）
            if (("\n".equals(s) || "\r".equals(s)) && !key.modifiers().alt() && !key.modifiers().ctrl()) {
                if (appState.focusPanel == PanelType.INPUT && !appState.inputBuffer.isBlank()) {
                    sendMessage();
                    return true;
                }
                return false;
            }
            // Ctrl+H (ASCII 0x08) sent as CHAR by some terminals
            if (key.modifiers().ctrl() && s.length() == 1 && s.charAt(0) == '\b') {
                appState.toggleHelpPopup();
                return true;
            }
            // Alt+Enter 插入换行
            if (key.modifiers().alt() && "\n".equals(s)) {
                int pos = appState.inputCursorPos;
                appState.inputBuffer = appState.inputBuffer.substring(0, pos) + "\n" + appState.inputBuffer.substring(pos);
                appState.inputCursorPos = pos + 1;
                return true;
            }
            // Space 在 ChatPanel 焦点时展开工具消息
            if (" ".equals(s) && appState.focusPanel == PanelType.CENTER) {
                appState.toggleLastToolMessage();
                return true;
            }
            // 普通字符输入
            if (!s.isEmpty() && !key.modifiers().ctrl() && !key.modifiers().alt()) {
                int pos = appState.inputCursorPos;
                appState.inputBuffer = appState.inputBuffer.substring(0, pos) + s + appState.inputBuffer.substring(pos);
                appState.inputCursorPos = pos + s.length();
                return true;
            }
        }

        return false;
    }


    /**
     * 处理鼠标事件。
     * <ul>
     *   <li>点击 ChatPanel 区域 → 聚焦 CENTER</li>
     *   <li>点击 InputPanel 区域 → 聚焦 INPUT</li>
     *   <li>点击状态栏主题图标 → 切换 dark/light 主题</li>
     * </ul>
     */
    private boolean handleMouseEvent(MouseEvent mouse) {
        if (mouse.kind() != MouseEventKind.RELEASE) return false;

        int mx = mouse.x();
        int my = mouse.y();

        // 获取当前布局尺寸
        Rect area = runner.terminal().area();
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
            return true;
        }

        // 点击状态栏区域 - 右下角主题切换
        if (my == mainHeight + inputHeight) {
            // 主题图标在右下角，点击切换
            // 主题图标区域：右侧约 10 个字符宽度
            if (mx >= width - 10) {
                appState.toggleTheme();
                setThemeMode(appState.isDarkMode);
                return true;
            }
        }

        return false;
    }
    private void sendMessage() {
        String message = appState.inputBuffer.trim();
        if (message.isEmpty()) return;

        // Add TFX animation on first message load
        if (!firstMessageAnimationAdded && appState.currentSession().messages.isEmpty()) {
            tfx.addEffect(
                    Fx.slideIn(Motion.LEFT_TO_RIGHT, 10, 0, Color.BLACK, 1500, Interpolation.SineInOut)
                            .withFilter(CellFilter.text())
            );
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
        if (appState.isHelpPopupVisible) {
            int popupW = Math.min(50, width - 4);
            int popupH = Math.min(24, height - 4);
            int popupX = (width - popupW) / 2;
            int popupY = (height - popupH) / 2;
            frame.renderWidget(new HelpPopupWidget(appState, theme), new Rect(popupX, popupY, popupW, popupH));
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

    /**
     * 强制触发界面刷新：通过 dispatch ResizeEvent 让事件循环调用 safeRender()
     * 因为 handle() 对 UiRunnable 返回 false，不会触发渲染，
     * 而 ResizeEvent 在事件循环中直接触发 safeRender()（不经过 handle()）。
     */
    private void forceRender() {
        if (runner != null && runner.isRunning()) {
            runner.dispatch(ResizeEvent.of(
                runner.terminal().area().width(),
                runner.terminal().area().height()
            ));
        }
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
