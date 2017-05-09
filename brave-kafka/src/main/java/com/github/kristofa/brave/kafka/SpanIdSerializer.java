package com.github.kristofa.brave.kafka;

import com.github.kristofa.brave.SpanId;
import java.util.Map;
import org.apache.kafka.common.serialization.Serializer;

public final class SpanIdSerializer implements Serializer<SpanId> {

  @Override public void configure(Map<String, ?> map, boolean b) {
  }

  @Override public byte[] serialize(String s, SpanId spanId) {
    return spanId.bytes();
  }

  @Override public void close() {
  }
}