package com.xr21.ai.agent.config;

import com.alibaba.cloud.ai.graph.agent.Agent;
import com.xr21.ai.agent.LocalAgent;
import com.xr21.ai.agent.session.ConversationSessionManager;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Shell配置类
 */
@Configuration
public class ShellConfig {

    @Bean
    public ChatModel chatModel() {
        return AiModels.MINIMAX_M2_1.createChatModel();
    }

    @Bean
    public LocalAgent localAgent(ChatModel chatModel) {
        LocalAgent agent = new LocalAgent();
        agent.chatModel = chatModel;
        return agent;
    }

    @Bean
    public ConversationSessionManager conversationSessionManager() {
        ConversationSessionManager sessionManager = new ConversationSessionManager();
        sessionManager.init();
        return sessionManager;
    }

    @Bean
    public Agent supervisorAgent(LocalAgent localAgent) {
        return localAgent.buildSupervisorAgent();
    }
}
