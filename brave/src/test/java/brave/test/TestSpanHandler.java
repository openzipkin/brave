/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.test;

import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.propagation.TraceContext;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

// copy of what's in brave-tests
public final class TestSpanHandler extends SpanHandler implements Iterable<MutableSpan> {
  final List<MutableSpan> spans = new ArrayList<>();

  public MutableSpan get(int i) {
    return spans.get(i);
  }

  public List<MutableSpan> spans() {
    return spans;
  }

  @Override public boolean end(TraceContext context, MutableSpan span, Cause cause) {
    spans.add(span);
    return true;
  }

  @Override public Iterator<MutableSpan> iterator() {
    return spans.iterator();
  }

  public void clear() {
    spans.clear();
  }

  @Override public String toString() {
    return "TestSpanHandler{" + spans + "}";
  }
}
