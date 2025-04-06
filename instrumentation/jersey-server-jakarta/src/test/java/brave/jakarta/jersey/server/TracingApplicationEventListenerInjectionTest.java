/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.jakarta.jersey.server;

import brave.Tracing;
import brave.http.HttpTracing;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TracingApplicationEventListenerInjectionTest {
  Tracing tracing = Tracing.newBuilder().build();

  Injector injector = Guice.createInjector(new AbstractModule() {
    @Override protected void configure() {
      bind(HttpTracing.class).toInstance(HttpTracing.create(tracing));
    }
  });

  @AfterEach void close() {
    tracing.close();
  }

  @Test void onlyRequiresHttpTracing() {
    assertThat(injector.getInstance(TracingApplicationEventListener.class))
      .isNotNull();
  }
}
