package com.github.kristofa.brave.internal;

import com.github.kristofa.brave.SpanId;
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
    return codec.writeSpan(toZipkin(span));
  }

  @Override
  public byte[] writeSpans(List<Span> spans) {
    List<zipkin.Span> out = new ArrayList<zipkin.Span>(spans.size());
    for (Span span : spans) {
      out.add(toZipkin(span));
    }
    return codec.writeSpans(out);
  }

  @Override
  public Span readSpan(byte[] bytes) {
    return fromZipkin(codec.readSpan(bytes));
  }

  public static Span fromZipkin(zipkin.Span in) {
    Span result = Span.create(SpanId.builder()
        .traceIdHigh(in.traceIdHigh)
        .traceId(in.traceId)
        .spanId(in.id)
        .parentId(in.parentId)
        .debug(in.debug != null ? in.debug : false).build()
    );
    result.setName(in.name);
    result.setTimestamp(in.timestamp);
    result.setDuration(in.duration);
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
    return Endpoint.builder()
        .ipv4(host.ipv4)
        .ipv6(host.ipv6)
        .port(host.port)
        .serviceName(host.serviceName).build();
  }

  public static zipkin.Span toZipkin(Span span) {
    zipkin.Span.Builder result = zipkin.Span.builder();
    result.traceId(span.getTrace_id());
    result.traceIdHigh(span.getTrace_id_high());
    result.id(span.getId());
    result.parentId(span.getParent_id());
    result.name(span.getName());
    result.timestamp(span.getTimestamp());
    result.duration(span.getDuration());
    result.debug(span.isDebug());
    for (Annotation a : span.getAnnotations()) {
      result.addAnnotation(zipkin.Annotation.create(a.timestamp, a.value, from(a.host)));
    }
    for (BinaryAnnotation a : span.getBinary_annotations()) {
      result.addBinaryAnnotation(zipkin.BinaryAnnotation.builder()
          .key(a.key)
          .value(a.value)
          .type(zipkin.BinaryAnnotation.Type.fromValue(a.type.getValue()))
          .endpoint(from(a.host))
          .build());
    }
    return result.build();
  }

  private static zipkin.Endpoint from(Endpoint host) {
    if (host == null) return null;
    return zipkin.Endpoint.builder()
        .ipv4(host.ipv4)
        .ipv6(host.ipv6)
        .port(host.port)
        .serviceName(host.service_name).build();
  }
}
