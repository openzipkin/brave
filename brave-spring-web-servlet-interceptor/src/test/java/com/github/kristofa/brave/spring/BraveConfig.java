package com.github.kristofa.brave.spring;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.BraveApiConfig;
import com.github.kristofa.brave.ServerRequestInterceptor;
import com.github.kristofa.brave.ServerResponseInterceptor;
import com.github.kristofa.brave.ServerSpanThreadBinder;
import com.github.kristofa.brave.http.DefaultSpanNameProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

@Configuration
@Import(BraveApiConfig.class)
@EnableWebMvc
public class BraveConfig extends WebMvcConfigurerAdapter {

    @Autowired
    private ServerRequestInterceptor requestInterceptor;

    @Autowired
    private ServerResponseInterceptor responseInterceptor;

    @Autowired
    private ServerSpanThreadBinder serverThreadBinder;

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
        registry.addInterceptor(new ServletHandlerInterceptor(requestInterceptor, responseInterceptor, new DefaultSpanNameProvider(), serverThreadBinder));
    }

}
