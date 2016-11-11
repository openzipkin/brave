package com.github.kristofa.brave.jaxrs2;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.http.DefaultSpanNameProvider;
import com.github.kristofa.brave.http.SpanNameProvider;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This ensures all filters can be injected, supplied with only {@linkplain Brave} and a
 * {@linkplain SpanNameProvider}.
 */
public class InjectionTest {

  Injector injector = Guice.createInjector(new AbstractModule() {
    @Override protected void configure() {
      bind(Brave.class).toInstance(new Brave.Builder().build());
      bind(SpanNameProvider.class).to(DefaultSpanNameProvider.class);
    }
  });

  @Test
  public void braveClientRequestFilter() throws Exception {
    assertThat(injector.getInstance(BraveClientRequestFilter.class))
        .isNotNull();
  }

  @Test
  public void braveClientResponseFilter() throws Exception {
    assertThat(injector.getInstance(BraveClientResponseFilter.class))
        .isNotNull();
  }

  @Test
  public void braveContainerRequestFilter() throws Exception {
    assertThat(injector.getInstance(BraveContainerRequestFilter.class))
        .isNotNull();
  }

  @Test
  public void braveContainerResponseFilter() throws Exception {
    assertThat(injector.getInstance(BraveContainerResponseFilter.class))
        .isNotNull();
  }

  @Test
  public void braveTraceFeature() throws Exception {
    assertThat(injector.getInstance(BraveTracingFeature.class))
        .isNotNull();
  }
}
