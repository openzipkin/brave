package brave.propagation;

import brave.internal.HexCodec;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;

public class B3PropagationTest {

  Propagation<String> propagation = Propagation.B3_STRING;
  Map<String, String> map = new LinkedHashMap<>();
  MapEntry mapEntry = new MapEntry();

  TraceContext rootSpan = TraceContext.newBuilder()
      .traceId(1L)
      .spanId(1L)
      .sampled(true).build();
  TraceContext childSpan = rootSpan.toBuilder()
      .parentId(rootSpan.spanId())
      .spanId(2).build();

  @Test
  public void extractTraceContext_rootSpan() throws Exception {
    map.put("X-B3-TraceId", "0000000000000001");
    map.put("X-B3-SpanId", "0000000000000001");
    map.put("X-B3-Sampled", "1");

    TraceContext traceContext = propagation.extractor(mapEntry).extract(map).context();

    assertThat(traceContext)
        .isEqualTo(rootSpan);
  }

  @Test
  public void extractTraceContext_128BitTrace() throws Exception {
    String high64Bits = "463ac35c9f6413ad";
    String low64Bits = "48485a3953bb6124";

    MapEntry mapEntry = new MapEntry();
    map.put("X-B3-TraceId", high64Bits + low64Bits);
    map.put("X-B3-SpanId", low64Bits);
    map.put("X-B3-Sampled", "1");

    TraceContext traceContext = propagation.extractor(mapEntry).extract(map).context();

    assertThat(traceContext.traceIdHigh())
        .isEqualTo(HexCodec.lowerHexToUnsignedLong(high64Bits));
    assertThat(traceContext.traceId())
        .isEqualTo(HexCodec.lowerHexToUnsignedLong(low64Bits));
  }

  @Test
  public void extractTraceContext_childSpan() throws Exception {
    MapEntry mapEntry = new MapEntry();
    map.put("X-B3-TraceId", "0000000000000001");
    map.put("X-B3-ParentSpanId", "0000000000000001");
    map.put("X-B3-SpanId", "0000000000000002");
    map.put("X-B3-Sampled", "1");

    TraceContext traceContext = propagation.extractor(mapEntry).extract(map).context();

    assertThat(traceContext)
        .isEqualTo(childSpan);
  }

  @Test
  public void extractTraceContext_notSampled() throws Exception {
    MapEntry mapEntry = new MapEntry();
    map.put("X-B3-TraceId", "0000000000000001");
    map.put("X-B3-ParentSpanId", "0000000000000001");
    map.put("X-B3-SpanId", "0000000000000002");
    map.put("X-B3-Sampled", "0");

    TraceContext traceContext = propagation.extractor(mapEntry).extract(map).context();

    assertThat(traceContext)
        .isEqualTo(childSpan.toBuilder().sampled(false).build());
  }

  @Test
  public void extractTraceContext_notSampled_noIds() throws Exception {
    MapEntry mapEntry = new MapEntry();
    map.put("X-B3-Sampled", "0");

    SamplingFlags result = propagation.extractor(mapEntry).extract(map).samplingFlags();

    assertThat(result)
        .isEqualTo(SamplingFlags.NOT_SAMPLED);
  }

  @Test
  public void extractTraceContext_sampledFalse() {
    MapEntry mapEntry = new MapEntry();
    map.put("X-B3-Sampled", "false");

    SamplingFlags result = propagation.extractor(mapEntry).extract(map).samplingFlags();

    assertThat(result)
        .isEqualTo(SamplingFlags.NOT_SAMPLED);
  }

  @Test
  public void extractTraceContext_sampledFalseUpperCase() {
    MapEntry mapEntry = new MapEntry();
    map.put("X-B3-Sampled", "FALSE");

    SamplingFlags result = propagation.extractor(mapEntry).extract(map).samplingFlags();

    assertThat(result)
        .isEqualTo(SamplingFlags.NOT_SAMPLED);
  }

  @Test
  public void extractTraceContext_sampledTrueNoOtherTraceHeaders() {
    MapEntry mapEntry = new MapEntry();
    map.put("X-B3-Sampled", "1");

    SamplingFlags result = propagation.extractor(mapEntry).extract(map).samplingFlags();

    assertThat(result)
        .isEqualTo(SamplingFlags.SAMPLED);
  }

  @Test
  public void extractTraceContext_debug() {
    MapEntry mapEntry = new MapEntry();
    map.put("X-B3-Flags", "1");

    SamplingFlags result = propagation.extractor(mapEntry).extract(map).samplingFlags();

    assertThat(result)
        .isEqualTo(SamplingFlags.DEBUG);
  }

  @Test
  public void extractTraceContext_empty() throws Exception {
    MapEntry mapEntry = new MapEntry();

    SamplingFlags result = propagation.extractor(mapEntry).extract(map).samplingFlags();

    assertThat(result)
        .isEqualTo(SamplingFlags.EMPTY);
  }

  /**
   * When the caller propagates IDs, but not a sampling decision, the local process should decide.
   */
  @Test
  public void extractTraceContext_externallyProvidedIds() {
    MapEntry mapEntry = new MapEntry();
    map.put("X-B3-TraceId", "1");
    map.put("X-B3-SpanId", "1");

    TraceContext traceContext = propagation.extractor(mapEntry).extract(map).context();

    assertThat(traceContext)
        .isEqualTo(rootSpan.toBuilder().sampled(null).build());
  }

  @Test
  public void injectTraceContext_rootSpan() throws Exception {
    propagation.injector(mapEntry).inject(rootSpan, map);

    assertThat(map).containsExactly(
        entry("X-B3-TraceId", "0000000000000001"),
        entry("X-B3-SpanId", "0000000000000001"),
        entry("X-B3-Sampled", "1")
    );
  }

  @Test
  public void injectTraceContext_childSpan() throws Exception {
    MapEntry mapEntry = new MapEntry();
    propagation.injector(mapEntry).inject(childSpan, map);

    assertThat(map).containsExactly(
        entry("X-B3-TraceId", "0000000000000001"),
        entry("X-B3-SpanId", "0000000000000002"),
        entry("X-B3-ParentSpanId", "0000000000000001"),
        entry("X-B3-Sampled", "1")
    );
  }

  @Test
  public void injectTraceContext_rootSpan128bit() throws Exception {
    MapEntry mapEntry = new MapEntry();
    propagation.injector(mapEntry)
        .inject(rootSpan.toBuilder().traceIdHigh(3).traceId(1).build(), map);

    assertThat(map).containsExactly(
        entry("X-B3-TraceId", "00000000000000030000000000000001"),
        entry("X-B3-SpanId", "0000000000000001"),
        entry("X-B3-Sampled", "1")
    );
  }

  @Test
  public void injectTraceContext_unsampled() throws Exception {
    MapEntry mapEntry = new MapEntry();
    propagation.injector(mapEntry).inject(rootSpan.toBuilder().sampled(false).build(), map);

    assertThat(map).containsExactly(
        entry("X-B3-TraceId", "0000000000000001"),
        entry("X-B3-SpanId", "0000000000000001"),
        entry("X-B3-Sampled", "0")
    );
  }

  static class MapEntry implements
      Propagation.Getter<Map<String, String>, String>,
      Propagation.Setter<Map<String, String>, String> {

    @Override public void put(Map<String, String> carrier, String key, String value) {
      carrier.put(key, value);
    }

    @Override public String get(Map<String, String> carrier, String key) {
      return carrier.get(key);
    }
  }
}
