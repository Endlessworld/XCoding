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
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.MouseCaptureMode;
import com.googlecode.lanterna.terminal.swing.*;
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
import java.awt.font.FontRenderContext;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.googlecode.lanterna.terminal.swing.TerminalEmulatorDeviceConfiguration.CursorStyle.VERTICAL_BAR;

/**
 * 基于 Lanterna 的 TUI 界面
 * 特性：透明背景、渐变边框、14号粗体字体、现代化UI设计
 */
@Slf4j
public class AITerminalUI {

    // ==================== 优化后的配色方案 ====================

    // 🎨 聊天窗口背景色 - 温暖的深色，护眼舒适
    private static final TextColor CHAT_BG = new TextColor.RGB(30, 32, 40);           // 聊天区域主背景 - 深灰蓝
    private static final TextColor CHAT_EDITABLE_BG = new TextColor.RGB(40, 44, 52);   // 聊天输入区域 - 稍浅的灰蓝
    private static final TextColor CHAT_SELECTED_BG = new TextColor.RGB(60, 100, 140); // 聊天选中状态 - 柔和蓝

    // 🎨 会话状态背景色 - 稳重的深色调，区分明显
    private static final TextColor STATUS_BG = new TextColor.RGB(25, 28, 35);          // 会话状态主背景 - 深色灰
    private static final TextColor STATUS_EDITABLE_BG = new TextColor.RGB(35, 40, 48);  // 会话状态编辑区域 - 中等灰
    private static final TextColor STATUS_SELECTED_BG = new TextColor.RGB(80, 85, 95);  // 会话状态选中 - 柔和灰

    // 🎨 公共配色 - 强调色和通用背景
    private static final TextColor ACCENT_COLOR = new TextColor.RGB(70, 180, 240);     // 强调色 - 柔和青蓝
    private static final TextColor GUI_BACKGROUND = new TextColor.RGB(22, 24, 30);     // GUI主背景 - 更深的色调

    // 保留原有常量用于兼容
    private static final TextColor TRANSPARENT_BG = STATUS_BG;
    // 用于跟踪滚动位置，支持自动滚动
    private static AtomicInteger scrollPosition = new AtomicInteger(0);
    private static AtomicBoolean autoScrollEnabled = new AtomicBoolean(true); // 默认启用自动滚动


    private final ConversationSessionManager sessionManager;
    private final LocalAgent localAgent;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicReference<InterruptionMetadata> interruptionMetadata = new AtomicReference<>();
    private final Map<String, Object> stateUpdate = new HashMap<>();
    // 2. 创建会话状态主题 - 稳重的深色调，层次分明
    Theme modernTheme = SimpleTheme.makeTheme(
            true,                                // activeIsBold: 活动组件使用粗体
            TextColor.ANSI.WHITE_BRIGHT,         // baseForeground - 明亮白色文字
            STATUS_BG,                           // baseBackground - 会话状态主背景
            ACCENT_COLOR,                        // editableForeground - 强调色文字
            STATUS_EDITABLE_BG,                  // editableBackground - 编辑区域背景
            TextColor.ANSI.WHITE_BRIGHT,         // selectedForeground - 选中项白色文字
            STATUS_SELECTED_BG,                  // selectedBackground - 选中状态背景
            GUI_BACKGROUND                       // guiBackground - GUI主背景
    );

    // 3. 创建聊天窗口主题 - 温暖护眼的深色，舒适阅读
    Theme chatTheme = SimpleTheme.makeTheme(
            true,                                // activeIsBold: 活动组件使用粗体
            TextColor.ANSI.WHITE_BRIGHT,         // baseForeground - 明亮白色文字
            CHAT_BG,                             // baseBackground - 聊天区域主背景
            new TextColor.RGB(140, 210, 255),    // editableForeground - 柔和青蓝色文字
            CHAT_EDITABLE_BG,                    // editableBackground - 输入区域背景
            TextColor.ANSI.WHITE_BRIGHT,         // selectedForeground - 选中项白色文字
            CHAT_SELECTED_BG,                    // selectedBackground - 选中状态背景
            CHAT_BG                              // guiBackground - 聊天区域背景
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

    // 优化后的字体配置 - 支持多种现代等宽字体
    private static SwingTerminalFontConfiguration createOptimizedFontConfiguration() {
        String selectedFont = findAvailableFont();
        int fontSize = 16; // 14号字体，适合中文显示
        var planFont = new Font(selectedFont, Font.PLAIN, fontSize);
        var italicFont = new Font(selectedFont, Font.ITALIC, fontSize);
        var boldFont = new Font(selectedFont, Font.BOLD, fontSize);
        return SwingTerminalFontConfiguration.newInstance(planFont, italicFont, boldFont);
    }

    /**
     * 查找系统中可用的等宽字体
     * @return 可用的等宽字体名称，如果没有找到则返回系统默认等宽字体
     */
    private static String findAvailableFont() {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();

        // 收集所有可用的字体名称
        Set<String> availableFonts = new HashSet<>();
        for (Font font : ge.getAllFonts()) {
            availableFonts.add(font.getFamily());
            availableFonts.add(font.getFontName());
        }

        // 收集所有可用的等宽字体
        Set<String> monospacedFonts = new HashSet<>();
        for (String fontName : availableFonts) {
            if (isMonospacedFont(fontName)) {
                monospacedFonts.add(fontName);
            }
        }

        log.debug("系统中发现的等宽字体: {}", monospacedFonts);

        // 如果没有找到预定义字体，返回系统默认等宽字体
        String defaultMonospaced = "Monospaced";
        if (monospacedFonts.contains(defaultMonospaced)) {
            log.info("使用系统默认等宽字体: {}", defaultMonospaced);
            return defaultMonospaced;
        }

        // 如果 Monospaced 不可用，从发现的等宽字体中选择一个
        if (!monospacedFonts.isEmpty()) {
            String firstMonospaced = monospacedFonts.iterator().next();
            log.info("使用发现的等宽字体: {}", firstMonospaced);
            return firstMonospaced;
        }

        log.warn("未找到任何等宽字体，使用通用字体名称");
        return defaultMonospaced;
    }

    /**
     * 检查指定名称的字体是否为等宽字体
     * @param fontName 字体名称
     * @return 如果是等宽字体返回 true，否则返回 false
     */
    private static boolean isMonospacedFont(String fontName) {
        try {
            // 创建字体的 12pt 纯样式版本
            Font font = new Font(fontName, Font.PLAIN, 12);
            if (font == null || font.getFamily().equals("Dialog")) {
                return false;
            }

            // 测试字符：'i' (窄字符) 和 'W' (宽字符)
            FontRenderContext frc = new FontRenderContext(null, true, true);
            double widthI = font.getStringBounds("i", frc).getWidth();
            double widthW = font.getStringBounds("W", frc).getWidth();

            // 如果两个字符宽度相同或非常接近（考虑浮点误差），则为等宽字体
            double tolerance = 0.1;
            return Math.abs(widthI - widthW) < tolerance;
        } catch (Exception e) {
            log.trace("检查字体等宽性失败: {}, 原因: {}", fontName, e.getMessage());
            return false;
        }
    }

    /**
     * 给聊天组件包一层特殊的渐变边框
     */
    private static Component wrapWithChatBorder(Component component) {
        return component; // 暂时不使用边框，避免编译错误
    }

    public void start() {
        try {
            // 1. 优化字体配置 - 使用系统最佳等宽字体
            SwingTerminalFontConfiguration fontConfig = createOptimizedFontConfiguration();

            // 2. 配置终端仿真器设备设置，优化显示效果
            TerminalEmulatorDeviceConfiguration deviceConfig = new TerminalEmulatorDeviceConfiguration(2000, 500, VERTICAL_BAR, new TextColor.RGB(255, 255, 255), true, true);
            // 3. 创建终端工厂并应用配置
            DefaultTerminalFactory factory = new DefaultTerminalFactory();
            factory.setInitialTerminalSize(new TerminalSize(120, 45));
            factory.setMouseCaptureMode(MouseCaptureMode.CLICK);
            factory.setTerminalEmulatorDeviceConfiguration(deviceConfig);
//            factory.setForceAWTOverSwing(true);
            factory.setTerminalEmulatorTitle("AI AGENTS - 智能助手 v2.0.0");
            factory.setTerminalEmulatorFontConfiguration(fontConfig);
            factory.setTerminalEmulatorColorConfiguration(TerminalEmulatorColorConfiguration.newInstance(TerminalEmulatorPalette.MAC_OS_X_TERMINAL_APP));
            // 4. 直接创建屏幕（纯终端模式）
            Screen screen = new TerminalScreen(factory.createTerminal());
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


    /**
     * 计算文本在指定宽度下需要的行数（改进版）
     * 正确处理换行符、中文字符和特殊字符
     */
    private int calculateTextLines(String text, int width) {
        if (text == null || text.isEmpty()) {
            return 1;
        }

        // 移除 "AI: " 前缀（4个字符）
        String content = text.startsWith("AI: ") ? text.substring(4) : text;

        if (content.isEmpty()) {
            return 1;
        }

        // 先按原文本中的换行符分割，计算每行的行数
        String[] lines = content.split("\n", -1);
        int totalLines = 0;

        for (String line : lines) {
            if (line.isEmpty()) {
                // 空行也需要1行高度
                totalLines++;
                continue;
            }

            // 计算单行文本在指定宽度下需要的行数
            int lineLines = calculateSingleLineWrappedLines(line, width);
            totalLines += lineLines;
        }

        return Math.max(1, totalLines);
    }

    /**
     * 计算单行文本在指定宽度下自动换行需要的行数
     */
    private int calculateSingleLineWrappedLines(String line, int width) {
        if (line == null || line.isEmpty()) {
            return 1;
        }

        int wrappedLines = 1;
        int currentLineLength = 0;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            // 处理中文字符和其他非ASCII字符（占2个字符宽度）
            int charWidth = (c > 127) ? 2 : 1;
            // 特殊处理全角标点符号
            if (c >= 0xFF00 && c <= 0xFFEF) {
                charWidth = 2;
            }

            if (currentLineLength + charWidth > width) {
                wrappedLines++;
                currentLineLength = charWidth;
            } else {
                currentLineLength += charWidth;
            }
        }

        return wrappedLines;
    }

    /**
     * 根据文本内容动态计算并设置TextBox的推荐尺寸
     * 考虑了文本的实际行数和最小高度需求
     */
    private TerminalSize calculateTextBoxSize(String text, int width) {
        int calculatedLines = calculateTextLines(text, width);
        // 根据文本内容动态调整最小高度
        // 短文本（少于100字符）：最小3行
        // 中等文本（100-500字符）：最小4行
        // 长文本（超过500字符）：最小5行
        int minHeight = text.length() > 500 ? 5 : (text.length() > 100 ? 4 : 3);
        int height = Math.max(minHeight, calculatedLines);
        // 限制最大高度，防止过长文本导致布局问题
        height = Math.min(height, 50);
        return new TerminalSize(width, height);
    }

    /**
     * 计算聊天区域的可用宽度（减去前缀标签和边距）
     */
    private int calculateAvailableWidth(int totalWidth) {
        // 聊天区域占85%，再减去前缀标签（大约6个字符宽度）和边距
        return (int) (totalWidth * 0.85) - 10;
    }

    /**
     * 滚动到面板底部
     */
    private void scrollToBottom(Panel contentPanel, Panel scrollPanel, Component input) {
        try {
            // 计算内容面板的总高度
            int totalHeight = 0;
            for (Component component : contentPanel.getChildrenList()) {
                TerminalSize size = component.getPreferredSize();
                if (size != null) {
                    totalHeight += size.getRows();
                }
            }

            // 获取滚动面板的可见区域大小
            TerminalSize scrollPanelSize = scrollPanel.getPreferredSize();
            if (scrollPanelSize == null) {
                scrollPanelSize = scrollPanel.getSize();
            }

            // 计算需要滚动的距离
            int scrollAmount = Math.max(0, totalHeight - scrollPanelSize.getRows() + input.getPreferredSize()
                    .getRows());

            // 更新滚动位置
            scrollPosition.set(scrollAmount);

            // 使内容面板重新布局以反映新的滚动位置
            contentPanel.invalidate();

        } catch (Exception e) {
            log.debug("滚动到底部失败: {}", e.getMessage());
        }
    }

    /**
     * 添加消息后自动滚动到底部
     */
    private void addMessageAndScroll(Panel contentPanel, Panel scrollPanel, Component message, TextBox input) {
        contentPanel.addComponent(message);
        // 自动滚动到底部
        scrollToBottom(contentPanel, scrollPanel, input);
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

        Label titleLabel = new Label("\n=== AI AGENTS ===\n");
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

        window.setComponent(mainPanel);

        // 添加ESC键监听器，按ESC直接结束程序
        window.addWindowListener(new WindowListenerAdapter() {
            @Override
            public void onUnhandledInput(Window basePane, KeyStroke key, AtomicBoolean handled) {
                if (key.getKeyType() == KeyType.Escape) {
                    running.set(false);
                    window.close();
                }
            }
        });

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

        // 左侧聊天面板 (85%宽度) - 使用优化后的聊天主题
        Panel chatPanel = new Panel(new LinearLayout(Direction.VERTICAL));
        chatPanel.setPreferredSize(new TerminalSize(85, 100)); // 85%宽度，100%高度
        chatPanel.setTheme(chatTheme);


        // 使用可滚动的面板来显示消息
        Panel scrollableMsgArea = new Panel(new LinearLayout(Direction.VERTICAL));
        scrollableMsgArea.setTheme(chatTheme);

        // 创建内容容器 - 使用 GridLayout 以支持滚动
        Panel contentPanel = new Panel(new com.googlecode.lanterna.gui2.GridLayout(1)); // 单列网格布局
        contentPanel.setTheme(chatTheme);

        // 创建垂直滚动条 - 优化样式，支持自动滚动
        ScrollBar verticalScrollBar = new ScrollBar(Direction.VERTICAL);
        verticalScrollBar.setTheme(chatTheme);
        // 不再隐藏滚动条，保持可见以便用户可以手动滚动

        // 创建滚动面板容器
        Panel scrollPanel = new Panel(new BorderLayout());
        scrollPanel.setTheme(chatTheme);

        // 将内容面板添加到滚动面板
        scrollPanel.addComponent(contentPanel, BorderLayout.Location.CENTER);
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

                // 为AI响应创建TextBox，其他消息类型使用Label
                if (msg.getType() == com.xr21.ai.agent.entity.ConversationMessage.MessageType.ASSISTANT) {
                    Label aiPrefixLabel = new Label(prefix);
                    aiPrefixLabel.setTheme(chatTheme);
                    contentPanel.addComponent(aiPrefixLabel);
                    TextBox historyTextBox = new TextBox(msg.getContent(), TextBox.Style.MULTI_LINE);
                    historyTextBox.setTheme(chatTheme);
                    historyTextBox.setReadOnly(true);
                    historyTextBox.setVerticalFocusSwitching(false);
                    // 使用动态宽度计算，与聊天区域85%宽度保持一致
                    // 终端默认宽度120，聊天区域占85%，再减去边距
                    int dynamicWidth = Math.max(50, 120 * 85 / 100 - 10);
                    TerminalSize textBoxSize = calculateTextBoxSize(msg.getContent(), dynamicWidth);
                    historyTextBox.setPreferredSize(textBoxSize);
                    // 添加到所有响应TextBox列表
                    allResponseTextBoxes.add(historyTextBox);
                    contentPanel.addComponent(historyTextBox);
                    // 加载历史消息后重新布局并滚动到底部
                    contentPanel.invalidate();
                    scrollToBottom(contentPanel, scrollPanel, historyTextBox);
                } else {
                    Label msgLabel = new Label(prefix + msg.getContent());
                    msgLabel.setTheme(chatTheme);
                    contentPanel.addComponent(msgLabel);
                    // 加载历史消息后滚动到底部
                    scrollToBottom(contentPanel, scrollPanel, currentResponseTextBox.get());
                }
            }
        }

        // 创建自定义 TextBox，支持粘贴，处理 Ctrl+Enter 和 Ctrl+V
        TextBox input = new TextBox("", TextBox.Style.MULTI_LINE) {
            @Override
            public Result handleKeyStroke(KeyStroke keyStroke) {
                // 如果是 Ctrl+Enter，不处理，让它传递到父级发送消息
                if (keyStroke.getKeyType() == KeyType.Enter && keyStroke.isCtrlDown()) {
                    return Result.UNHANDLED;
                }
                // 如果是 Ctrl+V，尝试获取系统剪贴板内容并粘贴
                if (keyStroke.getCharacter() != null && keyStroke.getCharacter() == 'v' && keyStroke.isCtrlDown()) {
                    try {
                        java.awt.datatransfer.Clipboard clipboard = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
                        if (clipboard.isDataFlavorAvailable(java.awt.datatransfer.DataFlavor.stringFlavor)) {
                            String pasteText = (String) clipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor);
                            if (pasteText != null) {
                                // 在当前文本末尾插入粘贴的文本
                                String currentText = getText();
                                String newText = currentText + pasteText;
                                setText(newText);
                                setCaretPosition((int) newText.lines().count(), newText.lines().skip(newText.lines().count() - 1).findAny().orElse("").length());
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
        input.setPreferredSize(new TerminalSize(70, 5)); // 调整输入框宽度为70
        input.setTheme(chatTheme);
        input.setVerticalFocusSwitching(false); // 禁用垂直焦点切换
        // TextBox不支持addStyle，通过主题设置样式

        // 添加快捷键说明面板
        Panel shortcutPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        Label shortcutLabel = new Label("快捷键: Ctrl+Enter 发送 | Ctrl+/- 调整字体 | Esc 返回");
        shortcutLabel.addStyle(SGR.BOLD);
        shortcutLabel.setTheme(modernTheme);
        shortcutLabel.setForegroundColor(new TextColor.RGB(255, 200, 100));
        shortcutPanel.addComponent(shortcutLabel);

        chatPanel.addComponent(wrapWithChatBorder(scrollContainer));
        Label inputLabel = new Label("输入消息:");
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
                        // 根据实际行数动态计算高度
                        String text = responseBox.getText();
                        newSize = calculateTextBoxSize(text, responseBoxWidth);
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
                scrollToBottom(contentPanel, scrollPanel, input);
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
                        Label userLabel = new Label("你: " + txt);
                        userLabel.setTheme(chatTheme);
                        contentPanel.addComponent(userLabel);

                        // 发送用户消息后滚动到底部
                        scrollToBottom(contentPanel, scrollPanel, input);

                        // 2. 立即刷新界面显示用户消息
                        textGUI.getGUIThread().invokeLater(() -> {
                            contentPanel.invalidate();
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
                        Label loading = new Label("🤖 AI正在思考...");
                        loading.setTheme(chatTheme);
                        loading.setForegroundColor(new TextColor.RGB(255, 200, 100));
                        contentPanel.addComponent(loading);
                        loadingLabel.set(loading);

                        // 添加loading后滚动到底部
                        scrollToBottom(contentPanel, scrollPanel, input);

                        // 刷新界面显示loading
                        textGUI.getGUIThread().invokeLater(() -> {
                            contentPanel.invalidate();
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
                                updateUIWithGraph(txt, contentPanel, scrollPanel, sessionManager, currentResponseTextBox,
                                        textGUI, statusPanel, allResponseTextBoxes, loadingLabel, isWaitingForResponse, input);
                            } catch (Exception e) {
                                // 隐藏loading
                                hideLoading(textGUI, contentPanel, scrollPanel, loadingLabel, isWaitingForResponse, input);
                                Label errorLabel = new Label("[错误]: " + e.getMessage());
                                errorLabel.setTheme(chatTheme);
                                errorLabel.setForegroundColor(TextColor.ANSI.RED);
                                contentPanel.addComponent(errorLabel);
                                // 错误消息添加后滚动到底部
                                scrollToBottom(contentPanel, scrollPanel, input);
                                sessionManager.addErrorMessage(sessionId, e.getMessage());
                                textGUI.getGUIThread().invokeLater(() -> {
                                    contentPanel.invalidate();
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

        // 注意：Lanterna 的 Panel 不直接支持 scrollDown/scrollUp 方法
        // 鼠标滚轮事件通过 ScrollBar 隐式处理
        // 如需自定义滚动行为，可使用键盘方向键或 PageUp/PageDown
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

        root.addComponent(table);
        root.addComponent(btnPanel);

        window.setComponent(root);
        textGUI.addWindowAndWait(window);
    }

    private void updateUIWithGraph(String input, Panel contentPanel, Panel scrollPanel, ConversationSessionManager sessionManager,
                                   AtomicReference<TextBox> currentResponseTextBox, WindowBasedTextGUI textGUI,
                                   Panel statusPanel, java.util.List<TextBox> allResponseTextBoxes,
                                   AtomicReference<Label> loadingLabel, AtomicBoolean isWaitingForResponse,
                                   TextBox inputBox) {
        StringBuilder responseBuilder = new StringBuilder();

        Flux<ServerSentEvent<AgentOutput<Object>>> outputFlux = localAgent.processWithGraphV2(
                supervisorAgent, input, currentSessionId, interruptionMetadata.get(), stateUpdate);

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
                        hideLoading(textGUI, contentPanel, scrollPanel, loadingLabel, isWaitingForResponse, inputBox);
                    });
                }

                responseBuilder.append(chunk);
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
                    Label aiLabel = new Label("AI: ");
                    aiLabel.setTheme(chatTheme);
                    contentPanel.addComponent(aiLabel);
                    contentPanel.addComponent(responseTextBox);

                    // 添加新的AI响应后重新布局并滚动到底部
                    contentPanel.invalidate();
                    scrollToBottom(contentPanel, scrollPanel, inputBox);
                } else {
                    // 更新现有TextBox的内容
                    responseTextBox.setText(responseBuilder.toString());
                    // 使用辅助方法根据实际行数动态设置高度
                    TerminalSize size = calculateTextBoxSize(responseBuilder.toString(), dynamicWidth);
                    responseTextBox.setPreferredSize(size);

                    // 重新布局所有响应TextBox以防止重叠
                    for (TextBox textBox : allResponseTextBoxes) {
                        if (textBox != null && textBox != responseTextBox) {
                            String text = textBox.getText();
                            int textHeight = calculateTextBoxSize(text, dynamicWidth).getRows();
                            textHeight = Math.max(3, textHeight);
                            textBox.setPreferredSize(new TerminalSize(dynamicWidth, textHeight));
                        }
                    }

                    // 重新布局contentPanel
                    contentPanel.invalidate();
                    // 自动滚动到底部
                    scrollToBottom(contentPanel, scrollPanel, inputBox);

                    TextBox.TextBoxRenderer renderer = responseTextBox.getRenderer();
                    if (renderer instanceof TextBox.DefaultTextBoxRenderer defaultRenderer) {
                        // 计算文本总行数
                        int totalLines = responseTextBox.getLineCount();
                        TerminalPosition viewTopLeft = defaultRenderer.getViewTopLeft();
                        defaultRenderer.setViewTopLeft(viewTopLeft.withRow(totalLines));
                        defaultRenderer.setHideScrollBars(true);
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
                                feedbackLabel.setForegroundColor(new TextColor.RGB(255, 200, 100)); // 柔和橙色
                                contentPanel.addComponent(feedbackLabel);
                                // 添加工具反馈后滚动到底部
                                scrollToBottom(contentPanel, scrollPanel, inputBox);
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
                    // 标记发生了工具调用，后续响应需要使用新的TextBox
                    AtomicBoolean toolCallOccurred = new AtomicBoolean(false);

                    for (AssistantMessage.ToolCall toolCall : message.getToolCalls()) {
                        String toolCallMsg = String.format("\n[工具调用]: %s 参数: %s", toolCall.name(), toolCall.arguments());
                        System.err.println(toolCallMsg);
                        // 在GUI线程中添加工具调用消息
                        try {
                            if (textGUI != null) {
                                textGUI.getGUIThread().invokeLater(() -> {
                                    Label toolCallLabel = new Label(toolCallMsg);
                                    toolCallLabel.setTheme(theme);
                                    toolCallLabel.setForegroundColor(new TextColor.RGB(100, 200, 255)); // 柔和蓝色
                                    contentPanel.addComponent(toolCallLabel);
                                    // 添加工具调用后滚动到底部
                                    scrollToBottom(contentPanel, scrollPanel, toolCallLabel);
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
            hideLoading(textGUI, contentPanel, scrollPanel, loadingLabel, isWaitingForResponse, inputBox);

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

            // 隐藏loading并重新启用输入框
            hideLoading(textGUI, contentPanel, scrollPanel, loadingLabel, isWaitingForResponse, inputBox);

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

    /**
     * 隐藏loading状态并重新启用输入框，同时聚焦到输入框
     */
    private void hideLoading(WindowBasedTextGUI textGUI, Panel contentPanel, Panel scrollPanel,
                             AtomicReference<Label> loadingLabel, AtomicBoolean isWaitingForResponse,
                             TextBox inputBox) {
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
        scrollToBottom(contentPanel, scrollPanel, inputBox);

        // 刷新界面
        if (textGUI != null) {
            textGUI.getGUIThread().invokeLater(() -> {
                contentPanel.invalidate();
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
        Label title = new Label("=== 会话状态 ===");
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
                    Label idTitle = new Label("会话ID:");
                    idTitle.setTheme(modernTheme);
                    idTitle.addStyle(SGR.BOLD);
                    statusPanel.addComponent(idTitle);
                    Label idLabel = new Label(sessionId.length() > 12 ? sessionId.substring(0, 12) + "..." : sessionId);
                    idLabel.setTheme(modernTheme);
                    idLabel.setForegroundColor(new TextColor.RGB(150, 220, 255));
                    statusPanel.addComponent(idLabel);

                    statusPanel.addComponent(new Label(""));

                    // 创建时间
                    Label timeTitle = new Label("创建时间:");
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

                    Label userLabel = new Label("用户: " + userCount);
                    userLabel.setTheme(modernTheme);
                    userLabel.setForegroundColor(new TextColor.RGB(150, 255, 150));
                    statusPanel.addComponent(userLabel);

                    Label aiLabel = new Label("AI: " + assistantCount);
                    aiLabel.setTheme(modernTheme);
                    aiLabel.setForegroundColor(new TextColor.RGB(150, 220, 255));
                    statusPanel.addComponent(aiLabel);

                    Label systemLabel = new Label("系统: " + systemCount);
                    systemLabel.setTheme(modernTheme);
                    systemLabel.setForegroundColor(new TextColor.RGB(255, 200, 100));
                    statusPanel.addComponent(systemLabel);

                    Label toolLabel = new Label("工具: " + toolCount);
                    toolLabel.setTheme(modernTheme);
                    toolLabel.setForegroundColor(new TextColor.RGB(255, 150, 200));
                    statusPanel.addComponent(toolLabel);

                    statusPanel.addComponent(new Label(""));

                    // 简要描述
                    Label descTitle = new Label("简要描述:");
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
                        Label noDescLabel = new Label("暂无描述");
                        noDescLabel.setTheme(modernTheme);
                        noDescLabel.setForegroundColor(new TextColor.RGB(150, 150, 150));
                        statusPanel.addComponent(noDescLabel);
                    }
                } else {
                    // 新会话还没有任何消息
                    Label newIdTitle = new Label("会话ID:");
                    newIdTitle.setTheme(modernTheme);
                    newIdTitle.addStyle(SGR.BOLD);
                    statusPanel.addComponent(newIdTitle);
                    Label idLabel = new Label(sessionId.length() > 12 ? sessionId.substring(0, 12) + "..." : sessionId);
                    idLabel.setTheme(modernTheme);
                    idLabel.setForegroundColor(new TextColor.RGB(150, 220, 255));
                    statusPanel.addComponent(idLabel);

                    statusPanel.addComponent(new Label(""));
                    Label statusLabel = new Label("状态: 新会话");
                    statusLabel.setTheme(modernTheme);
                    statusLabel.setForegroundColor(new TextColor.RGB(150, 255, 150));
                    statusPanel.addComponent(statusLabel);
                    Label msgCountLabel = new Label("消息数: 0");
                    msgCountLabel.setTheme(modernTheme);
                    msgCountLabel.setForegroundColor(new TextColor.RGB(200, 200, 200));
                    statusPanel.addComponent(msgCountLabel);
                }
            } catch (Exception e) {
                Label errorTitle = new Label("状态更新失败:");
                errorTitle.setTheme(modernTheme);
                errorTitle.addStyle(SGR.BOLD);
                statusPanel.addComponent(errorTitle);
                Label errorLabel = new Label(e.getMessage());
                errorLabel.setTheme(modernTheme);
                errorLabel.setForegroundColor(new TextColor.RGB(255, 100, 100));
                statusPanel.addComponent(errorLabel);
            }
        } else {
            Label noSessionLabel = new Label("无会话信息");
            noSessionLabel.setTheme(modernTheme);
            noSessionLabel.setForegroundColor(new TextColor.RGB(150, 150, 150));
            statusPanel.addComponent(noSessionLabel);
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

    /**
     * 设置自定义窗口图标，替换默认JDK图标
     */
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
                    if (parent != null) {
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