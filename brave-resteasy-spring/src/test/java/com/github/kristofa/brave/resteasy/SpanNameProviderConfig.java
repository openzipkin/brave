package com.github.kristofa.brave.resteasy;


import com.github.kristofa.brave.http.DefaultSpanNameProvider;
import com.github.kristofa.brave.http.SpanNameProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
public class SpanNameProviderConfig {

    @Bean
    @Scope(value = "singleton")
    public SpanNameProvider spanNameProvider() {
        return new DefaultSpanNameProvider();
    }
}
