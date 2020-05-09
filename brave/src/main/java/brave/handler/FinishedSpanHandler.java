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
package brave.handler;

import brave.Tracing;
import brave.TracingCustomizer;
import brave.propagation.TraceContext;

/**
 * @since 5.4
 * @deprecated Since 5.12 use {@link SpanHandler#end(TraceContext, MutableSpan, Cause)} with {@link
 * Cause#FINISHED}
 */
@Deprecated public abstract class FinishedSpanHandler extends SpanHandler {
  /**
   * Use to avoid comparing against {@code null} references.
   *
   * @since 5.4
   * @deprecated Since 5.12 use {@link SpanHandler#NOOP}
   */
  public static final FinishedSpanHandler NOOP = new FinishedSpanHandler() {
    @Override public boolean handle(TraceContext context, MutableSpan span) {
      return true;
    }

    @Override public String toString() {
      return "NoopFinishedSpanHandler{}";
    }
  };

  /**
   * @since 5.4
   * @deprecated Since 5.12 use {@link SpanHandler#end(TraceContext, MutableSpan, Cause)} with
   * {@link Cause#FINISHED}
   */
  public abstract boolean handle(TraceContext context, MutableSpan span);

  /**
   * @since 5.7
   * @deprecated Since 5.12 use {@link SpanHandler#end(TraceContext, MutableSpan, Cause)} with
   * {@link Cause#ORPHANED}
   */
  public boolean supportsOrphans() {
    return false;
  }

  /**
   * @since 5.4
   * @deprecated Since 5.12, set {@link Tracing.Builder#alwaysSampleLocal()}. Tip: the same {@link
   * TracingCustomizer} that {@linkplain Tracing.Builder#addSpanHandler(SpanHandler) adds this
   * handler} can also also set {@link Tracing.Builder#alwaysSampleLocal()}.
   */
  @Deprecated public boolean alwaysSampleLocal() {
    return false;
  }

  @Override public boolean end(TraceContext context, MutableSpan span, Cause cause) {
    switch (cause) {
      case ABANDONED:
        return true;
      case FLUSHED:
      case FINISHED:
        return handle(context, span);
      case ORPHANED:
        return !supportsOrphans() || handle(context, span);
      default:
        assert false : "Bug!: missing state handling for " + cause;
        return true;
    }
  }
}
