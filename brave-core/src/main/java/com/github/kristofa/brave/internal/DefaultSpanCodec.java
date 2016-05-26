package com.github.kristofa.brave.internal;

import com.twitter.zipkin.gen.Annotation;
import com.twitter.zipkin.gen.AnnotationType;
import com.twitter.zipkin.gen.BinaryAnnotation;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import com.twitter.zipkin.gen.SpanCodec;
import java.util.ArrayList;
import java.util.List;
import zipkin.Codec;

public final class DefaultSpanCodec implements SpanCodec {
  public static final SpanCodec JSON = new DefaultSpanCodec(Codec.JSON);
  public static final SpanCodec THRIFT = new DefaultSpanCodec(Codec.THRIFT);

  private final Codec codec;

  private DefaultSpanCodec(Codec codec) {
    this.codec = codec;
  }

  @Override
  public byte[] writeSpan(Span span) {
    return codec.writeSpan(span.toZipkin());
  }

  @Override
  public byte[] writeSpans(List<Span> spans) {
    List<zipkin.Span> out = new ArrayList<zipkin.Span>(spans.size());
    for (Span span : spans) {
      out.add(span.toZipkin());
    }
    return codec.writeSpans(out);
  }

  @Override
  public Span readSpan(byte[] bytes) {
    zipkin.Span in = codec.readSpan(bytes);
    Span result = new Span();
    result.setTrace_id(in.traceId);
    result.setId(in.id);
    result.setParent_id(in.parentId);
    result.setName(in.name);
    result.setTimestamp(in.timestamp);
    result.setDuration(in.duration);
    result.setDebug(in.debug);
    for (zipkin.Annotation a : in.annotations) {
      result.addToAnnotations(Annotation.create(
          a.timestamp,
          a.value,
          to(a.endpoint)));
    }
    for (zipkin.BinaryAnnotation a : in.binaryAnnotations) {
      result.addToBinary_annotations(BinaryAnnotation.create(
          a.key,
          a.value,
          AnnotationType.fromValue(a.type.value),
          to(a.endpoint)));
    }
    return result;
  }

  private static Endpoint to(zipkin.Endpoint host) {
    if (host == null) return null;
    if (host.port == null) return Endpoint.create(host.serviceName, host.ipv4);
    return Endpoint.create(host.serviceName, host.ipv4, host.port);
  }
}
