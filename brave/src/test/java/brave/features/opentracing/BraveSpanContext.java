package brave.features.opentracing;

import brave.propagation.ExtraFieldPropagation;
import brave.propagation.TraceContext;
import io.opentracing.SpanContext;
import java.util.Map;

final class BraveSpanContext implements SpanContext {

  static BraveSpanContext wrap(TraceContext context) {
    if (context == null) throw new NullPointerException("context == null");
    return new BraveSpanContext(context);
  }

  final TraceContext context;

  BraveSpanContext(TraceContext context) {
    this.context = context;
  }

  final TraceContext unwrap() {
    return context;
  }

  @Override public Iterable<Map.Entry<String, String>> baggageItems() {
    return ExtraFieldPropagation.getAll(context).entrySet();
  }
}
