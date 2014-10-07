package com.github.kristofa.brave.jersey2;

import com.github.kristofa.brave.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
@ComponentScan(basePackages={"com.github.kristofa.brave"})
public class JerseyTestSpringConfig {

    @Bean
    public SpanCollector spanCollector() {
        return SpanCollectorForTesting.getInstance();
    }

    @Bean
    public TraceFilters traceFilters() {
        return new TraceFilters(Arrays.<TraceFilter>asList(new FixedSampleRateTraceFilter(1)));
    }
}
