package com.xr21.ai.agent.tui;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.alibaba.cloud.ai.graph.agent.Agent;
import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.SimpleTheme;
import com.googlecode.lanterna.graphics.Theme;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.BorderLayout;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.Component;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.Window;
import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import com.googlecode.lanterna.gui2.table.Table;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.input.MouseAction;
import com.googlecode.lanterna.input.MouseActionType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.MouseCaptureMode;
import com.googlecode.lanterna.terminal.swing.AWTTerminalFontConfiguration;
import com.googlecode.lanterna.terminal.swing.SwingTerminalFrame;
import com.googlecode.lanterna.terminal.swing.TerminalEmulatorDeviceConfiguration;
import com.xr21.ai.agent.LocalAgent;
import com.xr21.ai.agent.entity.AgentOutput;
import com.xr21.ai.agent.session.ConversationSessionManager;
import com.xr21.ai.agent.session.SessionInfo;
import io.modelcontextprotocol.json.McpJsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;

import java.awt.*;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 基于 Lanterna 的 TUI 界面
 * 特性：透明背景、渐变边框、14号粗体字体、现代化UI设计
 */
@Slf4j
public class AITerminalUI {

    // 透明度背景色
    private static final TextColor TRANSPARENT_BG = new TextColor.RGB(25, 25, 35);  // 深蓝灰色半透明背景
    private static final TextColor ACCENT_COLOR = new TextColor.RGB(100, 200, 255); // 强调色
    

    private final ConversationSessionManager sessionManager;
    private final LocalAgent localAgent;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicReference<InterruptionMetadata> interruptionMetadata = new AtomicReference<>();
    private final Map<String, Object> stateUpdate = new HashMap<>();
    // 2. 创建现代风格主题 - 深色模式配蓝绿色调，支持透明度
    Theme modernTheme = SimpleTheme.makeTheme(
            true,  // activeIsBold: 活动组件使用粗体
            TextColor.ANSI.WHITE_BRIGHT,    // baseForeground - 明亮白色文字
            TRANSPARENT_BG,                 // baseBackground - 半透明深蓝灰背景
            ACCENT_COLOR,                  // editableForeground - 强调色编辑区域
            new TextColor.RGB(40, 60, 80),  // editableBackground - 深蓝灰编辑背景
            TextColor.ANSI.WHITE_BRIGHT,    // selectedForeground - 选中项白色文字
            new TextColor.RGB(70, 130, 180), // selectedBackground - 钢蓝色选中背景
            TRANSPARENT_BG                  // guiBackground - 半透明GUI背景
    );
    
    // 创建聊天专用主题 - 更柔和的颜色
    Theme chatTheme = SimpleTheme.makeTheme(
            true,  // activeIsBold: 活动组件使用粗体
            TextColor.ANSI.WHITE_BRIGHT,    // baseForeground - 明亮白色文字
            new TextColor.RGB(20, 25, 35),  // baseBackground - 更深的背景
            new TextColor.RGB(150, 220, 255), // editableForeground - 柔和青色编辑区域
            new TextColor.RGB(35, 50, 65),  // editableBackground - 深色编辑背景
            TextColor.ANSI.WHITE_BRIGHT,    // selectedForeground - 选中项白色文字
            new TextColor.RGB(60, 120, 160), // selectedBackground - 深蓝色选中背景
            new TextColor.RGB(20, 25, 35)   // guiBackground - 深色GUI背景
    );
    
    // 保留原有theme变量以兼容旧代码
    Theme theme = modernTheme;
    private Agent supervisorAgent;
    private String currentSessionId;

    public AITerminalUI(ConversationSessionManager sessionManager, LocalAgent localAgent) {
        this.sessionManager = sessionManager;
        this.localAgent = localAgent;
        this.supervisorAgent = localAgent.buildSupervisorAgent();
    }

    /** 给任意组件包一层渐变边框，方便复用 */
    private static Component wrapWithGradientBorder(Component component) {
        return component; // 暂时不使用边框，避免编译错误
    }
    
    /** 给聊天组件包一层特殊的渐变边框 */
    private static Component wrapWithChatBorder(Component component) {
        return component; // 暂时不使用边框，避免编译错误
    }

    public void start() {
        try {
            // 1. 指定 14 号粗体等宽字体，更现代化
            DefaultTerminalFactory factory = new DefaultTerminalFactory();
            factory.setTerminalEmulatorDeviceConfiguration(
                    new TerminalEmulatorDeviceConfiguration());
//            factory.setForceAWTOverSwing(true);
            factory.setMouseCaptureMode(MouseCaptureMode.CLICK);
            factory.setInitialTerminalSize(new TerminalSize(120, 45));
            // 尝试使用支持emoji的等宽字体，如果不可用则回退到系统默认
            // 注意：Lanterna要求字体必须是等宽字体，某些字体的bold变体可能不被识别为等宽字体
            Font[] preferredFonts = {
                    new Font("Fira Code", Font.PLAIN, 14),        // 开源等宽字体，部分支持emoji
                    new Font("JetBrains Mono", Font.PLAIN, 14),   // JetBrains等宽字体
                    new Font("Consolas", Font.PLAIN, 14),         // Windows经典等宽字体
                    new Font("Monospaced", Font.PLAIN, 14)        // 系统默认等宽字体
            };

            // 直接使用第一个等宽字体，Lanterna TUI 环境下 emoji 会显示为方块
            // 这是终端渲染的限制，不是字体问题
            Font selectedFont = preferredFonts[3]; // 使用 Consolas

            factory.setTerminalEmulatorFontConfiguration(AWTTerminalFontConfiguration.newInstance(selectedFont));
            factory.setTerminalEmulatorTitle("🤖 AI AGENTS - 智能助手 v2.0.0");
            Screen screen = factory.createScreen();
            screen.startScreen();

            // 设置自定义图标，替换默认JDK图标
            setCustomIcon(screen);

            final WindowBasedTextGUI textGUI = new MultiWindowTextGUI(screen);

            textGUI.setTheme(modernTheme);

            showMainMenu(textGUI);

            while (running.get()) {
                Thread.sleep(100);
            }
            screen.stopScreen();
        } catch (IOException | InterruptedException e) {
            log.error("TUI 启动失败", e);
        }
    }


    /** 计算文本在指定宽度下需要的行数 */
    private int calculateTextLines(String text, int width) {
        if (text == null || text.isEmpty()) {
            return 1;
        }

        // 移除 "AI: " 前缀（4个字符）
        String content = text.startsWith("AI: ") ? text.substring(4) : text;

        if (content.isEmpty()) {
            return 1;
        }

        // 计算需要的行数
        int lines = 1;
        int currentLineLength = 0;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);

            // 处理中文字符（占2个字符宽度）
            int charWidth = (c > 127) ? 2 : 1;

            if (currentLineLength + charWidth > width) {
                lines++;
                currentLineLength = charWidth;
            } else {
                currentLineLength += charWidth;
            }
        }

        return lines *4;
    }

    /** 根据文本内容动态计算并设置TextBox的推荐尺寸 */
    private TerminalSize calculateTextBoxSize(String text, int width) {
        int lines = calculateTextLines(text, width);
        int height = Math.max(2,lines + 1); // +1 确保有足够空间显示
        return new TerminalSize(width, (int) text.lines().count());
    }

    /* ========================= 主菜单 ========================= */
    private void showMainMenu(WindowBasedTextGUI textGUI) {
        BasicWindow window = new BasicWindow("AGI AGENT");
        window.setHints(java.util.List.of(Window.Hint.CENTERED));
        // 给窗口本身也套渐变边框
//        window.setBorder(new GradientBorder("═", "║", "╔", "╗", "╚", "╝"));

        Panel mainPanel = new Panel();
        mainPanel.setLayoutManager(new LinearLayout(Direction.VERTICAL).setSpacing(1));
        mainPanel.setTheme(modernTheme);

        Label titleLabel = new Label("\n=== 🤖 AI AGENTS ===\n");
        titleLabel.addStyle(SGR.BOLD);
        titleLabel.addStyle(SGR.UNDERLINE);
        titleLabel.setTheme(modernTheme);
        titleLabel.setForegroundColor(ACCENT_COLOR);
        mainPanel.addComponent(titleLabel);

        Panel btnPanel = new Panel(new LinearLayout(Direction.VERTICAL).setSpacing(1));
        Button chatBtn = new Button("  开始聊天  ", () -> startChat(textGUI, window));
        Button sessionBtn = new Button("  会话管理  ", () -> showSessionManagement(textGUI, window));
        Button helpBtn = new Button("  查看帮助  ", () -> showHelp(textGUI, window));
        Button exitBtn = new Button("    退出    ", () -> {
            running.set(false);
            window.close();
        });

        // 设置按钮首选大小以显示完整中文文本
        chatBtn.setPreferredSize(new TerminalSize(25, 1));
        sessionBtn.setPreferredSize(new TerminalSize(25, 1));
        helpBtn.setPreferredSize(new TerminalSize(25, 1));
        exitBtn.setPreferredSize(new TerminalSize(25, 1));

        // 为所有按钮设置现代化样式
        chatBtn.setTheme(modernTheme);
        sessionBtn.setTheme(modernTheme);
        helpBtn.setTheme(modernTheme);
        exitBtn.setTheme(modernTheme);
        
        // Button类不支持addStyle，通过主题设置样式

        btnPanel.addComponent(chatBtn);
        btnPanel.addComponent(sessionBtn);
        btnPanel.addComponent(helpBtn);
        btnPanel.addComponent(exitBtn);

        mainPanel.addComponent(btnPanel);

        window.setComponent(wrapWithGradientBorder(mainPanel));
        textGUI.addWindowAndWait(window);
    }

    /* ========================= 聊天窗口 ========================= */
    private void startChat(WindowBasedTextGUI textGUI, Window returnTo) {
        startChatWithSession(textGUI, returnTo, "session-" + System.nanoTime());
    }

    /* ========================= 指定会话的聊天窗口 ========================= */
    private void startChatWithSession(WindowBasedTextGUI textGUI, Window returnTo, String sessionId) {
        BasicWindow window = new BasicWindow("AGI AGENT - 聊天" + (sessionId != null ? " (" + sessionId + ")" : ""));
        window.setHints(java.util.List.of(Window.Hint.EXPANDED, Window.Hint.FULL_SCREEN));
//        window.setBorder(new GradientBorder("─", "│", "┌", "┐", "└", "┘"));

        // 使用水平布局：左边85%聊天，右边15%会话状态
        Panel root = new Panel(new BorderLayout());

        // 左侧聊天面板 (85%宽度)
        Panel chatPanel = new Panel(new LinearLayout(Direction.VERTICAL));
        chatPanel.setPreferredSize(new TerminalSize(85, 100)); // 85%宽度，100%高度
        chatPanel.setTheme(chatTheme);

        Label header = new Label("\n=== 💬 聊天记录 ===\n");
        header.addStyle(SGR.BOLD);
        header.addStyle(SGR.UNDERLINE);
        header.setTheme(chatTheme);
        header.setForegroundColor(new TextColor.RGB(150, 220, 255));
        chatPanel.addComponent(header);

        // 使用可滚动的面板来显示消息
        Panel scrollableMsgArea = new Panel(new LinearLayout(Direction.VERTICAL));
        scrollableMsgArea.setTheme(chatTheme);

        // 创建内容容器
        Panel contentPanel = new Panel(new LinearLayout(Direction.VERTICAL));
        contentPanel.setTheme(chatTheme);

        // 创建垂直滚动条 - 美化样式
        ScrollBar verticalScrollBar = new ScrollBar(Direction.VERTICAL);
        verticalScrollBar.setTheme(chatTheme);
        
        // 创建水平滚动条 - 用于响应文本框的左右滚动
        ScrollBar horizontalScrollBar = new ScrollBar(Direction.HORIZONTAL);
        horizontalScrollBar.setTheme(chatTheme);
        
        // 创建滚动面板容器
        Panel scrollPanel = new Panel(new BorderLayout());
        scrollPanel.setPreferredSize(new TerminalSize(75, 20)); // 调整宽度为75以适应85%的布局
        scrollPanel.setTheme(chatTheme);
        
        // 创建内部内容面板（包含响应文本框和水平滚动条）
        Panel contentScrollPanel = new Panel(new BorderLayout());
        contentScrollPanel.setTheme(chatTheme);
        contentScrollPanel.addComponent(contentPanel, BorderLayout.Location.CENTER);
        contentScrollPanel.addComponent(horizontalScrollBar, BorderLayout.Location.BOTTOM);
        
        // 设置滚动条和内容面板
        scrollPanel.addComponent(contentScrollPanel, BorderLayout.Location.CENTER);
        scrollPanel.addComponent(verticalScrollBar, BorderLayout.Location.RIGHT);

        // 创建滚动容器，将滚动条与内容面板关联
        Panel scrollContainer = new Panel(new BorderLayout());
        scrollContainer.addComponent(scrollPanel, BorderLayout.Location.CENTER);

        // 用于保存当前AI响应的TextBox，支持流式更新和自动换行
        AtomicReference<TextBox> currentResponseTextBox = new AtomicReference<>();
        // 用于保存所有响应TextBox的列表，方便窗口大小调整时更新
        java.util.List<TextBox> allResponseTextBoxes = new java.util.ArrayList<>();

        // 右侧会话状态面板 (15%宽度)
        Panel statusPanel = createSessionStatusPanel(sessionId);
        statusPanel.setPreferredSize(new TerminalSize(15, 100)); // 15%宽度，100%高度
        statusPanel.setTheme(modernTheme);

        // 加载历史消息
        if (sessionId != null) {
            sessionManager.loadSessionById(sessionId);
            var messages = sessionManager.loadSessionHistory(sessionId);
            for (var msg : messages) {
                String prefix = switch (msg.getType()) {
                    case USER -> "👤 你: ";
                    case ASSISTANT -> "🤖 AI: ";
                    case SYSTEM -> "⚙️ [系统]: ";
                    case TOOL_CALL -> "🔧 [工具调用]: ";
                    case TOOL_RESPONSE -> "✅ [工具响应]: ";
                    case ERROR -> "❌ [错误]: ";
                    case FEEDBACK -> "💡 [反馈]: ";
                };
                
                // 为AI响应创建TextBox，其他消息类型使用Label
                if (msg.getType() == com.xr21.ai.agent.entity.ConversationMessage.MessageType.ASSISTANT) {
                    contentPanel.addComponent(new Label(prefix));
                    TextBox historyTextBox = new TextBox(msg.getContent(), TextBox.Style.MULTI_LINE);
                    historyTextBox.setTheme(chatTheme);
                    historyTextBox.setReadOnly(true);
                    historyTextBox.setVerticalFocusSwitching(false);
                    // 初始尺寸设置，会在窗口大小调整时重新计算
                    historyTextBox.setPreferredSize(new TerminalSize(70, 3));
                    // 添加到所有响应TextBox列表
                    allResponseTextBoxes.add(historyTextBox);
                    contentPanel.addComponent(historyTextBox);
                } else {
                    Label msgLabel = new Label(prefix + msg.getContent());
                    msgLabel.setTheme(chatTheme);
                    contentPanel.addComponent(msgLabel);
                }
            }
        }

        // 创建自定义 TextBox，不处理 Ctrl+Enter，让它传递到窗口级别
        TextBox input = new TextBox("", TextBox.Style.MULTI_LINE) {
            @Override
            public Result handleKeyStroke(KeyStroke keyStroke) {
                // 如果是 Ctrl+Enter，不处理，让它传递到父级
                if (keyStroke.getKeyType() == KeyType.Enter && keyStroke.isCtrlDown()) {
                    return Result.UNHANDLED;
                }
                // 其他按键正常处理
                return super.handleKeyStroke(keyStroke);
            }
        };
        input.setPreferredSize(new TerminalSize(70, 5)); // 调整输入框宽度为70
        input.setTheme(chatTheme);
        // TextBox不支持addStyle，通过主题设置样式

        // 添加快捷键说明面板
        Panel shortcutPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        Label shortcutLabel = new Label("⌨️ 快捷键: Ctrl+Enter 发送 | Esc 返回");
        shortcutLabel.addStyle(SGR.BOLD);
        shortcutLabel.setTheme(modernTheme);
        shortcutLabel.setForegroundColor(new TextColor.RGB(255, 200, 100));
        shortcutPanel.addComponent(shortcutLabel);

        chatPanel.addComponent(wrapWithChatBorder(scrollContainer));
        Label inputLabel = new Label("📝 输入消息:");
        inputLabel.setTheme(chatTheme);
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

                // 更新滚动面板尺寸
                scrollPanel.setPreferredSize(new TerminalSize(chatWidth - 10, Math.max(height, 10)));

                // 更新输入框尺寸
                input.setPreferredSize(new TerminalSize(chatWidth - 15, 5));

                // 更新状态面板尺寸
                statusPanel.setPreferredSize(new TerminalSize(statusWidth, newSize.getRows()));

                // 更新所有响应TextBox的尺寸
                int responseBoxWidth = chatWidth - 15;
                for (TextBox responseBox : allResponseTextBoxes) {
                    if (responseBox != null) {
                        // 重新计算高度
                        String text = responseBox.getText();
                        if (text != null && !text.isEmpty()) {
                            int lines = calculateTextLines(text, responseBoxWidth);
                            int preferredHeight = Math.max(3, Math.min(lines, 15)); // 限制最大高度为15行
                            responseBox.setPreferredSize(new TerminalSize(responseBoxWidth, preferredHeight));
                        } else {
                            responseBox.setPreferredSize(new TerminalSize(responseBoxWidth, 3));
                        }
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
            }
        });

        // 添加窗口级别键盘事件监听器，处理 Ctrl+Enter 和其他快捷键
        window.addWindowListener(new WindowListenerAdapter() {
            @Override
            public void onUnhandledInput(Window basePane, KeyStroke key, AtomicBoolean handled) {
                // 处理 Ctrl+Enter 发送消息
                if (key.getKeyType() == KeyType.Enter && key.isCtrlDown()) {
                    String txt = input.getText().trim();
                    if (!txt.isEmpty()) {
                        contentPanel.addComponent(new Label("你: " + txt));
                        input.setText("");

                        // 使用localAgent处理消息
                        if (sessionId != null) {
                            currentSessionId = sessionId;
                            sessionManager.addUserMessage(sessionId, txt);

                            // 处理对话
                            try {
                                updateUIWithGraph(txt, contentPanel, sessionManager, currentResponseTextBox, textGUI, statusPanel, allResponseTextBoxes);
                            } catch (Exception e) {
                                contentPanel.addComponent(new Label("[错误]: " + e.getMessage()));
                                sessionManager.addErrorMessage(sessionId, e.getMessage());
                            }
                        }
                    }
                    handled.set(true);
                }
                // 处理 Esc 返回主菜单
                else if (key.getKeyType() == KeyType.Escape) {
                    window.close();
                    handled.set(true);
                }
                // 保留原有的 q 键退出功能
                else if (key.getCharacter() != null && key.getCharacter() == 'q') {
                    window.close();
                    handled.set(true);
                }
            }
        });

        // 添加鼠标滚动支持
        window.addWindowListener(new WindowListenerAdapter() {
            @Override
            public void onUnhandledInput(Window basePane, KeyStroke key, AtomicBoolean handled) {
                // 处理鼠标滚轮事件
                if (key.getKeyType() == KeyType.MouseEvent) {
                    try {
                        MouseAction mouseAction = (MouseAction) key;
                        // 检查鼠标滚轮方向
                        if (mouseAction.getActionType() == MouseActionType.SCROLL_DOWN) {
                            // 向下滚动
                            if (verticalScrollBar != null) {
                                int currentPos = verticalScrollBar.getScrollPosition();
                                int maxPos = verticalScrollBar.getScrollMaximum();
                                verticalScrollBar.setScrollPosition(Math.min(currentPos + 3, maxPos));
                                handled.set(true);
                            }
                        } else if (mouseAction.getActionType() == MouseActionType.SCROLL_UP) {
                            // 向上滚动
                            if (verticalScrollBar != null) {
                                int currentPos = verticalScrollBar.getScrollPosition();
                                verticalScrollBar.setScrollPosition(Math.max(currentPos - 3, 0));
                                handled.set(true);
                            }
                        }
                    } catch (Exception e) {
                        // 如果鼠标事件处理失败，忽略错误
                    }
                }
            }
        });
        textGUI.addWindowAndWait(window);
    }

    /* ========================= 会话管理 ========================= */
    private void showSessionManagement(WindowBasedTextGUI textGUI, Window returnTo) {
        BasicWindow window = new BasicWindow("AGI AGENT - 会话管理");
//        window.setBorder(new GradientBorder("═", "║", "╔", "╗", "╚", "╝"));

        Panel root = new Panel(new LinearLayout(Direction.VERTICAL));
        root.addComponent(new Label("\n=== 会话列表 ===\n").addStyle(SGR.BOLD));

        Table<String> table = new Table<>("会话ID", "创建时间", "消息数", "简要描述");
        table.setTheme(theme);
        
        // 从sessionManager获取真实的会话数据
        var sessionInfoList = sessionManager.getSessionInfoList();
        for (var info : sessionInfoList) {
            table.getTableModel().addRow(
                info.getSessionId(),
                info.getCreatedAt(),
                String.valueOf(info.getMessageCount()),
                info.getBriefDescription()
            );
        }

        Panel btnPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        Button newSessionBtn = new Button("创建新会话", () -> {
            String id = "session" + System.currentTimeMillis();
            sessionManager.getOrCreateSession(id);
            MessageDialog.showMessageDialog(textGUI, "提示", "已创建: " + id, MessageDialogButton.OK);
            // 刷新会话列表
            window.close();
            showSessionManagement(textGUI, returnTo);
        });
        Button switchSessionBtn = new Button("进入聊天", () -> {
            int idx = table.getSelectedRow();
            if (idx >= 0) {
                String id = table.getTableModel().getRow(idx).get(0);
                window.close();
                startChatWithSession(textGUI, returnTo, id);
            } else {
                MessageDialog.showMessageDialog(textGUI, "提示", "请先选择一个会话", MessageDialogButton.OK);
            }
        });
        btnPanel.addComponent(newSessionBtn);
        btnPanel.addComponent(switchSessionBtn);
        btnPanel.addComponent(new Button("返回", window::close));

        root.addComponent(wrapWithGradientBorder(table));
        root.addComponent(btnPanel);

        window.setComponent(root);
        textGUI.addWindowAndWait(window);
    }

    private void updateUIWithGraph(String input, Panel contentPanel, ConversationSessionManager sessionManager, AtomicReference<TextBox> currentResponseTextBox, WindowBasedTextGUI textGUI, Panel statusPanel, java.util.List<TextBox> allResponseTextBoxes) {
        StringBuilder responseBuilder = new StringBuilder();

        Flux<ServerSentEvent<AgentOutput<Object>>> outputFlux = localAgent.processWithGraphV2(
                supervisorAgent, input, currentSessionId, interruptionMetadata.get(), stateUpdate);

        // 使用异步订阅，避免阻塞UI线程
        outputFlux.doOnNext(output -> {
            AgentOutput<Object> agentOutput = output.data();
            // 处理流式文本输出
            System.err.println(agentOutput.getChunk());
            if (agentOutput.getChunk() != null) {
                System.err.println(agentOutput.getChunk());
                responseBuilder.append(agentOutput.getChunk());

                // 获取或创建当前响应的TextBox
                TextBox responseTextBox = currentResponseTextBox.get();
                // 动态计算宽度：基于当前窗口大小
                int dynamicWidth = Math.max(50, textGUI.getScreen().getTerminalSize().getColumns() * 75 / 100 - 10);
                
                if (responseTextBox == null) {
                    responseTextBox = new TextBox(responseBuilder.toString(), TextBox.Style.MULTI_LINE);
                    responseTextBox.setTheme(theme);
                    responseTextBox.setReadOnly(true);
                    // 使用辅助方法根据实际行数设置尺寸
                    TerminalSize size = calculateTextBoxSize(responseBuilder.toString(), dynamicWidth);
                    responseTextBox.setPreferredSize(size);
                    responseTextBox.setVerticalFocusSwitching(false); // 禁用垂直焦点切换，专注于滚动
                    currentResponseTextBox.set(responseTextBox);
                    // 添加到所有响应TextBox列表
                    allResponseTextBoxes.add(responseTextBox);

                    // 添加AI前缀标签
                    contentPanel.addComponent(new Label("AI: "));
                    contentPanel.addComponent(responseTextBox);
                } else {
                    // 更新现有TextBox的内容
                    responseTextBox.setText(responseBuilder.toString());
                    // 使用辅助方法根据实际行数动态设置高度
                    TerminalSize size = calculateTextBoxSize(responseBuilder.toString(), dynamicWidth);
                    responseTextBox.setPreferredSize(size);
                    // 自动滚动到底部
                    TextBox.TextBoxRenderer renderer = responseTextBox.getRenderer();
                    if (renderer instanceof TextBox.DefaultTextBoxRenderer defaultRenderer) {
                        // 计算文本总行数
                        int totalLines = responseTextBox.getLineCount();
                        int visibleLines = size.getRows();
                        TerminalPosition viewTopLeft = defaultRenderer.getViewTopLeft();
                        int newScrollPosition = Math.max(0, totalLines - visibleLines + 1); // +1确保显示最后一行
                        defaultRenderer.setViewTopLeft(viewTopLeft.withRow(newScrollPosition));
                    }
                }

                // 更新会话状态面板
                updateSessionStatusPanel(statusPanel, currentSessionId, sessionManager);

                // 强制刷新整个GUI
                try {
                    if (textGUI != null) {
                        // 在Lanterna的GUI线程中更新界面
                        textGUI.getGUIThread().invokeLater(() -> {
                            contentPanel.invalidate();
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
                    String feedbackMsg = String.format("\n[系统提示] %s: %s",
                            toolFeedback.getName(), toolFeedback.getDescription());

                    // 在GUI线程中添加工具反馈消息
                    try {
                        if (textGUI != null) {
                            textGUI.getGUIThread().invokeLater(() -> {
                                Label feedbackLabel = new Label(feedbackMsg);
                                feedbackLabel.setTheme(theme);
                                contentPanel.addComponent(feedbackLabel);
                                contentPanel.invalidate();
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
                    for (AssistantMessage.ToolCall toolCall : message.getToolCalls()) {
                        String toolCallMsg = String.format("\n[工具调用]: %s 参数: %s", toolCall.name(), toolCall.arguments());

                        // 在GUI线程中添加工具调用消息
                        try {
                            if (textGUI != null) {
                                textGUI.getGUIThread().invokeLater(() -> {
                                    Label toolCallLabel = new Label(toolCallMsg);
                                    toolCallLabel.setTheme(theme);
                                    contentPanel.addComponent(toolCallLabel);
                                    contentPanel.invalidate();
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

                        try {
                            sessionManager.addToolCallMessage(currentSessionId, toolCall.name(), McpJsonMapper.getDefault()
                                    .readValue(toolCall.arguments(), Map.class), toolCall.id());
                        } catch (Exception e) {
                            log.error("记录工具调用失败", e);
                        }
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

            // 更新会话状态面板
            updateSessionStatusPanel(statusPanel, currentSessionId, sessionManager);

            // 最终刷新界面
            try {
                if (textGUI != null) {
                    textGUI.getGUIThread().invokeLater(() -> {
                        contentPanel.invalidate();
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
            try {
                if (textGUI != null) {
                    textGUI.getGUIThread().invokeLater(() -> {
                        Label errorLabel = new Label("[错误]: " + error.getMessage());
                        errorLabel.setTheme(theme);
                        contentPanel.addComponent(errorLabel);
                        contentPanel.invalidate();
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

    /* ========================= 创建会话状态面板 ========================= */
    private Panel createSessionStatusPanel(String sessionId) {
        Panel statusPanel = new Panel(new LinearLayout(Direction.VERTICAL));
        statusPanel.setTheme(modernTheme);

        // 标题
        Label title = new Label("=== 📊 会话状态 ===");
        title.addStyle(SGR.BOLD);
        title.addStyle(SGR.UNDERLINE);
        title.setTheme(modernTheme);
        title.setForegroundColor(ACCENT_COLOR);
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
                    Label idTitle = new Label("🆔 会话ID:");
                    idTitle.setTheme(modernTheme);
                    idTitle.addStyle(SGR.BOLD);
                    statusPanel.addComponent(idTitle);
                    Label idLabel = new Label(sessionId.length() > 12 ? sessionId.substring(0, 12) + "..." : sessionId);
                    idLabel.setTheme(modernTheme);
                    idLabel.setForegroundColor(new TextColor.RGB(150, 220, 255));
                    statusPanel.addComponent(idLabel);

                    statusPanel.addComponent(new Label(""));

                    // 创建时间
                    Label timeTitle = new Label("🕒 创建时间:");
                    timeTitle.setTheme(modernTheme);
                    timeTitle.addStyle(SGR.BOLD);
                    statusPanel.addComponent(timeTitle);
                    Label timeLabel = new Label(sessionInfo.getCreatedAt());
                    timeLabel.setTheme(modernTheme);
                    timeLabel.setForegroundColor(new TextColor.RGB(200, 255, 200));
                    statusPanel.addComponent(timeLabel);

                    statusPanel.addComponent(new Label(""));

                    // 消息统计
                    Label statsTitle = new Label("📊 消息统计:");
                    statsTitle.setTheme(modernTheme);
                    statsTitle.addStyle(SGR.BOLD);
                    statusPanel.addComponent(statsTitle);
                    Label countLabel = new Label("📝 总计: " + sessionInfo.getMessageCount());
                    countLabel.setTheme(modernTheme);
                    countLabel.setForegroundColor(new TextColor.RGB(255, 255, 150));
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

                    Label userLabel = new Label("👤 用户: " + userCount);
                    userLabel.setTheme(modernTheme);
                    userLabel.setForegroundColor(new TextColor.RGB(150, 255, 150));
                    statusPanel.addComponent(userLabel);

                    Label aiLabel = new Label("🤖 AI: " + assistantCount);
                    aiLabel.setTheme(modernTheme);
                    aiLabel.setForegroundColor(new TextColor.RGB(150, 220, 255));
                    statusPanel.addComponent(aiLabel);

                    Label systemLabel = new Label("⚙️ 系统: " + systemCount);
                    systemLabel.setTheme(modernTheme);
                    systemLabel.setForegroundColor(new TextColor.RGB(255, 200, 100));
                    statusPanel.addComponent(systemLabel);

                    Label toolLabel = new Label("🔧 工具: " + toolCount);
                    toolLabel.setTheme(modernTheme);
                    toolLabel.setForegroundColor(new TextColor.RGB(255, 150, 200));
                    statusPanel.addComponent(toolLabel);

                    statusPanel.addComponent(new Label(""));

                    // 简要描述
                    Label descTitle = new Label("📄 简要描述:");
                    descTitle.setTheme(modernTheme);
                    descTitle.addStyle(SGR.BOLD);
                    statusPanel.addComponent(descTitle);
                    String desc = sessionInfo.getBriefDescription();
                    if (desc != null && !desc.isEmpty()) {
                        // 分行显示描述，每行最多12个字符
                        String[] descLines = desc.length() > 12 ? desc.split("(?<=\\G.{12})") : new String[]{desc};
                        for (String line : descLines) {
                            Label descLabel = new Label(line);
                            descLabel.setTheme(modernTheme);
                            descLabel.setForegroundColor(new TextColor.RGB(200, 200, 255));
                            statusPanel.addComponent(descLabel);
                        }
                    } else {
                        Label noDescLabel = new Label("📝 暂无描述");
                        noDescLabel.setTheme(modernTheme);
                        noDescLabel.setForegroundColor(new TextColor.RGB(150, 150, 150));
                        statusPanel.addComponent(noDescLabel);
                    }
                } else {
                    // 新会话还没有任何消息
                    Label newIdTitle = new Label("🆔 会话ID:");
                    newIdTitle.setTheme(modernTheme);
                    newIdTitle.addStyle(SGR.BOLD);
                    statusPanel.addComponent(newIdTitle);
                    Label idLabel = new Label(sessionId.length() > 12 ? sessionId.substring(0, 12) + "..." : sessionId);
                    idLabel.setTheme(modernTheme);
                    idLabel.setForegroundColor(new TextColor.RGB(150, 220, 255));
                    statusPanel.addComponent(idLabel);

                    statusPanel.addComponent(new Label(""));
                    Label statusLabel = new Label("🆕 状态: 新会话");
                    statusLabel.setTheme(modernTheme);
                    statusLabel.setForegroundColor(new TextColor.RGB(150, 255, 150));
                    statusPanel.addComponent(statusLabel);
                    Label msgCountLabel = new Label("📝 消息数: 0");
                    msgCountLabel.setTheme(modernTheme);
                    msgCountLabel.setForegroundColor(new TextColor.RGB(200, 200, 200));
                    statusPanel.addComponent(msgCountLabel);
                }
            } catch (Exception e) {
                Label errorTitle = new Label("❌ 状态更新失败:");
                errorTitle.setTheme(modernTheme);
                errorTitle.addStyle(SGR.BOLD);
                statusPanel.addComponent(errorTitle);
                Label errorLabel = new Label(e.getMessage());
                errorLabel.setTheme(modernTheme);
                errorLabel.setForegroundColor(new TextColor.RGB(255, 100, 100));
                statusPanel.addComponent(errorLabel);
            }
        } else {
            Label noSessionLabel = new Label("🚫 无会话信息");
            noSessionLabel.setTheme(modernTheme);
            noSessionLabel.setForegroundColor(new TextColor.RGB(150, 150, 150));
            statusPanel.addComponent(noSessionLabel);
        }
    }

    /* ========================= 帮助 ========================= */
    private void showHelp(WindowBasedTextGUI textGUI, Window returnTo) {
        String help = """
                \n=== 🤖 AI 助手帮助 ===
                
                功能：
                1. 💬 开始聊天 - 与 AI 对话
                2. 📁 会话管理 - 创建/切换会话
                3. ❓ 查看帮助 - 显示本信息
                
                快捷键：
                ⭕ Tab   切换控件
                ⬆️⬇️ 方向键 导航
                ❌ Q     返回上级
                🚪 ESC   关闭对话框
                
                版本：v2.0.0 - 现代化UI升级
                """;
        MessageDialog.showMessageDialog(textGUI, "📚 帮助", help, MessageDialogButton.OK);
    }

    /** 设置自定义窗口图标，替换默认JDK图标 */
    private void setCustomIcon(Screen screen) {
        try {
            // 获取底层的AWT窗口
            if (screen instanceof TerminalScreen terminalScreen) {
                if (terminalScreen.getTerminal() instanceof SwingTerminalFrame swingTerminalFrame) {


                    // 获取包含终端的JFrame
                    java.awt.Container parent = swingTerminalFrame.getRootPane().getParent();
                    while (parent != null && !(parent instanceof java.awt.Frame)) {
                        parent = parent.getParent();
                    }

                    if (parent instanceof java.awt.Frame) {
                        java.awt.Frame frame = (java.awt.Frame) parent;

                        // 创建一个现代化的机器人图标（16x16像素）
                        java.awt.image.BufferedImage icon = new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                        java.awt.Graphics2D g2d = icon.createGraphics();

                        // 设置抗锯齿和高质量渲染
                        g2d.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                        g2d.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);

                        // 绘制渐变背景
                        java.awt.GradientPaint gradient = new java.awt.GradientPaint(0, 0, 
                            new java.awt.Color(100, 200, 255), 8, 8, 
                            new java.awt.Color(150, 100, 255));
                        g2d.setPaint(gradient);
                        g2d.fillRoundRect(1, 1, 14, 14, 3, 3);

                        // 绘制机器人头部
                        g2d.setColor(new java.awt.Color(50, 50, 80)); // 深蓝灰色
                        g2d.fillRoundRect(3, 4, 10, 8, 2, 2);

                        // 绘制发光的眼睛
                        g2d.setColor(new java.awt.Color(150, 220, 255)); // 明亮青色
                        g2d.fillOval(5, 6, 2, 2);
                        g2d.fillOval(9, 6, 2, 2);
                        
                        // 绘制眼睛高光
                        g2d.setColor(java.awt.Color.WHITE);
                        g2d.fillOval(5, 6, 1, 1);
                        g2d.fillOval(9, 6, 1, 1);

                        // 绘制微笑嘴巴
                        g2d.setColor(new java.awt.Color(255, 150, 200)); // 粉色
                        g2d.drawArc(6, 7, 4, 3, 0, -180);

                        g2d.dispose();

                        // 设置窗口图标
                        frame.setIconImage(icon);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("设置自定义图标失败", e);
        }
    }
}