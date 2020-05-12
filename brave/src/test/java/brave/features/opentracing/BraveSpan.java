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
package brave.features.opentracing;

import brave.baggage.BaggageField;
import brave.internal.Nullable;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.tag.Tag;
import io.opentracing.tag.Tags;
import java.util.Map;

final class BraveSpan implements io.opentracing.Span {

  static BraveSpan wrap(brave.Span span) {
    if (span == null) throw new NullPointerException("span == null");
    return new BraveSpan(span);
  }

  final brave.Span delegate;
  final BraveSpanContext context;

  BraveSpan(brave.Span delegate) {
    this.delegate = delegate;
    this.context = new BraveSpanContext(delegate.context());
  }

  public final brave.Span unwrap() {
    return delegate;
  }

  @Override public SpanContext context() {
    return context;
  }

  @Override public void finish() {
    delegate.finish();
  }

  @Override public void finish(long finishMicros) {
    delegate.finish(finishMicros);
  }

  @Override public io.opentracing.Span setTag(String key, String value) {
    brave.Span.Kind kind = trySetKind(key, value);
    if (kind != null) {
      delegate.kind(kind);
      context.kind = kind;
      return this;
    }
    delegate.tag(key, value);
    return this;
  }

  @Override public io.opentracing.Span setTag(String key, boolean value) {
    return setTag(key, Boolean.toString(value));
  }

  @Override public io.opentracing.Span setTag(String key, Number value) {
    return setTag(key, value.toString());
  }

  @Override public <T> Span setTag(Tag<T> tag, T t) {
    if (t instanceof String) return setTag(tag.getKey(), (String) t);
    if (t instanceof Number) return setTag(tag.getKey(), (Number) t);
    if (t instanceof Boolean) return setTag(tag.getKey(), (Boolean) t);
    throw new IllegalArgumentException("tag value not a string, number or boolean: " + tag);
  }

  @Override public io.opentracing.Span log(Map<String, ?> fields) {
    if (fields.isEmpty()) return this;
    // in real life, do like zipkin-go-opentracing: "key1=value1 key2=value2"
    return log(fields.toString());
  }

  @Override public io.opentracing.Span log(long timestampMicroseconds, Map<String, ?> fields) {
    if (fields.isEmpty()) return this;
    // in real life, do like zipkin-go-opentracing: "key1=value1 key2=value2"
    return log(timestampMicroseconds, fields.toString());
  }

  @Override public io.opentracing.Span log(String event) {
    delegate.annotate(event);
    return this;
  }

  @Override public io.opentracing.Span log(long timestampMicroseconds, String event) {
    delegate.annotate(timestampMicroseconds, event);
    return this;
  }

  @Override public io.opentracing.Span setBaggageItem(String key, String value) {
    BaggageField field = BaggageField.getByName(delegate.context(), key);
    if (field == null) return this;
    field.updateValue(delegate.context(), value);
    return this;
  }

  @Override public String getBaggageItem(String key) {
    BaggageField field = BaggageField.getByName(delegate.context(), key);
    return field != null ? field.getValue(delegate.context()) : null;
  }

  @Override public io.opentracing.Span setOperationName(String operationName) {
    delegate.name(operationName);
    return this;
  }

  @Nullable static brave.Span.Kind trySetKind(String key, String value) {
    if (!Tags.SPAN_KIND.getKey().equals(key)) return null;

    brave.Span.Kind kind;
    if (Tags.SPAN_KIND_CLIENT.equals(value)) {
      kind = brave.Span.Kind.CLIENT;
    } else if (Tags.SPAN_KIND_SERVER.equals(value)) {
      kind = brave.Span.Kind.SERVER;
    } else if (Tags.SPAN_KIND_PRODUCER.equals(value)) {
      kind = brave.Span.Kind.PRODUCER;
    } else if (Tags.SPAN_KIND_CONSUMER.equals(value)) {
      kind = brave.Span.Kind.CONSUMER;
    } else {
      return null;
    }
    return kind;
  }
}
