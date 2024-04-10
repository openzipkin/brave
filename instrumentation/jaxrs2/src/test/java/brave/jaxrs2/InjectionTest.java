/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.jaxrs2;

import brave.Tracing;
import brave.http.HttpTracing;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** This ensures all filters can be injected, supplied with only {@linkplain HttpTracing}. */
public class InjectionTest {
  Tracing tracing = Tracing.newBuilder().build();

  Injector injector = Guice.createInjector(new AbstractModule() {
    @Override protected void configure() {
      bind(HttpTracing.class).toInstance(HttpTracing.create(tracing));
    }
  });

  @AfterEach void close() {
    tracing.close();
  }

  @Test void tracingClientFilter() {
    assertThat(injector.getInstance(TracingClientFilter.class))
      .isNotNull();
  }

  @Test void spanCustomizingContainerFilter() {
    SpanCustomizingContainerFilter filter =
      injector.getInstance(SpanCustomizingContainerFilter.class);

    assertThat(filter.parser.getClass())
      .isSameAs(ContainerParser.class);
  }

  @Test void spanCustomizingContainerFilter_resource() {
    SpanCustomizingContainerFilter filter = injector.createChildInjector(new AbstractModule() {
      @Override protected void configure() {
        bind(ContainerParser.class).toInstance(ContainerParser.NOOP);
      }
    }).getInstance(SpanCustomizingContainerFilter.class);

    assertThat(filter.parser)
      .isSameAs(ContainerParser.NOOP);
  }
}
