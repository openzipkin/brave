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

import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.propagation.TraceContext;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LazySpanTest {
  List<zipkin2.Span> spans = new ArrayList<>();
  Tracing tracing = Tracing.newBuilder()
    .currentTraceContext(ThreadLocalCurrentTraceContext.create())
    .spanReporter(spans::add)
    .build();

  TraceContext context = tracing.tracer().newTrace().context();
  TraceContext context2 = tracing.tracer().newTrace().context();

  @After public void close() {
    tracing.close();
  }

  @Test public void equals_sameContext() {
    Span current1, current2;
    try (Scope ws = tracing.currentTraceContext().newScope(context)) {
      current1 = tracing.tracer().currentSpan();
      current2 = tracing.tracer().currentSpan();
    }

    assertThat(current1)
      .isInstanceOf(LazySpan.class)
      .isNotSameAs(current2)
      .isEqualTo(current2);
  }

  @Test public void equals_notSameContext() {
    Span current1, current2;
    try (Scope ws = tracing.currentTraceContext().newScope(context)) {
      current1 = tracing.tracer().currentSpan();
    }
    try (Scope ws = tracing.currentTraceContext().newScope(context2)) {
      current2 = tracing.tracer().currentSpan();
    }

    assertThat(current1).isNotEqualTo(current2);
  }

  @Test public void equals_realSpan_sameContext() {
    Span current;
    try (Scope ws = tracing.currentTraceContext().newScope(context)) {
      current = tracing.tracer().currentSpan();
    }

    assertThat(current).isEqualTo(tracing.tracer().toSpan(context));
  }

  @Test public void equals_realSpan_notSameContext() {
    Span current;
    try (Scope ws = tracing.currentTraceContext().newScope(context)) {
      current = tracing.tracer().currentSpan();
    }

    assertThat(current).isNotEqualTo(tracing.tracer().toSpan(context2));
  }
}
