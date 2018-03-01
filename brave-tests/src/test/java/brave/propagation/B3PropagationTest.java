package brave.propagation;

import brave.internal.Nullable;
import java.util.Map;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class B3PropagationTest extends PropagationTest<String> {
  @Override Propagation<String> propagation() {
    return Propagation.B3_STRING;
  }

  @Override void inject(Map<String, String> map, @Nullable String traceId,
      @Nullable String parentId, @Nullable String spanId, @Nullable Boolean sampled,
      @Nullable Boolean debug) {
    if (traceId != null) map.put("X-B3-TraceId", traceId);
    if (parentId != null) map.put("X-B3-ParentSpanId", parentId);
    if (spanId != null) map.put("X-B3-SpanId", spanId);
    if (sampled != null) map.put("X-B3-Sampled", sampled ? "1" : "0");
    if (debug != null) map.put("X-B3-Flags", debug ? "1" : "0");
  }

  @Override void inject(Map<String, String> carrier, SamplingFlags flags) {
    if (flags.debug()) {
      carrier.put("X-B3-Flags", "1");
    } else if (flags.sampled() != null) {
      carrier.put("X-B3-Sampled", flags.sampled() ? "1" : "0");
    }
  }

  @Test public void extractTraceContext_sampledFalse() {
    MapEntry mapEntry = new MapEntry();
    map.put("X-B3-Sampled", "false");

    SamplingFlags result = propagation().extractor(mapEntry).extract(map).samplingFlags();

    assertThat(result)
        .isEqualTo(SamplingFlags.NOT_SAMPLED);
  }

  @Test public void extractTraceContext_sampledFalseUpperCase() {
    MapEntry mapEntry = new MapEntry();
    map.put("X-B3-Sampled", "FALSE");

    SamplingFlags result = propagation().extractor(mapEntry).extract(map).samplingFlags();

    assertThat(result)
        .isEqualTo(SamplingFlags.NOT_SAMPLED);
  }

  @Test public void extractTraceContext_malformed() {
    MapEntry mapEntry = new MapEntry();
    map.put("X-B3-TraceId", "463ac35c9f6413ad48485a3953bb6124"); // ok
    map.put("X-B3-SpanId",  "48485a3953bb6124"); // ok
    map.put("X-B3-ParentSpanId", "-"); // not ok

    SamplingFlags result = propagation().extractor(mapEntry).extract(map).samplingFlags();

    assertThat(result)
        .isEqualTo(SamplingFlags.EMPTY);
  }

  @Test public void extractTraceContext_malformed_sampled() {
    MapEntry mapEntry = new MapEntry();
    map.put("X-B3-TraceId", "-"); // not ok
    map.put("X-B3-Sampled", "1"); // ok

    SamplingFlags result = propagation().extractor(mapEntry).extract(map).samplingFlags();

    assertThat(result)
        .isEqualTo(SamplingFlags.EMPTY);
  }
}
