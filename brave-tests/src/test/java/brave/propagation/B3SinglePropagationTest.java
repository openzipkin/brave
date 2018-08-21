package brave.propagation;

import brave.internal.Nullable;
import brave.test.propagation.PropagationTest;
import java.util.Map;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class B3SinglePropagationTest extends PropagationTest<String> {
  @Override protected Propagation<String> propagation() {
    return Propagation.B3_SINGLE_STRING;
  }

  @Override protected void inject(Map<String, String> map, @Nullable String traceId,
      @Nullable String parentId, @Nullable String spanId, @Nullable Boolean sampled,
      @Nullable Boolean debug) {
    StringBuilder builder = new StringBuilder();
    if (traceId == null) {
      if (sampled != null) {
        builder.append(sampled ? '1' : '0');
        if (debug != null) builder.append(debug ? "-1" : "-0");
      } else if (Boolean.TRUE.equals(debug)) {
        builder.append("1-1");
      }
    } else {
      builder.append(traceId).append('-').append(spanId);
      if (sampled != null) builder.append(sampled ? "-1" : "-0");
      if (parentId != null) builder.append('-').append(parentId);
      if (debug != null) builder.append(debug ? "-1" : "-0");
    }
    if (builder.length() != 0) map.put("b3", builder.toString());
  }

  @Override protected void inject(Map<String, String> carrier, SamplingFlags flags) {
    if (flags.debug()) {
      carrier.put("b3", "1-1");
    } else if (flags.sampled() != null) {
      carrier.put("b3", flags.sampled() ? "1" : "0");
    }
  }

  @Test public void extractTraceContext_sampledFalse() {
    MapEntry mapEntry = new MapEntry();
    map.put("b3", "0");

    SamplingFlags result = propagation().extractor(mapEntry).extract(map).samplingFlags();

    assertThat(result)
        .isEqualTo(SamplingFlags.NOT_SAMPLED);
  }

  @Test public void extractTraceContext_sampledFalseUpperCase() {
    MapEntry mapEntry = new MapEntry();
    map.put("B3", "0");

    SamplingFlags result = propagation().extractor(mapEntry).extract(map).samplingFlags();

    assertThat(result)
        .isEqualTo(SamplingFlags.NOT_SAMPLED);
  }

  @Test public void extractTraceContext_malformed() {
    MapEntry mapEntry = new MapEntry();
    map.put("b3", "not-a-tumor");

    SamplingFlags result = propagation().extractor(mapEntry).extract(map).samplingFlags();

    assertThat(result)
        .isEqualTo(SamplingFlags.EMPTY);
  }

  @Test public void extractTraceContext_malformed_uuid() {
    MapEntry mapEntry = new MapEntry();
    map.put("b3", "b970dafd-0d95-40aa-95d8-1d8725aebe40");

    SamplingFlags result = propagation().extractor(mapEntry).extract(map).samplingFlags();

    assertThat(result)
        .isEqualTo(SamplingFlags.EMPTY);
  }

  @Test public void extractTraceContext_debug_with_ids() {
    MapEntry mapEntry = new MapEntry();

    map.put("b3", "4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-1-1");

    TraceContext result = propagation().extractor(mapEntry).extract(map).context();

    assertThat(result.debug())
        .isTrue();
  }
}
