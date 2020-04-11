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
import brave.propagation.TraceContext;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;

import static brave.test.util.ClassLoaders.assertRunIsUnloadable;

public class NoopAwareSpanHandlerClassLoaderTest {
  @Test public void unloadable_afterHandle() {
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
      handler.end(context, span, SpanHandler.Cause.FINISH);
    }
  }

  @Test public void unloadable_afterErrorHandling() {
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
      handler.end(context, span, SpanHandler.Cause.FINISH);
    }
  }
}
