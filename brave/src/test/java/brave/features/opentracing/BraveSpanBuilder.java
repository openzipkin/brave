package brave.features.opentracing;

import brave.Span;
import brave.propagation.TraceContext;
import io.opentracing.References;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

final class BraveSpanBuilder implements Tracer.SpanBuilder {
  final brave.Tracer tracer;
  final String operationName;
  final Map<String, String> tags = new LinkedHashMap<>();

  long timestamp;
  TraceContext parent;

  BraveSpanBuilder(brave.Tracer tracer, String operationName) {
    this.tracer = tracer;
    this.operationName = operationName;
  }

  @Override public BraveSpanBuilder asChildOf(SpanContext spanContext) {
    return addReference(References.CHILD_OF, spanContext);
  }

  @Override public BraveSpanBuilder asChildOf(io.opentracing.Span span) {
    return asChildOf(span.context());
  }

  @Override public BraveSpanBuilder addReference(String reference, SpanContext spanContext) {
    if (parent != null) return this;// yolo pick the first parent!
    if (References.CHILD_OF.equals(reference) || References.FOLLOWS_FROM.equals(reference)) {
      this.parent = ((BraveSpanContext) spanContext).context;
    }
    return this;
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

  @Override public BraveSpanBuilder withStartTimestamp(long timestamp) {
    this.timestamp = timestamp;
    return this;
  }

  @Override public Iterable<Map.Entry<String, String>> baggageItems() {
    return Collections.emptySet();
  }

  @Override public BraveSpan start() {
    Span result = parent == null ? tracer.newTrace() : tracer.newChild(parent);
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
