package com.github.kristofa.brave;

import com.github.kristofa.brave.internal.DefaultSpanCodec;
import com.github.kristofa.brave.internal.V2SpanConverter;
import com.twitter.zipkin.gen.Span;
import zipkin2.reporter.Reporter;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

final class SpanCollectorReporterAdapter implements SpanCollector, Reporter<zipkin2.Span> {

  final SpanCollector delegate;

  SpanCollectorReporterAdapter(SpanCollector delegate) {
    this.delegate = checkNotNull(delegate, "span collector");
  }

  @Override public void report(zipkin2.Span span) {
    checkNotNull(span, "Null span");
    collect(DefaultSpanCodec.fromZipkin(V2SpanConverter.toSpan(span)));
  }

  @Override
  public void collect(Span span) {
    checkNotNull(span, "Null span");
    delegate.collect(span);
  }

  @Deprecated
  @Override
  public void addDefaultAnnotation(String key, String value) {
    delegate.addDefaultAnnotation(key, value);
  }
}
