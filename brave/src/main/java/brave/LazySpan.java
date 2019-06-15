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
import brave.propagation.TraceContext;

import static brave.RealSpan.isEqualToRealOrLazySpan;

/**
 * This defers creation of a span until first public method call.
 *
 * <p>This type was created to reduce overhead for code that calls {@link Tracer#currentSpan()},
 * but without ever using the result.
 */
final class LazySpan extends Span {
  final Tracer tracer;
  final TraceContext context;
  Span delegate;

  LazySpan(Tracer tracer, TraceContext context) {
    this.tracer = tracer;
    this.context = context;
  }

  @Override public boolean isNoop() {
    return false;
  }

  @Override public TraceContext context() {
    return context;
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
   * This also matches equals against a real span. The rationale is least surprise to the user, as
   * code should not act differently given an instance of lazy or {@link RealSpan}.
   */
  @Override public boolean equals(Object o) {
    if (o == this) return true;
    return isEqualToRealOrLazySpan(context, o);
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
    return delegate = tracer.toSpan(context);
  }

  @Override public int hashCode() {
    return context.hashCode();
  }
}
