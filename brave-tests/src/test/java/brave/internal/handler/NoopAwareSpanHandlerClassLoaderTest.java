/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.internal.handler;

import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.propagation.TraceContext;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

import static brave.test.util.ClassLoaders.assertRunIsUnloadable;

class NoopAwareSpanHandlerClassLoaderTest {
  @Test void unloadable_afterHandle() {
    assertRunIsUnloadable(Handle.class, getClass().getClassLoader());
  }

  static class Handle implements Runnable {
    @Override public void run() {
      SpanHandler handler =
        NoopAwareSpanHandler.create(new SpanHandler[] {new SpanHandler() {
          @Override public boolean end(TraceContext context, MutableSpan span, Cause cause) {
            return true;
          }
        }}, new AtomicBoolean());

      TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).build();
      MutableSpan span = new MutableSpan();
      handler.begin(context, span, null);
      handler.end(context, span, SpanHandler.Cause.FINISHED);
    }
  }

  @Test void unloadable_afterErrorHandling() {
    assertRunIsUnloadable(ErrorHandling.class, getClass().getClassLoader());
  }

  static class ErrorHandling implements Runnable {
    @Override public void run() {
      SpanHandler handler =
        NoopAwareSpanHandler.create(new SpanHandler[] {new SpanHandler() {
          @Override public boolean end(TraceContext context, MutableSpan span, Cause cause) {
            throw new RuntimeException();
          }
        }}, new AtomicBoolean());

      TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).build();
      MutableSpan span = new MutableSpan();
      handler.begin(context, span, null);
      handler.end(context, span, SpanHandler.Cause.FINISHED);
    }
  }
}
