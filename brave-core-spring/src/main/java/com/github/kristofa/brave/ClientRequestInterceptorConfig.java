package com.github.kristofa.brave;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
public class ClientRequestInterceptorConfig {

    @Autowired
    private ClientTracer clientTracer;

    @Bean
    @Scope(value= "singleton")
    public ClientRequestInterceptor clientRequestInterceptor() {
        return new ClientRequestInterceptor(clientTracer);
    }
}
