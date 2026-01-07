package com.xr21.ai.agent.config;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI配置排除类
 */
@Configuration
@EnableAutoConfiguration(exclude = {
    // Exclude all Spring AI auto-configurations to avoid API key issues
})
public class SpringAiExcludeConfig {
}
