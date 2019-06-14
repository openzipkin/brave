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

import brave.Tracing;
import brave.propagation.ExtraFieldPropagation;
import brave.propagation.Propagation;
import brave.propagation.Propagation.Getter;
import brave.propagation.Propagation.Setter;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import io.opentracing.Scope;
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

  final Tracing tracing;
  final brave.Tracer tracer;
  final TraceContext.Injector<TextMap> injector;
  final TraceContext.Extractor<TextMap> extractor;

  BraveTracer(brave.Tracing tracing) {
    this.tracing = tracing;
    tracer = tracing.tracer();
    injector = tracing.propagation().injector(TEXT_MAP_SETTER);
    extractor = new TextMapExtractorAdaptor(tracing.propagation());
  }

  @Override public ScopeManager scopeManager() {
    return null; // out-of-scope for a simple example
  }

  @Override public Span activeSpan() {
    return null; // out-of-scope for a simple example
  }

  @Override public Scope activateSpan(Span span) {
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
    TraceContextOrSamplingFlags extractionResult = extractor.extract((TextMap) carrier);
    return BraveSpanContext.create(extractionResult);
  }

  @Override public void close() {
    tracing.close();
  }

  static final Setter<TextMap, String> TEXT_MAP_SETTER = new Setter<TextMap, String>() {
    @Override public void put(TextMap carrier, String key, String value) {
      carrier.put(key, value);
    }

    @Override public String toString() {
      return "TextMap::put";
    }
  };

  static final Getter<Map<String, String>, String> LC_MAP_GETTER =
    new Getter<Map<String, String>, String>() {
      @Override public String get(Map<String, String> carrier, String key) {
        return carrier.get(key.toLowerCase(Locale.ROOT));
      }

      @Override public String toString() {
        return "Map::getLowerCase";
      }
    };

  /**
   * Eventhough TextMap is named like Map, it doesn't have a retrieve-by-key method.
   *
   * <p>See https://github.com/opentracing/opentracing-java/issues/305
   */
  static final class TextMapExtractorAdaptor implements TraceContext.Extractor<TextMap> {
    final Set<String> allPropagationKeys;
    final TraceContext.Extractor<Map<String, String>> delegate;

    TextMapExtractorAdaptor(Propagation<String> propagation) {
      allPropagationKeys = lowercaseSet(propagation.keys());
      if (propagation instanceof ExtraFieldPropagation) {
        allPropagationKeys.addAll(((ExtraFieldPropagation<String>) propagation).extraKeys());
      }
      delegate = propagation.extractor(LC_MAP_GETTER);
    }

    /** Performs case-insensitive lookup */
    @Override public TraceContextOrSamplingFlags extract(TextMap entries) {
      Map<String, String> cache = new LinkedHashMap<>();
      for (Iterator<Map.Entry<String, String>> it = entries.iterator(); it.hasNext(); ) {
        Map.Entry<String, String> next = it.next();
        String inputKey = next.getKey().toLowerCase(Locale.ROOT);
        if (allPropagationKeys.contains(inputKey)) {
          cache.put(inputKey, next.getValue());
        }
      }
      return delegate.extract(cache);
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
