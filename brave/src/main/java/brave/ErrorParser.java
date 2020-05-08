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
package brave;

import brave.handler.MutableSpan;
import brave.propagation.TraceContext;

/** @deprecated Since 5.12 Use Tags#ERROR or defer to {@link zipkin2.reporter.brave.ZipkinSpanHandler} */
public class ErrorParser extends Tag<Throwable> {
  public ErrorParser() {
    super("error");
  }

  /** Adds no tags to the span representing the operation in error. */
  public static final ErrorParser NOOP = new ErrorParser() {
    @Override protected void error(Throwable error, Object customizer) {
    }
  };

  /** Used to parse errors on a subtype of {@linkplain SpanCustomizer} */
  public final void error(Throwable error, SpanCustomizer customizer) {
    error(error, (Object) customizer);
  }

  /** Used to parse errors on a subtype of {@linkplain MutableSpan} */
  public final void error(Throwable error, MutableSpan span) {
    error(error, (Object) span);
  }

  /**
   * Override to change what data from the error are parsed into the span modeling it. By default,
   * this tags "error" as the message or simple name of the type.
   */
  protected void error(Throwable error, Object span) {
    Tags.ERROR.tag(span, error, null);
  }

  /** Prefers {@link Throwable#getMessage()} over the {@link Class#getSimpleName()}. */
  static String parse(Throwable error) {
    if (error == null) throw new NullPointerException("error == null");
    String message = error.getMessage();
    if (message != null) return message;
    if (error.getClass().isAnonymousClass()) { // avoids ""
      return error.getClass().getSuperclass().getSimpleName();
    }
    return error.getClass().getSimpleName();
  }

  /** Same behaviour as {@link brave.SpanCustomizer#annotate(String)} */
  protected final void annotate(Object span, String value) {
    if (span instanceof SpanCustomizer) {
      ((SpanCustomizer) span).annotate(value);
    }
  }

  /** Same behaviour as {@link brave.SpanCustomizer#tag(String, String)} */
  protected final void tag(Object span, String key, String message) {
    if (span instanceof SpanCustomizer) {
      ((SpanCustomizer) span).tag(key, message);
    } else if (span instanceof MutableSpan) {
      ((MutableSpan) span).tag(key, message);
    } else if (span instanceof KeyValueAdapter) {
      KeyValueAdapter keyValueAdapter = (KeyValueAdapter) span;
      keyValueAdapter.key = key;
      keyValueAdapter.value = message;
    }
  }

  static final class KeyValueAdapter {
    String key, value;
  }

  @Override protected final String key(Throwable input) {
    if (getClass() == ErrorParser.class) return Tags.ERROR.key();
    KeyValueAdapter kv = new KeyValueAdapter();
    error(input, kv);
    return kv.key;
  }

  @Override protected final String parseValue(Throwable input, TraceContext context) {
    if (getClass() == ErrorParser.class) return parse(input);
    KeyValueAdapter kv = new KeyValueAdapter();
    error(input, kv);
    return kv.value;
  }
}
