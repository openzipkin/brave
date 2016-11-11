package com.github.kristofa.brave.resteasy3;

import com.github.kristofa.brave.jaxrs2.BraveTracingFeature;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Imports jaxrs2 filters used in resteasy3. */
@Configuration
@Import(BraveTracingFeature.class)
public class BraveTracingFeatureConfiguration {
}
