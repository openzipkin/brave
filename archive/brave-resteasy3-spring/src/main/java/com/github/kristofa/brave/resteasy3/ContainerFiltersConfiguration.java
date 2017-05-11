package com.github.kristofa.brave.resteasy3;

import com.github.kristofa.brave.jaxrs2.BraveContainerRequestFilter;
import com.github.kristofa.brave.jaxrs2.BraveContainerResponseFilter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Imports jaxrs2 filters used in resteasy3.
 * @deprecated use {@link BraveTracingFeatureConfiguration} now.
 */
@Configuration
@Import({BraveContainerRequestFilter.class, BraveContainerResponseFilter.class})
@Deprecated
public class ContainerFiltersConfiguration {
}
