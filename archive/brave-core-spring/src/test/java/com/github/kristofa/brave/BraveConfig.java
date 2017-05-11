package com.github.kristofa.brave;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import static org.mockito.Mockito.mock;

@Configuration
public class BraveConfig {

        @Bean
        @Scope(value = "singleton")
        public Brave brave() {
            final Brave.Builder builder = new Brave.Builder();
            return builder
                    .spanCollector(mock(SpanCollector.class))
                    .traceSampler(mock(Sampler.class))
                    .build();
        }
}
