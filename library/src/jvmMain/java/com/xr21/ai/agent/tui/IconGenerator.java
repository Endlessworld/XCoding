package com.xr21.ai.agent.tui;

import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.swing.SwingTerminalFrame;
import org.slf4j.Logger;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * 图标生成器 - 创建自定义窗口图标
 */
public class IconGenerator {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(IconGenerator.class);

    private IconGenerator() {
        // 工具类，禁止实例化
    }

    /**
     * 设置自定义窗口图标，替换默认JDK图标
     */
    public static void setCustomIcon(Screen screen) {
        try {
            if (screen instanceof TerminalScreen terminalScreen) {
                if (terminalScreen.getTerminal() instanceof SwingTerminalFrame swingTerminalFrame) {
                    Container parent = swingTerminalFrame.getRootPane().getParent();
                    while (parent != null && !(parent instanceof Frame)) {
                        parent = parent.getParent();
                    }
                    if (parent != null) {
                        Frame frame = (Frame) parent;
                        BufferedImage icon = createRobotIcon();
                        frame.setIconImage(icon);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("设置自定义图标失败", e);
        }
    }

    /**
     * 创建机器人图标（16x16像素）
     */
    public static BufferedImage createRobotIcon() {
        BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = icon.createGraphics();

        // 设置抗锯齿和高质量渲染
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // 绘制渐变背景
        GradientPaint gradient = new GradientPaint(0, 0,
                new Color(100, 200, 255), 8, 8,
                new Color(150, 100, 255));
        g2d.setPaint(gradient);
        g2d.fillRoundRect(1, 1, 14, 14, 3, 3);

        // 绘制机器人头部
        g2d.setColor(new Color(50, 50, 80)); // 深蓝灰色
        g2d.fillRoundRect(3, 4, 10, 8, 2, 2);

        // 绘制发光的眼睛
        g2d.setColor(new Color(150, 220, 255)); // 明亮青色
        g2d.fillOval(5, 6, 2, 2);
        g2d.fillOval(9, 6, 2, 2);

        // 绘制眼睛高光
        g2d.setColor(Color.WHITE);
        g2d.fillOval(5, 6, 1, 1);
        g2d.fillOval(9, 6, 1, 1);

        // 绘制微笑嘴巴
        g2d.setColor(new Color(255, 150, 200)); // 粉色
        g2d.drawArc(6, 7, 4, 3, 0, -180);

        g2d.dispose();
        return icon;
    }
}
