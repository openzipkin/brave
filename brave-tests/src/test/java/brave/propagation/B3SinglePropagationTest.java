package brave.propagation;

import brave.internal.Nullable;
import brave.test.propagation.PropagationTest;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class B3SinglePropagationTest extends PropagationTest<String> {
  @Override protected Class<? extends Supplier<Propagation<String>>> propagationSupplier() {
    return PropagationSupplier.class;
  }

  static class PropagationSupplier implements Supplier<Propagation<String>> {
    @Override public Propagation<String> get() {
      return Propagation.B3_SINGLE_STRING;
    }
  }

  @Override protected void inject(Map<String, String> map, @Nullable String traceId,
      @Nullable String parentId, @Nullable String spanId, @Nullable Boolean sampled,
      @Nullable Boolean debug) {
    StringBuilder builder = new StringBuilder();
    char sampledChar = sampledChar(sampled, debug);
    if (traceId == null) {
      if (sampledChar != 0) builder.append(sampledChar);
    } else {
      builder.append(traceId).append('-').append(spanId);
      if (sampledChar != 0) builder.append('-').append(sampledChar);
      if (parentId != null) builder.append('-').append(parentId);
    }
    if (builder.length() != 0) map.put("b3", builder.toString());
  }

  /** returns 0 if there's no sampling status */
  static char sampledChar(@Nullable Boolean sampled, @Nullable Boolean debug) {
    if (Boolean.TRUE.equals(debug)) return 'd';
    if (sampled != null) return sampled ? '1' : '0';
    return 0;
  }

  @Override protected void inject(Map<String, String> carrier, SamplingFlags flags) {
    char sampledChar = sampledChar(flags.sampled(), flags.debug());
    if (sampledChar != 0) carrier.put("b3", String.valueOf(sampledChar));
  }

  @Test public void extractTraceContext_sampledFalse() {
    MapEntry<String> mapEntry = new MapEntry<>();
    map.put("b3", "0");

    SamplingFlags result = propagation.extractor(mapEntry).extract(map).samplingFlags();

    assertThat(result)
        .isEqualTo(SamplingFlags.NOT_SAMPLED);
  }

  @Test public void extractTraceContext_malformed() {
    MapEntry<String> mapEntry = new MapEntry<>();
    map.put("b3", "not-a-tumor");

    SamplingFlags result = propagation.extractor(mapEntry).extract(map).samplingFlags();

    assertThat(result)
        .isEqualTo(SamplingFlags.EMPTY);
  }

  @Test public void extractTraceContext_malformed_uuid() {
    MapEntry<String> mapEntry = new MapEntry<>();
    map.put("b3", "b970dafd-0d95-40aa-95d8-1d8725aebe40");

    SamplingFlags result = propagation.extractor(mapEntry).extract(map).samplingFlags();

    assertThat(result)
        .isEqualTo(SamplingFlags.EMPTY);
  }

  @Test public void extractTraceContext_debug_with_ids() {
    MapEntry<String> mapEntry = new MapEntry<>();

    map.put("b3", "4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-d");

    TraceContext result = propagation.extractor(mapEntry).extract(map).context();

    assertThat(result.debug())
        .isTrue();
  }
}
