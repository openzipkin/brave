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
package brave.internal.handler;

import brave.handler.FinishedSpanHandler;
import brave.handler.MutableSpan;
import brave.internal.Platform;
import brave.propagation.TraceContext;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import zipkin2.Call;

/**
 * When {@code noop}, this drops input spans by returning false. Otherwise, it logs exceptions
 * instead of raising an error, as the supplied handler could have bugs.
 */
public abstract class NoopAwareFinishedSpanHandler extends FinishedSpanHandler {
  public static FinishedSpanHandler create(List<FinishedSpanHandler> handlers, AtomicBoolean noop) {
    if (handlers.isEmpty()) return FinishedSpanHandler.NOOP;

    if (handlers.size() == 1) {
      FinishedSpanHandler onlyHandler = handlers.get(0);
      return onlyHandler == FinishedSpanHandler.NOOP ? onlyHandler : new Single(onlyHandler, noop);
    }

    boolean alwaysSampleLocal = false, supportsOrphans = false;
    for (FinishedSpanHandler handler : handlers) {
      if (handler.alwaysSampleLocal()) alwaysSampleLocal = true;
      if (handler.supportsOrphans()) supportsOrphans = true;
    }
    return new Multiple(handlers, noop, alwaysSampleLocal, supportsOrphans);
  }

  final AtomicBoolean noop;
  boolean alwaysSampleLocal, supportsOrphans;

  NoopAwareFinishedSpanHandler(AtomicBoolean noop, boolean alwaysSampleLocal,
    boolean supportsOrphans) {
    this.noop = noop;
    this.alwaysSampleLocal = alwaysSampleLocal;
    this.supportsOrphans = supportsOrphans;
  }

  @Override public final boolean handle(TraceContext context, MutableSpan span) {
    if (noop.get()) return false;
    try {
      return doHandle(context, span);
    } catch (Throwable t) {
      Call.propagateIfFatal(t);
      Platform.get().log("error handling {0}", context, t);
      return false;
    }
  }

  @Override public final boolean alwaysSampleLocal() {
    return alwaysSampleLocal;
  }

  @Override public final boolean supportsOrphans() {
    return supportsOrphans;
  }

  abstract boolean doHandle(TraceContext context, MutableSpan span);

  static final class Single extends NoopAwareFinishedSpanHandler {
    final FinishedSpanHandler delegate;

    Single(FinishedSpanHandler delegate, AtomicBoolean noop) {
      super(noop, delegate.alwaysSampleLocal(), delegate.supportsOrphans());
      this.delegate = delegate;
    }

    @Override boolean doHandle(TraceContext context, MutableSpan span) {
      return delegate.handle(context, span);
    }

    @Override public String toString() {
      return delegate.toString();
    }
  }

  static final class Multiple extends NoopAwareFinishedSpanHandler {
    final FinishedSpanHandler[] handlers; // Array ensures no iterators are created at runtime

    Multiple(List<FinishedSpanHandler> handlers, AtomicBoolean noop, boolean alwaysSampleLocal,
      boolean supportsOrphans) {
      super(noop, alwaysSampleLocal, supportsOrphans);
      this.handlers = handlers.toArray(new FinishedSpanHandler[0]);
    }

    @Override boolean doHandle(TraceContext context, MutableSpan span) {
      for (FinishedSpanHandler handler : handlers) {
        if (!handler.handle(context, span)) return false;
      }
      return true;
    }

    @Override public String toString() {
      return Arrays.toString(handlers);
    }
  }
}
