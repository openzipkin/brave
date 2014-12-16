package com.github.kristofa.brave.resteasy3;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import com.github.kristofa.brave.SpanCollector;

@Configuration
public class SpanCollectorConfiguration {

    @Bean
    @Scope(value = "singleton")
    public SpanCollector spanCollector() {
        return SpanCollectorForTesting.getInstance();
    }

}
