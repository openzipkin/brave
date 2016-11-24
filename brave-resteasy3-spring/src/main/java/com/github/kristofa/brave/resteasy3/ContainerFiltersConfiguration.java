package com.github.kristofa.brave.resteasy3;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.http.SpanNameProvider;
import com.github.kristofa.brave.jaxrs2.BraveContainerRequestFilter;
import com.github.kristofa.brave.jaxrs2.BraveContainerResponseFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring adapter for jaxrs2 filters, notably avoids deprecated constructors.
 */
@Configuration
public class ContainerFiltersConfiguration {

    @Autowired
    private Brave brave;

    @Autowired
    private SpanNameProvider spanNameProvider;

    @Bean
    public BraveContainerRequestFilter getContainerRequestFilter() {
        return BraveContainerRequestFilter.builder(brave).spanNameProvider(spanNameProvider).build();
    }

    @Bean
    public BraveContainerResponseFilter getContainerResponseFilter() {
        return BraveContainerResponseFilter.create(brave);
    }
}
