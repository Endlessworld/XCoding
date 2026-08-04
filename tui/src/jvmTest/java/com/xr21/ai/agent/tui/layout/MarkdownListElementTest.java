/*
 * Copyright \u00a9 2026 XR21 Team. All rights reserved.
 */
package com.xr21.ai.agent.tui.layout;

import com.agentclientprotocol.model.SessionUpdate;
import com.xr21.ai.agent.model.ChatMessage;
import com.xr21.ai.agent.model.MessageRole;
import com.xr21.ai.agent.tui.Session;
import com.xr21.ai.agent.tui.TuiTheme;
import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.toolkit.markdown.MarkdownElement;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 烟雾测试：构造几条 ChatMessage + MarkdownListElement，调用 renderContent。
 * 验证不会抛异常、不会空指针。
 */
public class MarkdownListElementTest {

    @Before
    public void setUp() throws Exception {
        // 将测试线程标记为渲染线程，避免 ElementRegistry.register()
        // 在渲染过程中抛出"必须在渲染线程上调用"异常。
        Class<?> renderThreadClass = Class.forName("dev.tamboui.tui.RenderThread");
        Method markMethod = renderThreadClass.getDeclaredMethod("markAsRenderThread");
        markMethod.setAccessible(true);
        markMethod.invoke(null);
    }

    @After
    public void tearDown() throws Exception {
        Class<?> renderThreadClass = Class.forName("dev.tamboui.tui.RenderThread");
        Method clearMethod = renderThreadClass.getDeclaredMethod("clearRenderThread");
        clearMethod.setAccessible(true);
        clearMethod.invoke(null);
    }

    @Test
    public void rendersListOfMarkdownItems() {
        MarkdownListElement list = new MarkdownListElement();
        list.title("test");
        list.id("test-list");
        list.focusable();
        list.addItem(simpleText("Hello"));
        list.addItem(simpleText("World"));
        list.addItem(simpleText("Streaming \u258C"));

        Rect area = new Rect(0, 0, 60, 20);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        list.render(frame, area, RenderContext.empty());

        // 渲染不抛异常即为成功
        assertNotNull(list);
        assertTrue(true);
    }

    @Test
    public void rendersChatMessageItems() {
        TuiTheme theme = TuiTheme.fromTcss(SAMPLE_TCSS);
        Session session = new Session();
        ChatMessage userMsg = new ChatMessage(MessageRole.USER, "hi");
        session.messages.add(userMsg);
        ChatMessage aiMsg = new ChatMessage(MessageRole.ASSISTANT);
        List<SessionUpdate> events = new ArrayList<>();

        aiMsg.events.addAll(events);
        session.messages.add(aiMsg);

        MarkdownListElement list = new MarkdownListElement();
        list.title("test");
        list.id("chat");
        list.focusable();
        for (int i = 0; i < session.messages.size(); i++) {
            list.addItem(new ChatMessageItem(session.messages.get(i), theme, i));
        }

        Rect area = new Rect(0, 0, 80, 25);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        list.render(frame, area, RenderContext.empty());

        assertNotNull(list);
        assertTrue(true);
    }

    @Test
    public void markdownElementAlone() {
        Element md = MarkdownElement.of("# Title\n\nbody **bold** text");
        Rect area = new Rect(0, 0, 40, 5);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        md.render(frame, area, RenderContext.empty());
        assertNotNull(md);
    }

    private static Element simpleText(String s) {
        return dev.tamboui.toolkit.Toolkit.text(s);
    }

    private static final String SAMPLE_TCSS =
            "$border-normal: gray;\n" +
            "$border-focused: cyan;\n" +
            "$fg-primary: white;\n" +
            "$fg-secondary: gray;\n" +
            "$fg-muted: dark-gray;\n" +
            "$accent: cyan;\n" +
            "$user-msg: green;\n" +
            "$assistant-msg: cyan;\n" +
            "$system-msg: yellow;\n" +
            "$tool-msg: magenta;\n" +
            "$error-msg: red;\n" +
            "$success: green;\n" +
            "$warning: yellow;\n" +
            "$error: red;\n" +
            "$info: cyan;\n" +
            "$scroll-hint: dark-gray;\n";
}
