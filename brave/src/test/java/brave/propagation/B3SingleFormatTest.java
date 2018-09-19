package brave.propagation;

import org.junit.Test;

import static brave.propagation.B3SingleFormat.parseB3SingleFormat;
import static org.assertj.core.api.Assertions.assertThat;

public class B3SingleFormatTest {
  String traceId = "0000000000000001";
  String parentId = "0000000000000002";
  String spanId = "0000000000000003";

  @Test public void parseB3SingleFormat_idsNotYetSampled() {
    assertThat(parseB3SingleFormat(traceId + "-" + spanId).context())
        .isEqualToComparingFieldByField(
            TraceContext.newBuilder().traceId(1).spanId(3).build()
        );
  }

  @Test public void parseB3SingleFormat_idsNotYetSampled128() {
    assertThat(parseB3SingleFormat(traceId + traceId + "-" + spanId).context())
        .isEqualToComparingFieldByField(
            TraceContext.newBuilder().traceIdHigh(1).traceId(1).spanId(3).build()
        );
  }

  @Test public void parseB3SingleFormat_idsUnsampled() {
    assertThat(parseB3SingleFormat(traceId + "-" + spanId + "-0").context())
        .isEqualToComparingFieldByField(
            TraceContext.newBuilder().traceId(1).spanId(3).sampled(false).build()
        );
  }

  @Test public void parseB3SingleFormat_parent_unsampled() {
    assertThat(parseB3SingleFormat(traceId + "-" + spanId + "-0-" + parentId).context())
        .isEqualToComparingFieldByField(
            TraceContext.newBuilder().traceId(1).parentId(2).spanId(3).sampled(false).build()
        );
  }

  @Test public void parseB3SingleFormat_parent_debug() {
    assertThat(parseB3SingleFormat(traceId + "-" + spanId + "-d-" + parentId).context())
        .isEqualToComparingFieldByField(
            TraceContext.newBuilder().traceId(1).parentId(2).spanId(3).debug(true).build()
        );
  }

  @Test public void parseB3SingleFormat_idsWithDebug() {
    assertThat(parseB3SingleFormat(traceId + "-" + spanId + "-d").context())
        .isEqualToComparingFieldByField(
            TraceContext.newBuilder().traceId(1).spanId(3).debug(true).build()
        );
  }

  @Test public void parseB3SingleFormat_sampledFalse() {
    assertThat(parseB3SingleFormat("0").samplingFlags())
        .isEqualTo(SamplingFlags.NOT_SAMPLED);
  }

  @Test public void parseB3SingleFormat_sampled() {
    assertThat(parseB3SingleFormat("1").samplingFlags())
        .isEqualTo(SamplingFlags.SAMPLED);
  }

  @Test public void parseB3SingleFormat_debug() {
    assertThat(parseB3SingleFormat("d").samplingFlags())
        .isEqualTo(SamplingFlags.DEBUG);
  }

  @Test public void parseB3SingleFormat_malformed_traceId() {
    assertThat(parseB3SingleFormat(traceId.substring(0, 15) + "?-" + spanId))
        .isNull(); // instead of raising exception
  }

  @Test public void parseB3SingleFormat_malformed_id() {
    assertThat(parseB3SingleFormat(traceId + "-" + spanId.substring(0, 15) + "?"))
        .isNull(); // instead of raising exception
  }

  @Test public void parseB3SingleFormat_malformed_sampled_parentid() {
    assertThat(
        parseB3SingleFormat(traceId + "-" + spanId + "-1-" + parentId.substring(0, 15) + "?"))
        .isNull(); // instead of raising exception
  }

  // odd but possible to not yet sample a child
  @Test public void parseB3SingleFormat_malformed_parentid_notYetSampled() {
    assertThat(parseB3SingleFormat(traceId + "-" + spanId + "-" + parentId.substring(0, 15) + "?"))
        .isNull(); // instead of raising exception
  }

  @Test public void parseB3SingleFormat_malformed() {
    assertThat(parseB3SingleFormat("not-a-tumor"))
        .isNull(); // instead of raising exception
  }

  @Test public void parseB3SingleFormat_malformed_uuid() {
    assertThat(parseB3SingleFormat("b970dafd-0d95-40aa-95d8-1d8725aebe40"))
        .isNull(); // instead of raising exception
  }

  @Test public void parseB3SingleFormat_truncated() {
    assertThat(parseB3SingleFormat(""))
        .isNull(); // instead of raising exception
    assertThat(parseB3SingleFormat("-"))
        .isNull();
    assertThat(parseB3SingleFormat("-1"))
        .isNull();
    assertThat(parseB3SingleFormat("1-"))
        .isNull();
    assertThat(parseB3SingleFormat(traceId.substring(0, 15)))
        .isNull();
    assertThat(parseB3SingleFormat(traceId))
        .isNull();
    assertThat(parseB3SingleFormat(traceId + "-"))
        .isNull();
    assertThat(parseB3SingleFormat(traceId.substring(0, 15) + "-" + spanId))
        .isNull();
    assertThat(parseB3SingleFormat(traceId + "-" + spanId.substring(0, 15)))
        .isNull();
    assertThat(parseB3SingleFormat(traceId + "-" + spanId + "-"))
        .isNull();
    assertThat(parseB3SingleFormat(traceId + "-" + spanId + "-1-"))
        .isNull();
    assertThat(parseB3SingleFormat(traceId + "-" + spanId + "-1-" + parentId.substring(0, 15)))
        .isNull();
  }

  @Test public void parseB3SingleFormat_tooBig() {
    // overall length is ok, but it is malformed as parent is too long
    assertThat(parseB3SingleFormat(traceId + "-" + spanId + "-" + traceId + traceId))
        .isNull(); // instead of raising exception
    // overall length is not ok
    assertThat(parseB3SingleFormat(traceId + traceId + traceId + "-" + spanId + "-" + traceId))
        .isNull();
  }
}
