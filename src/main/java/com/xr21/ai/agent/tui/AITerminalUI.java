package com.xr21.ai.agent.tui;

import cn.hutool.core.convert.Convert;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.alibaba.cloud.ai.graph.agent.Agent;
import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.Theme;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import com.googlecode.lanterna.gui2.table.Table;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.MouseCaptureMode;
import com.googlecode.lanterna.terminal.swing.SwingTerminalFontConfiguration;
import com.googlecode.lanterna.terminal.swing.TerminalEmulatorColorConfiguration;
import com.googlecode.lanterna.terminal.swing.TerminalEmulatorDeviceConfiguration;
import com.googlecode.lanterna.terminal.swing.TerminalEmulatorPalette;
import com.xr21.ai.agent.LocalAgent;
import com.xr21.ai.agent.entity.AgentOutput;
import com.xr21.ai.agent.entity.ConversationMessage;
import com.xr21.ai.agent.session.ConversationSessionManager;
import com.xr21.ai.agent.session.SessionInfo;
import com.xr21.ai.agent.utils.Json;
import io.modelcontextprotocol.json.McpJsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.googlecode.lanterna.terminal.swing.TerminalEmulatorDeviceConfiguration.CursorStyle.VERTICAL_BAR;


/**
 * 基于 Lanterna 的 TUI 界面
 * 特性：透明背景、渐变边框、14号粗体字体、现代化UI设计
 * 优化：使用tui工具类（FontUtils, IconGenerator, ScrollHelper, TextMeasureUtils, UIComponentFactory, UIThemeConfig）
 */
@Slf4j
public class AITerminalUI {

    // 使用tui工具类
    private final ScrollHelper scrollHelper = new ScrollHelper();
    private final UIComponentFactory componentFactory = new UIComponentFactory();
    private final ConversationSessionManager sessionManager;
    private final LocalAgent localAgent;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicReference<InterruptionMetadata> interruptionMetadata = new AtomicReference<>();
    private final Map<String, Object> stateUpdate = new HashMap<>();

    // 主题使用UIThemeConfig中的配置
    private final Theme chatTheme = UIThemeConfig.createChatTheme();
    private final Theme modernTheme = UIThemeConfig.createModernTheme();

    private Agent supervisorAgent;
    private String currentSessionId;


    public AITerminalUI(ConversationSessionManager sessionManager, LocalAgent localAgent) {
        this.sessionManager = sessionManager;
        this.localAgent = localAgent;
        this.supervisorAgent = localAgent.buildSupervisorAgent();
    }

    /**
     * 给聊天组件包一层特殊的渐变边框
     */
    private static Component wrapWithChatBorder(Component component) {
        return component; // 暂时不使用边框，避免编译错误
    }

    public void start() {
        try {
            // 使用FontUtils创建优化后的字体配置
            SwingTerminalFontConfiguration fontConfig = FontUtils.createOptimizedFontConfiguration();

            // 配置终端仿真器设备设置
            TerminalEmulatorDeviceConfiguration deviceConfig = new TerminalEmulatorDeviceConfiguration(2000, 500, VERTICAL_BAR, new TextColor.RGB(255, 255, 255), true, true);
            DefaultTerminalFactory factory = new DefaultTerminalFactory();
            factory.setInitialTerminalSize(new TerminalSize(120, 45));
            factory.setMouseCaptureMode(MouseCaptureMode.CLICK_RELEASE);
            factory.setTerminalEmulatorDeviceConfiguration(deviceConfig);
            factory.setTerminalEmulatorTitle("AI AGENTS - 智能助手 v2.0.0");
            factory.setTerminalEmulatorFontConfiguration(fontConfig);
            factory.setTerminalEmulatorColorConfiguration(TerminalEmulatorColorConfiguration.newInstance(TerminalEmulatorPalette.MAC_OS_X_TERMINAL_APP));

            Screen screen = new TerminalScreen(factory.createTerminal());
            screen.startScreen();

            // 使用IconGenerator设置自定义图标
            IconGenerator.setCustomIcon(screen);

            final WindowBasedTextGUI textGUI = new MultiWindowTextGUI(screen);
            textGUI.setTheme(modernTheme);

            // 直接开始聊天，不显示主菜单
            startChat(textGUI);

            while (running.get()) {
                Thread.sleep(100);
            }
            screen.stopScreen();
        } catch (IOException | InterruptedException e) {
            log.error("TUI 启动失败", e);
        }
    }

    /* ========================= 聊天窗口 ========================= */
    private void startChat(WindowBasedTextGUI textGUI) {
        startChatWithSession(textGUI, "session-" + System.nanoTime());
    }

    /* ========================= 指定会话的聊天窗口 ========================= */
    private void startChatWithSession(WindowBasedTextGUI textGUI, String sessionId) {
        BasicWindow window = new BasicWindow("AGI AGENT - 聊天" + (sessionId != null ? " (" + sessionId + ")" : ""));
        window.setHints(java.util.List.of(Window.Hint.EXPANDED, Window.Hint.FULL_SCREEN));
//        window.setBorder(new GradientBorder("─", "│", "┌", "┐", "└", "┘"));

        // 使用水平布局：左边85%聊天，右边15%会话状态
        Panel root = new Panel(new BorderLayout());

        // 左侧聊天面板 (85%宽度) - 使用优化后的聊天主题
        Panel chatPanel = componentFactory.createVerticalPanel();
        chatPanel.setTheme(chatTheme);

        // 使用可滚动的面板来显示消息
        Panel scrollableMsgArea = componentFactory.createVerticalPanel();
        scrollableMsgArea.setTheme(chatTheme);

        // 创建内容容器 - 使用 GridLayout 以支持滚动
        Panel contentPanel = componentFactory.createVerticalPanel();
        contentPanel.setLayoutManager(new GridLayout(1)); // 单列网格布局
        contentPanel.setTheme(chatTheme);

        // 创建垂直滚动条 - 优化样式，支持自动滚动
        ScrollBar verticalScrollBar = componentFactory.createVerticalScrollBar(chatTheme);
        // 设置ScrollHelper的滚动条
        scrollHelper.setVerticalScrollBar(verticalScrollBar);


        // 创建滚动面板容器 - 使用 BorderLayout 实现内容区域和滚动条的正确布局
        // CENTER 放置内容面板，RIGHT 放置滚动条，确保滚动条紧贴内容右侧，无额外空白
        Panel scrollPanel = new Panel(new BorderLayout());
        scrollPanel.setTheme(chatTheme);

        // 将内容面板添加到滚动面板的中央区域
        scrollPanel.addComponent(contentPanel, BorderLayout.Location.CENTER);
        // 将滚动条添加到滚动面板的右侧，紧贴内容面板
        scrollPanel.addComponent(verticalScrollBar, BorderLayout.Location.RIGHT);

        // 创建滚动容器
        Panel scrollContainer = new Panel(new BorderLayout());
        scrollContainer.addComponent(scrollPanel, BorderLayout.Location.CENTER);

        // 用于保存当前AI响应的TextBox，支持流式更新
        AtomicReference<TextBox> currentResponseTextBox = new AtomicReference<>();
        // 用于保存所有响应TextBox的列表，方便窗口大小调整时更新
        java.util.List<TextBox> allResponseTextBoxes = new java.util.ArrayList<>();
        // 用于显示loading状态的Label
        AtomicReference<Label> loadingLabel = new AtomicReference<>();
        // 用于跟踪是否正在等待AI响应
        AtomicBoolean isWaitingForResponse = new AtomicBoolean(false);

        // 右侧会话状态面板 (15%宽度) - 使用优化后的状态主题
        Panel statusPanel = createSessionStatusPanel(sessionId);
        statusPanel.setPreferredSize(new TerminalSize(15, 100)); // 15%宽度，100%高度
        statusPanel.setTheme(modernTheme);

        // 加载历史消息
        if (sessionId != null) {
            sessionManager.loadSessionById(sessionId);
            var messages = sessionManager.loadSessionHistory(sessionId);
            // 动态计算宽度：基于当前窗口大小
            int dynamicWidth = textGUI.getScreen().getTerminalSize().getColumns() * 75 / 100 - 10;

            for (var msg : messages) {
                String prefix = switch (msg.getType()) {
                    case USER -> "你: ";
                    case ASSISTANT -> "AI: ";
                    case SYSTEM -> "[系统]: ";
                    case TOOL_CALL -> "[工具调用]: ";
                    case TOOL_RESPONSE -> "[工具响应]: ";
                    case ERROR -> "[错误]: ";
                    case FEEDBACK -> "[反馈]: ";
                };
                var message = Convert.toStr(msg.getContent(), "");
                // 为AI响应创建可选择TextBox，其他消息类型使用可选择Label
                if (ConversationMessage.MessageType.TOOL_CALL == msg.getType()) {
                    if (msg.getToolCall() != null) {
                        message += Json.toJson(msg.getToolCall());
                    }
                }
                Label aiPrefixLabel = componentFactory.createLabel(prefix, chatTheme);
                contentPanel.addComponent(aiPrefixLabel);
                TextBox historyTextBox = componentFactory.createReadOnlyTextBox(message, dynamicWidth);

                allResponseTextBoxes.add(historyTextBox);
                contentPanel.addComponent(historyTextBox);
            }

            // 加载完所有历史消息后，重新计算所有消息高度并滚动到底部
            scrollHelper.recalculateAllMessageHeights(contentPanel, scrollPanel, textGUI, allResponseTextBoxes);
            scrollHelper.scrollToBottom(contentPanel, scrollPanel, textGUI);
        }

        // 创建自定义 TextBox，支持粘贴，处理 Ctrl+Enter 和 Ctrl+V
        TextBox input = new TextBox("", TextBox.Style.MULTI_LINE) {
            @Override
            public Result handleKeyStroke(KeyStroke keyStroke) {
                // 如果是 Ctrl+Enter，不处理，让它传递到父级发送消息
                if (keyStroke.getKeyType() == KeyType.Enter && keyStroke.isCtrlDown()) {
                    return Result.UNHANDLED;
                }
                // 如果是 Ctrl+N，不处理，让它传递到父级处理新建会话
                if (keyStroke.getCharacter() != null && keyStroke.getCharacter() == 'n' && keyStroke.isCtrlDown()) {
                    return Result.UNHANDLED;
                }
                // 如果是 Ctrl+S，不处理，让它传递到父级处理会话管理
                if (keyStroke.getCharacter() != null && keyStroke.getCharacter() == 's' && keyStroke.isCtrlDown()) {
                    return Result.UNHANDLED;
                }
                // 如果是 Ctrl+H，不处理，让它传递到父级处理帮助
                if (keyStroke.getCharacter() != null && keyStroke.getCharacter() == 'h' && keyStroke.isCtrlDown()) {
                    return Result.UNHANDLED;
                }
                // 如果是 Ctrl+V，尝试获取系统剪贴板内容并粘贴
                if (keyStroke.getCharacter() != null && keyStroke.getCharacter() == 'v' && keyStroke.isCtrlDown()) {
                    try {
                        java.awt.datatransfer.Clipboard clipboard = java.awt.Toolkit.getDefaultToolkit()
                                .getSystemClipboard();
                        if (clipboard.isDataFlavorAvailable(java.awt.datatransfer.DataFlavor.stringFlavor)) {
                            String pasteText = (String) clipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor);
                            if (pasteText != null) {
                                // 在当前文本末尾插入粘贴的文本
                                String currentText = getText();
                                String newText = currentText + pasteText;
                                setText(newText);
                                setCaretPosition((int) newText.lines().count(), newText.lines()
                                        .skip(newText.lines().count() - 1)
                                        .findAny()
                                        .orElse("")
                                        .length());
                            }
                        }
                        return Result.HANDLED;
                    } catch (Exception e) {
                        log.warn("粘贴失败", e);
                    }
                }
                // 其他按键正常处理
                return super.handleKeyStroke(keyStroke);
            }
        };
        input.setPreferredSize(new TerminalSize(70, 5));
        input.setTheme(chatTheme);
        input.setVerticalFocusSwitching(false);

        // 添加快捷键说明面板
        Panel shortcutPanel = componentFactory.createHorizontalPanel();
        Label shortcutLabel = componentFactory.createStyledLabel("快捷键: Ctrl+Enter 发送 | Ctrl+N 新建会话 | Ctrl+S 会话管理 | Ctrl+H 帮助 | Esc 返回", UIThemeConfig.LOADING_COLOR, SGR.BOLD);
        shortcutPanel.addComponent(shortcutLabel);

        chatPanel.addComponent(wrapWithChatBorder(scrollContainer));
        Label inputLabel = componentFactory.createChatLabel("输入消息:");
        inputLabel.addStyle(SGR.BOLD);
        chatPanel.addComponent(inputLabel);
        chatPanel.addComponent(input);
        chatPanel.addComponent(shortcutPanel);

        // 将左右面板添加到根面板
        root.addComponent(chatPanel, BorderLayout.Location.CENTER);
        root.addComponent(statusPanel, BorderLayout.Location.RIGHT);

        window.setComponent(root);

        // 添加窗口大小变化监听器
        window.addWindowListener(new WindowListenerAdapter() {
            @Override
            public void onResized(Window window, TerminalSize oldSize, TerminalSize newSize) {
                // 重新计算左右面板的宽度比例
                int chatWidth = newSize.getColumns() * 85 / 100;  // 85%宽度给聊天
                int statusWidth = newSize.getColumns() * 15 / 100; // 15%宽度给状态
                int height = newSize.getRows() - 8; // 减去按钮和输入框占用的空间

                // 更新聊天面板尺寸
                chatPanel.setPreferredSize(new TerminalSize(chatWidth, newSize.getRows()));

                // 更新滚动面板尺寸 - 调整计算方式，确保滚动条正确显示且紧贴内容面板
                // 减去2个字符宽度给滚动条，避免与状态面板之间产生空白
                scrollPanel.setPreferredSize(new TerminalSize(Math.max(chatWidth - 2, 10), Math.max(height, 10)));

                // 更新输入框尺寸
                input.setPreferredSize(new TerminalSize(chatWidth - 15, 5));

                // 更新状态面板尺寸
                statusPanel.setPreferredSize(new TerminalSize(statusWidth, newSize.getRows()));

                // 更新所有响应TextBox的尺寸
                int responseBoxWidth = chatWidth - 15;
                for (TextBox responseBox : allResponseTextBoxes) {
                    if (responseBox != null) {
                        // 根据实际行数动态计算高度
                        String text = responseBox.getText();
                        newSize = TextMeasureUtils.calculateTextBoxSize(text, responseBoxWidth);
                        responseBox.setPreferredSize(newSize);
                    }
                }

                // 强制刷新界面
                textGUI.getGUIThread().invokeLater(() -> {
                    window.invalidate();
                    try {
                        if (textGUI.getScreen() != null) {
                            textGUI.getScreen().refresh();
                        }
                    } catch (IOException e) {
                        log.error("窗口大小调整后刷新失败", e);
                    }
                });

                // 窗口大小改变后滚动到底部
                // 加载完所有历史消息后，重新计算所有消息高度并滚动到底部
                scrollHelper.recalculateAllMessageHeights(contentPanel, scrollPanel, textGUI, allResponseTextBoxes);

                scrollHelper.scrollToBottom(contentPanel, scrollPanel, textGUI);
            }
        });

        // 添加窗口级别键盘事件监听器，处理 Ctrl+Enter 和其他快捷键
        window.addWindowListener(new WindowListenerAdapter() {
            @Override
            public void onUnhandledInput(Window basePane, KeyStroke key, AtomicBoolean handled) {
                // 处理 Ctrl+Enter 发送消息
                if (key.getKeyType() == KeyType.Enter && key.isCtrlDown()) {
                    String txt = input.getText().trim();
                    if (!txt.isEmpty() && !isWaitingForResponse.get()) {
                        // 1. 立即渲染用户消息到界面上
                        Label userLabel = componentFactory.createChatLabel("你: " + txt);
                        contentPanel.addComponent(userLabel);

                        // 重新计算所有消息高度，确保布局正确
                        scrollHelper.recalculateAllMessageHeights(contentPanel, scrollPanel, textGUI, allResponseTextBoxes);

                        // 发送用户消息后滚动到底部
                        scrollHelper.scrollToBottom(contentPanel, scrollPanel, textGUI);

                        // 2. 立即刷新界面显示用户消息
                        textGUI.getGUIThread().invokeLater(() -> {
                            contentPanel.invalidate();
                            scrollPanel.invalidate();
                            try {
                                if (textGUI.getScreen() != null) {
                                    textGUI.getScreen().refresh();
                                }
                            } catch (IOException e) {
                                log.error("用户消息渲染刷新失败", e);
                            }
                        });

                        // 清空输入框
                        input.setText("");

                        // 禁用输入框防止重复发送
                        input.setEnabled(false);

                        // 3. 显示loading状态
                        Label loading = componentFactory.createLoadingLabel("🤖 AI正在思考...");
                        contentPanel.addComponent(loading);
                        loadingLabel.set(loading);

                        // 更新滚动条范围
                        scrollHelper.updateScrollBarRange(contentPanel, scrollPanel);
                        scrollHelper.recalculateAllMessageHeights(contentPanel, scrollPanel, textGUI, allResponseTextBoxes);
                        // 添加loading后滚动到底部
                        scrollHelper.scrollToBottom(contentPanel, scrollPanel, textGUI);

                        // 刷新界面显示loading - 使用更彻底的刷新机制
                        textGUI.getGUIThread().invokeLater(() -> {
                            contentPanel.invalidate();
                            scrollPanel.invalidate();
                            try {
                                if (textGUI.getScreen() != null) {
                                    textGUI.getScreen().refresh();
                                }
                            } catch (IOException e) {
                                log.error("loading渲染刷新失败", e);
                            }
                        });

                        // 使用localAgent处理消息
                        if (sessionId != null) {
                            currentSessionId = sessionId;
                            sessionManager.addUserMessage(sessionId, txt);
                            isWaitingForResponse.set(true);

                            // 处理对话
                            try {
                                updateUIWithGraph(txt, contentPanel, scrollPanel, sessionManager, currentResponseTextBox, textGUI, statusPanel, allResponseTextBoxes, loadingLabel, isWaitingForResponse, input);
                            } catch (Exception e) {
                                // 隐藏loading
                                hideLoading(contentPanel, scrollPanel, loadingLabel, isWaitingForResponse, input, textGUI);
                                Label errorLabel = componentFactory.createErrorLabel(e.getMessage());
                                contentPanel.addComponent(errorLabel);
                                // 更新滚动条范围
                                scrollHelper.updateScrollBarRange(contentPanel, scrollPanel);
                                // 错误消息添加后滚动到底部
                                scrollHelper.recalculateAllMessageHeights(contentPanel, scrollPanel, textGUI, allResponseTextBoxes);

                                scrollHelper.scrollToBottom(contentPanel, scrollPanel, textGUI);
                                sessionManager.addErrorMessage(sessionId, e.getMessage());
                                textGUI.getGUIThread().invokeLater(() -> {
                                    contentPanel.invalidate();
                                    scrollPanel.invalidate();
                                    try {
                                        if (textGUI.getScreen() != null) {
                                            textGUI.getScreen().refresh();
                                        }
                                    } catch (IOException ex) {
                                        log.error("错误消息刷新失败", ex);
                                    }
                                });
                            }
                        }
                    }
                    handled.set(true);
                }
                // 处理 Ctrl+N 新建会话
                else if (key.getCharacter() != null && key.getCharacter() == 'n' && key.isCtrlDown()) {
                    String newSessionId = sessionManager.createSession();
                    window.close();
                    startChatWithSession(textGUI, newSessionId);
                    handled.set(true);
                }
                // 处理 Ctrl+S 打开会话管理
                else if (key.getCharacter() != null && key.getCharacter() == 's' && key.isCtrlDown()) {
                    window.close();
                    showSessionManagement(textGUI);
                    handled.set(true);
                }
                // 处理 Ctrl+H 显示帮助
                else if (key.getCharacter() != null && key.getCharacter() == 'h' && key.isCtrlDown()) {
                    showHelp(textGUI, window);
                    handled.set(true);
                }
                // 处理 Esc 返回主菜单
                else if (key.getKeyType() == KeyType.Escape) {
                    window.close();
                    System.exit(0);
                }
            }
        });

        // 注意：Lanterna 的 Panel 不直接支持 scrollDown/scrollUp 方法
        // 鼠标滚轮事件通过 ScrollBar 隐式处理
        // 如需自定义滚动行为，可使用键盘方向键或 PageUp/PageDown
        textGUI.addWindowAndWait(window);
    }

    /* ========================= 会话管理 ========================= */
    private void showSessionManagement(WindowBasedTextGUI textGUI) {
        BasicWindow window = new BasicWindow("AGI AGENT - 会话管理");
//        window.setBorder(new GradientBorder("═", "║", "╔", "╗", "╚", "╝"));

        Panel root = new Panel(new LinearLayout(Direction.VERTICAL));
        root.addComponent(new Label("\n=== 会话列表 ===\n").addStyle(SGR.BOLD));

        Table<String> table = new Table<>("会话ID", "创建时间", "消息数", "简要描述");
        table.setTheme(modernTheme);

        // 从sessionManager获取真实的会话数据
        var sessionInfoList = sessionManager.getSessionInfoList();
        for (var info : sessionInfoList) {
            table.getTableModel()
                    .addRow(info.getSessionId(), info.getCreatedAt(), String.valueOf(info.getMessageCount()), info.getBriefDescription());
        }

        Panel btnPanel = componentFactory.createHorizontalPanel();
        btnPanel.addComponent(componentFactory.createButton("返回", window::close, 50));

        root.addComponent(table);
        root.addComponent(btnPanel);
        // 添加窗口级别键盘事件监听器，处理回车键进入会话
        window.addWindowListener(new WindowListenerAdapter() {
            @Override
            public void onInput(Window basePane, KeyStroke key, AtomicBoolean handled) {
                // 处理回车键进入会话
                if (key.getKeyType() == KeyType.Enter) {
                    int idx = table.getSelectedRow();
                    if (idx >= 0) {
                        String id = table.getTableModel().getRow(idx).get(0);
                        window.close();
                        startChatWithSession(textGUI, id);
                    } else {
                        MessageDialog.showMessageDialog(textGUI, "提示", "请先选择一个会话", MessageDialogButton.OK);
                    }
                    handled.set(true);
                }
            }
        });

        window.setComponent(root);
        textGUI.addWindowAndWait(window);
    }

    private void updateUIWithGraph(String input, Panel contentPanel, Panel scrollPanel, ConversationSessionManager sessionManager, AtomicReference<TextBox> currentResponseTextBox, WindowBasedTextGUI textGUI, Panel statusPanel, java.util.List<TextBox> allResponseTextBoxes, AtomicReference<Label> loadingLabel, AtomicBoolean isWaitingForResponse, TextBox inputBox) {
        StringBuilder responseBuilder = new StringBuilder();

        Flux<ServerSentEvent<AgentOutput<Object>>> outputFlux = localAgent.processWithGraphV2(supervisorAgent, input, currentSessionId, interruptionMetadata.get(), stateUpdate);

        // 使用异步订阅，避免阻塞UI线程
        outputFlux.doOnNext(output -> {
            AgentOutput<Object> agentOutput = output.data();
            // 处理流式文本输出
            if (agentOutput != null && agentOutput.getChunk() != null) {
                System.err.println(agentOutput.getChunk());

                // 检查是否有内容需要处理，避免空响应
                String chunk = agentOutput.getChunk();
                if (chunk == null || chunk.isEmpty()) {
                    return;
                }

                // 首次收到有效响应时，隐藏loading状态
                // 使用compareAndSet确保只在第一次时隐藏
                if (isWaitingForResponse.compareAndSet(true, false)) {
                    // 确保在GUI线程中隐藏loading，避免竞态条件
                    textGUI.getGUIThread().invokeLater(() -> {
                        hideLoading(contentPanel, scrollPanel, loadingLabel, isWaitingForResponse, inputBox, textGUI);
                    });
                }

                responseBuilder.append(chunk);
                // 获取或创建当前响应的TextBox
                TextBox responseTextBox = currentResponseTextBox.get();
                // 动态计算宽度：基于当前窗口大小
                int dynamicWidth = textGUI.getScreen().getTerminalSize().getColumns() * 75 / 100 - 10;

                if (responseTextBox == null) {
                    responseTextBox = new TextBox(responseBuilder.toString(), TextBox.Style.MULTI_LINE);
                    responseTextBox.setTheme(chatTheme);
                    responseTextBox.setReadOnly(true);
                    // 使用辅助方法根据实际行数设置尺寸
                    TerminalSize size = TextMeasureUtils.calculateTextBoxSize(responseBuilder.toString(), dynamicWidth);
                    responseTextBox.setPreferredSize(size);
                    responseTextBox.setVerticalFocusSwitching(false); // 禁用垂直焦点切换，专注于滚动
                    currentResponseTextBox.set(responseTextBox);
                    // 添加到所有响应TextBox列表
                    allResponseTextBoxes.add(responseTextBox);

                    // 添加AI前缀标签
                    Label aiLabel = new Label("AI: ");
                    aiLabel.setTheme(chatTheme);
                    contentPanel.addComponent(aiLabel);
                    contentPanel.addComponent(responseTextBox);

                    // 重新计算所有消息高度，确保布局正确
                    scrollHelper.recalculateAllMessageHeights(contentPanel, scrollPanel, textGUI, allResponseTextBoxes);

                    scrollHelper.scrollToBottom(contentPanel, scrollPanel, textGUI);
                } else {
                    // 更新现有TextBox的内容
                    responseTextBox.setText(responseBuilder.toString());
                    // 使用辅助方法根据实际行数动态设置高度
                    TerminalSize size = TextMeasureUtils.calculateTextBoxSize(responseBuilder.toString(), dynamicWidth);
                    responseTextBox.setPreferredSize(size);
                    // 重新计算所有消息高度，确保布局正确
                    scrollHelper.recalculateAllMessageHeights(contentPanel, scrollPanel, textGUI, allResponseTextBoxes);
                    // 自动滚动到底部
                    scrollHelper.scrollToBottom(contentPanel, scrollPanel, textGUI);
                }

                // 更新会话状态面板
                updateSessionStatusPanel(statusPanel, currentSessionId, sessionManager);

                // 强制刷新整个GUI - 使用更彻底的刷新机制
                try {
                    if (textGUI != null) {
                        // 在Lanterna的GUI线程中更新界面
                        textGUI.getGUIThread().invokeLater(() -> {
                            contentPanel.invalidate();
                            scrollPanel.invalidate();
                            statusPanel.invalidate();
                            try {
                                if (textGUI.getScreen() != null) {
                                    textGUI.getScreen().refresh();
                                }
                            } catch (IOException e) {
                                log.error("屏幕刷新失败", e);
                            }
                        });
                    }
                } catch (Exception e) {
                    log.error("更新UI失败", e);
                }
            }

            // 处理工具反馈
            if (!CollectionUtils.isEmpty(agentOutput.getToolFeedbacks())) {
                for (InterruptionMetadata.ToolFeedback toolFeedback : agentOutput.getToolFeedbacks()) {
                    String feedbackMsg = String.format("\n[系统提示] %s: %s", toolFeedback.getName(), toolFeedback.getDescription());

                    // 在GUI线程中添加工具反馈消息到responseTextBox
                    try {
                        if (textGUI != null) {
                            textGUI.getGUIThread().invokeLater(() -> {
                                // 使用responseTextBox统一展示工具反馈
                                TextBox feedbackTextBox = new TextBox(feedbackMsg, TextBox.Style.MULTI_LINE);
                                feedbackTextBox.setTheme(chatTheme);
                                feedbackTextBox.setReadOnly(true);
                                int dynamicWidth = textGUI.getScreen().getTerminalSize().getColumns() * 75 / 100 - 10;
                                TerminalSize size = TextMeasureUtils.calculateTextBoxSize(feedbackMsg, dynamicWidth);
                                feedbackTextBox.setPreferredSize(size);
                                feedbackTextBox.setVerticalFocusSwitching(false);

                                contentPanel.addComponent(feedbackTextBox);
                                allResponseTextBoxes.add(feedbackTextBox);

                                // 重新计算所有消息高度，确保布局正确
                                scrollHelper.recalculateAllMessageHeights(contentPanel, scrollPanel, textGUI, allResponseTextBoxes);

                                // 添加工具反馈后滚动到底部
                                scrollHelper.scrollToBottom(contentPanel, scrollPanel, textGUI);
                                contentPanel.invalidate();
                                scrollPanel.invalidate();
                                statusPanel.invalidate();
                                try {
                                    if (textGUI.getScreen() != null) {
                                        textGUI.getScreen().refresh();
                                    }
                                } catch (IOException e) {
                                    log.error("屏幕刷新失败", e);
                                }
                            });
                        }
                    } catch (Exception e) {
                        log.error("更新UI失败", e);
                    }

                    InterruptionMetadata.Builder newBuilder = InterruptionMetadata.builder()
                            .nodeId(agentOutput.getNode())
                            .state(new OverAllState(agentOutput.getData()));
                    InterruptionMetadata.ToolFeedback approvedFeedback = InterruptionMetadata.ToolFeedback.builder(toolFeedback)
                            .description(toolFeedback.getDescription())
                            .result(InterruptionMetadata.ToolFeedback.FeedbackResult.APPROVED)
                            .build();
                    newBuilder.addToolFeedback(approvedFeedback);
                    interruptionMetadata.set(newBuilder.build());

                    sessionManager.addSystemMessage(currentSessionId, feedbackMsg.trim());
                }
            }

            // 处理工具调用
            if (agentOutput.getMessage() instanceof AssistantMessage message) {
                if (!CollectionUtils.isEmpty(message.getToolCalls())) {
                    // 标记发生了工具调用，后续响应需要使用新的TextBox
                    AtomicBoolean toolCallOccurred = new AtomicBoolean(false);

                    for (AssistantMessage.ToolCall toolCall : message.getToolCalls()) {
                        String arguments = toolCall.arguments();
                        String displayArgs = (arguments != null && !arguments.trim().isEmpty()) ? arguments : "无参数";
                        String toolCallMsg = String.format("\n[工具调用]: %s 参数: %s", toolCall.name(), displayArgs);
                        System.err.println(toolCallMsg);
                        // 在GUI线程中添加工具调用消息到responseTextBox
                        try {
                            if (textGUI != null) {
                                textGUI.getGUIThread().invokeLater(() -> {
                                    // 使用responseTextBox统一展示工具调用消息
                                    TextBox toolCallTextBox = new TextBox(toolCallMsg, TextBox.Style.MULTI_LINE);
                                    toolCallTextBox.setCaretWarp(true);
                                    toolCallTextBox.setTheme(chatTheme);
                                    toolCallTextBox.setReadOnly(true);
                                    toolCallTextBox.setPreferredSize(new TerminalSize(99, 1));
                                    toolCallTextBox.setVerticalFocusSwitching(false);

                                    contentPanel.addComponent(toolCallTextBox);
                                    allResponseTextBoxes.add(toolCallTextBox);

                                    responseBuilder.setLength(0);
                                    // 重新计算所有消息高度，确保布局正确
                                    scrollHelper.recalculateAllMessageHeights(contentPanel, scrollPanel, textGUI, allResponseTextBoxes);

                                    // 添加工具调用后滚动到底部
                                    scrollHelper.scrollToBottom(contentPanel, scrollPanel, textGUI);
                                });
                            }
                        } catch (Exception e) {
                            log.error("更新UI失败", e);
                        }

                        try {
                            Map<String, Object> argumentsMap = new HashMap<>();
                            if (arguments != null && !arguments.trim().isEmpty()) {
                                try {
                                    argumentsMap = McpJsonMapper.getDefault().readValue(arguments, Map.class);
                                } catch (Exception jsonException) {
                                    log.warn("解析工具调用参数失败: {}", jsonException.getMessage());
                                    // 使用原始字符串作为参数
                                    argumentsMap.put("raw_arguments", arguments);
                                }
                            }
                            sessionManager.addToolCallMessage(currentSessionId, toolCall.name(), argumentsMap, toolCall.id());
                        } catch (Exception e) {
                            log.error("记录工具调用失败", e);
                        }

                        toolCallOccurred.set(true);
                    }

                    // 如果发生了工具调用，重置当前响应TextBox，后续响应将使用新的TextBox
                    if (toolCallOccurred.get()) {
                        currentResponseTextBox.set(null);
                    }
                }
            }
        }).doOnComplete(() -> {
            // 流完成时清理当前响应TextBox引用，但保留在allResponseTextBoxes中
            currentResponseTextBox.set(null);

            // 保存完整的AI响应到会话
            String fullResponse = responseBuilder.toString();
            if (!fullResponse.isEmpty()) {
                sessionManager.addAssistantMessage(currentSessionId, fullResponse);
            }

            // 隐藏loading并重新启用输入框
            hideLoading(contentPanel, scrollPanel, loadingLabel, isWaitingForResponse, inputBox, textGUI);

            // 更新会话状态面板
            updateSessionStatusPanel(statusPanel, currentSessionId, sessionManager);

            // 最终刷新界面 - 使用更彻底的刷新机制
            try {
                if (textGUI != null) {
                    textGUI.getGUIThread().invokeLater(() -> {
                        contentPanel.invalidate();
                        scrollPanel.invalidate();
                        statusPanel.invalidate();
                        try {
                            if (textGUI.getScreen() != null) {
                                textGUI.getScreen().refresh();
                            }
                        } catch (IOException e) {
                            log.error("屏幕刷新失败", e);
                        }

                    });
                }
            } catch (Exception e) {
                log.error("最终UI更新失败", e);
            }
        }).doOnError(error -> {
            log.error("流处理出错", error);

            // 隐藏loading并重新启用输入框
            hideLoading(contentPanel, scrollPanel, loadingLabel, isWaitingForResponse, inputBox, textGUI);

            try {
                if (textGUI != null) {
                    textGUI.getGUIThread().invokeLater(() -> {
                        // 使用responseTextBox统一展示错误消息
                        String errorMsg = "[错误]: " + error.getMessage();
                        TextBox errorTextBox = new TextBox(errorMsg, TextBox.Style.MULTI_LINE);
                        errorTextBox.setTheme(chatTheme);
                        errorTextBox.setReadOnly(true);
                        int dynamicWidth = textGUI.getScreen().getTerminalSize().getColumns() * 75 / 100 - 10;
                        TerminalSize size = TextMeasureUtils.calculateTextBoxSize(errorMsg, dynamicWidth);
                        errorTextBox.setPreferredSize(size);
                        errorTextBox.setVerticalFocusSwitching(false);

                        contentPanel.addComponent(errorTextBox);
                        allResponseTextBoxes.add(errorTextBox);

                        // 更新滚动条范围
                        scrollHelper.updateScrollBarRange(contentPanel, scrollPanel);
                        scrollHelper.recalculateAllMessageHeights(contentPanel, scrollPanel, textGUI, allResponseTextBoxes);

                        // 滚动到底部
                        scrollHelper.scrollToBottom(contentPanel, scrollPanel, textGUI);
                        contentPanel.invalidate();
                        scrollPanel.invalidate();
                        statusPanel.invalidate();
                        try {
                            if (textGUI.getScreen() != null) {
                                textGUI.getScreen().refresh();
                            }
                        } catch (IOException e) {
                            log.error("屏幕刷新失败", e);
                        }

                    });
                }
            } catch (Exception e) {
                log.error("错误UI更新失败", e);
            }
        }).subscribe();
    }

    /**
     * 隐藏loading状态并重新启用输入框，同时聚焦到输入框
     */
    private void hideLoading(Panel contentPanel, Panel scrollPanel, AtomicReference<Label> loadingLabel, AtomicBoolean isWaitingForResponse, TextBox inputBox, WindowBasedTextGUI textGUI) {
        Label loading = loadingLabel.get();
        if (loading != null) {
            try {
                contentPanel.removeComponent(loading);
            } catch (Exception e) {
                log.warn("移除loading标签失败", e);
            }
            loadingLabel.set(null);
        }

        // 重新启用输入框
        isWaitingForResponse.set(false);
        if (inputBox != null) {
            inputBox.setEnabled(true);
            inputBox.setVerticalFocusSwitching(true);
            // 聚焦到输入框，方便用户继续输入
            textGUI.getGUIThread().invokeLater(() -> {
                inputBox.takeFocus();
            });
        }

        // 隐藏loading后滚动到底部
        scrollHelper.scrollToBottom(contentPanel, scrollPanel, textGUI);

        // 刷新界面 - 使用更彻底的刷新机制
        if (textGUI != null) {
            textGUI.getGUIThread().invokeLater(() -> {
                contentPanel.invalidate();
                scrollPanel.invalidate();
                try {
                    if (textGUI.getScreen() != null) {
                        textGUI.getScreen().refresh();
                    }
                } catch (IOException e) {
                    log.error("loading隐藏后刷新失败", e);
                }
            });
        }
    }

    /* ========================= 创建会话状态面板 ========================= */
    private Panel createSessionStatusPanel(String sessionId) {
        Panel statusPanel = new Panel(new LinearLayout(Direction.VERTICAL));
        statusPanel.setTheme(modernTheme);

        // 标题
        Label title = componentFactory.createTitleLabel("=== 会话状态 ===");
        statusPanel.addComponent(title);

        // 初始化状态信息
        updateSessionStatusPanel(statusPanel, sessionId, sessionManager);

        return statusPanel;
    }

    /* ========================= 更新会话状态面板 ========================= */
    private void updateSessionStatusPanel(Panel statusPanel, String sessionId, ConversationSessionManager sessionManager) {
        // 清空现有组件（保留标题）
        var components = statusPanel.getChildren();
        var componentsList = new java.util.ArrayList<>(components);
        for (int i = componentsList.size() - 1; i > 0; i--) {
            statusPanel.removeComponent(componentsList.get(i));
        }

        if (sessionId != null) {
            try {
                // 从sessionInfoList中查找对应的会话信息
                var sessionInfoList = sessionManager.getSessionInfoList();
                SessionInfo sessionInfo = null;
                for (SessionInfo info : sessionInfoList) {
                    if (sessionId.equals(info.getSessionId())) {
                        sessionInfo = info;
                        break;
                    }
                }

                // 如果在文件中找不到会话信息，尝试从内存中获取
                if (sessionInfo == null) {
                    var messages = sessionManager.loadSessionHistory(sessionId);
                    if (!messages.isEmpty()) {
                        // 创建临时的会话信息
                        sessionInfo = new SessionInfo();
                        sessionInfo.setSessionId(sessionId);
                        sessionInfo.setMessageCount(messages.size());
                        sessionInfo.setCreatedAt(messages.get(0)
                                .getTimestamp()
                                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                        sessionInfo.setLastUpdated(messages.get(messages.size() - 1)
                                .getTimestamp()
                                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

                        // 提取第一条用户消息作为简要描述
                        String firstUserMessage = messages.stream()
                                .filter(m -> m.getType() == com.xr21.ai.agent.entity.ConversationMessage.MessageType.USER)
                                .findFirst()
                                .map(com.xr21.ai.agent.entity.ConversationMessage::getContent)
                                .orElse("新会话");
                        sessionInfo.setBriefDescription(firstUserMessage.length() > 50 ? firstUserMessage.substring(0, 50) + "..." : firstUserMessage);
                    }
                }

                if (sessionInfo != null) {
                    // 会话ID
                    Label idTitle = componentFactory.createModernLabel("会话ID:");
                    idTitle.addStyle(SGR.BOLD);
                    statusPanel.addComponent(idTitle);
                    Label idLabel = new Label(sessionId.length() > 12 ? sessionId.substring(0, 12) + "..." : sessionId);
                    idLabel.setTheme(modernTheme);
                    idLabel.setForegroundColor(new TextColor.RGB(150, 220, 255));
                    statusPanel.addComponent(idLabel);

                    statusPanel.addComponent(new Label(""));

                    // 创建时间
                    Label timeTitle = componentFactory.createModernLabel("创建时间:");
                    timeTitle.addStyle(SGR.BOLD);
                    statusPanel.addComponent(timeTitle);
                    Label timeLabel = componentFactory.createModernLabel(sessionInfo.getCreatedAt());
                    timeLabel.setForegroundColor(UIThemeConfig.SYSTEM_MSG_COLOR);
                    statusPanel.addComponent(timeLabel);

                    statusPanel.addComponent(new Label(""));

                    // 消息统计
                    Label statsTitle = componentFactory.createModernLabel("消息统计:");
                    statsTitle.addStyle(SGR.BOLD);
                    statusPanel.addComponent(statsTitle);
                    Label countLabel = componentFactory.createModernLabel("总计: " + sessionInfo.getMessageCount());
                    countLabel.setForegroundColor(UIThemeConfig.TOOL_MSG_COLOR);
                    statusPanel.addComponent(countLabel);

                    // 按类型统计消息数量
                    var messages = sessionManager.loadSessionHistory(sessionId);
                    int userCount = 0, assistantCount = 0, systemCount = 0, toolCount = 0;

                    for (var msg : messages) {
                        switch (msg.getType()) {
                            case USER -> userCount++;
                            case ASSISTANT -> assistantCount++;
                            case SYSTEM -> systemCount++;
                            case TOOL_CALL, TOOL_RESPONSE -> toolCount++;
                        }
                    }

                    Label userLabel = componentFactory.createModernLabel("用户: " + userCount);
                    userLabel.setForegroundColor(UIThemeConfig.USER_MSG_COLOR);
                    statusPanel.addComponent(userLabel);

                    Label aiLabel = componentFactory.createModernLabel("AI: " + assistantCount);
                    aiLabel.setForegroundColor(UIThemeConfig.AI_MSG_COLOR);
                    statusPanel.addComponent(aiLabel);
                    String desc = sessionInfo.getBriefDescription();
                    if (desc != null && !desc.isEmpty()) {
                        // 分行显示描述，每行最多12个字符
                        String[] descLines = desc.length() > 12 ? desc.split("(?<=\\G.{12})") : new String[]{desc};
                        for (String line : descLines) {

                            statusPanel.addComponent(new Label(""));
                            Label statusLabel = componentFactory.createModernLabel("状态: 新会话");
                            statusLabel.setForegroundColor(UIThemeConfig.USER_MSG_COLOR);
                            statusPanel.addComponent(statusLabel);
                            Label msgCountLabel = componentFactory.createModernLabel("消息数: 0");
                            msgCountLabel.setForegroundColor(new TextColor.RGB(200, 200, 200));
                            statusPanel.addComponent(msgCountLabel);
                        }
                    }
                }

            } catch (Exception e) {
                Label errorTitle = componentFactory.createModernLabel("状态更新失败:");
                errorTitle.addStyle(SGR.BOLD);
                statusPanel.addComponent(errorTitle);
                Label errorLabel = componentFactory.createErrorLabel(e.getMessage());
                statusPanel.addComponent(errorLabel);
            }
        }
    }

    /* ========================= 帮助 ========================= */

    private void showHelp(WindowBasedTextGUI textGUI, Window returnTo) {
        String help = """
                \n=== AI 助手帮助 ===
                
                功能：
                1. 开始聊天 - 与 AI 对话
                2. 会话管理 - 创建/切换会话
                3. 查看帮助 - 显示本信息
                
                快捷键：
                Tab   切换控件
                方向键 导航
                Q     返回上级
                ESC   关闭对话框
                
                版本：v2.0.0 - 现代化UI升级
                """;
        MessageDialog.showMessageDialog(textGUI, "帮助", help, MessageDialogButton.OK);
    }
}