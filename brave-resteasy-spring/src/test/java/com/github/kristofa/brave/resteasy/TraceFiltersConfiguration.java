package com.github.kristofa.brave.resteasy;

import java.util.Arrays;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import com.github.kristofa.brave.FixedSampleRateTraceFilter;
import com.github.kristofa.brave.TraceFilter;
import com.github.kristofa.brave.TraceFilters;

@Configuration
public class TraceFiltersConfiguration {

    @Bean
    @Scope(value = "singleton")
    public TraceFilters traceFilters() {
        // Sample rate = 1 means every request will get traced.
        return new TraceFilters(Arrays.<TraceFilter>asList(new FixedSampleRateTraceFilter(1)));
    }

}
