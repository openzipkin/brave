package com.github.kristofa.brave;

import com.twitter.zipkin.gen.Annotation;
import com.twitter.zipkin.gen.AnnotationType;
import com.twitter.zipkin.gen.BinaryAnnotation;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import zipkin.reporter.Reporter;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

final class SpanCollectorReporterAdapter implements SpanCollector, Reporter<zipkin.Span> {

  final SpanCollector delegate;

  SpanCollectorReporterAdapter(SpanCollector delegate) {
    this.delegate = checkNotNull(delegate, "span collector");
  }

  @Override public void report(zipkin.Span span) {
    checkNotNull(span, "Null span");
    collect(toBrave(span));
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

  /** Changes this to a brave-native span object. */
  static Span toBrave(zipkin.Span input) {
    Span result = new Span();
    result.setTrace_id_high(input.traceIdHigh);
    result.setTrace_id(input.traceId);
    result.setId(input.id);
    result.setParent_id(input.parentId);
    result.setName(input.name);
    result.setTimestamp(input.timestamp);
    result.setDuration(input.duration);
    result.setDebug(input.debug);
    for (zipkin.Annotation a : input.annotations) {
      result.addToAnnotations(Annotation.create(a.timestamp, a.value, from(a.endpoint)));
    }
    for (zipkin.BinaryAnnotation a : input.binaryAnnotations) {
      result.addToBinary_annotations(BinaryAnnotation.create(
          a.key, a.value, AnnotationType.fromValue(a.type.value), from(a.endpoint)
      ));
    }
    return result;
  }

  private static Endpoint from(zipkin.Endpoint endpoint) {
    if (endpoint == null) return null;
    return Endpoint.builder()
        .ipv4(endpoint.ipv4)
        .ipv6(endpoint.ipv6)
        .port(endpoint.port)
        .serviceName(endpoint.serviceName).build();
  }
}
