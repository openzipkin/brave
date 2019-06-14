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

import brave.handler.MutableSpan;

/** This is a simplified type used for parsing errors. It only allows annotations or tags. */
// This implementation works with SpanCustomizer and ScopedSpan which don't share a common interface
// yet both support tag and annotations.
public class ErrorParser {
  /** Adds no tags to the span representing the operation in error. */
  public static final ErrorParser NOOP = new ErrorParser() {
    @Override protected void error(Throwable error, Object customizer) {
    }
  };

  /** Used to parse errors on a subtype of {@linkplain ScopedSpan} */
  public final void error(Throwable error, ScopedSpan scopedSpan) {
    error(error, (Object) scopedSpan);
  }

  /** Used to parse errors on a subtype of {@linkplain SpanCustomizer} */
  public final void error(Throwable error, SpanCustomizer customizer) {
    error(error, (Object) customizer);
  }

  /** Used to parse errors on a subtype of {@linkplain SpanCustomizer} */
  public final void error(Throwable error, MutableSpan span) {
    error(error, (Object) span);
  }

  /**
   * Override to change what data from the error are parsed into the span modeling it. By default,
   * this tags "error" as the message or simple name of the type.
   */
  protected void error(Throwable error, Object span) {
    String message = error.getMessage();
    if (message == null) message = error.getClass().getSimpleName();
    tag(span, "error", message);
  }

  /** Same behaviour as {@link brave.SpanCustomizer#annotate(String)} */
  protected final void annotate(Object span, String value) {
    if (span instanceof SpanCustomizer) {
      ((SpanCustomizer) span).annotate(value);
    } else if (span instanceof ScopedSpan) {
      ((ScopedSpan) span).annotate(value);
    }
  }

  /** Same behaviour as {@link brave.SpanCustomizer#tag(String, String)} */
  protected final void tag(Object span, String key, String message) {
    if (span instanceof SpanCustomizer) {
      ((SpanCustomizer) span).tag(key, message);
    } else if (span instanceof ScopedSpan) {
      ((ScopedSpan) span).tag(key, message);
    } else if (span instanceof MutableSpan) {
      ((MutableSpan) span).tag(key, message);
    }
  }
}
