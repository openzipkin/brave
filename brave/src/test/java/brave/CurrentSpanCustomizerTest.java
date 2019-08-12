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
package brave;

import brave.propagation.ThreadLocalCurrentTraceContext;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Test;
import zipkin2.Annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

public class CurrentSpanCustomizerTest {

  List<zipkin2.Span> spans = new ArrayList<>();
  Tracing tracing = Tracing.newBuilder()
    .currentTraceContext(ThreadLocalCurrentTraceContext.create())
    .spanReporter(spans::add)
    .build();
  CurrentSpanCustomizer spanCustomizer = CurrentSpanCustomizer.create(tracing);
  Span span = tracing.tracer().newTrace();

  @After public void close() {
    Tracing.current().close();
  }

  @Test public void name() {
    span.start();
    try (Tracer.SpanInScope ws = tracing.tracer().withSpanInScope(span)) {
      spanCustomizer.name("newname");
    }
    span.flush();

    assertThat(spans).extracting(zipkin2.Span::name)
      .containsExactly("newname");
  }

  @Test public void name_when_no_current_span() {
    spanCustomizer.name("newname");
  }

  @Test public void tag() {
    span.start();
    try (Tracer.SpanInScope ws = tracing.tracer().withSpanInScope(span)) {
      spanCustomizer.tag("foo", "bar");
    }
    span.flush();

    assertThat(spans).flatExtracting(s -> s.tags().entrySet())
      .containsExactly(entry("foo", "bar"));
  }

  @Test public void tag_when_no_current_span() {
    spanCustomizer.tag("foo", "bar");
  }

  @Test public void annotate() {
    span.start();
    try (Tracer.SpanInScope ws = tracing.tracer().withSpanInScope(span)) {
      spanCustomizer.annotate("foo");
    }
    span.flush();

    assertThat(spans).flatExtracting(zipkin2.Span::annotations)
      .extracting(Annotation::value)
      .containsExactly("foo");
  }

  @Test public void annotate_when_no_current_span() {
    spanCustomizer.annotate("foo");
  }
}
