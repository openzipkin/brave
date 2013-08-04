package com.github.kristofa.brave;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * Spring dependency injection configuration classes for {@link ServerSpanThreadBinder}.
 * 
 * @author kristof
 */
@Configuration
public class ServerSpanThreadBinderConfig {

    /**
     * Gets singleton {@link ServerSpanThreadBinder}.
     * 
     * @return Singleton {@link ServerSpanThreadBinder}.
     */
    @Bean
    @Scope(value = "singleton")
    public ServerSpanThreadBinder threadBinder() {
        return Brave.getServerSpanThreadBinder();
    }

}
