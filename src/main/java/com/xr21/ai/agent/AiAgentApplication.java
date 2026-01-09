package com.xr21.ai.agent;

import com.xr21.ai.agent.session.ConversationSessionManager;
import com.xr21.ai.agent.tui.AITerminalUI;

public class AiAgentApplication {

    public static void main(String[] args) {
        var aiTerminalUI = new AITerminalUI(new ConversationSessionManager(), new LocalAgent());
        aiTerminalUI.start();
    }
}
