package com.xr21.ai.agent.tui;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.SimpleTheme;
import com.googlecode.lanterna.graphics.Theme;

/**
 * UI主题配置类 - 集中管理所有颜色和主题配置
 */
public class UIThemeConfig {

    // ==================== 聊天窗口配色 ====================
    /** 聊天区域主背景 - 深灰蓝 */
    public static final TextColor CHAT_BG = new TextColor.RGB(30, 32, 40);
    /** 聊天输入区域 - 稍浅的灰蓝 */
    public static final TextColor CHAT_EDITABLE_BG = new TextColor.RGB(40, 44, 52);
    /** 聊天选中状态 - 柔和蓝 */
    public static final TextColor CHAT_SELECTED_BG = new TextColor.RGB(60, 100, 140);

    // ==================== 会话状态配色 ====================
    /** 会话状态主背景 - 深色灰 */
    public static final TextColor STATUS_BG = new TextColor.RGB(25, 28, 35);
    /** 会话状态编辑区域 - 中等灰 */
    public static final TextColor STATUS_EDITABLE_BG = new TextColor.RGB(35, 40, 48);
    /** 会话状态选中 - 柔和灰 */
    public static final TextColor STATUS_SELECTED_BG = new TextColor.RGB(80, 85, 95);

    // ==================== 公共配色 ====================
    /** 强调色 - 柔和青蓝 */
    public static final TextColor ACCENT_COLOR = new TextColor.RGB(70, 180, 240);
    /** GUI主背景 - 更深的色调 */
    public static final TextColor GUI_BACKGROUND = new TextColor.RGB(22, 24, 30);
    /** 兼容旧代码 */
    public static final TextColor TRANSPARENT_BG = STATUS_BG;

    // ==================== 消息类型颜色 ====================
    public static final TextColor USER_MSG_COLOR = new TextColor.RGB(150, 255, 150);
    public static final TextColor AI_MSG_COLOR = new TextColor.RGB(150, 220, 255);
    public static final TextColor SYSTEM_MSG_COLOR = new TextColor.RGB(255, 200, 100);
    public static final TextColor TOOL_MSG_COLOR = new TextColor.RGB(255, 150, 200);
    public static final TextColor ERROR_MSG_COLOR = new TextColor.RGB(255, 100, 100);
    public static final TextColor DESC_MSG_COLOR = new TextColor.RGB(200, 200, 255);
    public static final TextColor LOADING_COLOR = new TextColor.RGB(255, 200, 100);

    private UIThemeConfig() {
        // 工具类，禁止实例化
    }

    /**
     * 创建现代化主题 - 用于会话状态面板
     */
    public static Theme createModernTheme() {
        return SimpleTheme.makeTheme(
                true,                                // activeIsBold
                TextColor.ANSI.WHITE_BRIGHT,         // baseForeground
                STATUS_BG,                           // baseBackground
                ACCENT_COLOR,                        // editableForeground
                STATUS_EDITABLE_BG,                  // editableBackground
                TextColor.ANSI.WHITE_BRIGHT,         // selectedForeground
                STATUS_SELECTED_BG,                  // selectedBackground
                GUI_BACKGROUND                       // guiBackground
        );
    }

    /**
     * 创建聊天窗口主题 - 用于聊天区域
     */
    public static Theme createChatTheme() {
        return SimpleTheme.makeTheme(
                true,                                // activeIsBold
                TextColor.ANSI.WHITE_BRIGHT,         // baseForeground
                CHAT_BG,                             // baseBackground
                new TextColor.RGB(140, 210, 255),    // editableForeground
                CHAT_EDITABLE_BG,                    // editableBackground
                TextColor.ANSI.WHITE_BRIGHT,         // selectedForeground
                CHAT_SELECTED_BG,                    // selectedBackground
                CHAT_BG                              // guiBackground
        );
    }
}
