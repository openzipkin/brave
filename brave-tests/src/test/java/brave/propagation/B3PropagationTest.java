package brave.propagation;

import java.util.Map;
import javax.annotation.Nullable;
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
}
