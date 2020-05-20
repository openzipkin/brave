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
import brave.handler.SpanHandler;
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
   * <p><em>Note</em>: Overrides of {@link Tags#ERROR} must return a valid value when
   * {@param context} is {@code null}, even if that value is "" (empty string). Otherwise, error
   * spans will not be marked as such.
   *
   * @return The result to add as a span tag. {@code null} means no tag will be added. Note: empty
   * string is a valid tag value!
   * @since 5.11
   */
  @Nullable protected abstract String parseValue(I input, @Nullable TraceContext context);

  /**
   * Returns the value that would be tagged to the span or {@code null}.
   *
   * @see #key()
   * @see #parseValue(Object, TraceContext)
   * @since 5.12
   */
  @Nullable public String value(@Nullable I input, @Nullable TraceContext context) {
    if (input == null) return null;
    return parseValue(input, context);
  }

  /** Overrides the tag key based on the input */
  protected String key(I input) {
    return key;
  }

  /**
   * Tags the value parsed from the {@code input}.
   *
   * @since 5.11
   */
  public final void tag(I input, ScopedSpan span) {
    if (input == null) throw new NullPointerException("input == null");
    if (span == null) throw new NullPointerException("span == null");
    if (span.isNoop()) return;
    tag(span, input, span.context());
  }

  /**
   * Tags the value parsed from the {@code input}.
   *
   * @since 5.11
   */
  public final void tag(I input, Span span) {
    if (input == null) throw new NullPointerException("input == null");
    if (span == null) throw new NullPointerException("span == null");
    if (span.isNoop()) return;
    tag(span, input, span.context());
  }

  /**
   * Tags the value parsed from the {@code input}.
   *
   * @since 5.11
   */
  // ex void parse(HttpRequest request, TraceContext context, SpanCustomizer span);
  public final void tag(I input, @Nullable TraceContext context, SpanCustomizer span) {
    if (input == null) throw new NullPointerException("input == null");
    if (span == null) throw new NullPointerException("span == null");
    if (span == NoopSpanCustomizer.INSTANCE) return;
    tag(span, input, context);
  }

  /**
   * Tags the value parsed from the {@code input}.
   *
   * @since 5.11
   */
  public final void tag(I input, SpanCustomizer span) {
    if (input == null) throw new NullPointerException("input == null");
    if (span == null) throw new NullPointerException("span == null");
    if (span == NoopSpanCustomizer.INSTANCE) return;
    tag(span, input, null);
  }

  /**
   * Tags the value parsed from the {@code input}.
   *
   * @see SpanHandler#end(TraceContext, MutableSpan, SpanHandler.Cause)
   * @since 5.11
   */
  public final void tag(I input, @Nullable TraceContext context, MutableSpan span) {
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
    String key = null;
    String value = null;
    Throwable error = null;

    // Defensively call the only protected methods
    try {
      key = key(input);
      value = parseValue(input, context);
    } catch (Throwable e) {
      error = e;
      propagateIfFatal(e);
    }

    if (key == null || key.isEmpty()) {
      Platform.get().log("Error parsing tag key of input %s", input, error);
      return;
    } else if (error != null) {
      Platform.get().log("Error parsing tag value of input %s", input, error);
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
