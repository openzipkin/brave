package com.github.kristofa.brave;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * Spring Java Dependency Injection Configuration for {@link EndpointSubmitter}.
 * 
 * @author kristof
 */
@Configuration
public class EndpointSubmitterConfig {

    /**
     * Gets a singleton {@link EndpointSubmitter}.
     * 
     * @return Singleton {@link EndpointSubmitter}.
     */
    @Bean
    @Scope(value = "singleton")
    public EndpointSubmitter endpointSubmitter() {
        return Brave.getEndpointSubmitter();
    }
}
