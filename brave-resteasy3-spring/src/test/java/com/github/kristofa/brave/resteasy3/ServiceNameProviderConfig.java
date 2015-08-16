package com.github.kristofa.brave.resteasy3;

import com.github.kristofa.brave.http.ServiceNameProvider;
import com.github.kristofa.brave.http.StringServiceNameProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
public class ServiceNameProviderConfig {

    @Bean
    @Scope(value = "singleton")
    public ServiceNameProvider serviceNameProvider() {
        return new StringServiceNameProvider("BraveRestEasyIntegration");
    }
}
