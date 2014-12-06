package com.github.kristofa.brave.resteasy3;

import com.github.kristofa.brave.EndPointSubmitter;
import com.github.kristofa.brave.ServerTracer;
import com.github.kristofa.brave.jaxrs2.BraveContainerRequestFilter;
import com.github.kristofa.brave.jaxrs2.BraveContainerResponseFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

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
    private EndPointSubmitter endPointSubmitter;

    @Bean
    public BraveContainerRequestFilter getContainerRequestFilter() {
        return new BraveContainerRequestFilter(serverTracer, endPointSubmitter);
    }

    @Bean
    public BraveContainerResponseFilter getContainerResponseFilter() {
        return new BraveContainerResponseFilter(serverTracer);
    }
}
