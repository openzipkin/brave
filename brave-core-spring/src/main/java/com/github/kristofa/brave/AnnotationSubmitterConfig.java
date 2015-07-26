package com.github.kristofa.brave;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * Spring dependency injection configuration for {@link AnnotationSubmitter}.
 * 
 * @author kristof
 */
@Configuration
public class AnnotationSubmitterConfig {

    /**
     * Creates a singleton {@link AnnotationSubmitter}.
     * 
     * @return singleton {@link AnnotationSubmitter}.
     */
    @Bean
    @Scope(value = "singleton")
    public AnnotationSubmitter annotationSubmitter() {
        return Brave.getServerSpanAnnotationSubmitter();
    }
}
