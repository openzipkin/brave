package com.github.kristofa.brave.jersey;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.http.DefaultSpanNameProvider;
import com.github.kristofa.brave.http.SpanNameProvider;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This ensures {@link ServletTraceFilter} and {@link JerseyClientTraceFilter} can be injected,
 * supplied with only {@linkplain Brave} and a {@linkplain SpanNameProvider}.
 */
public class InjectionTest {

  Injector injector = Guice.createInjector(new AbstractModule() {
    @Override protected void configure() {
      bind(Brave.class).toInstance(new Brave.Builder().build());
      bind(SpanNameProvider.class).to(DefaultSpanNameProvider.class);
    }
  });

  @Test
  public void jerseyClientTraceFilter() throws Exception {
    assertThat(injector.getInstance(JerseyClientTraceFilter.class))
        .isNotNull();
  }

  @Test
  public void servletTraceFilter() throws Exception {
    assertThat(injector.getInstance(ServletTraceFilter.class))
        .isNotNull();
  }
}
