/*
 * Copyright 2013-2023 The OpenZipkin Authors
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

import brave.test.TestSpanHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class RealSpanCustomizerTest {
  TestSpanHandler spans = new TestSpanHandler();
  Tracing tracing = Tracing.newBuilder().addSpanHandler(spans).build();
  Span span = tracing.tracer().newTrace();
  SpanCustomizer spanCustomizer = span.customizer();

  @AfterEach void close() {
    tracing.close();
  }

  @Test void name() {
    spanCustomizer.name("foo");
    span.flush();

    assertThat(spans.get(0).name())
      .isEqualTo("foo");
  }

  @Test void annotate() {
    spanCustomizer.annotate("foo");
    span.flush();

    assertThat(spans.get(0).containsAnnotation("foo"))
      .isTrue();
  }

  @Test void tag() {
    spanCustomizer.tag("foo", "bar");
    span.flush();

    assertThat(spans).flatExtracting(s -> s.tags().entrySet())
      .containsExactly(entry("foo", "bar"));
  }
}
