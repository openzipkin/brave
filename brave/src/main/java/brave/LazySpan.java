/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave;

import brave.handler.MutableSpan;
import brave.propagation.TraceContext;

/**
 * This defers creation of a span until first public method call.
 *
 * <p>This type was created to reduce overhead for code that calls {@link Tracer#currentSpan()},
 * but without ever using the result.
 */
final class LazySpan extends Span {
  final Tracer tracer;
  TraceContext context;
  Span delegate;

  LazySpan(Tracer tracer, TraceContext context) {
    this.tracer = tracer;
    this.context = context;
  }

  @Override public boolean isNoop() {
    return span().isNoop();
  }

  @Override public TraceContext context() {
    return span().context();
  }

  @Override public SpanCustomizer customizer() {
    return new SpanCustomizerShield(this);
  }

  @Override public Span start() {
    return span().start();
  }

  @Override public Span start(long timestamp) {
    return span().start(timestamp);
  }

  @Override public Span name(String name) {
    return span().name(name);
  }

  @Override public Span kind(Kind kind) {
    return span().kind(kind);
  }

  @Override public Span annotate(String value) {
    return span().annotate(value);
  }

  @Override public Span annotate(long timestamp, String value) {
    return span().annotate(timestamp, value);
  }

  @Override public Span tag(String key, String value) {
    return span().tag(key, value);
  }

  @Override public Span error(Throwable throwable) {
    return span().error(throwable);
  }

  @Override public Span remoteServiceName(String remoteServiceName) {
    return span().remoteServiceName(remoteServiceName);
  }

  @Override public boolean remoteIpAndPort(String remoteIp, int remotePort) {
    return span().remoteIpAndPort(remoteIp, remotePort);
  }

  @Override public void finish() {
    span().finish();
  }

  @Override public void finish(long timestamp) {
    span().finish(timestamp);
  }

  @Override public void abandon() {
    if (delegate == null) return; // prevent resurrection
    span().abandon();
  }

  @Override public void flush() {
    if (delegate == null) return; // prevent resurrection
    span().flush();
  }

  @Override public String toString() {
    return "LazySpan(" + context + ")";
  }

  /**
   * This also matches equals against an actual span. The rationale is least surprise to the user,
   * as code should not act differently given an instance of lazy {@link NoopSpan} or {@link
   * RealSpan}.
   */
  @Override public boolean equals(Object o) {
    if (o == this) return true;
    if (o instanceof LazySpan) {
      return context.equals(((LazySpan) o).context);
    } else if (o instanceof RealSpan) {
      return context.equals(((RealSpan) o).context);
    } else if (o instanceof NoopSpan) {
      return context.equals(((NoopSpan) o).context);
    }
    return false;
  }

  @Override public int hashCode() {
    return context.hashCode();
  }

  /**
   * This does not guard on all concurrent edge cases assigning the delegate field. That's because
   * this type is only used when a user calls {@link Tracer#currentSpan()}, which is unlikley to be
   * exposed in such a way that multiple threads end up in a race assigning the field. Finally,
   * there is no state risk if {@link Tracer#toSpan(TraceContext)} is called concurrently. Duplicate
   * instances of span may occur, but they would share the same {@link MutableSpan} instance
   * internally.
   */
  Span span() {
    Span result = delegate;
    if (result != null) return result;
    delegate = tracer.toSpan(context);
    context = delegate.context();
    return delegate;
  }
}
