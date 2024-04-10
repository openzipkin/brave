/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave;

/** This reduces exposure of methods on {@link Span} to those exposed on {@link SpanCustomizer}. */
final class SpanCustomizerShield implements SpanCustomizer {
  final Span delegate;

  SpanCustomizerShield(Span delegate) {
    this.delegate = delegate;
  }

  @Override public SpanCustomizer name(String name) {
    delegate.name(name);
    return this;
  }

  @Override public SpanCustomizer annotate(String value) {
    delegate.annotate(value);
    return this;
  }

  @Override public SpanCustomizer tag(String key, String value) {
    delegate.tag(key, value);
    return this;
  }

  @Override public String toString() {
    return "SpanCustomizer(" + delegate + ")";
  }
}
