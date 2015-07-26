package com.github.kristofa.brave;

import static org.mockito.Mockito.mock;

import java.util.Arrays;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
public class TraceFiltersMockConfig {

    public static TraceFilter MOCK_TRACE_FILTER = mock(TraceFilter.class);

    @Bean
    @Scope(value = "singleton")
    public TraceFilters traceFilters() {
        final List<TraceFilter> filters = Arrays.asList(MOCK_TRACE_FILTER);
        return new TraceFilters(filters);
    }

}
