package com.xr21.ai.agent.plugins;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.tools.ToolContextHelper;
import com.alibaba.cloud.ai.graph.state.ReplaceAllWith;
import com.xr21.ai.agent.utils.Json;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ToolContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 插件脚本访问工作流上下文（对话记录 + 状态）的门面（设计文档 §6）。
 * <p>
 * 封装 {@link ToolContextHelper}：通过 ToolContext 读取 {@link OverAllState} 与
 * stateForUpdate，提供 messages()/state()/replaceMessages()/setState()/appendMessage()。
 * 写回语义与 ConversationCompactionTool 一致：replaceMessages 构造
 * {@link ReplaceAllWith} 整体替换；setState 普通值走 AppendStrategy 合并。
 */
public class ConversationAccess {

    private final ToolContext toolContext;

    public ConversationAccess(ToolContext toolContext) {
        this.toolContext = toolContext;
    }

    /** 读当前对话记录（Message → 友好 Map）。 */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> messages() {
        OverAllState state = ToolContextHelper.getState(toolContext).orElse(null);
        if (state == null) {
            return List.of();
        }
        List<Message> messages = state.value("messages", List.class).orElse(List.of());
        List<Map<String, Object>> out = new ArrayList<>();
        for (Message m : messages) {
            out.add(toMap(m));
        }
        return out;
    }

    /** 读任意工作流状态值。 */
    @SuppressWarnings("unchecked")
    public Object state(String key) {
        OverAllState state = ToolContextHelper.getState(toolContext).orElse(null);
        if (state == null) {
            return null;
        }
        return state.value(key, Object.class).orElse(null);
    }

    /** ReplaceAllWith 整体替换对话记录。 */
    public Map<String, Object> replaceMessages(List<?> newMessages) {
        Map<String, Object> stateForUpdate = ToolContextHelper.getStateForUpdate(toolContext).orElse(null);
        if (stateForUpdate == null) {
            return Map.of("success", false, "error", "无法获取工作流状态，写回未生效");
        }
        List<Message> messages = new ArrayList<>();
        for (Object o : newMessages) {
            if (o instanceof Message m) {
                messages.add(m);
            } else if (o instanceof Map<?, ?> m) {
                messages.add(toMessage((Map<String, Object>) m));
            }
        }
        stateForUpdate.put("messages", ReplaceAllWith.of(messages));
        return Map.of("success", true, "messages", messages.size());
    }

    /** 写入状态值（普通值，走 AppendStrategy 合并；PLUGIN_ROOT/DATA 为保留键，拒绝覆盖）。 */
    public Map<String, Object> setState(String key, Object value) {
        if ("PLUGIN_ROOT".equals(key) || "PLUGIN_DATA".equals(key)) {
            return Map.of("success", false, "error", "保留键 " + key + " 不允许覆盖");
        }
        Map<String, Object> stateForUpdate = ToolContextHelper.getStateForUpdate(toolContext).orElse(null);
        if (stateForUpdate == null) {
            return Map.of("success", false, "error", "无法获取工作流状态，写回未生效");
        }
        stateForUpdate.put(key, value);
        return Map.of("success", true, "key", key);
    }

    /** 追加一条消息（Map → Message）。 */
    public Map<String, Object> appendMessage(Map<String, Object> message) {
        Map<String, Object> stateForUpdate = ToolContextHelper.getStateForUpdate(toolContext).orElse(null);
        if (stateForUpdate == null) {
            return Map.of("success", false, "error", "无法获取工作流状态，写回未生效");
        }
        Object existing = stateForUpdate.get("messages");
        @SuppressWarnings("unchecked")
        List<Object> list = existing instanceof List<?> l
                ? new ArrayList<>((List<Object>) l)
                : new ArrayList<>();
        list.add(toMessage(message));
        // 追加策略：仅放入 stateForUpdate，由框架 AppendStrategy 合并（不包 ReplaceAllWith）。
        stateForUpdate.put("messages", list);
        return Map.of("success", true, "appended", 1);
    }

    /** Message → 友好 Map（role/text/toolCalls/responses）。 */
    private Map<String, Object> toMap(Message m) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("role", m.getMessageType().getValue());
        if (m.getText() != null) {
            out.put("text", m.getText());
        }
        if (m instanceof AssistantMessage am && am.getToolCalls() != null) {
            List<Map<String, Object>> calls = new ArrayList<>();
            for (AssistantMessage.ToolCall tc : am.getToolCalls()) {
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("id", tc.id());
                c.put("name", tc.name());
                c.put("arguments", tc.arguments());
                calls.add(c);
            }
            out.put("toolCalls", calls);
        }
        if (m instanceof ToolResponseMessage trm && trm.getResponses() != null) {
            List<Map<String, Object>> responses = new ArrayList<>();
            for (ToolResponseMessage.ToolResponse r : trm.getResponses()) {
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("id", r.id());
                c.put("name", r.name());
                c.put("responseData", r.responseData());
                responses.add(c);
            }
            out.put("responses", responses);
        }
        return out;
    }

    /** Map → Message（按 role 构造对应类型）。 */
    private Message toMessage(Map<String, Object> map) {
        String role = String.valueOf(map.get("role"));
        String text = map.get("text") == null ? "" : String.valueOf(map.get("text"));
        return switch (role.toLowerCase()) {
            case "system" -> new SystemMessage(text);
            case "assistant" -> AssistantMessage.builder().content(text)
                    .toolCalls(parseToolCalls(map.get("toolCalls"))).build();
            case "tool" -> ToolResponseMessage.builder().responses(parseResponses(map.get("responses"))).build();
            default -> new UserMessage(text);
        };
    }

    private List<AssistantMessage.ToolCall> parseToolCalls(Object o) {
        List<AssistantMessage.ToolCall> calls = new ArrayList<>();
        if (o instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    calls.add(new AssistantMessage.ToolCall(
                            String.valueOf(m.get("id")),
                            m.get("type") == null ? "function" : String.valueOf(m.get("type")),
                            String.valueOf(m.get("name")),
                            Json.toJson(toPlain(m.get("arguments")))));
                }
            }
        }
        return calls;
    }

    private List<ToolResponseMessage.ToolResponse> parseResponses(Object o) {
        List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
        if (o instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    responses.add(new ToolResponseMessage.ToolResponse(
                            String.valueOf(m.get("id")),
                            String.valueOf(m.get("name")),
                            Json.toJson(toPlain(m.get("responseData")))));
                }
            }
        }
        return responses;
    }

    private Object toPlain(Object o) {
        if (o instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, v) -> out.put(String.valueOf(k), toPlain(v)));
            return out;
        }
        if (o instanceof List<?> l) {
            List<Object> out = new ArrayList<>();
            l.forEach(v -> out.add(toPlain(v)));
            return out;
        }
        if (o instanceof groovy.lang.GString g) {
            return g.toString();
        }
        return o;
    }
}
