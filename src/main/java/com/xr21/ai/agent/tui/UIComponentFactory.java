package com.xr21.ai.agent.tui;

import com.googlecode.lanterna.SGR;
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
     * 创建只读的多行TextBox
     */
    public TextBox createReadOnlyTextBox(String text, int width) {
        TextBox textBox = new TextBox(text, TextBox.Style.MULTI_LINE);
        textBox.setTheme(chatTheme);
        textBox.setReadOnly(true);
        textBox.setVerticalFocusSwitching(false);
        TerminalSize size = TextMeasureUtils.calculateTextBoxSize(text, width);
        textBox.setPreferredSize(size);
        return textBox;
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
