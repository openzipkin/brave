package com.github.kristofa.brave;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * Spring Java Dependency Injection Configuration for {@link EndPointSubmitter}.
 * 
 * @author kristof
 */
@Configuration
public class EndPointSubmitterConfig {

    /**
     * Gets a singleton {@link EndPointSubmitter}.
     * 
     * @return Singleton {@link EndPointSubmitter}.
     */
    @Bean
    @Scope(value = "singleton")
    public EndPointSubmitter endPointSubmitter() {
        return Brave.getEndPointSubmitter();
    }
}
