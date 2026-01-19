package com.xr21.ai.agent.tui;

import com.googlecode.lanterna.terminal.swing.SwingTerminalFontConfiguration;
import org.slf4j.Logger;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.util.HashSet;
import java.util.Set;

/**
 * 字体工具类 - 处理字体查找和配置
 */
public class FontUtils {

    private static final int DEFAULT_FONT_SIZE = 16;
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(FontUtils.class);

    private FontUtils() {
        // 工具类，禁止实例化
    }

    /**
     * 创建优化后的字体配置 - 支持多种现代等宽字体
     */
    public static SwingTerminalFontConfiguration createOptimizedFontConfiguration() {
        return createOptimizedFontConfiguration(DEFAULT_FONT_SIZE);
    }

    /**
     * 创建优化后的字体配置
     * @param fontSize 字体大小
     */
    public static SwingTerminalFontConfiguration createOptimizedFontConfiguration(int fontSize) {
        String selectedFont = findAvailableFont();
        Font plainFont = new Font(selectedFont, Font.PLAIN, fontSize);
        Font italicFont = new Font(selectedFont, Font.ITALIC, fontSize);
        Font boldFont = new Font(selectedFont, Font.BOLD, fontSize);
        return SwingTerminalFontConfiguration.newInstance(plainFont, italicFont, boldFont);
    }

    /**
     * 查找系统中可用的等宽字体
     * @return 可用的等宽字体名称
     */
    public static String findAvailableFont() {
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

        String defaultMonospaced = "Monospaced";
        if (monospacedFonts.contains(defaultMonospaced)) {
            log.info("使用系统默认等宽字体: {}", defaultMonospaced);
            return defaultMonospaced;
        }

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
     * @return 如果是等宽字体返回 true
     */
    public static boolean isMonospacedFont(String fontName) {
        try {
            Font font = new Font(fontName, Font.PLAIN, 12);
            if (font.getFamily().equals("Dialog")) {
                return false;
            }

            // 测试字符：'i' (窄字符) 和 'W' (宽字符)
            FontRenderContext frc = new FontRenderContext(null, true, true);
            double widthI = font.getStringBounds("i", frc).getWidth();
            double widthW = font.getStringBounds("W", frc).getWidth();

            double tolerance = 0.1;
            return Math.abs(widthI - widthW) < tolerance;
        } catch (Exception e) {
            log.trace("检查字体等宽性失败: {}, 原因: {}", fontName, e.getMessage());
            return false;
        }
    }
}
