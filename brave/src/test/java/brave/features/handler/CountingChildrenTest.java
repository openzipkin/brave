/*
 * Copyright 2013-2020 The OpenZipkin Authors
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

import brave.Tracer;
import brave.Tracing;
import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.junit.After;
import org.junit.Test;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * This shows how a {@link SpanHandler} can add data some external formats need, such as child
 * count.
 *
 * <p><em>Note:</em> this currently only works with children fully enclosed by their parents. If
 * you have spans that finish after their parent, you'll need a more fancy implementation.
 */
public class CountingChildrenTest {
  static final class TagChildCount extends ParentToChildrenHandler {
    @Override protected void onFinish(MutableSpan parent, Iterator<MutableSpan> children) {
      int count = 0;
      for (; children.hasNext(); children.next()) {
        count++;
      }
      parent.tag("childCount", String.valueOf(count));
    }
  }

  List<zipkin2.Span> spans = new ArrayList<>();
  Tracing tracing = Tracing.newBuilder()
    .spanReporter(spans::add)
    .addSpanHandler(new TagChildCount())
    .build();
  Tracer tracer = tracing.tracer();

  @After public void after() {
    tracing.close();
  }

  @Test public void countChildren() {
    brave.Span root1 = tracer.newTrace().name("root1").start();
    brave.Span root2 = tracer.newTrace().name("root2").start();
    brave.Span root1Child1 = tracer.newChild(root1.context()).name("root1Child1").start();
    brave.Span root1Child1Child1 =
      tracer.newChild(root1Child1.context()).name("root1Child1Child1").start();
    brave.Span root2Child1 = tracer.newChild(root2.context()).name("root2Child1").start();
    brave.Span root1Child1Child2 =
      tracer.newChild(root1Child1.context()).name("root1Child1Child2").start();
    brave.Span root1Child1Child2Child1 =
      tracer.newChild(root1Child1Child1.context()).name("root1Child1Child2Child1").start();
    root1Child1Child2Child1.finish();
    root2Child1.finish();
    root1Child1Child1.finish();
    root2.finish();
    root1Child1Child2.finish();
    root1Child1.finish();
    root1.finish();

    assertThat(spans)
      .extracting(Span::name, s -> s.tags().get("childCount"))
      .containsExactly(
        tuple("root1child1child2child1", "0"),
        tuple("root2child1", "0"),
        tuple("root1child1child1", "1"),
        tuple("root2", "1"),
        tuple("root1child1child2", "0"),
        tuple("root1child1", "2"),
        tuple("root1", "1")
      );
  }

  /**
   * This reliably counts children even in async with one caveat: If a parent speculatively creates
   * children, the count will be higher than it should be, if it calls {@link brave.Span#abandon()}
   * after the parent finishes. This is quite an edge case.
   */
  @Test public void countChildren_async() {
    brave.Span root1 = tracer.newTrace().name("root1").start();
    brave.Span root1Child1 = tracer.newChild(root1.context()).name("root1Child1").start();
    brave.Span root1Child2 = tracer.newChild(root1.context()).name("root1Child2").start();
    root1.finish();
    root1Child1.finish();
    root1Child2.finish();

    assertThat(spans)
      .extracting(Span::name, s -> s.tags().get("childCount"))
      .containsExactly(
        tuple("root1", "2"), tuple("root1child1", "0"), tuple("root1child2", "0")
      );
  }
}
