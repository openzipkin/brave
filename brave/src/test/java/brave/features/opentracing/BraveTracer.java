package brave.features.opentracing;

import brave.internal.Nullable;
import brave.propagation.ExtraFieldPropagation;
import brave.propagation.Propagation;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class BraveTracer implements Tracer {

  static BraveTracer wrap(brave.Tracing tracing) {
    if (tracing == null) throw new NullPointerException("tracing == null");
    return new BraveTracer(tracing);
  }

  final brave.Tracer tracer;
  final Set<String> allPropagationKeys;
  final TraceContext.Injector<TextMap> injector;
  final TraceContext.Extractor<TextMapView> extractor;

  BraveTracer(brave.Tracing tracing) {
    tracer = tracing.tracer();
    Propagation<String> propagation = tracing.propagation();
    allPropagationKeys = lowercaseSet(propagation.keys());
    if (propagation instanceof ExtraFieldPropagation) {
      allPropagationKeys.addAll(((ExtraFieldPropagation<String>) propagation).extraKeys());
    }
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
        extractor.extract(new TextMapView(allPropagationKeys, (TextMap) carrier));
    TraceContext context = extracted.context() != null
        ? tracer.joinSpan(extracted.context()).context()
        : tracer.nextSpan(extracted).context();
    return new BraveSpanContext(context);
  }

  /**
   * Eventhough TextMap is named like Map, it doesn't have a retrieve-by-key method.
   *
   * <p>See https://github.com/opentracing/opentracing-java/issues/305
   */
  static final class TextMapView {
    final Iterator<Map.Entry<String, String>> input;
    final Map<String, String> cache = new LinkedHashMap<>();
    final Set<String> allPropagationKeys;

    TextMapView(Set<String> allPropagationKeys, TextMap input) {
      this.allPropagationKeys = allPropagationKeys;
      this.input = input.iterator();
    }

    /** Performs case-insensitive lookup */
    @Nullable String get(String key) {
      key = key.toLowerCase(Locale.ROOT);
      String result = cache.get(key);
      if (result != null) return result;
      while (input.hasNext()) {
        Map.Entry<String, String> next = input.next();
        String keyInCarrier = next.getKey().toLowerCase(Locale.ROOT);
        if (keyInCarrier.equals(key)) {
          return next.getValue();
        } else if (allPropagationKeys.contains(keyInCarrier)) {
          cache.put(keyInCarrier, next.getValue());
        }
      }
      return null;
    }
  }

  static Set<String> lowercaseSet(List<String> fields) {
    Set<String> lcSet = new LinkedHashSet<>();
    for (String f : fields) {
      lcSet.add(f.toLowerCase(Locale.ROOT));
    }
    return lcSet;
  }
}
