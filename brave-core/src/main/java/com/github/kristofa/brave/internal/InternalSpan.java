package com.github.kristofa.brave.internal;

import com.github.kristofa.brave.SpanId;
import com.twitter.zipkin.gen.Span;

/**
 * Allows internal classes outside the package {@code com.twitter.zipkin.gen} to use non-public
 * methods. This allows us access internal methods while also making obvious the hooks are not for
 * public use. The only implementation of this interface is in {@link com.twitter.zipkin.gen.Span}.
 *
 * <p>Originally designed by OkHttp team, derived from {@code okhttp3.internal.Internal}
 */
public abstract class InternalSpan {

  public static void initializeInstanceForTests() {
    // Needed in tests to ensure that the instance is actually pointing to something.
    new Span();
  }

  public abstract Span newSpan(SpanId context);

  /**
   * In normal course, this returns the context of a span created by one of the tracers. This can
   * return null when an invalid span was set externally via a custom thread binder or span state.
   */
  public abstract @Nullable SpanId context(Span span);

  public static InternalSpan instance;
}
