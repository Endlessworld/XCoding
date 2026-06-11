/*
 * Copyright TamboUI Contributors
 * SPDX-License-Identifier: MIT
 */
package com.xr21.ai.agent.tui.element;

import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.toolkit.element.Size;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.PasteEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.block.Title;
import dev.tamboui.widgets.input.TextArea;
import dev.tamboui.widgets.input.TextAreaState;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A DSL wrapper for the TextArea widget.
 * <p>
 * A multi-line text input field with scrolling support.
 * <pre>{@code
 * textArea(textState)
 *     .placeholder("Enter text...")
 *     .title("Description")
 *     .showLineNumbers()
 *     .rounded()
 * }</pre>
 *
 * <h2>CSS Child Selectors</h2>
 * <p>
 * The following child selectors can be used to style sub-components:
 * <ul>
 *   <li>{@code TextAreaInputElement-cursor} - The cursor style (default: reversed)</li>
 *   <li>{@code TextAreaInputElement-placeholder} - The placeholder text style (default: dim)</li>
 *   <li>{@code TextAreaInputElement-line-number} - The line number style (default: dim)</li>
 * </ul>
 * <p>
 * Example CSS:
 * <pre>{@code
 * TextAreaInputElement-cursor { text-style: reversed; background: cyan; }
 * TextAreaInputElement-placeholder { color: gray; text-style: italic; }
 * TextAreaInputElement-line-number { color: #666666; }
 * }</pre>
 * <p>
 * Note: Programmatic styles set via the corresponding setter methods take precedence over CSS styles.
 */
@Slf4j
public final class TextAreaInputElement extends StyledElement<TextAreaInputElement> {

    private static final Style DEFAULT_CURSOR_STYLE = Style.EMPTY.reversed();
    private static final Style DEFAULT_PLACEHOLDER_STYLE = Style.EMPTY.dim();
    private static final Style DEFAULT_LINE_NUMBER_STYLE = Style.EMPTY.dim();

    private TextAreaState state;
    private Style cursorStyle;
    private String placeholder = "";
    private Style placeholderStyle;
    private String title;
    private BorderType borderType;
    private Color borderColor;
    private Color focusedBorderColor;
    private boolean showCursor = true;
    private boolean showLineNumbers = false;
    private Style lineNumberStyle;
    private TextChangeListener changeListener;

    /**
     * Creates a new text area element with a default state.
     */
    public TextAreaInputElement() {
        this.state = new TextAreaState();
    }

    /**
     * Creates a new text area element with the given state.
     *
     * @param state the text area state, or null for a default state
     */
    public TextAreaInputElement(TextAreaState state) {
        this.state = state != null ? state : new TextAreaState();
    }

    /**
     * Handles common key events for text area input.
     */
    private static boolean handleTextAreaKey(TextAreaState state, KeyEvent event) {
        switch (event.code()) {
            case BACKSPACE:
                state.deleteBackward();
                return true;
            case DELETE:
                state.deleteForward();
                return true;
            case LEFT:
                state.moveCursorLeft();
                return true;
            case RIGHT:
                state.moveCursorRight();
                return true;
            case UP:
                state.moveCursorUp();
                return true;
            case DOWN:
                state.moveCursorDown();
                return true;
            case HOME:
                state.moveCursorToLineStart();
                return true;
            case END:
                state.moveCursorToLineEnd();
                return true;
            case ENTER:
                // Enter is handled by parent (InputPanelElement) for submit vs newline
                return false;
            case TAB:
                state.insert("    "); // 4 spaces for tab
                return true;
            case CHAR:
                // Don't consume characters with Ctrl or Alt modifiers - those are control sequences
                if (event.modifiers().ctrl() || event.modifiers().alt()) {
                    return false;
                }
                int c = event.codePoint();
                if (c >= 32 && c != 127) {
                    state.insert(event.string());
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    /**
     * Sets the text area state.
     *
     * @param state the text area state
     * @return this builder
     */
    public TextAreaInputElement state(TextAreaState state) {
        this.state = state != null ? state : new TextAreaState();
        return this;
    }

    /**
     * Returns the current state.
     *
     * @return the text area state
     */
    public TextAreaState getState() {
        return state;
    }

    /**
     * Sets the initial text.
     *
     * @param text the initial text content
     * @return this builder
     */
    public TextAreaInputElement text(String text) {
        if (state != null && text != null) {
            state.setText(text);
        }
        return this;
    }

    /**
     * Sets the placeholder text.
     *
     * @param placeholder the placeholder text
     * @return this builder
     */
    public TextAreaInputElement placeholder(String placeholder) {
        this.placeholder = placeholder != null ? placeholder : "";
        return this;
    }

    /**
     * Sets the placeholder style.
     *
     * @param style the placeholder style
     * @return this builder
     */
    public TextAreaInputElement placeholderStyle(Style style) {
        this.placeholderStyle = style;
        return this;
    }

    /**
     * Sets the placeholder color.
     *
     * @param color the placeholder color
     * @return this builder
     */
    public TextAreaInputElement placeholderColor(Color color) {
        this.placeholderStyle = Style.EMPTY.fg(color);
        return this;
    }

    /**
     * Sets the cursor style.
     *
     * @param style the cursor style
     * @return this builder
     */
    public TextAreaInputElement cursorStyle(Style style) {
        this.cursorStyle = style;
        return this;
    }

    /**
     * Sets whether to show the cursor.
     *
     * @param show true to show the cursor
     * @return this builder
     */
    public TextAreaInputElement showCursor(boolean show) {
        this.showCursor = show;
        return this;
    }

    /**
     * Enables line number display.
     *
     * @return this builder
     */
    public TextAreaInputElement showLineNumbers() {
        this.showLineNumbers = true;
        return this;
    }

    /**
     * Sets the line number style.
     *
     * @param style the line number style
     * @return this builder
     */
    public TextAreaInputElement lineNumberStyle(Style style) {
        this.lineNumberStyle = style;
        return this;
    }

    /**
     * Sets the title for the border.
     *
     * @param title the border title
     * @return this builder
     */
    public TextAreaInputElement title(String title) {
        this.title = title;
        return this;
    }

    /**
     * Uses rounded borders.
     *
     * @return this builder
     */
    public TextAreaInputElement rounded() {
        this.borderType = BorderType.ROUNDED;
        return this;
    }

    /**
     * Sets the border color.
     *
     * @param color the border color
     * @return this builder
     */
    public TextAreaInputElement borderColor(Color color) {
        this.borderColor = color;
        return this;
    }

    /**
     * Sets the border color to use when focused.
     * If not set, no special focused border styling is applied
     * (CSS :focused pseudo-class can be used instead).
     *
     * @param color the focused border color
     * @return this builder
     */
    public TextAreaInputElement focusedBorderColor(Color color) {
        this.focusedBorderColor = color;
        return this;
    }

    /**
     * Sets a listener for text changes.
     *
     * @param listener the text change listener
     * @return this builder
     */
    public TextAreaInputElement onTextChange(TextChangeListener listener) {
        this.changeListener = listener;
        return this;
    }

    @Override
    public Size preferredSize(int availableWidth, int availableHeight, RenderContext context) {
        // Calculate max line width from content
        int maxWidth = 0;
        int lineCount = 1;
        if (state != null) {
            String text = state.text();
            for (String line : text.split("\n", -1)) {
                maxWidth = Math.max(maxWidth, line.length());
            }
            lineCount = text.isEmpty() ? 1 : text.split("\n", -1).length;
        }
        // Add minimum width, line number width if applicable, and border
        int lineNumWidth = showLineNumbers ? 5 : 0;
        int borderWidth = (title != null || borderType != null) ? 2 : 0;
        int width = Math.max(maxWidth, 20) + lineNumWidth + borderWidth;
        // Add border height
        int borderHeight = (title != null || borderType != null) ? 2 : 0;
        int height = lineCount + borderHeight;
        return Size.of(width, height);
    }

    @Override
    public boolean isFocusable() {
        return true;
    }

    @Override
    public Map<String, String> styleAttributes() {
        Map<String, String> attrs = new LinkedHashMap<>(super.styleAttributes());
        if (title != null) {
            attrs.put("title", title);
        }
        if (placeholder != null && !placeholder.isEmpty()) {
            attrs.put("placeholder", placeholder);
        }
        return Collections.unmodifiableMap(attrs);
    }

    /**
     * Handles a key event for text area input.
     * <p>
     * Note: The {@code focused} parameter is informational only.
     * If the event reached this element, it should be processed.
     */
    @Override
    public EventResult handlePasteEvent(PasteEvent event) {
        state.insert(event.text());
        if (changeListener != null) {
            changeListener.onTextChange(state.text());
        }
        return EventResult.HANDLED;
    }

    @Override
    public EventResult handleKeyEvent(KeyEvent event, boolean focused) {
        log.info("TextAreaInputElement handleKeyEvent {}", event);
        // Text input requires focus - only handle events when focused
        // to avoid multiple text areas in the same container all receiving input
        if (!focused) {
            return EventResult.UNHANDLED;
        }
        boolean handled = handleTextAreaKey(state, event);
        if (handled) {
            if (changeListener != null) {
                changeListener.onTextChange(state.text());
            }
            return EventResult.HANDLED;
        }
        // TextArea 未处理的事件（如 Ctrl+Enter）委托给父类的 keyHandler
        // 这样通过 .onKeyEvent() 注册的外部处理器仍能收到事件
        if (keyHandler != null) {
            return keyHandler.handle(event);
        }
        return EventResult.UNHANDLED;
    }

    @Override
    protected void renderContent(Frame frame, Rect area, RenderContext context) {
        if (area.isEmpty()) {
            return;
        }

        boolean isFocused = elementId != null && context.isFocused(elementId);

        // Resolve styles with priority: explicit > CSS > default
        Style effectiveCursorStyle = resolveEffectiveStyle(context, "cursor", cursorStyle, DEFAULT_CURSOR_STYLE);
        Style effectivePlaceholderStyle = resolveEffectiveStyle(context, "placeholder", placeholderStyle, DEFAULT_PLACEHOLDER_STYLE);
        Style effectiveLineNumberStyle = resolveEffectiveStyle(context, "line-number", lineNumberStyle, DEFAULT_LINE_NUMBER_STYLE);

        TextArea.Builder builder = TextArea.builder()
                .style(context.currentStyle())
                .cursorStyle(effectiveCursorStyle)
                .placeholder(placeholder)
                .placeholderStyle(effectivePlaceholderStyle)
                .showLineNumbers(showLineNumbers)
                .lineNumberStyle(effectiveLineNumberStyle);

        Color effectiveBorderColor = isFocused && focusedBorderColor != null
                ? focusedBorderColor
                : borderColor;

        if (title != null || borderType != null || effectiveBorderColor != null) {
            Block.Builder blockBuilder = Block.builder()
                    .borders(Borders.ALL)
                    .styleResolver(styleResolver(context));
            if (title != null) {
                blockBuilder.title(Title.from(title));
            }
            if (borderType != null) {
                blockBuilder.borderType(borderType);
            }
            if (effectiveBorderColor != null) {
                blockBuilder.borderColor(effectiveBorderColor);
            }
            builder.block(blockBuilder.build());
        }

        TextArea widget = builder.build();

        if (showCursor && isFocused) {
            widget.renderWithCursor(area, frame.buffer(), state, frame);
        } else {
            frame.renderStatefulWidget(widget, area, state);
        }
    }

    /**
     * Listener for text changes in the text area.
     */
    @FunctionalInterface
    public interface TextChangeListener {
        /**
         * Called when the text content changes.
         *
         * @param newText the new text content
         */
        void onTextChange(String newText);
    }
}
