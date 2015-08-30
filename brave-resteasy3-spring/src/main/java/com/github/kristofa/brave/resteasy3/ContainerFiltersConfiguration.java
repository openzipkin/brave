package com.github.kristofa.brave.resteasy3;

import com.github.kristofa.brave.ServerRequestInterceptor;
import com.github.kristofa.brave.ServerResponseInterceptor;
import com.github.kristofa.brave.http.SpanNameProvider;
import com.github.kristofa.brave.jaxrs2.BraveContainerRequestFilter;
import com.github.kristofa.brave.jaxrs2.BraveContainerResponseFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * TODO: Add description
 *
 * @author volzhev
 */
@Configuration
public class ContainerFiltersConfiguration {

    @Autowired
    private ServerRequestInterceptor serverRequestInterceptor;

    @Autowired
    private ServerResponseInterceptor serverResponseInterceptor;


    @Autowired
    private SpanNameProvider spanNameProvider;

    @Bean
    public BraveContainerRequestFilter getContainerRequestFilter() {
        return new BraveContainerRequestFilter(serverRequestInterceptor, spanNameProvider);
    }

    @Bean
    public BraveContainerResponseFilter getContainerResponseFilter() {
        return new BraveContainerResponseFilter(serverResponseInterceptor);
    }
}
