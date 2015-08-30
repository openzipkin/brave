package com.github.kristofa.brave;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * Spring configuration for Brave api components.
 * <p>
 * You will need to provide your own configuration for the Brave object which is
 * configured through the Brave.Builder and which configures SpanCollector, TraceFilters,...
 * </p>
 */
@Configuration
public class BraveApiConfig {

    @Autowired
    Brave brave;

    @Bean
    @Scope(value = "singleton")
    public ClientTracer clientTracer() {
        return brave.clientTracer();
    }

    @Bean
    @Scope(value = "singleton")
    public ServerTracer serverTracer() {
        return brave.serverTracer();
    }

    @Bean
    @Scope(value = "singleton")
    public ClientRequestInterceptor clientRequestInterceptor() {
        return brave.clientRequestInterceptor();
    }

    @Bean
    @Scope(value = "singleton")
    public ClientResponseInterceptor clientResponseInterceptor() {
        return brave.clientResponseInterceptor();
    }

    @Bean
    @Scope(value = "singleton")
    public ServerRequestInterceptor serverRequestInterceptor() {
        return brave.serverRequestInterceptor();
    }

    @Bean
    @Scope(value = "singleton")
    public ServerResponseInterceptor serverResponseInterceptor() {
        return brave.serverResponseInterceptor();
    }

    @Bean(name = "serverSpanAnnotationSubmitter")
    @Scope(value = "singleton")
    public AnnotationSubmitter serverSpanAnnotationSubmitter() {
       return brave.serverSpanAnnotationSubmitter();
    }

    @Bean
    @Scope(value = "singleton")
    public ServerSpanThreadBinder serverSpanThreadBinder() {
        return brave.serverSpanThreadBinder();
    }
}
