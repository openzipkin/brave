package brave.propagation.aws;

import brave.Tracing;
import brave.propagation.B3Propagation;
import brave.propagation.CurrentTraceContext;
import brave.propagation.ExtraFieldPropagation;
import brave.propagation.Propagation;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.propagation.TraceIdContext;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

import static brave.internal.HexCodec.lowerHexToUnsignedLong;
import static org.assertj.core.api.Assertions.assertThat;

public class AWSPropagationTest {
  Map<String, String> carrier = new LinkedHashMap<>();
  TraceContext.Injector<Map<String, String>> injector =
      AWSPropagation.FACTORY.create(Propagation.KeyFactory.STRING).injector(Map::put);
  TraceContext.Extractor<Map<String, String>> extractor =
      AWSPropagation.FACTORY.create(Propagation.KeyFactory.STRING).extractor(Map::get);

  String sampledTraceId =
      "Root=1-67891233-abcdef012345678912345678;Parent=463ac35c9f6413ad;Sampled=1";
  TraceContext sampledContext = TraceContext.newBuilder()
      .traceIdHigh(lowerHexToUnsignedLong("67891233abcdef01"))
      .traceId(lowerHexToUnsignedLong("2345678912345678"))
      .spanId(lowerHexToUnsignedLong("463ac35c9f6413ad"))
      .sampled(true)
      .extra(AWSPropagation.DEFAULT_EXTRA)
      .build();

  @Test public void traceId() {
    assertThat(AWSPropagation.traceId(sampledContext))
        .isEqualTo("1-67891233-abcdef012345678912345678");
  }

  @Test public void traceIdWhenPassThrough() {
    carrier.put("x-amzn-trace-id", "Robot=Hello;Self=1-582113d1-1e48b74b3603af8479078ed6;  " +
        "Root=1-58211399-36d228ad5d99923122bbe354;  " +
        "TotalTimeSoFar=112ms;CalledFrom=Foo");

    TraceContext context = contextWithPassThrough();

    assertThat(AWSPropagation.traceId(context))
        .isEqualTo("1-58211399-36d228ad5d99923122bbe354");
  }

  @Test public void traceIdWhenPassThrough_nullOnTruncated() {
    carrier.put("x-amzn-trace-id", "Root=1-58211399-36d228ad5d99923122bbe3");

    TraceContext context = contextWithPassThrough();

    assertThat(AWSPropagation.traceId(context))
        .isNull();
  }

  TraceContext contextWithPassThrough() {
    extractor = ExtraFieldPropagation.newFactory(B3Propagation.FACTORY, "x-amzn-trace-id")
        .create(Propagation.KeyFactory.STRING).extractor(Map::get);

    TraceContextOrSamplingFlags extracted = extractor.extract(carrier);

    // sanity check
    assertThat(extracted.samplingFlags())
        .isEqualTo(SamplingFlags.EMPTY);
    assertThat(extracted.extra())
        .isNotEmpty();

    // Make a context that wasn't from AWSPropagation
    return TraceContext.newBuilder()
        .traceId(1L)
        .spanId(2L)
        .sampled(true)
        .extra(extracted.extra())
        .build();
  }

  @Test public void traceId_null_if_not_aws() {
    TraceContext notAWS = sampledContext.toBuilder().extra(Collections.emptyList()).build();
    assertThat(AWSPropagation.traceId(notAWS))
        .isNull();
  }

  @Test public void currentTraceId() {
    try (Tracing t = Tracing.newBuilder().propagationFactory(AWSPropagation.FACTORY).build();
         CurrentTraceContext.Scope scope = t.currentTraceContext().newScope(sampledContext)) {
      assertThat(AWSPropagation.currentTraceId())
          .isEqualTo("1-67891233-abcdef012345678912345678");
    }
  }

  @Test public void currentTraceId_null_if_no_current_context() {
    try (Tracing t = Tracing.newBuilder().propagationFactory(AWSPropagation.FACTORY).build()) {
      assertThat(AWSPropagation.currentTraceId())
          .isNull();
    }
  }

  @Test public void currentTraceId_null_if_nothing_current() {
    assertThat(AWSPropagation.currentTraceId())
        .isNull();
  }

  @Test public void inject() {
    injector.inject(sampledContext, carrier);

    assertThat(carrier).containsEntry("x-amzn-trace-id", sampledTraceId);
  }

  @Test public void extract() {
    carrier.put("x-amzn-trace-id", sampledTraceId);

    assertThat(extractor.extract(carrier).context())
        .isEqualTo(sampledContext);
  }

  @Test public void extract_containsMarker() {
    carrier.put("x-amzn-trace-id", sampledTraceId);

    TraceContextOrSamplingFlags extracted = extractor.extract(carrier);
    assertThat(extracted.context().extra())
        .containsExactly(AWSPropagation.MARKER);
  }

  /** If invoked extract, a 128-bit trace ID will be created, compatible with AWS format */
  @Test public void extract_fail_containsMarker() {
    TraceContextOrSamplingFlags extracted = extractor.extract(carrier);
    assertThat(extracted.extra())
        .containsExactly(AWSPropagation.MARKER);
  }

  @Test public void extract_static() {
    assertThat(AWSPropagation.extract(sampledTraceId).context())
        .isEqualTo(sampledContext);
  }

  @Test public void extractDifferentOrder() {
    carrier.put("x-amzn-trace-id",
        "Sampled=1;Parent=463ac35c9f6413ad;Root=1-67891233-abcdef012345678912345678");

    assertThat(extractor.extract(carrier).context())
        .isEqualTo(sampledContext);
  }

  @Test public void extract_noParent() {
    carrier.put("x-amzn-trace-id", "Root=1-5759e988-bd862e3fe1be46a994272793;Sampled=1");

    assertThat(extractor.extract(carrier).traceIdContext())
        .isEqualTo(TraceIdContext.newBuilder()
            .traceIdHigh(lowerHexToUnsignedLong("5759e988bd862e3f"))
            .traceId(lowerHexToUnsignedLong("e1be46a994272793"))
            .sampled(true)
            .build());
  }

  @Test public void extract_noSamplingDecision() {
    carrier.put("x-amzn-trace-id", sampledTraceId.replace("Sampled=1", "Sampled=?"));

    assertThat(extractor.extract(carrier).context())
        .isEqualTo(sampledContext.toBuilder().sampled(null).build());
  }

  @Test public void extract_sampledFalse() {
    carrier.put("x-amzn-trace-id", sampledTraceId.replace("Sampled=1", "Sampled=0"));

    assertThat(extractor.extract(carrier).context())
        .isEqualTo(sampledContext.toBuilder().sampled(false).build());
  }

  /** Shows we skip whitespace and extra fields like self or custom ones */
  // https://aws.amazon.com/blogs/aws/application-performance-percentiles-and-request-tracing-for-aws-application-load-balancer/
  @Test public void extract_skipsSelfField() {
    // TODO: check with AWS if it is valid to have arbitrary fields in front of standard ones.
    // we currently permit them
    carrier.put("x-amzn-trace-id", "Robot=Hello;Self=1-582113d1-1e48b74b3603af8479078ed6;  " +
        "Root=1-58211399-36d228ad5d99923122bbe354;  " +
        "TotalTimeSoFar=112ms;CalledFrom=Foo");

    TraceContextOrSamplingFlags extracted = extractor.extract(carrier);
    assertThat(extracted.traceIdContext())
        .isEqualTo(TraceIdContext.newBuilder()
            .traceIdHigh(lowerHexToUnsignedLong("5821139936d228ad"))
            .traceId(lowerHexToUnsignedLong("5d99923122bbe354"))
            .build());

    assertThat(((AWSPropagation.Extra) extracted.extra().get(0)).fields)
        .contains(new StringBuilder(";Robot=Hello;TotalTimeSoFar=112ms;CalledFrom=Foo"));
  }

  @Test public void toString_fields() {
    AWSPropagation.Extra extra = new AWSPropagation.Extra();
    extra.fields = ";Robot=Hello;TotalTimeSoFar=112ms;CalledFrom=Foo";

    assertThat(extra)
        .hasToString("AWSPropagation{fields=" + extra.fields + "}");
  }

  @Test public void toString_none() {
    AWSPropagation.Extra extra = new AWSPropagation.Extra();

    assertThat(extra)
        .hasToString("AWSPropagation{}");
  }

  @Test public void injectExtraStuff() {
    AWSPropagation.Extra extra = new AWSPropagation.Extra();
    extra.fields = ";Robot=Hello;TotalTimeSoFar=112ms;CalledFrom=Foo";
    TraceContext extraContext = sampledContext.toBuilder().extra(Arrays.asList(extra)).build();
    injector.inject(extraContext, carrier);

    assertThat(carrier)
        .containsEntry("x-amzn-trace-id",
            "Root=1-67891233-abcdef012345678912345678;Parent=463ac35c9f6413ad;Sampled=1;Robot=Hello;TotalTimeSoFar=112ms;CalledFrom=Foo");
  }

  @Test public void extract_skipsLaterVersion() {
    carrier.put("x-amzn-trace-id", "Root=2-58211399-36d228ad5d99923122bbe354");

    assertThat(extractor.extract(carrier).samplingFlags())
        .isEqualTo(SamplingFlags.EMPTY);
  }

  @Test public void extract_skipsTruncatedId() {
    carrier.put("x-amzn-trace-id", "Root=1-58211399-36d228ad5d99923122bbe35");

    assertThat(extractor.extract(carrier).samplingFlags())
        .isEqualTo(SamplingFlags.EMPTY);
  }

  @Test public void extract_skips_leadingEquals() {
    carrier.put("x-amzn-trace-id", "=Root=1-58211399-36d228ad5d99923122bbe354");

    assertThat(extractor.extract(carrier).samplingFlags())
        .isEqualTo(SamplingFlags.EMPTY);
  }

  @Test public void extract_skips_doubleEquals() {
    carrier.put("x-amzn-trace-id", "Root==1-58211399-36d228ad5d99923122bbe354");

    assertThat(extractor.extract(carrier).samplingFlags())
        .isEqualTo(SamplingFlags.EMPTY);
  }

  @Test public void extract_skips_noEquals() {
    carrier.put("x-amzn-trace-id", "1-58211399-36d228ad5d99923122bbe354");

    assertThat(extractor.extract(carrier).samplingFlags())
        .isEqualTo(SamplingFlags.EMPTY);
  }

  @Test public void extract_skips_malformed() {
    carrier.put("x-amzn-trace-id",
        "Sampled=-;Parent=463ac35%Af6413ad;Root=1-??-abc!#%0123456789123456");

    assertThat(extractor.extract(carrier).samplingFlags())
        .isEqualTo(SamplingFlags.EMPTY);
  }

  @Test public void extract_skips_really_malformed() {
    carrier.put("x-amzn-trace-id", "holy 💩");

    assertThat(extractor.extract(carrier).samplingFlags())
        .isEqualTo(SamplingFlags.EMPTY);
  }
}
