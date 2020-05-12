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
package brave.internal.handler;

import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.internal.Platform;
import brave.internal.collect.LongBitSet;
import brave.propagation.TraceContext;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import static brave.internal.Throwables.propagateIfFatal;
import static brave.internal.collect.LongBitSet.isSet;
import static brave.internal.collect.LongBitSet.setBit;

/** This logs exceptions instead of raising an error, as the supplied collector could have bugs. */
public final class NoopAwareSpanHandler extends SpanHandler {
  // Array ensures no iterators are created at runtime
  public static SpanHandler create(SpanHandler[] handlers,
      AtomicBoolean noop) {
    if (handlers.length == 0) return SpanHandler.NOOP;
    if (handlers.length == 1) return new NoopAwareSpanHandler(handlers[0], noop);
    if (handlers.length > LongBitSet.MAX_SIZE) {
      throw new IllegalArgumentException("handlers.length > " + LongBitSet.MAX_SIZE);
    }
    return new NoopAwareSpanHandler(new CompositeSpanHandler(handlers), noop);
  }

  final SpanHandler delegate;
  final AtomicBoolean noop;

  NoopAwareSpanHandler(SpanHandler delegate, AtomicBoolean noop) {
    this.delegate = delegate;
    this.noop = noop;
  }

  @Override public boolean begin(TraceContext context, MutableSpan span, TraceContext parent) {
    if (noop.get()) return false;
    try {
      return delegate.begin(context, span, parent);
    } catch (Throwable t) {
      propagateIfFatal(t);
      Platform.get().log("error handling begin {0}", context, t);
      return true; // user error in this handler shouldn't impact another
    }
  }

  @Override public boolean end(TraceContext context, MutableSpan span, Cause cause) {
    if (noop.get()) return false;
    try {
      return delegate.end(context, span, cause);
    } catch (Throwable t) {
      propagateIfFatal(t);
      Platform.get().log("error handling end {0}", context, t);
      return true; // user error in this handler shouldn't impact another
    }
  }

  @Override public boolean handlesAbandoned() {
    return delegate.handlesAbandoned();
  }

  @Override public int hashCode() {
    return delegate.hashCode();
  }

  @Override public boolean equals(Object obj) {
    return delegate.equals(obj);
  }

  @Override public String toString() {
    return delegate.toString();
  }

  static final class CompositeSpanHandler extends SpanHandler {
    final long handlesAbandonedBitset;
    final SpanHandler[] handlers;

    CompositeSpanHandler(SpanHandler[] handlers) {
      this.handlers = handlers;
      long handlesAbandonedBitset = 0;
      for (int i = 0; i < handlers.length; i++) {
        if (handlers[i].handlesAbandoned()) {
          handlesAbandonedBitset = setBit(handlesAbandonedBitset, i);
        }
      }
      this.handlesAbandonedBitset = handlesAbandonedBitset;
    }

    @Override public boolean begin(TraceContext context, MutableSpan span, TraceContext parent) {
      for (SpanHandler handler : handlers) {
        if (!handler.begin(context, span, parent)) return false;
      }
      return true;
    }

    @Override public boolean end(TraceContext context, MutableSpan span, Cause cause) {
      if (cause != Cause.ABANDONED) {
        for (SpanHandler handler : handlers) {
          if (!handler.end(context, span, cause)) return false;
        }
        return true;
      }

      for (int i = 0; i < handlers.length; i++) {
        if (isSet(handlesAbandonedBitset, i)) {
          if (!handlers[i].end(context, span, cause)) return false;
        }
      }
      return true;
    }

    @Override public boolean handlesAbandoned() {
      return handlesAbandonedBitset > 0;
    }

    @Override public int hashCode() {
      return Arrays.hashCode(handlers);
    }

    @Override public boolean equals(Object obj) {
      if (!(obj instanceof CompositeSpanHandler)) return false;
      return Arrays.equals(((CompositeSpanHandler) obj).handlers, handlers);
    }

    @Override public String toString() {
      return Arrays.toString(handlers);
    }
  }
}
