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

import brave.propagation.TraceContext;

final class NoopSpan extends Span {

  final TraceContext context;

  NoopSpan(TraceContext context) {
    this.context = context;
  }

  @Override public SpanCustomizer customizer() {
    return NoopSpanCustomizer.INSTANCE;
  }

  @Override public boolean isNoop() {
    return true;
  }

  @Override public TraceContext context() {
    return context;
  }

  @Override public Span start() {
    return this;
  }

  @Override public Span start(long timestamp) {
    return this;
  }

  @Override public Span name(String name) {
    return this;
  }

  @Override public Span kind(Kind kind) {
    return this;
  }

  @Override public Span annotate(String value) {
    return this;
  }

  @Override public Span annotate(long timestamp, String value) {
    return this;
  }

  @Override public Span remoteServiceName(String remoteServiceName) {
    return this;
  }

  /** Returns true in order to prevent secondary conditions when in no-op mode */
  @Override public boolean remoteIpAndPort(String remoteIp, int port) {
    return true;
  }

  @Override public Span tag(String key, String value) {
    return this;
  }

  @Override public Span error(Throwable throwable) {
    return this;
  }

  @Override public void finish() {
  }

  @Override public void finish(long timestamp) {
  }

  @Override public void abandon() {
  }

  @Override public void flush() {
  }

  @Override public String toString() {
    return "NoopSpan(" + context + ")";
  }

  /**
   * This also matches equals against a lazy span. The rationale is least surprise to the user, as
   * code should not act differently given an instance of lazy or {@link NoopSpan}.
   */
  @Override public boolean equals(Object o) {
    if (o == this) return true;
    return isEqualToNoopOrLazySpan(context, o);
  }

  // We don't compare a RealSpan vs a NoopSpan as they can never equal each other.
  // RealSpan's are always locally sampled and Noop ones are always not.
  static boolean isEqualToNoopOrLazySpan(TraceContext context, Object o) {
    if (o instanceof LazySpan) {
      return context.equals(((LazySpan) o).context);
    } else if (o instanceof NoopSpan) {
      return context.equals(((NoopSpan) o).context);
    }
    return false;
  }

  @Override public int hashCode() {
    return context.hashCode();
  }
}
