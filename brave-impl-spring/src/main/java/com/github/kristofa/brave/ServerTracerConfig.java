package com.github.kristofa.brave;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * Spring Java Dependency Injection Configuration for {@link ServerTracer}.
 * <p/>
 * Important: You will need to make sure you have Dependency Injection configuration set up for {@link SpanCollector}. A
 * SpanCollector is needed for creating {@link ServerTracer}.
 * 
 * @author kristof
 */
@Configuration
public class ServerTracerConfig {

    @Autowired
    private SpanCollector spanCollector;

    /**
     * Creates a singleton ServerTracer instance.
     * 
     * @return Singleton ServerTracer instance.
     */
    @Bean
    @Scope(value = "singleton")
    public ServerTracer serverTracer() {
        return Brave.getServerTracer(spanCollector);
    }

}
