/*
 * Copyright 2013-2019 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package brave.jaxrs2;

import brave.Tracing;
import brave.http.HttpTracing;
import brave.propagation.ThreadLocalCurrentTraceContext;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.After;
import org.junit.Test;
import zipkin2.reporter.Reporter;

import static org.assertj.core.api.Assertions.assertThat;

/** This ensures all filters can be injected, supplied with only {@linkplain HttpTracing}. */
public class InjectionTest {
  Tracing tracing = Tracing.newBuilder()
    .currentTraceContext(ThreadLocalCurrentTraceContext.create())
    .spanReporter(Reporter.NOOP)
    .build();

  Injector injector = Guice.createInjector(new AbstractModule() {
    @Override protected void configure() {
      bind(HttpTracing.class).toInstance(HttpTracing.create(tracing));
    }
  });

  @After public void close() {
    tracing.close();
  }

  @Test public void tracingClientFilter() {
    assertThat(injector.getInstance(TracingClientFilter.class))
      .isNotNull();
  }

  @Test public void spanCustomizingContainerFilter() {
    SpanCustomizingContainerFilter filter =
      injector.getInstance(SpanCustomizingContainerFilter.class);

    assertThat(filter.parser.getClass())
      .isSameAs(ContainerParser.class);
  }

  @Test public void spanCustomizingContainerFilter_resource() {
    SpanCustomizingContainerFilter filter = injector.createChildInjector(new AbstractModule() {
      @Override protected void configure() {
        bind(ContainerParser.class).toInstance(ContainerParser.NOOP);
      }
    }).getInstance(SpanCustomizingContainerFilter.class);

    assertThat(filter.parser)
      .isSameAs(ContainerParser.NOOP);
  }
}
