package brave.propagation.aws;

import brave.internal.HexCodec;
import brave.propagation.Propagation;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.propagation.TraceIdContext;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AWSPropagationTest {
  Map<String, String> carrier = new LinkedHashMap<>();
  TraceContext.Injector<Map<String, String>> injector =
      new AWSPropagation.Factory().create(Propagation.KeyFactory.STRING).injector(Map::put);
  TraceContext.Extractor<Map<String, String>> extractor =
      new AWSPropagation.Factory().create(Propagation.KeyFactory.STRING).extractor(Map::get);

  String sampledTraceId =
      "Root=1-67891233-abcdef012345678912345678;Parent=463ac35c9f6413ad;Sampled=1";
  TraceContext sampledContext = TraceContext.newBuilder()
      .traceIdHigh(HexCodec.lowerHexToUnsignedLong("67891233abcdef01"))
      .traceId(HexCodec.lowerHexToUnsignedLong("2345678912345678"))
      .spanId(HexCodec.lowerHexToUnsignedLong("463ac35c9f6413ad"))
      .sampled(true)
      .build();

  @Test public void traceIdString() throws Exception {
    assertThat(AWSPropagation.traceIdString(sampledContext))
        .isEqualTo("1-67891233-abcdef012345678912345678");
  }

  @Test public void inject() throws Exception {
    injector.inject(sampledContext, carrier);

    assertThat(carrier).containsEntry("x-amzn-trace-id", sampledTraceId);
  }

  @Test public void extract() throws Exception {
    carrier.put("x-amzn-trace-id", sampledTraceId);

    assertThat(extractor.extract(carrier).context())
        .isEqualTo(sampledContext);
  }

  @Test public void extract_static() throws Exception {
    assertThat(AWSPropagation.extract(sampledTraceId).context())
        .isEqualTo(sampledContext);
  }

  @Test public void extractDifferentOrder() throws Exception {
    carrier.put("x-amzn-trace-id",
        "Sampled=1;Parent=463ac35c9f6413ad;Root=1-67891233-abcdef012345678912345678");

    assertThat(extractor.extract(carrier).context())
        .isEqualTo(sampledContext);
  }

  @Test public void extract_noParent() throws Exception {
    carrier.put("x-amzn-trace-id", "Root=1-5759e988-bd862e3fe1be46a994272793;Sampled=1");

    assertThat(extractor.extract(carrier).traceIdContext())
        .isEqualTo(TraceIdContext.newBuilder()
            .traceIdHigh(HexCodec.lowerHexToUnsignedLong("5759e988bd862e3f"))
            .traceId(HexCodec.lowerHexToUnsignedLong("e1be46a994272793"))
            .sampled(true)
            .build());
  }

  @Test public void extract_noSamplingDecision() throws Exception {
    carrier.put("x-amzn-trace-id", sampledTraceId.replace("Sampled=1", "Sampled=?"));

    assertThat(extractor.extract(carrier).context())
        .isEqualTo(sampledContext.toBuilder().sampled(null).build());
  }

  @Test public void extract_sampledFalse() throws Exception {
    carrier.put("x-amzn-trace-id", sampledTraceId.replace("Sampled=1", "Sampled=0"));

    assertThat(extractor.extract(carrier).context())
        .isEqualTo(sampledContext.toBuilder().sampled(false).build());
  }

  /** Shows we skip whitespace and extra fields like self or custom ones */
  // https://aws.amazon.com/blogs/aws/application-performance-percentiles-and-request-tracing-for-aws-application-load-balancer/
  @Test public void extract_skipsSelfField() throws Exception {
    // TODO: check with AWS if it is valid to have arbitrary fields in front of standard ones.
    // we currently permit them
    carrier.put("x-amzn-trace-id", "Robot=Hello;Self=1-582113d1-1e48b74b3603af8479078ed6;  " +
        "Root=1-58211399-36d228ad5d99923122bbe354;  " +
        "TotalTimeSoFar=112ms;CalledFrom=Foo");

    TraceContextOrSamplingFlags extracted = extractor.extract(carrier);
    assertThat(extracted.traceIdContext())
        .isEqualTo(TraceIdContext.newBuilder()
            .traceIdHigh(HexCodec.lowerHexToUnsignedLong("5821139936d228ad"))
            .traceId(HexCodec.lowerHexToUnsignedLong("5d99923122bbe354"))
            .build());

    assertThat(((AWSPropagation.Extra) extracted.extra().get(0)).fields)
        .contains(new StringBuilder(";Robot=Hello;TotalTimeSoFar=112ms;CalledFrom=Foo"));
  }

  @Test public void injectExtraStuff() throws Exception {
    AWSPropagation.Extra extra = new AWSPropagation.Extra();
    extra.fields = ";Robot=Hello;TotalTimeSoFar=112ms;CalledFrom=Foo";
    TraceContext extraContext = sampledContext.toBuilder().extra(Arrays.asList(extra)).build();
    injector.inject(extraContext, carrier);

    assertThat(carrier)
        .containsEntry("x-amzn-trace-id",
            "Root=1-67891233-abcdef012345678912345678;Parent=463ac35c9f6413ad;Sampled=1;Robot=Hello;TotalTimeSoFar=112ms;CalledFrom=Foo");
  }

  @Test public void extract_skipsLaterVersion() throws Exception {
    carrier.put("x-amzn-trace-id", "Root=2-58211399-36d228ad5d99923122bbe354");

    assertThat(extractor.extract(carrier).samplingFlags())
        .isEqualTo(SamplingFlags.EMPTY);
  }

  @Test public void extract_skipsTruncatedId() throws Exception {
    carrier.put("x-amzn-trace-id", "Root=1-58211399-36d228ad5d99923122bbe35");

    assertThat(extractor.extract(carrier).samplingFlags())
        .isEqualTo(SamplingFlags.EMPTY);
  }

  @Test public void extract_skips_leadingEquals() throws Exception {
    carrier.put("x-amzn-trace-id", "=Root=1-58211399-36d228ad5d99923122bbe354");

    assertThat(extractor.extract(carrier).samplingFlags())
        .isEqualTo(SamplingFlags.EMPTY);
  }

  @Test public void extract_skips_doubleEquals() throws Exception {
    carrier.put("x-amzn-trace-id", "Root==1-58211399-36d228ad5d99923122bbe354");

    assertThat(extractor.extract(carrier).samplingFlags())
        .isEqualTo(SamplingFlags.EMPTY);
  }

  @Test public void extract_skips_noEquals() throws Exception {
    carrier.put("x-amzn-trace-id", "1-58211399-36d228ad5d99923122bbe354");

    assertThat(extractor.extract(carrier).samplingFlags())
        .isEqualTo(SamplingFlags.EMPTY);
  }
}
