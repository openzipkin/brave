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
package brave.context.log4j12;

import brave.internal.propagation.CorrelationFieldScopeDecorator;
import brave.propagation.CurrentTraceContext.ScopeDecorator;
import org.apache.log4j.MDC;

/**
 * Adds {@linkplain MDC} properties "traceId", "parentId", "spanId" and "sampled" when a {@link
 * brave.Tracer#currentSpan() span is current}. "traceId" and "spanId" are used in log correlation.
 * "parentId" is used for scenarios such as log parsing that reconstructs the trace tree. "sampled"
 * is used as a hint that a span found in logs might be in Zipkin.
 *
 * <p>Ex.
 * <pre>{@code
 * tracing = Tracing.newBuilder()
 *                  .currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
 *                    .addScopeDecorator(MDCScopeDecorator.create())
 *                    .build()
 *                  )
 *                  ...
 *                  .build();
 * }</pre>
 */
public final class MDCScopeDecorator extends CorrelationFieldScopeDecorator {
  public static ScopeDecorator create() {
    return new MDCScopeDecorator();
  }

  @Override protected String get(String key) {
    Object result = MDC.get(key);
    return result instanceof String ? (String) result : null;
  }

  @Override protected void put(String key, String value) {
    MDC.put(key, value);
  }

  @Override protected void remove(String key) {
    MDC.remove(key);
  }

  MDCScopeDecorator() {
  }
}
