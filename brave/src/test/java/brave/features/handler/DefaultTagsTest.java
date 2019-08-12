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

import brave.ScopedSpan;
import brave.Tracing;
import brave.handler.FinishedSpanHandler;
import brave.handler.MutableSpan;
import brave.propagation.TraceContext;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 * This shows how you can add a tag once per span as it enters a process. This is helpful for
 * environment details that are not request-specific, such as region.
 */
public class DefaultTagsTest {
  List<zipkin2.Span> spans = new ArrayList<>();
  Tracing tracing = Tracing.newBuilder()
    .addFinishedSpanHandler(new FinishedSpanHandler() {
      @Override public boolean handle(TraceContext context, MutableSpan span) {
        if (context.isLocalRoot()) {
          // pretend these are sourced from the environment
          span.tag("env", "prod");
          span.tag("region", "east");
        }
        return true;
      }
    })
    .spanReporter(spans::add)
    .build();

  @After public void close() {
    tracing.close();
  }

  @Test public void defaultTagsOnlyAddedOnce() {
    ScopedSpan parent = tracing.tracer().startScopedSpan("parent");
    try {
      tracing.tracer().startScopedSpan("child").finish();
    } finally {
      parent.finish();
    }

    assertThat(spans.get(0).name()).isEqualTo("child");
    assertThat(spans.get(0).tags()).isEmpty();

    assertThat(spans.get(1).name()).isEqualTo("parent");
    assertThat(spans.get(1).tags()).containsExactly(
      entry("env", "prod"),
      entry("region", "east")
    );
  }
}
