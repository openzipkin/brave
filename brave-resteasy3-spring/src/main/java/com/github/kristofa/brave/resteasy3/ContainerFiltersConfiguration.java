package com.github.kristofa.brave.resteasy3;

import com.github.kristofa.brave.EndpointSubmitter;
import com.github.kristofa.brave.ServerTracer;
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
    private ServerTracer serverTracer;

    @Autowired
    private EndpointSubmitter endpointSubmitter;

    @Bean
    public BraveContainerRequestFilter getContainerRequestFilter() {
        return new BraveContainerRequestFilter(serverTracer, endpointSubmitter);
    }

    @Bean
    public BraveContainerResponseFilter getContainerResponseFilter() {
        return new BraveContainerResponseFilter(serverTracer);
    }
}
