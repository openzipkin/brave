package com.github.kristofa.brave.jersey2;

import com.github.kristofa.brave.*;
import com.github.kristofa.brave.http.DefaultSpanNameProvider;
import com.github.kristofa.brave.http.ServiceNameProvider;
import com.github.kristofa.brave.http.SpanNameProvider;
import com.github.kristofa.brave.http.StringServiceNameProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
@ComponentScan(basePackages={"com.github.kristofa.brave"})
public class JerseyTestSpringConfig {



    @Bean
    public Brave brave() {
        Brave.Builder builder = new Brave.Builder("brave-jersey2");
        return builder.spanCollector(SpanCollectorForTesting.getInstance()).build();
    }

    @Bean
    public ServiceNameProvider serviceNameProvider() {
        return new StringServiceNameProvider("brave-jersey2");
    }

    @Bean
    public SpanNameProvider spanNameProvider() {
        return new DefaultSpanNameProvider();
    }
}
