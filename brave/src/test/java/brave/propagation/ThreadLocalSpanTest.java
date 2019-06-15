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
package brave.propagation;

import brave.Tracing;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Test;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;

public class ThreadLocalSpanTest {

  List<Span> spans = new ArrayList<>();
  Tracing tracing = Tracing.newBuilder()
    .currentTraceContext(ThreadLocalCurrentTraceContext.create())
    .spanReporter(spans::add)
    .build();

  ThreadLocalSpan threadLocalSpan = ThreadLocalSpan.create(tracing.tracer());

  @After public void close() {
    tracing.close();
  }

  @Test public void next() {
    assertThat(threadLocalSpan.next())
      .isEqualTo(threadLocalSpan.remove());
  }

  @Test public void next_extracted() {
    assertThat(threadLocalSpan.next(TraceContextOrSamplingFlags.DEBUG))
      .isEqualTo(threadLocalSpan.remove());
  }
}
