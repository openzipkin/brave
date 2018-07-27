package brave.spring.web;

import brave.http.HttpTracing;
import java.util.Collections;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.AsyncRestTemplate;
import org.springframework.web.client.RestTemplate;

@ConditionalOnBean(HttpTracing.class)
@Configuration
public class TracingClientAutoConfiguration {

  @ConditionalOnMissingBean
  @Bean
  public RestTemplate restTemplate(HttpTracing httpTracing) {
    RestTemplate restTemplate = new RestTemplate();
    restTemplate.setInterceptors(Collections.singletonList(
        new TracingClientHttpRequestInterceptor(httpTracing)));

    return restTemplate;
  }

  @ConditionalOnMissingBean
  @Bean
  public AsyncRestTemplate asyncRestTemplate(HttpTracing httpTracing) {
    AsyncRestTemplate asyncRestTemplate = new AsyncRestTemplate();
    asyncRestTemplate.setInterceptors(Collections.singletonList(
        new TracingAsyncClientHttpRequestInterceptor(httpTracing)));
    return asyncRestTemplate;
  }
}
