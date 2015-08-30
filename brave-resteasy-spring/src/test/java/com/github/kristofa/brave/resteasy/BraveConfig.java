package com.github.kristofa.brave.resteasy;

import com.github.kristofa.brave.Brave;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
public class BraveConfig {

    @Bean
    @Scope(value="singleton")
    public Brave brave() {
        Brave.Builder builder = new Brave.Builder("BraveRestEasyIntegration")
                .spanCollector(SpanCollectorForTesting.getInstance());
        return builder.build();
    }
}
