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
package brave.features.opentracing;

import io.opentracing.References;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.tag.Tag;
import java.util.LinkedHashMap;
import java.util.Map;

final class BraveSpanBuilder implements Tracer.SpanBuilder {
  final brave.Tracer tracer;
  final String operationName;
  final Map<String, String> tags = new LinkedHashMap<>();

  long timestamp;
  BraveSpanContext reference;

  BraveSpanBuilder(brave.Tracer tracer, String operationName) {
    this.tracer = tracer;
    this.operationName = operationName;
  }

  @Override public BraveSpanBuilder asChildOf(SpanContext parent) {
    return addReference(References.CHILD_OF, parent);
  }

  @Override public BraveSpanBuilder asChildOf(io.opentracing.Span parent) {
    return asChildOf(parent != null ? parent.context() : null);
  }

  @Override public BraveSpanBuilder addReference(String type, SpanContext context) {
    if (reference != null || context == null) return this; // yolo pick the first parent!
    if (References.CHILD_OF.equals(type) || References.FOLLOWS_FROM.equals(type)) {
      this.reference = (BraveSpanContext) context;
    }
    return this;
  }

  @Override public Tracer.SpanBuilder ignoreActiveSpan() {
    return this; // out-of-scope for a simple example
  }

  @Override public BraveSpanBuilder withTag(String key, String value) {
    tags.put(key, value);
    return this;
  }

  @Override public BraveSpanBuilder withTag(String key, boolean value) {
    return withTag(key, Boolean.toString(value));
  }

  @Override public BraveSpanBuilder withTag(String key, Number value) {
    return withTag(key, value.toString());
  }

  @Override public <T> Tracer.SpanBuilder withTag(Tag<T> tag, T t) {
    if (t instanceof String) return withTag(tag.getKey(), (String) t);
    if (t instanceof Number) return withTag(tag.getKey(), (Number) t);
    if (t instanceof Boolean) return withTag(tag.getKey(), (Boolean) t);
    throw new IllegalArgumentException("tag value not a string, number or boolean: " + tag);
  }

  @Override public BraveSpanBuilder withStartTimestamp(long timestamp) {
    this.timestamp = timestamp;
    return this;
  }

  @Override public BraveSpan start() {
    brave.Span result;
    if (reference == null) {
      result = tracer.nextSpan();
    } else if (reference.context != null) {
      result = tracer.newChild(reference.context);
    } else {
      result = tracer.nextSpan(((BraveSpanContext.Incomplete) reference).extractionResult);
    }

    if (operationName != null) result.name(operationName);
    for (Map.Entry<String, String> tag : tags.entrySet()) {
      result.tag(tag.getKey(), tag.getValue());
    }
    if (timestamp != 0) {
      return new BraveSpan(result.start(timestamp));
    }
    return new BraveSpan(result.start());
  }
}
