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
    if (cause == Cause.ABANDONED) return true;
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
