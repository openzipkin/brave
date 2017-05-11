package com.twitter.zipkin.gen;

import com.github.kristofa.brave.internal.DefaultSpanCodec;
import java.util.List;

public interface SpanCodec {
  SpanCodec THRIFT = DefaultSpanCodec.THRIFT;
  SpanCodec JSON = DefaultSpanCodec.JSON;

  byte[] writeSpan(Span span);

  byte[] writeSpans(List<Span> spans);

  /** throws {@linkplain IllegalArgumentException} if the span couldn't be decoded */
  Span readSpan(byte[] bytes);
}
