package com.github.kristofa.brave.internal;

import com.github.kristofa.brave.SpanId;
import com.twitter.zipkin.gen.Annotation;
import com.twitter.zipkin.gen.AnnotationType;
import com.twitter.zipkin.gen.BinaryAnnotation;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import com.twitter.zipkin.gen.SpanCodec;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.v1.V1Span;
import zipkin2.v1.V1SpanConverter;
import zipkin2.v1.V2SpanConverter;

import static com.github.kristofa.brave.internal.Util.UTF_8;

public final class DefaultSpanCodec implements SpanCodec {
  public static final SpanCodec JSON =
      new DefaultSpanCodec(SpanBytesEncoder.JSON_V1, SpanBytesDecoder.JSON_V1);
  public static final SpanCodec THRIFT =
      new DefaultSpanCodec(SpanBytesEncoder.THRIFT, SpanBytesDecoder.THRIFT);

  private final SpanBytesEncoder encoder;
  private final SpanBytesDecoder decoder;

  private DefaultSpanCodec(SpanBytesEncoder encoder, SpanBytesDecoder decoder) {
    this.encoder = encoder;
    this.decoder = decoder;
  }

  @Override
  public byte[] writeSpan(Span span) {
    V1SpanConverter converter = V1SpanConverter.create();
    List<zipkin2.Span> v1Spans = converter.convert(toZipkin(span));
    return encoder.encode(v1Spans.get(0));
  }

  @Override
  public byte[] writeSpans(List<Span> spans) {
    V1SpanConverter converter = V1SpanConverter.create();
    List<zipkin2.Span> out = new ArrayList<>(spans.size());
    for (Span span : spans) {
      out.addAll(converter.convert(toZipkin(span)));
    }
    return encoder.encodeList(out);
  }

  @Override
  public Span readSpan(byte[] bytes) {
    return fromZipkin(decoder.decodeOne(bytes));
  }

  public static Span fromZipkin(zipkin2.Span v2) {
    V1Span in = V2SpanConverter.create().convert(v2);
    Span result = newSpan(SpanId.builder()
        .traceIdHigh(in.traceIdHigh())
        .traceId(in.traceId())
        .spanId(in.id())
        .parentId(in.parentId() != 0L ? in.parentId() : null)
        .debug(in.debug() != null ? in.debug() : false).build()
    );
    result.setName(in.name());
    result.setTimestamp(in.timestamp() != 0L ? in.timestamp() : null);
    result.setDuration(in.duration() != 0L ? in.duration() : null);
    for (zipkin2.v1.V1Annotation a : in.annotations()) {
      result.addToAnnotations(Annotation.create(
          a.timestamp(),
          a.value(),
          to(a.endpoint())));
    }
    for (zipkin2.v1.V1BinaryAnnotation a : in.binaryAnnotations()) {
      if (a.type() != 0 && a.type() != 6) continue;;
      result.addToBinary_annotations(BinaryAnnotation.create(
          a.key(),
          a.type() == 0 ? new byte[]{1}: a.stringValue().getBytes(UTF_8),
          a.type() == 0 ? AnnotationType.BOOL : AnnotationType.STRING,
          to(a.endpoint())));
    }
    return result;
  }

  private static Endpoint to(zipkin2.Endpoint host) {
    if (host == null) return null;
    byte[] ipv4 = host.ipv4Bytes();
    return Endpoint.builder()
        .ipv4(ipv4 != null ? ByteBuffer.wrap(ipv4).getInt() : 0)
        .ipv6(host.ipv6Bytes())
        .port(host.portAsInt())
        .serviceName(host.serviceName()).build();
  }

  public static zipkin2.v1.V1Span toZipkin(Span span) {
    zipkin2.v1.V1Span.Builder result = zipkin2.v1.V1Span.newBuilder();
    result.traceId(span.getTrace_id());
    result.traceIdHigh(span.getTrace_id_high());
    result.id(span.getId());
    result.parentId(span.getParent_id() != null ? span.getParent_id() : 0L);
    result.name(span.getName());
    result.timestamp(span.getTimestamp() != null ? span.getTimestamp() : 0L);
    result.duration(span.getDuration() != null ? span.getDuration() : 0L);
    result.debug(span.isDebug());
    for (Annotation a : span.getAnnotations()) {
      result.addAnnotation(a.timestamp, a.value, a.host != null ? a.host.toV2() : null);
    }
    for (BinaryAnnotation a : span.getBinary_annotations()) {
      zipkin2.Endpoint endpoint = a.host != null ? a.host.toV2() : null;
      if (a.type == AnnotationType.STRING) {
        result.addBinaryAnnotation(a.key, new String(a.value, UTF_8), endpoint);
      } else if (a.type == AnnotationType.BOOL && endpoint != null) {
        result.addBinaryAnnotation(a.key, endpoint);
      }
    }
    return result.build();
  }

  static Span newSpan(SpanId context) {
    return InternalSpan.instance.toSpan(context);
  }
}
