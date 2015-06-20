package com.github.kristofa.brave.resteasy3;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import com.github.kristofa.brave.client.spanfilter.DefaultSpanNameFilter;
import com.github.kristofa.brave.client.spanfilter.SpanNameFilter;

@Configuration
public class SpanNameFilterConfig {

    @Bean
    @Scope(value = "singleton")
    public SpanNameFilter spanNameFilter() {
        return new DefaultSpanNameFilter();
    }
}