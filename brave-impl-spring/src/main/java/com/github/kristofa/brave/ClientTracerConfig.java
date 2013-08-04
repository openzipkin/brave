package com.github.kristofa.brave;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * Spring Java Dependency Injection Configuration for {@link ClientTracer}.
 * <p/>
 * Important: You will need to make sure you have Dependency Injection configuration set up for {@link SpanCollector} and
 * {@link TraceFilter}. These are needed for creating {@link ClientTracer}.
 * 
 * @author kristof
 */
@Configuration
public class ClientTracerConfig {

    @Autowired
    private SpanCollector spanCollector;

    @Autowired
    private TraceFilters traceFilters;

    /**
     * Creates a singleton ClientTracer instance.
     * 
     * @return Singleton ClientTracer instance.
     */
    @Bean
    @Scope(value = "singleton")
    public ClientTracer clientTracer() {
        return Brave.getClientTracer(spanCollector, traceFilters.getTraceFilters());
    }
}
