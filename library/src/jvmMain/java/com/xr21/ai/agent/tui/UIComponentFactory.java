package com.xr21.ai.agent.tui;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.Theme;
import com.googlecode.lanterna.gui2.*;

/**
 * UI组件工厂 - 简化UI组件的创建
 */
public class UIComponentFactory {

    private final Theme chatTheme;
    private final Theme modernTheme;

    public UIComponentFactory() {
        this.chatTheme = UIThemeConfig.createChatTheme();
        this.modernTheme = UIThemeConfig.createModernTheme();
    }

    public UIComponentFactory(Theme chatTheme, Theme modernTheme) {
        this.chatTheme = chatTheme;
        this.modernTheme = modernTheme;
    }

    /**
     * 创建带样式的Label
     */
    public Label createLabel(String text, Theme theme) {
        Label label = new Label(text);
        label.setTheme(theme);
        return label;
    }

    /**
     * 创建带样式的Label（使用聊天主题）
     */
    public Label createChatLabel(String text) {
        return createLabel(text, chatTheme);
    }

    /**
     * 创建带样式的Label（使用现代主题）
     */
    public Label createModernLabel(String text) {
        return createLabel(text, modernTheme);
    }

    /**
     * 创建带颜色和样式的Label
     */
    public Label createStyledLabel(String text, TextColor foregroundColor, SGR... styles) {
        Label label = new Label(text);
        label.setTheme(modernTheme);
        if (foregroundColor != null) {
            label.setForegroundColor(foregroundColor);
        }
        for (SGR style : styles) {
            label.addStyle(style);
        }
        return label;
    }

    /**
     * 创建标题Label
     */
    public Label createTitleLabel(String text) {
        Label label = new Label(text);
        label.addStyle(SGR.BOLD);
        label.addStyle(SGR.UNDERLINE);
        label.setTheme(modernTheme);
        label.setForegroundColor(UIThemeConfig.ACCENT_COLOR);
        return label;
    }

    /**
     * 创建只读的多行TextBox - 优化版本，支持更好的自动换行
     */
    public TextBox createReadOnlyTextBox(String text, int width) {
        TextBox textBox = new TextBox(text, TextBox.Style.MULTI_LINE);
        textBox.setTheme(chatTheme);
        textBox.setReadOnly(true);
        textBox.setVerticalFocusSwitching(false);

        // 优化：确保最小宽度，避免过窄导致换行异常
        int optimizedWidth = Math.max(width, 30);

        // 优化：使用改进的文本测量算法
        TerminalSize size = TextMeasureUtils.calculateTextBoxSize(text, optimizedWidth);
        textBox.setPreferredSize(size);

        // 优化：设置文本换行策略
        enableWordWrapping(textBox);

        return textBox;
    }

    /**
     * 创建自适应宽度的只读多行TextBox
     * @param text 文本内容
     * @param terminalColumns 终端列数
     * @param percentage 宽度百分比（0-100）
     * @param margin 边距
     * @return 配置好的TextBox
     */
    public TextBox createAdaptiveReadOnlyTextBox(String text, int terminalColumns, int percentage, int margin) {
        int dynamicWidth = TextMeasureUtils.calculateDynamicWidth(terminalColumns, percentage, margin);
        return createReadOnlyTextBox(text, dynamicWidth);
    }

    /**
     * 为TextBox启用更好的单词换行功能
     */
    private void enableWordWrapping(TextBox textBox) {
        // 通过设置自定义渲染器来改善换行效果
        TextBox.TextBoxRenderer renderer = textBox.getRenderer();
        if (renderer instanceof TextBox.DefaultTextBoxRenderer defaultRenderer) {
            // 隐藏滚动条，让文本自然换行
            int totalLines = textBox.getLineCount();
            TerminalPosition viewTopLeft = defaultRenderer.getViewTopLeft();
            defaultRenderer.setViewTopLeft(viewTopLeft.withRow(totalLines));
            defaultRenderer.setHideScrollBars(true);
        }

    }

    /**
     * 创建可编辑的多行TextBox
     */
    public TextBox createEditableTextBox(int width, int height) {
        TextBox textBox = new TextBox("", TextBox.Style.MULTI_LINE);
        textBox.setPreferredSize(new TerminalSize(width, height));
        textBox.setTheme(chatTheme);
        textBox.setVerticalFocusSwitching(false);
        return textBox;
    }

    /**
     * 创建带尺寸的按钮
     */
    public Button createButton(String text, Runnable action, int width) {
        Button button = new Button(text, action);
        button.setPreferredSize(new TerminalSize(width, 1));
        button.setTheme(modernTheme);
        return button;
    }

    /**
     * 创建Loading标签
     */
    public Label createLoadingLabel(String text) {
        Label label = new Label(text);
        label.setTheme(chatTheme);
        label.setForegroundColor(UIThemeConfig.LOADING_COLOR);
        return label;
    }

    /**
     * 创建错误消息Label
     */
    public Label createErrorLabel(String message) {
        Label label = new Label("[错误]: " + message);
        label.setTheme(chatTheme);
        label.setForegroundColor(UIThemeConfig.ERROR_MSG_COLOR);
        return label;
    }

    /**
     * 创建垂直布局的Panel
     */
    public Panel createVerticalPanel() {
        Panel panel = new Panel(new LinearLayout(Direction.VERTICAL));
        return panel;
    }

    /**
     * 创建水平布局的Panel
     */
    public Panel createHorizontalPanel() {
        Panel panel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        return panel;
    }

    /**
     * 创建带主题的垂直Panel
     */
    public Panel createThemedVerticalPanel(Theme theme) {
        Panel panel = createVerticalPanel();
        panel.setTheme(theme);
        return panel;
    }

    /**
     * 创建滚动条
     */
    public ScrollBar createVerticalScrollBar(Theme theme) {
        ScrollBar scrollBar = new ScrollBar(Direction.VERTICAL);
        scrollBar.setTheme(theme);
        scrollBar.setScrollMaximum(100);
        scrollBar.setScrollPosition(0);
        scrollBar.setVisible(true);
        return scrollBar;
    }

    public Theme getChatTheme() {
        return chatTheme;
    }

    public Theme getModernTheme() {
        return modernTheme;
    }
}
