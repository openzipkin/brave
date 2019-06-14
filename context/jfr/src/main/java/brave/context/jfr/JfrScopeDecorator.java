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
package brave.context.jfr;

import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.CurrentTraceContext.ScopeDecorator;
import brave.propagation.TraceContext;
import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;

/**
 * Adds {@linkplain Event} properties "traceId", "parentId" and "spanId" when a {@link
 * brave.Tracer#currentSpan() span is current}. These can be used to correlate JDK Flight recorder
 * events with logs or Zipkin.
 *
 * <p>Ex.
 * <pre>{@code
 * tracing = Tracing.newBuilder()
 *                  .currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
 *                    .addScopeDecorator(JfrScopeDecorator.create())
 *                    .build()
 *                  )
 *                  ...
 *                  .build();
 * }</pre>
 */
public final class JfrScopeDecorator implements ScopeDecorator {

  @Category("Zipkin")
  @Label("Scope")
  @Description("Zipkin event representing a span being placed in scope")
  static final class ScopeEvent extends Event {
    @Label("Trace Id") String traceId;
    @Label("Parent Id") String parentId;
    @Label("Span Id") String spanId;
  }

  public static ScopeDecorator create() {
    return new JfrScopeDecorator();
  }

  @Override public Scope decorateScope(@Nullable TraceContext currentSpan, Scope scope) {
    ScopeEvent event = new ScopeEvent();
    if (!event.isEnabled()) return scope;

    if (currentSpan != null) {
      event.traceId = currentSpan.traceIdString();
      event.parentId = currentSpan.parentIdString();
      event.spanId = currentSpan.spanIdString();
    }

    event.begin();

    class JfrCurrentTraceContextScope implements Scope {
      @Override public void close() {
        scope.close();
        event.commit();
      }
    }
    return new JfrCurrentTraceContextScope();
  }

  JfrScopeDecorator() {
  }
}
