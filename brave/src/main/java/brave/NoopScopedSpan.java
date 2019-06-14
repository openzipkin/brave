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

import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;

final class NoopScopedSpan extends ScopedSpan {

  final TraceContext context;
  final Scope scope;

  NoopScopedSpan(TraceContext context, Scope scope) {
    this.context = context;
    this.scope = scope;
  }

  @Override public boolean isNoop() {
    return true;
  }

  @Override public TraceContext context() {
    return context;
  }

  @Override public ScopedSpan annotate(String value) {
    return this;
  }

  @Override public ScopedSpan tag(String key, String value) {
    return this;
  }

  @Override public ScopedSpan error(Throwable throwable) {
    return this;
  }

  @Override public void finish() {
    scope.close();
  }

  @Override public String toString() {
    return "NoopScopedSpan(" + context + ")";
  }

  @Override public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof NoopScopedSpan)) return false;
    NoopScopedSpan that = (NoopScopedSpan) o;
    return context.equals(that.context) && scope.equals(that.scope);
  }

  @Override public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= context.hashCode();
    h *= 1000003;
    h ^= scope.hashCode();
    return h;
  }
}
