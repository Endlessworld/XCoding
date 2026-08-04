/*
 * Copyright \u00a9 2026 XR21 Team. All rights reserved.
 */
package com.xr21.ai.agent.tui.layout;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.toolkit.element.Size;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.toolkit.event.KeyEventHandler;
import dev.tamboui.toolkit.event.MouseEventHandler;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.block.Title;
import dev.tamboui.widgets.common.ScrollBarPolicy;
import dev.tamboui.widgets.scrollbar.Scrollbar;
import dev.tamboui.widgets.scrollbar.ScrollbarOrientation;
import dev.tamboui.widgets.scrollbar.ScrollbarState;

/**
 * 一个可滚动的垂直容器，主要用于展示多个可状态的 MarkdownElement（一条聊天消息一个项）。
 *
 * <p>提供：
 * <ul>
 *   <li>统一的垂直滚动与滚动条</li>
 *   <li>可选 followTail：新内容到达时自动跳到最底</li>
 *   <li>点击命中测试：点击某个项时调用 onItemClick</li>
 *   <li>键盘上/下/页/首尾、鼠标滚轮、点击切焦点全部内置</li>
 * </ul>
 *
 * <p>该元素不会干预条件/样式解析，所有子项的渲染、样式、CSS 依赖
 * RenderContext 传递。
 */
public final class MarkdownListElement extends StyledElement<MarkdownListElement> {

    private final List<Element> items = new ArrayList<>();
    private final List<Integer> itemHeights = new ArrayList<>();
    private final ScrollbarState scrollbarState = new ScrollbarState();
    private ScrollBarPolicy scrollbarPolicy = ScrollBarPolicy.AS_NEEDED;
    private Color borderColor;
    private Color focusedBorderColor;
    private BorderType borderType = BorderType.ROUNDED;
    private String title;
    private int padding = 0;
    private BiConsumer<Integer, MouseEvent> onItemClick;
    private boolean followTail = true;
    private Color scrollbarThumbColor;
    private Color scrollbarTrackColor;
    private KeyEventHandler externalKeyHandler;
    private MouseEventHandler externalMouseHandler;
    /** 外部位置保存（面向上下文 click 调度）。 */
    private int lastMouseX = -1;
    private int lastMouseY = -1;

    public MarkdownListElement() {
    }

    // ==================== 构造 API ====================

    public MarkdownListElement addItem(Element item) {
        items.add(item);
        return this;
    }

    public MarkdownListElement addItems(List<? extends Element> more) {
        items.addAll(more);
        return this;
    }

    public MarkdownListElement items(List<? extends Element> list) {
        items.clear();
        if (list != null) items.addAll(list);
        return this;
    }

    public MarkdownListElement clear() {
        items.clear();
        return this;
    }

    // ==================== 样式 / 边框 ====================

    public MarkdownListElement borderType(BorderType type) {
        this.borderType = type;
        return this;
    }

    public MarkdownListElement borderColor(Color color) {
        this.borderColor = color;
        return this;
    }

    public MarkdownListElement focusedBorderColor(Color color) {
        this.focusedBorderColor = color;
        return this;
    }

    public MarkdownListElement title(String title) {
        this.title = title;
        return this;
    }

    public MarkdownListElement padding(int n) {
        this.padding = Math.max(0, n);
        return this;
    }

    public MarkdownListElement scrollbar(ScrollBarPolicy policy) {
        this.scrollbarPolicy = policy == null ? ScrollBarPolicy.AS_NEEDED : policy;
        return this;
    }

    public MarkdownListElement scrollbarThumbColor(Color color) {
        this.scrollbarThumbColor = color;
        return this;
    }

    public MarkdownListElement scrollbarTrackColor(Color color) {
        this.scrollbarTrackColor = color;
        return this;
    }

    // ==================== 交互 ====================

    public MarkdownListElement onItemClick(BiConsumer<Integer, MouseEvent> handler) {
        this.onItemClick = handler;
        return this;
    }

    public MarkdownListElement followTail(boolean enabled) {
        this.followTail = enabled;
        return this;
    }

    public boolean followTail() {
        return followTail;
    }

    public ScrollbarState scrollbarState() {
        return scrollbarState;
    }

    public void scrollToBottom() {
        followTail = true;
        // position 会在下次 renderContent 时被正确计算为 maxScrollOffset
    }

    public void scrollUp(int lines) {
        followTail = false;
        for (int i = 0; i < lines; i++) scrollbarState.prev();
    }

    public void scrollDown(int lines) {
        for (int i = 0; i < lines; i++) scrollbarState.next();
        // 行级滚动：当 position >= maxScrollOffset 时认为到达底部
        int maxScrollOffset = Math.max(0, scrollbarState.contentLength() - scrollbarState.viewportContentLength());
        if (scrollbarState.position() >= maxScrollOffset) followTail = true;
    }

    // ==================== 保留外部 handler（ChatPanelElement 转发事件用） ====================

    @Override
    public MarkdownListElement onKeyEvent(KeyEventHandler handler) {
        this.externalKeyHandler = handler;
        return this;
    }

    @Override
    public MarkdownListElement onMouseEvent(MouseEventHandler handler) {
        this.externalMouseHandler = handler;
        return this;
    }

    @Override
    public KeyEventHandler keyEventHandler() {
        return externalKeyHandler;
    }

    @Override
    public MouseEventHandler mouseEventHandler() {
        return externalMouseHandler;
    }

    // ==================== 布局 / 渲染 ====================

    @Override
    public Size preferredSize(int availableWidth, int availableHeight, RenderContext context) {
        measure(availableWidth, context);
        int height = totalContentHeight();
        if (padding > 0) height += padding * 2;
        return Size.of(availableWidth, height);
    }

    @Override
    public Constraint constraint() {
        return layoutConstraint;
    }

    @Override
    protected void renderContent(Frame frame, Rect area, RenderContext context) {
        if (area.isEmpty()) return;

        // 1. 画边框 / title
        Rect contentArea = area;
        if (title != null || borderType != null || focusedBorderColor != null || borderColor != null) {
            boolean isFocused = elementId != null && context.isFocused(elementId);
            Color effectiveBorderColor = isFocused && focusedBorderColor != null
                    ? focusedBorderColor : borderColor;

            Block.Builder bb = Block.builder()
                    .borders(Borders.ALL)
                    .styleResolver(styleResolver(context));
            if (title != null) bb.title(Title.from(title));
            if (borderType != null) bb.borderType(borderType);
            if (effectiveBorderColor != null) bb.borderColor(effectiveBorderColor);
            Block block = bb.build();
            block.render(area, frame.buffer());
            contentArea = block.inner(area);
        }
        if (contentArea.isEmpty()) return;

        // 2. padding
        if (padding > 0) {
            int pw = Math.min(padding, contentArea.width() / 2);
            int ph = Math.min(padding, contentArea.height() / 2);
            contentArea = new Rect(
                    contentArea.x() + pw,
                    contentArea.y() + ph,
                    contentArea.width() - 2 * pw,
                    contentArea.height() - 2 * ph);
            if (contentArea.isEmpty()) return;
        }

        // 3. 先用 contentArea 宽度做一次初步测量，判断是否需要滚动条
        measure(contentArea.width(), context);
        int total = totalContentHeight();
        boolean reserveScrollbar = scrollbarPolicy == ScrollBarPolicy.ALWAYS
                || (scrollbarPolicy == ScrollBarPolicy.AS_NEEDED && total > contentArea.height());
        int textWidth = contentArea.width();
        if (reserveScrollbar && textWidth > 1) textWidth -= 1;

        // 如果预留了滚动条导致宽度变化，用实际渲染宽度重新测量
        if (textWidth != contentArea.width()) {
            measure(textWidth, context);
            total = totalContentHeight();
            // 重新判断是否需要滚动条（内容高度可能因宽度变化而变化）
            reserveScrollbar = scrollbarPolicy == ScrollBarPolicy.ALWAYS
                    || (scrollbarPolicy == ScrollBarPolicy.AS_NEEDED && total > contentArea.height());
            if (!reserveScrollbar && textWidth < contentArea.width()) {
                // 不需要滚动条了，恢复完整宽度并重新测量
                textWidth = contentArea.width();
                measure(textWidth, context);
                total = totalContentHeight();
            }
        }

        Rect textArea = new Rect(contentArea.x(), contentArea.y(), textWidth, contentArea.height());
        scrollbarState.contentLength(total);
        scrollbarState.viewportContentLength(contentArea.height());
        // 计算最大有效滚动偏移：确保最后一行内容刚好在视口底部
        int maxScrollOffset = Math.max(0, total - contentArea.height());
        if (followTail) {
            scrollbarState.position(maxScrollOffset);
        } else {
            if (scrollbarState.position() > maxScrollOffset) {
                scrollbarState.position(maxScrollOffset);
            }
        }
        int scrollOffset = scrollbarState.position();

        // 5. 逐项渲染，累加偏移超过 scrollOffset 则跳过
        int screenY = textArea.y();  // 屏幕坐标：下一个可渲染行
        int contentY = 0;            // 内容坐标：当前项顶部
        for (int i = 0; i < items.size(); i++) {
            Element item = items.get(i);
            int h = i < itemHeights.size() ? itemHeights.get(i) : 1;
            int itemBottom = contentY + h;
            // 完全在可见区上方：跳过
            if (itemBottom <= scrollOffset) {
                contentY = itemBottom;
                continue;
            }
            // 完全在可见区下方：结束
            if (contentY >= scrollOffset + textArea.height()) {
                break;
            }
            int skipAbove = Math.max(0, scrollOffset - contentY);
            int visibleHeight = h - skipAbove;
            visibleHeight = Math.min(visibleHeight, textArea.bottom() - screenY);
            // Render the item at its full preferred height, NOT clipped to visibleHeight.
            // The Column/Layout inside ChatMessageItem needs the full height to solve
            // its internal layout correctly. Scrolling/clipping is handled by the frame's
            // own clipping mechanism (renderChild will clip to the actual visible area).
            if (h > 0 && textWidth > 0) {
                int renderY = screenY - skipAbove;
                int renderH = h;
                Rect itemRect = new Rect(textArea.x(), renderY, textWidth, renderH);
                context.renderChild(item, frame, itemRect);
            }
            screenY += visibleHeight;
            contentY = itemBottom;
        }

        // 6. 右侧滚动条
        if (reserveScrollbar && contentArea.width() > 0) {
            Rect scrollbarArea = new Rect(
                    contentArea.x() + contentArea.width() - 1,
                    contentArea.y(),
                    1,
                    contentArea.height());
            renderScrollbar(frame, scrollbarArea, context);
        }
    }

    private void renderScrollbar(Frame frame, Rect area, RenderContext context) {
        if (area.isEmpty()) return;
        Scrollbar.Builder b = Scrollbar.builder()
                .orientation(ScrollbarOrientation.VERTICAL_RIGHT)
                .style(context.currentStyle());
        if (scrollbarThumbColor != null) b.thumbStyle(Style.EMPTY.fg(scrollbarThumbColor));
        if (scrollbarTrackColor != null) b.trackStyle(Style.EMPTY.fg(scrollbarTrackColor));
        Scrollbar sb = b.build();
        frame.renderStatefulWidget(sb, area, scrollbarState);
    }

    private void measure(int availableWidth, RenderContext context) {
        itemHeights.clear();
        if (items.isEmpty()) return;
        RenderContext ctx = context != null ? context : RenderContext.empty();
        for (Element item : items) {
            Size s;
            try {
                s = item.preferredSize(availableWidth, -1, ctx);
            } catch (Exception ex) {
                s = Size.UNKNOWN;
            }
            int h = s.height();
            if (h <= 0) h = 1;
            itemHeights.add(h);
        }
    }

    private int totalContentHeight() {
        int sum = 0;
        for (int h : itemHeights) sum += h;
        return sum;
    }

    /** 当前可见区中点击到的 item 下标；未命中返回 -1。 */
    private int hitTestItem(int localX, int localY, int contentX, int contentY, int contentW, int contentH) {
        if (localX < contentX || localX >= contentX + contentW
                || localY < contentY || localY >= contentY + contentH) {
            return -1;
        }
        int scrollOffset = scrollbarState.position();
        int y = scrollOffset + (localY - contentY);
        int itemTop = 0;
        for (int i = 0; i < items.size(); i++) {
            int h = i < itemHeights.size() ? itemHeights.get(i) : 1;
            int itemBottom = itemTop + h;
            if (y >= itemTop && y < itemBottom) return i;
            itemTop = itemBottom;
        }
        return -1;
    }

    @Override
    public EventResult handleKeyEvent(KeyEvent event, boolean focused) {
        EventResult r = super.handleKeyEvent(event, focused);
        if (r.isHandled()) return r;
        if (!focused) return EventResult.UNHANDLED;

        switch (event.code()) {
            case UP -> {
                scrollUp(1);
                return EventResult.HANDLED;
            }
            case DOWN -> {
                scrollDown(1);
                return EventResult.HANDLED;
            }
            case PAGE_UP -> {
                scrollUp(Math.max(1, (int) Math.floor(scrollbarState.viewportContentLength() * 0.9)));
                return EventResult.HANDLED;
            }
            case PAGE_DOWN -> {
                scrollDown(Math.max(1, (int) Math.floor(scrollbarState.viewportContentLength() * 0.9)));
                return EventResult.HANDLED;
            }
            case HOME -> {
                followTail = false;
                scrollbarState.first();
                return EventResult.HANDLED;
            }
            case END -> {
                followTail = true;
                scrollbarState.last();
                return EventResult.HANDLED;
            }
            default -> {
                if (externalKeyHandler != null) return externalKeyHandler.handle(event);
                return EventResult.UNHANDLED;
            }
        }
    }

    @Override
    public EventResult handleMouseEvent(MouseEvent event) {
        lastMouseX = event.x();
        lastMouseY = event.y();

        if (event.kind() == MouseEventKind.SCROLL_UP) {
            scrollUp(3);
            return EventResult.HANDLED;
        }
        if (event.kind() == MouseEventKind.SCROLL_DOWN) {
            scrollDown(3);
            return EventResult.HANDLED;
        }
        if (event.kind() == MouseEventKind.PRESS && event.isLeftButton()) {
            // 会调用上下文的 onItemClick，这里只负责点击命中测试
            // 同时传递给 externalMouseHandler（如 ChatPanelElement 的焦点切换逻辑）
            EventResult r = super.handleMouseEvent(event);
            if (r.isHandled()) return r;
            if (externalMouseHandler != null) return externalMouseHandler.handle(event);
            return EventResult.HANDLED;
        }
        EventResult r = super.handleMouseEvent(event);
        if (r.isHandled()) return r;
        if (externalMouseHandler != null) return externalMouseHandler.handle(event);
        return EventResult.UNHANDLED;
    }

    /** 由 TuiApp 在点击后调用：根据上一次鼠标位置交给 onItemClick。 */
    public void dispatchClick(MouseEvent event) {
        if (onItemClick == null) return;
        Rect contentArea = lastRenderedArea;
        if (contentArea == null) return;
        int idx = hitTestItem(event.x(), event.y(), contentArea.x() + 1, contentArea.y() + 1,
                contentArea.width() - 2, contentArea.height() - 2);
        if (idx >= 0) onItemClick.accept(idx, event);
    }

}
