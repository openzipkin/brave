package com.github.kristofa.brave;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
public class ServerResponseInterceptorConfig {

    @Autowired
    private ServerTracer serverTracer;

    @Bean
    @Scope(value = "singleton")
    public ServerResponseInterceptor serverResponseInterceptor() {
        return new ServerResponseInterceptor(serverTracer);
    }

}
