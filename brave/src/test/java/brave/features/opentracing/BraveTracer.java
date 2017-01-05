package brave.features.opentracing;

import brave.internal.Nullable;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class BraveTracer implements Tracer {

  static BraveTracer wrap(brave.Tracer tracer) {
    if (tracer == null) throw new NullPointerException("tracer == null");
    return new BraveTracer(tracer);
  }

  static final List<String> PROPAGATION_KEYS = Propagation.B3_STRING.keys();
  static final TraceContext.Injector<TextMap> INJECTOR =
      Propagation.B3_STRING.injector(TextMap::put);
  static final TraceContext.Extractor<TextMapView> EXTRACTOR =
      Propagation.B3_STRING.extractor(TextMapView::get);

  final brave.Tracer tracer;

  BraveTracer(brave.Tracer tracer) {
    this.tracer = tracer;
  }

  @Override public BraveSpanBuilder buildSpan(String operationName) {
    return new BraveSpanBuilder(tracer, operationName);
  }

  @Override public <C> void inject(SpanContext spanContext, Format<C> format, C carrier) {
    if (format != Format.Builtin.HTTP_HEADERS) {
      throw new UnsupportedOperationException(format + " != Format.Builtin.HTTP_HEADERS");
    }
    TraceContext traceContext = ((BraveSpanContext) spanContext).context;
    INJECTOR.inject(traceContext, (TextMap) carrier);
  }

  @Override public <C> BraveSpanContext extract(Format<C> format, C carrier) {
    if (format != Format.Builtin.HTTP_HEADERS) {
      throw new UnsupportedOperationException(format.toString());
    }
    TraceContextOrSamplingFlags result =
        EXTRACTOR.extract(new TextMapView(PROPAGATION_KEYS, (TextMap) carrier));
    TraceContext context = result.context() != null
        ? result.context().toBuilder().shared(true).build()
        : tracer.newTrace(result.samplingFlags()).context();
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
