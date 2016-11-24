package com.github.kristofa.brave.spring;

import com.github.kristofa.brave.Brave;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

@Configuration
@EnableWebMvc
public class BraveConfig extends WebMvcConfigurerAdapter {

    @Autowired
    private Brave brave;

    @Bean
    public Brave brave() {
        Brave.Builder builder = new Brave.Builder("BraveServletInterceptorIntegration")
                .spanCollector(SpanCollectorForTesting.getInstance());
        return builder.build();
    }

    @Bean
    public PingController pingController() {
        return new PingController();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(ServletHandlerInterceptor.create(brave));
    }

}
