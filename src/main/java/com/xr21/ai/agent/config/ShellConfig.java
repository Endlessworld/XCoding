package com.xr21.ai.agent.config;

import com.alibaba.cloud.ai.graph.agent.Agent;
import com.xr21.ai.agent.LocalAgent;
import com.xr21.ai.agent.session.ConversationSessionManager;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.shell.component.view.control.InputView;
import org.springframework.shell.jline.PromptProvider;

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

    /**
     * 配置提示符提供者
     */
    @Bean
    public PromptProvider promptProvider() {
        // InputView可以用于更复杂的输入处理
        InputView input = new InputView();
        return () -> new AttributedString("AI-Agent> ", AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN)
                .bold());
    }

    @Bean
    public Agent supervisorAgent(LocalAgent localAgent) {
        return localAgent.buildSupervisorAgent();
    }
}
