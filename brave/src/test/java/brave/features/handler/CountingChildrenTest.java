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
import brave.internal.Nullable;
import brave.propagation.TraceContext;
import brave.test.TestSpanHandler;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.assertj.core.groups.Tuple;
import org.junit.After;
import org.junit.Test;

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
  static final class TagChildCount extends SpanHandler {
    /** This holds the children of the current parent until the former is finished or abandoned. */
    final WeakConcurrentMap<TraceContext, TraceContext> childToParent =
        new WeakConcurrentMap<>(false);
    final WeakConcurrentMap<TraceContext, AtomicInteger> parentToChildCount =
        new WeakConcurrentMap<TraceContext, AtomicInteger>(false) {
          @Override protected AtomicInteger defaultValue(TraceContext key) {
            return new AtomicInteger();
          }
        };

    @Override
    public boolean begin(TraceContext context, MutableSpan span, @Nullable TraceContext parent) {
      if (!context.isLocalRoot()) { // a child
        childToParent.putIfProbablyAbsent(context, parent);
        parentToChildCount.get(parent).incrementAndGet();
      }
      return true;
    }

    @Override public boolean handlesAbandoned() {
      return true; // so that we have a callback to end for every begin
    }

    @Override public boolean end(TraceContext context, MutableSpan span, Cause cause) {
      // Kick-out if this was not a normal finish
      if ((cause != Cause.FINISHED && !context.isLocalRoot()) // a child
          || cause == Cause.ABANDONED) {
        TraceContext parent = childToParent.remove(context);
        AtomicInteger childCount = parentToChildCount.getIfPresent(parent);
        if (childCount != null) childCount.decrementAndGet();
        return true;
      }

      AtomicInteger childCount = parentToChildCount.getIfPresent(context);
      span.tag("childCount", childCount != null ? childCount.toString() : "0");

      // clean up so no OutOfMemoryException!
      childToParent.remove(context);
      parentToChildCount.remove(context);

      return true;
    }
  }

  TestSpanHandler spans = new TestSpanHandler();
  Tracing tracing = Tracing.newBuilder()
    .addSpanHandler(new TagChildCount())
    .addSpanHandler(spans)
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
    tracer.newChild(root1Child1.context()).name("root1Child1ChildAbandoned").start().abandon();
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

    List<Tuple> nameToChildCount = spans.spans().stream()
      .map(s -> tuple(s.name(), s.tags().get("childCount")))
      .collect(Collectors.toList());

    assertThat(nameToChildCount)
      .containsExactly(
        tuple("root1Child1Child2Child1", "0"),
        tuple("root2Child1", "0"),
        tuple("root1Child1Child1", "1"),
        tuple("root2", "1"),
        tuple("root1Child1Child2", "0"),
        tuple("root1Child1", "2"),
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
    tracer.newChild(root1.context()).name("root1ChildAbandoned").start().abandon();
    brave.Span root1Child2 = tracer.newChild(root1.context()).name("root1Child2").start();
    root1.finish();
    root1Child1.finish();
    root1Child2.finish();

    assertThat(spans)
      .extracting(MutableSpan::name, s -> s.tags().get("childCount"))
      .containsExactly(
        tuple("root1", "2"), tuple("root1Child1", "0"), tuple("root1Child2", "0")
      );
  }
}
