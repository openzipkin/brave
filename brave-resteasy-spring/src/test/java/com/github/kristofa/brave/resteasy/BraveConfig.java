package com.github.kristofa.brave.resteasy;

import com.github.kristofa.brave.Brave;
import java.util.Collections;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import zipkin.storage.InMemoryStorage;

@Configuration
public class BraveConfig {
    static final InMemoryStorage storage = new InMemoryStorage();

    @Bean
    @Scope(value="singleton")
    public Brave brave() {
        Brave.Builder builder = new Brave.Builder("BraveRestEasyIntegration")
                .reporter(s -> storage.spanConsumer().accept(Collections.singletonList(s)));
        return builder.build();
    }
}
