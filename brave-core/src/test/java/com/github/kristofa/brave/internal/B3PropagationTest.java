package com.github.kristofa.brave.internal;

import com.github.kristofa.brave.IdConversion;
import com.github.kristofa.brave.Propagation;
import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.TraceData;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

import static junit.framework.Assert.assertNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class B3PropagationTest {
  Propagation<String> propagation = Propagation.Factory.B3.create(Propagation.KeyFactory.STRING);
  Map<String, String> map = new LinkedHashMap<>();
  MapEntry mapEntry = new MapEntry();

  SpanId rootSpan = SpanId.builder().spanId(1L).traceId(1L).parentId(null).build();
  SpanId childSpan = SpanId.builder().spanId(2L).traceId(1L).parentId(1L).build();

  @Test
  public void extractTraceData_rootSpan() throws Exception {
    map.put("X-B3-TraceId", "1");
    map.put("X-B3-SpanId", "1");
    map.put("X-B3-Sampled", "1");

    TraceData traceData = propagation.extractor(mapEntry).extractTraceData(map);

    assertThat(traceData.getSpanId())
        .isEqualTo(rootSpan);
    assertThat(traceData.getSample())
        .isTrue();
  }

  @Test
  public void extractTraceData_128BitTrace() throws Exception {
    String high64Bits = "463ac35c9f6413ad";
    String low64Bits = "48485a3953bb6124";

    MapEntry mapEntry = new MapEntry();
    map.put("X-B3-TraceId", high64Bits + low64Bits);
    map.put("X-B3-SpanId", low64Bits);
    map.put("X-B3-Sampled", "1");

    TraceData traceData = propagation.extractor(mapEntry).extractTraceData(map);

    assertThat(traceData.getSpanId().traceIdHigh)
        .isEqualTo(IdConversion.convertToLong(high64Bits));
    assertThat(traceData.getSpanId().traceId)
        .isEqualTo(IdConversion.convertToLong(low64Bits));
  }

  @Test
  public void extractTraceData_childSpan() throws Exception {
    MapEntry mapEntry = new MapEntry();
    map.put("X-B3-TraceId", "1");
    map.put("X-B3-ParentSpanId", "1");
    map.put("X-B3-SpanId", "2");
    map.put("X-B3-Sampled", "1");

    TraceData traceData = propagation.extractor(mapEntry).extractTraceData(map);

    assertThat(traceData.getSpanId())
        .isEqualTo(childSpan);
  }

  /** This is according to the zipkin 'spec'. */
  @Test
  public void extractTraceData_notSampled() throws Exception {
    MapEntry mapEntry = new MapEntry();
    map.put("X-B3-Sampled", "0");

    TraceData traceData = propagation.extractor(mapEntry).extractTraceData(map);

    assertThat(traceData)
        .isEqualTo(TraceData.NOT_SAMPLED);
  }

  @Test
  public void extractTraceData_sampledFalse() {
    MapEntry mapEntry = new MapEntry();
    map.put("X-B3-Sampled", "false");

    TraceData traceData = propagation.extractor(mapEntry).extractTraceData(map);
    assertNotNull(traceData);
    assertFalse(traceData.getSample());
    assertNull(traceData.getSpanId());
  }

  @Test
  public void extractTraceData_sampledFalseUpperCase() {
    MapEntry mapEntry = new MapEntry();
    map.put("X-B3-Sampled", "FALSE");

    TraceData traceData = propagation.extractor(mapEntry).extractTraceData(map);
    assertNotNull(traceData);
    assertFalse(traceData.getSample());
    assertNull(traceData.getSpanId());
  }

  @Test
  public void extractTraceData_sampledTrueNoOtherTraceHeaders() {
    MapEntry mapEntry = new MapEntry();
    map.put("X-B3-Sampled", "1");

    TraceData traceData = propagation.extractor(mapEntry).extractTraceData(map);
    assertNotNull(traceData);
    assertNull(traceData.getSample());
    assertNull(traceData.getSpanId());
  }

  @Test
  public void extractTraceData_empty() throws Exception {
    MapEntry mapEntry = new MapEntry();

    TraceData traceData = propagation.extractor(mapEntry).extractTraceData(map);

    assertThat(traceData)
        .isEqualTo(TraceData.EMPTY);
  }

  /**
   * When the caller propagates IDs, but not a sampling decision, the local process should decide.
   */
  @Test
  public void extractTraceData_externallyProvidedIds() {
    MapEntry mapEntry = new MapEntry();
    map.put("X-B3-TraceId", "1");
    map.put("X-B3-SpanId", "1");

    TraceData traceData = propagation.extractor(mapEntry).extractTraceData(map);

    assertThat(traceData.getSpanId())
        .isEqualTo(rootSpan);
    assertThat(traceData.getSample())
        .isNull(); // defer decision
  }

  @Test
  public void injectSpanId_rootSpan() throws Exception {
    propagation.injector(mapEntry).injectSpanId(rootSpan, map);

    assertThat(map).containsExactly(
        entry("X-B3-TraceId", "0000000000000001"),
        entry("X-B3-SpanId", "1"),
        entry("X-B3-Sampled", "1")
    );
  }

  @Test
  public void injectSpanId_childSpan() throws Exception {
    MapEntry mapEntry = new MapEntry();
    propagation.injector(mapEntry).injectSpanId(childSpan, map);

    assertThat(map).containsExactly(
        entry("X-B3-TraceId", "0000000000000001"),
        entry("X-B3-SpanId", "2"),
        entry("X-B3-ParentSpanId", "1"),
        entry("X-B3-Sampled", "1")
    );
  }

  @Test
  public void injectSpanId_rootSpan128bit() throws Exception {
    MapEntry mapEntry = new MapEntry();
    propagation.injector(mapEntry)
        .injectSpanId(rootSpan.toBuilder().traceIdHigh(3L).build(), map);

    assertThat(map).containsExactly(
        entry("X-B3-TraceId", "00000000000000030000000000000001"),
        entry("X-B3-SpanId", "1"),
        entry("X-B3-Sampled", "1")
    );
  }

  @Test
  public void injectSpanId_unsampled() throws Exception {
    MapEntry mapEntry = new MapEntry();
    propagation.injector(mapEntry).injectSpanId(null, map);

    assertThat(map).containsExactly(
        entry("X-B3-Sampled", "0")
    );
  }

  static class MapEntry implements
      B3Propagation.Getter<Map<String, String>, String>,
      B3Propagation.Setter<Map<String, String>, String> {

    @Override public void put(Map<String, String> carrier, String key, String value) {
      carrier.put(key, value);
    }

    @Override public String get(Map<String, String> carrier, String key) {
      return carrier.get(key);
    }
  }
}
