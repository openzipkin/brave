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

import brave.handler.FinishedSpanHandler;
import brave.handler.MutableSpan;
import brave.internal.Nullable;
import brave.internal.Platform;
import brave.propagation.TraceContext;

import static brave.internal.Throwables.propagateIfFatal;

/**
 * This is a centralized type to parse a tag into any variant of a span. This also avoids the
 * clutter of checking null or guarding on exceptions.
 *
 * Here's an example of a potentially expensive tag:
 * <pre>{@code
 * SUMMARY_TAG = new Tag<Summarizer>("summary") {
 *   @Override protected String parseValue(Summarizer input, TraceContext context) {
 *     return input.computeSummary();
 *   }
 * }
 * SUMMARY_TAG.tag(span);
 * }</pre>
 *
 * @see Tags
 * @see SpanCustomizer#tag(String, String)
 * @see ScopedSpan#tag(String, String)
 * @see MutableSpan#tag(String, String)
 * @since 5.11
 */
public abstract class Tag<I> {
  public final String key() {
    return key;
  }

  /**
   * Override to change what data from the input are parsed into the span modeling it. Any
   * exceptions will be logged and ignored.
   *
   * @return possibly nullable result. Note: empty string is a valid tag value!
   * @since 5.11
   */
  @Nullable protected abstract String parseValue(I input, @Nullable TraceContext context);

  /**
   * {@linkplain SpanCustomizer#tag(String, String) Tags} the value parsed from the {@code input}
   * when non-null and the span is not no-op.
   *
   * @since 5.11
   */
  // ex void parse(HttpRequest request, TraceContext context, SpanCustomizer span);
  public final void tag(I input, TraceContext context, SpanCustomizer span) {
    if (input == null) throw new NullPointerException("input == null");
    if (context == null) throw new NullPointerException("context == null");
    if (span == null) throw new NullPointerException("span == null");
    if (span == NoopSpanCustomizer.INSTANCE) return;
    tag(span, input, context);
  }

  /**
   * {@linkplain SpanCustomizer#tag(String, String) Tags} the value parsed from the {@code input}
   * when non-null and the span is not no-op.
   *
   * @since 5.11
   */
  public final void tag(I input, SpanCustomizer span) {
    if (input == null) throw new NullPointerException("input == null");
    if (span == null) throw new NullPointerException("span == null");
    TraceContext context = null;
    if (span instanceof Span) {
      Span asSpan = (Span) span;
      if (asSpan.isNoop()) return;
      context = asSpan.context();
    } else if (span == NoopSpanCustomizer.INSTANCE) {
      return;
    }
    tag(span, input, context);
  }

  /**
   * {@linkplain MutableSpan#tag(String, String) Tags} the value parsed from the {@code input} when
   * non-null.
   *
   * @see FinishedSpanHandler#handle(TraceContext, MutableSpan)
   * @since 5.11
   */
  public final void tag(I input, TraceContext context, MutableSpan span) {
    if (input == null) throw new NullPointerException("input == null");
    if (span == null) throw new NullPointerException("span == null");
    tag(span, input, context);
  }

  final String key;

  /** @since 5.11 */
  protected Tag(String key) {
    this.key = validateNonEmpty("key", key);
  }

  @Override public String toString() {
    return getClass().getSimpleName() + "{" + key + "}";
  }

  final void tag(Object span, I input, @Nullable TraceContext context) {
    String value;
    try {
      value = parseValue(input, context);
    } catch (Throwable e) {
      propagateIfFatal(e);
      Platform.get().log("Error parsing tag value of input %s", input, e);
      return;
    }
    if (value == null) return;
    if (span instanceof SpanCustomizer) {
      ((SpanCustomizer) span).tag(key, value);
    } else if (span instanceof MutableSpan) {
      ((MutableSpan) span).tag(key, value);
    }
  }

  protected static String validateNonEmpty(String label, String value) {
    if (value == null) throw new NullPointerException(label + " == null");
    value = value.trim();
    if (value.isEmpty()) throw new IllegalArgumentException(label + " is empty");
    return value;
  }
}
