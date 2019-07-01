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

  @Override
  public String toString() {
    return "SpanCustomizer(" + delegate.toString() + ")";
  }
}
