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
import dev.tamboui.tfx.tui.TfxIntegration;
import dev.tamboui.toolkit.app.ToolkitRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.TuiConfig;
import dev.tamboui.tui.bindings.BindingSets;
import dev.tamboui.tui.error.RenderErrorHandlers;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import dev.tamboui.tui.event.ResizeEvent;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Collections;

import static dev.tamboui.toolkit.Toolkit.*;
import static dev.tamboui.tui.TuiConfig.*;

/**
 * Tamboui 版 TUI 应用主入口
 * <p>
 * 使用 ToolkitRunner.create() + Lambda DSL 方式启动。
 * 管理应用生命周期：初始化、运行、清理。
 */
@Slf4j
public class TuiApp {

    public final AppState appState = new AppState();
    private final AcpBridge acpBridge = new TambouiAcpBridge(appState);
    private final ThemeManager themeManager = new ThemeManager();
    private ToolkitRunner runner;
    private final TfxIntegration tfx = new TfxIntegration();
    private boolean firstMessageAnimationAdded = false;

    public static void main(String[] args) throws Exception {
        TuiApp app = new TuiApp();
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
                new FixedJLineBackend(),                          // backend (allows for lazy backend creation)
                null                         // scheduler
        );


        try (ToolkitRunner toolkitRunner = ToolkitRunner.builder().config(tuiConfig).styleEngine(themeManager.styleEngine()).build()) {
            this.runner = toolkitRunner;
            // 设置状态变更回调，触发界面刷新
            appState.onStateChanged = this::forceRender;
            // 启动状态栏定时刷新（每秒更新系统时间）
            runner.scheduleRepeating(this::forceRender, Duration.ofMillis(100));
            runner.tuiRunner().terminal().backend().enableMouseCapture();
            runner.tuiRunner().terminal().backend().enableBracketedPaste();
            runner.tuiRunner().terminal().backend().enableRawMode();
            if (runner.tuiRunner().terminal().backend() instanceof JLineBackend jLineBackend) {
                log.info("jlineTerminal attributes: {}", jLineBackend.jlineTerminal().getAttributes());
            }

//            runner.runOnRenderThread(() -> {
//                // 自动感知 OS 主题模式（夜间/白天）
//                Boolean isDark = null;
//                try {
//                    isDark = OsThemeDetector.isDarkMode(tuiConfig.backend(), 500);
//                } catch (IOException e) {
//                    throw new RuntimeException(e);
//                }
//                if (isDark != null) {
//                    appState.isDarkMode = isDark;
//                } else {
//                    appState.isDarkMode = false;
//                }
//                themeManager.setDarkMode(appState.isDarkMode);
            // 注册全局事件处理器（键盘 + 鼠标）
            runner.eventRouter().addGlobalHandler(event -> {
                if (event instanceof KeyEvent key) {
                    return handleGlobalKeyEvent(key);
                }
                if (event instanceof MouseEvent mouse) {
                    return handleGlobalMouseEvent(mouse);
                }
                return EventResult.UNHANDLED;
            });
            // 连接 ACP Agent（在 runner.run() 之前启动，connect 是异步的）
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

            // 运行 DSL 应用（阻塞，直到 TUI 退出）
            runner.run(this::buildElementTree);

            // Add TFX logo animation effect for initial chat load
//            Rect termArea = runner.tuiRunner().terminal().area();
//            int chatW = (int) (termArea.width() * 0.75);
//            int statusH = 1;
//            int inputH = Math.min(5, termArea.height() / 5);
//            int mainH = termArea.height() - statusH - inputH;
//            Rect chatArea = new Rect(0, 0, chatW, mainH);
//            tfx.addEffect(Fx.slideIn(Motion.LEFT_TO_RIGHT, 15, 0, Color.BLACK, 2500, Interpolation.SineInOut).withFilter(CellFilter.text()), chatArea);
//
//            tfx.runWith(runner.tuiRunner(), this, this);
        } finally {
            cleanup();
        }
    }

    private Element buildElementTree() {
        boolean chatFocused = appState.focusPanel == PanelType.CENTER;
        // 构建输入面板
        Element inputElement = new InputPanelElement(appState, themeManager.currentTheme())
                .onSubmit(this::sendMessage)
                .onCancel(() -> {
                    if (appState.isStreaming) {
                        appState.finishStreaming();
                        acpBridge.cancel();
                    }
                })
                .build();

        // 构建状态栏
        Element statusElement = new StatusBarElement(appState, themeManager.currentTheme())
                .onModelClick(() -> {
                    log.debug("statusElement onModelClick");
                    appState.toggleModelPopup();
                    runner.focusManager().setFocus("model-select-popup");
                })
                .onThemeToggle(() -> {
                    appState.toggleTheme();
                    themeManager.toggle();
                })
                .onConfigChange((configId, value) -> {
                    if (acpBridge != null) {
                        acpBridge.setConfigOption(configId, value);
                    }
                })
                .onConfigClick(option -> {
                    log.debug("statusElement onConfigClick: {}", option.name);
                    appState.toggleConfigPopup(option);
                    if (appState.isConfigPopupVisible) {
                        runner.focusManager().setFocus("config-select-popup");
                    }
                })
                .build();

        // 主布局：上下结构
        Element mainLayout = column(
                // 上部分：左右两栏
                row(
                        new ChatPanelElement(appState, themeManager.currentTheme(), chatFocused).build(),
                        new InfoPanelElement(appState, themeManager.currentTheme()).build()
                ).fill(),
                // 中间：输入面板
                inputElement,
                // 底部：状态栏
                statusElement
        );

        // 弹框覆盖
        if (appState.isSessionListPopupVisible) {
            mainLayout = stack(
                    mainLayout,
                    new SessionListPopupElement(appState, themeManager.currentTheme()).build()
            );
        }
        if (appState.isModelPopupVisible) {
            mainLayout = stack(
                    mainLayout,
                    new ModelSelectPopupElement(appState)
                            .onModelConfirm(() -> {
                                String modelId = appState.currentModelId;
                                if (!modelId.isEmpty() && acpBridge != null) {
                                    acpBridge.setModel(modelId);
                                }
                            })
                            .build()
            );
        }
        if (appState.isConfigPopupVisible && appState.currentConfigOption != null) {
            mainLayout = stack(
                    mainLayout,
                    new ConfigSelectPopupElement(appState, appState.currentConfigOption)
                            .onConfigConfirm(() -> {
                                ConfigOption opt = appState.currentConfigOption;
                                if (opt == null) {
                                    log.warn("onConfigConfirm: currentConfigOption is null, skipping");
                                    return;
                                }
                                String configId = opt.id;
                                String newValue = opt.currentValue;
                                if (acpBridge != null) {
                                    acpBridge.setConfigOption(configId, newValue);
                                }
                            })
                            .build()
            );
        }
        if (appState.isHelpPopupVisible) {
            mainLayout = stack(
                    mainLayout,
                    new HelpPopupElement(appState, themeManager.currentTheme()).build()
            );
        }

        // 弹框可见时自动聚焦到弹框（buildElementTree 在渲染线程中执行，可直接调用 setFocus）
        if (runner != null) {
            if (appState.isModelPopupVisible) {
                runner.focusManager().setFocus("model-select-content");
            } else if (appState.isConfigPopupVisible) {
                runner.focusManager().setFocus("config-select-content");
            } else if (appState.isSessionListPopupVisible) {
                runner.focusManager().setFocus("session-list-content");
            } else if (appState.focusPanel == PanelType.INPUT) {
                runner.focusManager().setFocus("input-panel");
            }
        }

        return mainLayout;
    }

    private EventResult handleGlobalKeyEvent(KeyEvent key) {
        log.info("handleGlobalKeyEvent {}", key);
        // 快捷键（全局）
        if (key.isChar('c') && key.modifiers().ctrl()) {
            if (appState.isStreaming) {
                appState.finishStreaming();
                if (acpBridge != null) acpBridge.cancel();
            } else {
                runner.quit();
            }
            return EventResult.HANDLED;
        }
        if (key.isChar('n') && key.modifiers().ctrl()) {
            appState.newSession();
            firstMessageAnimationAdded = false;
            return EventResult.HANDLED;
        }
        if (key.isChar('w') && key.modifiers().ctrl()) {
            appState.closeCurrentSession();
            firstMessageAnimationAdded = false;
            return EventResult.HANDLED;
        }
        if (key.isChar('q') && key.modifiers().ctrl()) {
            runner.quit();
            return EventResult.HANDLED;
        }
        if (key.isChar('p') && key.modifiers().ctrl()) {
            appState.toggleSessionListPopup();
            return EventResult.HANDLED;
        }
        if (key.isChar('l') && key.modifiers().ctrl()) {
            appState.toggleHelpPopup();
            return EventResult.HANDLED;
        }
        if (key.isChar('k') && key.modifiers().ctrl()) {
            appState.clearConversation();
            firstMessageAnimationAdded = false;
            return EventResult.HANDLED;
        }

        // Tab 焦点切换
        if (key.isFocusNext()) {
            appState.focusNext();
            return EventResult.HANDLED;
        }
        if (key.isFocusPrevious()) {
            appState.focusPrevious();
            return EventResult.HANDLED;
        }

        // 鼠标点击 ChatPanel 区域 → 聚焦 CENTER
        if (key.isUp() && appState.focusPanel != PanelType.CENTER) {
            appState.focusPanel = PanelType.CENTER;
            return EventResult.HANDLED;
        }

        return EventResult.UNHANDLED;
    }

    /**
     * 处理全局鼠标事件
     * <p>
     * 注意：此处理器在 EventRouter.route() 中先于 routeMouseEvent() 调用。
     * 对于已由元素级处理器（如 ChatPanelElement）消费的事件，此处返回 UNHANDLED
     * 以避免干扰元素级处理。
     */
    private EventResult handleGlobalMouseEvent(MouseEvent event) {
        // 弹框可见时，在全局处理器中处理鼠标点击
        if (event.kind() == MouseEventKind.PRESS && event.isLeftButton()) {
            if (appState.isModelPopupVisible) {
                // 鼠标点击 → 确认选择当前选中的模型
                if (appState.modelSelectIndex >= 0 && appState.modelSelectIndex < appState.availableModels.size()) {
                    ModelInfo selected = appState.availableModels.get(appState.modelSelectIndex);
                    appState.currentModelId = selected.id;
                    appState.modelName = selected.name.isEmpty() ? selected.id : selected.name;
                }
                appState.closeModelPopup();
                if (!appState.currentModelId.isEmpty() && acpBridge != null) {
                    acpBridge.setModel(appState.currentModelId);
                }
                return EventResult.HANDLED;
            }
            if (appState.isConfigPopupVisible) {
                appState.closeConfigPopup();
                return EventResult.HANDLED;
            }
            if (appState.isSessionListPopupVisible) {
                appState.closeSessionListPopup();
                return EventResult.HANDLED;
            }
            if (appState.isHelpPopupVisible) {
                appState.closeHelpPopup();
                return EventResult.HANDLED;
            }
        }
        return EventResult.UNHANDLED;
    }

    private void sendMessage() {
        String message = appState.inputState.text().trim();
        if (message.isEmpty()) return;

        appState.autoScroll = true;
        appState.sendMessage(message);
        if (acpBridge != null) {
            acpBridge.sendMessage(message);
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

    private void cleanup() {
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

        default void setReconnectStrategy(Object strategy) {
        }

        default void startReconnect() {
        }

        default void stopReconnect() {
        }

        default void authenticate(String provider, String token) {
        }

        default void logout() {
        }

        default void closeSession() {
        }

        default void loadSession(String sessionId) {
        }

        default void forkSession(String sourceSessionId) {
        }

        default void resumeSession(String sessionId) {
        }

        default void switchSession(String sessionId) {
        }

        default void closeSessionById(String sessionId) {
        }

        default String[] getActiveSessionIds() {
            return new String[0];
        }

        @Nullable
        default String getActiveSessionId() {
            return null;
        }

        default void destroy() {
        }
    }

    public interface ConnectionCallback {
        void onConnected(String agentName, String agentVersion, String modelName);

        void onDisconnected();

        void onEvent(AcpEvent event);

        void onError(String message);

        default void onReconnecting(int attempt, long delayMs) {
        }

        default void onReconnected() {
        }
    }

    // ========== 静态入口 ==========

    public interface AcpEvent {
        void apply(AppState state);
    }
}
