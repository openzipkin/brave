package com.github.kristofa.brave;

import static org.mockito.Mockito.mock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
public class SpanCollectorMockConfig {

    public static SpanCollector MOCK_SPAN_COLLECTOR = mock(SpanCollector.class);

    @Bean
    @Scope(value = "singleton")
    public SpanCollector spanCollector() {
        return MOCK_SPAN_COLLECTOR;
    }

}
