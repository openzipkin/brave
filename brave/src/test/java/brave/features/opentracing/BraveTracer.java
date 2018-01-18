package brave.features.opentracing;

import brave.internal.Nullable;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class BraveTracer implements Tracer {

  static BraveTracer wrap(brave.Tracing tracing) {
    if (tracing == null) throw new NullPointerException("tracing == null");
    return new BraveTracer(tracing);
  }

  final brave.Tracer tracer;
  final List<String> propagationKeys;
  final TraceContext.Injector<TextMap> injector;
  final TraceContext.Extractor<TextMapView> extractor;

  BraveTracer(brave.Tracing tracing) {
    tracer = tracing.tracer();
    propagationKeys = tracing.propagation().keys();
    injector = tracing.propagation().injector(TextMap::put);
    extractor = tracing.propagation().extractor(TextMapView::get);
  }

  @Override public ScopeManager scopeManager() {
    return null; // out-of-scope for a simple example
  }

  @Override public Span activeSpan() {
    return null; // out-of-scope for a simple example
  }

  @Override public BraveSpanBuilder buildSpan(String operationName) {
    return new BraveSpanBuilder(tracer, operationName);
  }

  @Override public <C> void inject(SpanContext spanContext, Format<C> format, C carrier) {
    if (format != Format.Builtin.HTTP_HEADERS) {
      throw new UnsupportedOperationException(format + " != Format.Builtin.HTTP_HEADERS");
    }
    TraceContext traceContext = ((BraveSpanContext) spanContext).context;
    injector.inject(traceContext, (TextMap) carrier);
  }

  @Override public <C> BraveSpanContext extract(Format<C> format, C carrier) {
    if (format != Format.Builtin.HTTP_HEADERS) {
      throw new UnsupportedOperationException(format.toString());
    }
    TraceContextOrSamplingFlags extracted =
        extractor.extract(new TextMapView(propagationKeys, (TextMap) carrier));
    TraceContext context = extracted.context() != null
        ? tracer.joinSpan(extracted.context()).context()
        : tracer.nextSpan(extracted).context();
    return new BraveSpanContext(context);
  }

  /** Eventhough TextMap is named like Map, it doesn't have a retrieve-by-key method */
  static final class TextMapView {
    final Iterator<Map.Entry<String, String>> input;
    final Map<String, String> cache = new LinkedHashMap<>();
    final List<String> fields;

    TextMapView(List<String> fields, TextMap input) {
      this.fields = fields;
      this.input = input.iterator();
    }

    @Nullable String get(String key) {
      String result = cache.get(key);
      if (result != null) return result;
      while (input.hasNext()) {
        Map.Entry<String, String> next = input.next();
        if (next.getKey().equals(key)) {
          return next.getValue();
        } else if (fields.contains(next.getKey())) {
          cache.put(next.getKey(), next.getValue());
        }
      }
      return null;
    }
  }
}
