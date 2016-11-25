package com.github.kristofa.brave.spring;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.http.DefaultSpanNameProvider;
import com.github.kristofa.brave.http.SpanNameProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

@Configuration
@Import(ServletHandlerInterceptor.class)
@EnableWebMvc
public class BraveConfig extends WebMvcConfigurerAdapter {

    @Autowired
    private ServletHandlerInterceptor interceptor;

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

    @Bean SpanNameProvider spanNameProvider() {
        return new DefaultSpanNameProvider();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor);
    }

}
