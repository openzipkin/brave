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

import brave.handler.FinishedSpanHandler;
import brave.handler.MutableSpan;
import brave.propagation.TraceContext;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import static java.util.Collections.emptyIterator;
import static java.util.Collections.synchronizedMap;

public abstract class FinishedChildrenHandler extends FinishedSpanHandler {

  protected abstract void handle(MutableSpan parent, Iterator<MutableSpan> children);

  /** This holds the children of the current parent until the former is finished or abandoned. */
  final ParentToChildren parentToChildren = new ParentToChildren();

  @Override public boolean handle(TraceContext context, MutableSpan span) {
    if (!context.isLocalRoot()) { // a child
      parentToChildren.add(context.parentIdString(), span);
    }

    // There could be a lot of children. Instead of copying the list result, expose the iterator.
    // The main goal is to not add too much overhead as this is invoked on the same thread as
    // application code which implicitly call Span.finish() through instrumentation.
    Set<MutableSpan> children = parentToChildren.remove(context.spanIdString());
    Iterator<MutableSpan> child = children != null ? children.iterator() : emptyIterator();
    FinishedChildrenHandler.this.handle(span, child);
    return true;
  }

  static final class ParentToChildren {
    final Map<String, Set<MutableSpan>> delegate = synchronizedMap(new WeakHashMap<>());

    void add(String key, MutableSpan span) {
      delegate.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(span);
    }

    Set<MutableSpan> remove(String key) {
      return delegate.remove(key);
    }
  }
}
