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
package brave.features.handler;

import brave.Tracing;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Test;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;

public class MetricsFinishedSpanHandlerTest {
  SimpleMeterRegistry registry = new SimpleMeterRegistry();
  List<Span> spans = new ArrayList<>();
  Tracing tracing = Tracing.newBuilder()
    .spanReporter(spans::add)
    .addFinishedSpanHandler(new MetricsFinishedSpanHandler(registry, "span", "foo"))
    .build();

  @After public void after() {
    tracing.close();
    registry.close();
  }

  @Test public void onlyRecordsSpansMatchingSpanName() {
    tracing.tracer().nextSpan().name("foo").start().finish();
    tracing.tracer().nextSpan().name("bar").start().finish();
    tracing.tracer().nextSpan().name("foo").start().finish();

    assertThat(registry.get("span")
      .tags("name", "foo", "exception", "None").timer().count())
      .isEqualTo(2L);

    try {
      registry.get("span").tags("name", "bar", "exception", "None").timer();

      failBecauseExceptionWasNotThrown(MeterNotFoundException.class);
    } catch (MeterNotFoundException expected) {
    }
  }

  @Test public void addsExceptionTagToSpan() {
    tracing.tracer().nextSpan().name("foo").start()
      .tag("error", "wow")
      .error(new IllegalStateException())
      .finish();

    assertThat(registry.get("span")
      .tags("name", "foo", "exception", "IllegalStateException").timer().count())
      .isEqualTo(1L);
    assertThat(spans.get(0).tags())
      .containsEntry("exception", "IllegalStateException");
  }
}
