package com.xr21.ai.agent.tui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 滚动辅助类 - 管理滚动条和自动滚动
 */
public class ScrollHelper {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(ScrollHelper.class);
    private final AtomicInteger scrollPosition = new AtomicInteger(0);
    private final AtomicBoolean autoScrollEnabled = new AtomicBoolean(true);
    private final AtomicInteger messageAreaLines = new AtomicInteger(0);
    private ScrollBar verticalScrollBar;

    public ScrollHelper() {
    }

    public ScrollBar getVerticalScrollBar() {
        return verticalScrollBar;
    }

    public void setVerticalScrollBar(ScrollBar scrollBar) {
        this.verticalScrollBar = scrollBar;
    }

    /**
     * 滚动到面板底部
     */
    public void scrollToBottom(Panel contentPanel, Panel scrollPanel, WindowBasedTextGUI textGUI) {
        try {
            autoScrollEnabled.set(true);

            contentPanel.invalidate();
            scrollPanel.invalidate();

            int totalHeight = contentPanel.getSize() != null ? contentPanel.getSize().getRows() : 0;
            messageAreaLines.set(totalHeight);

            TerminalSize scrollPanelSize = scrollPanel.getSize();
            int visibleHeight = scrollPanelSize != null ? scrollPanelSize.getRows() : 20;
            int scrollRange = Math.max(0, totalHeight - visibleHeight);

            scrollPosition.set(scrollRange);

            if (verticalScrollBar != null) {
                verticalScrollBar.setVisible(true);
                verticalScrollBar.setScrollMaximum(Math.max(0, scrollRange));
                verticalScrollBar.setScrollPosition(Math.max(0, scrollRange));
            }

            refreshGUI(textGUI);

            // 延迟再次刷新，确保布局完全更新
            textGUI.getGUIThread().invokeLater(() -> {
                try {
                    Thread.sleep(50);
                    contentPanel.invalidate();
                    scrollPanel.invalidate();

                    int newTotalHeight = 0;
                    for (Component component : contentPanel.getChildrenList()) {
                        TerminalSize size = component.getPreferredSize();
                        if (size != null) {
                            newTotalHeight += size.getRows();
                        }
                    }

                    int newScrollRange = Math.max(0, newTotalHeight - visibleHeight);
                    if (verticalScrollBar != null) {
                        verticalScrollBar.setScrollMaximum(Math.max(0, newScrollRange));
                        verticalScrollBar.setScrollPosition(Math.max(0, newScrollRange));
                    }

                    if (textGUI.getScreen() != null) {
                        textGUI.getScreen().refresh();
                    }
                } catch (Exception e) {
                    log.debug("延迟刷新失败: {}", e.getMessage());
                }
            });
        } catch (Exception e) {
            log.debug("滚动到底部失败: {}", e.getMessage());
        }
    }

    /**
     * 更新滚动条范围
     */
    public void updateScrollBarRange(Panel contentPanel, Panel scrollPanel) {
        try {
            int totalHeight = 0;
            for (Component component : contentPanel.getChildrenList()) {
                TerminalSize size = component.getPreferredSize();
                if (size != null) {
                    totalHeight += size.getRows();
                }
            }

            TerminalSize scrollPanelSize = scrollPanel.getPreferredSize();
            if (scrollPanelSize == null) {
                scrollPanelSize = scrollPanel.getSize();
            }
            int visibleHeight = scrollPanelSize != null ? scrollPanelSize.getRows() : 20;

            int newRange = Math.max(0, totalHeight - visibleHeight);
            messageAreaLines.set(totalHeight);

            if (verticalScrollBar != null) {
                verticalScrollBar.setScrollMaximum(Math.max(0, newRange));
                if (autoScrollEnabled.get()) {
                    verticalScrollBar.setScrollPosition(Math.max(0, newRange));
                }
            }
        } catch (Exception e) {
            log.debug("更新滚动条范围失败: {}", e.getMessage());
        }
    }

    /**
     * 重新计算并更新所有消息组件的高度
     */
    public void recalculateAllMessageHeights(Panel contentPanel, Panel scrollPanel,
                                             WindowBasedTextGUI textGUI,
                                             List<TextBox> allResponseTextBoxes) {
        try {
            int dynamicWidth = textGUI.getScreen().getTerminalSize().getColumns() * 75 / 100 - 10;
            dynamicWidth = Math.max(50, dynamicWidth);

            for (TextBox textBox : allResponseTextBoxes) {
                if (textBox != null) {
                    String text = textBox.getText();
                    if (text != null && !text.isEmpty()) {
                        TerminalSize newSize = TextMeasureUtils.calculateTextBoxSize(text, dynamicWidth);
                        textBox.setPreferredSize(newSize);
                    }
                }
            }

            contentPanel.invalidate();
            scrollPanel.invalidate();
            updateScrollBarRange(contentPanel, scrollPanel);
            refreshGUI(textGUI);
        } catch (Exception e) {
            log.debug("重新计算消息高度失败: {}", e.getMessage());
        }
    }

    /**
     * 刷新GUI界面
     */
    public void refreshGUI(WindowBasedTextGUI textGUI) {
        textGUI.getGUIThread().invokeLater(() -> {
            try {
                if (textGUI.getScreen() != null) {
                    textGUI.getScreen().refresh();
                }
            } catch (IOException e) {
                log.error("屏幕刷新失败", e);
            }
        });
    }

    /**
     * 刷新多个面板并更新GUI
     */
    public void refreshPanels(WindowBasedTextGUI textGUI, Panel... panels) {
        textGUI.getGUIThread().invokeLater(() -> {
            for (Panel panel : panels) {
                if (panel != null) {
                    panel.invalidate();
                }
            }
            try {
                if (textGUI.getScreen() != null) {
                    textGUI.getScreen().refresh();
                }
            } catch (IOException e) {
                log.error("屏幕刷新失败", e);
            }
        });
    }
}
