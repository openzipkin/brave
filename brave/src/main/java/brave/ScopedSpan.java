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
package brave;

import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;

/**
 * Used to model the latency of an operation within a method block.
 *
 * Here's a typical example of synchronous tracing from perspective of the scoped span:
 * <pre>{@code
 * // Note span methods chain. Explicitly start the span when ready.
 * ScopedSpan span = tracer.startScopedSpan("encode");
 * try {
 *   return encoder.encode();
 * } catch (RuntimeException | Error e) {
 *   span.error(e); // Unless you handle exceptions, you might not know the operation failed!
 *   throw e;
 * } finally {
 *   span.finish(); // finish - start = the duration of the operation in microseconds
 * }
 * }</pre>
 *
 * <p>Usage notes: All methods return {@linkplain ScopedSpan} for chaining, but the instance is
 * always the same. Also, this type is intended for in-process synchronous code. Do not leak this
 * onto another thread: it is not thread-safe. For advanced features or remote commands, use {@link
 * Span} instead.
 */
public abstract class ScopedSpan {

  /**
   * When true, no recording will take place, so no data is reported on finish. However, the trace
   * context is in scope until {@link #finish()} is called.
   */
  public abstract boolean isNoop();

  /** Returns the trace context associated with this span */
  // This api is exposed as there's always a context in scope by definition, and the context is
  // needed for methods like ExtraFieldPropagation.set
  public abstract TraceContext context();

  /**
   * Associates an event that explains latency with the current system time.
   *
   * @param value A short tag indicating the event, like "finagle.retry"
   */
  public abstract ScopedSpan annotate(String value);

  /**
   * Tags give your span context for search, viewing and analysis. For example, a key
   * "your_app.version" would let you lookup spans by version. A tag "sql.query" isn't searchable,
   * but it can help in debugging when viewing a trace.
   *
   * @param key Name used to lookup spans, such as "your_app.version".
   * @param value String value, cannot be <code>null</code>.
   */
  public abstract ScopedSpan tag(String key, String value);

  /** Adds tags depending on the configured {@link Tracing#errorParser() error parser} */
  public abstract ScopedSpan error(Throwable throwable);

  /**
   * Closes the {@link CurrentTraceContext#newScope(TraceContext) scope} associated with this span,
   * then reports the span complete, assigning the most precise duration possible.
   */
  public abstract void finish();

  ScopedSpan() {
  }
}
