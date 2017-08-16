package com.github.kristofa.brave.jersey;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.http.DefaultSpanNameProvider;
import com.github.kristofa.brave.http.SpanNameProvider;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.util.Collections;
import zipkin.storage.InMemoryStorage;

public enum TraceFilters {
  INSTANCE;

  final InMemoryStorage storage = new InMemoryStorage();
  final ServletTraceFilter server;
  final JerseyClientTraceFilter client;

  TraceFilters() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override protected void configure() {
        bind(Brave.class).toInstance(new Brave.Builder("brave-jersey")
                .reporter(s -> storage.spanConsumer().accept(Collections.singletonList(s))).build());
        bind(SpanNameProvider.class).to(DefaultSpanNameProvider.class);
      }
    });
    this.server = injector.getInstance(ServletTraceFilter.class);
    this.client = injector.getInstance(JerseyClientTraceFilter.class);
  }
}
