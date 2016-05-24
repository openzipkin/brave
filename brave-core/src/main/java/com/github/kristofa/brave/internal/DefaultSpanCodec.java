package com.github.kristofa.brave.internal;

import com.twitter.zipkin.gen.Annotation;
import com.twitter.zipkin.gen.AnnotationType;
import com.twitter.zipkin.gen.BinaryAnnotation;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import com.twitter.zipkin.gen.SpanCodec;
import java.util.ArrayList;
import java.util.List;
import zipkin.BinaryAnnotation.Type;
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
    return codec.writeSpan(from(span));
  }

  @Override
  public byte[] writeSpans(List<Span> spans) {
    List<zipkin.Span> out = new ArrayList<zipkin.Span>(spans.size());
    for (Span span : spans) {
      out.add(from(span));
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

  private static zipkin.Span from(Span in) {
    zipkin.Span.Builder result = zipkin.Span.builder();
    result.traceId(in.getTrace_id());
    result.id(in.getId());
    result.parentId(in.getParent_id());
    result.name(in.getName());
    result.timestamp(in.getTimestamp());
    result.duration(in.getDuration());
    result.debug(in.isDebug());
    for (Annotation a : in.getAnnotations()) {
      result.addAnnotation(zipkin.Annotation.create(a.timestamp, a.value, from(a.host)));
    }
    for (BinaryAnnotation a : in.getBinary_annotations()) {
      result.addBinaryAnnotation(zipkin.BinaryAnnotation.builder()
          .key(a.key)
          .value(a.value)
          .type(Type.fromValue(a.type.getValue()))
          .endpoint(from(a.host))
          .build());
    }
    return result.build();
  }

  private static zipkin.Endpoint from(Endpoint host) {
    if (host == null) return null;
    if (host.port == null) return zipkin.Endpoint.create(host.service_name, host.ipv4);
    return zipkin.Endpoint.create(host.service_name, host.ipv4, host.port);
  }

  private static Endpoint to(zipkin.Endpoint host) {
    if (host == null) return null;
    if (host.port == null) return Endpoint.create(host.serviceName, host.ipv4);
    return Endpoint.create(host.serviceName, host.ipv4, host.port);
  }
}
