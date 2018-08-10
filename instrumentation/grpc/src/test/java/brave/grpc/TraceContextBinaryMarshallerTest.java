package brave.grpc;

import brave.propagation.MutableTraceContext;
import brave.propagation.TraceContext;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests here are based on {@code io.opencensus.implcore.trace.propagation.BinaryFormatImplTest}
 */
public class TraceContextBinaryMarshallerTest {
  TraceContext context = TraceContext.newBuilder()
      .traceIdHigh(Long.MAX_VALUE).traceId(Long.MIN_VALUE)
      .spanId(-1)
      .sampled(true)
      .build();
  MutableTraceContext mutableContext = new MutableTraceContext();
  byte[] contextBytes = {
      0, // version
      0, 127, -1, -1, -1, -1, -1, -1, -1, -128, 0, 0, 0, 0, 0, 0, 0, // trace ID
      1, -1, -1, -1, -1, -1, -1, -1, -1, // span ID
      2, 1 // sampled
  };

  @Test public void roundtrip() {
    byte[] serialized = TraceContextBinaryMarshaller.toBytes(context);
    assertThat(serialized)
        .containsExactly(contextBytes);

    TraceContextBinaryMarshaller.extract(serialized, mutableContext);
    assertThat(mutableContext.toTraceContext())
        .isEqualTo(context);
  }

  @Test public void roundtrip_unsampled() {
    context = context.toBuilder().sampled(false).build();

    byte[] serialized = TraceContextBinaryMarshaller.toBytes(context);
    contextBytes[contextBytes.length - 1] = 0; // unsampled
    assertThat(serialized)
        .containsExactly(contextBytes);

    TraceContextBinaryMarshaller.extract(serialized, mutableContext);
    assertThat(mutableContext.toTraceContext())
        .isEqualTo(context);
  }

  @Test public void extract_empty_isEmpty() {
    TraceContextBinaryMarshaller.extract(new byte[0], mutableContext);

    assertThat(mutableContext.isEmpty()).isTrue();
  }

  @Test public void extract_unsupportedVersionId_isEmpty() {
    TraceContextBinaryMarshaller.extract(new byte[] {
        1, // bad version
        0, 127, -1, -1, -1, -1, -1, -1, -1, -128, 0, 0, 0, 0, 0, 0, 0,
        1, -1, -1, -1, -1, -1, -1, -1, -1,
        2, 1
    }, mutableContext);

    assertThat(mutableContext.isEmpty()).isTrue();
  }

  @Test public void extract_unsupportedFieldIdFirst_isEmpty() {
    TraceContextBinaryMarshaller.extract(new byte[] {
        0,
        4, 127, -1, -1, -1, -1, -1, -1, -1, -128, 0, 0, 0, 0, 0, 0, 0, // bad field number
        1, -1, -1, -1, -1, -1, -1, -1, -1,
        2, 1
    }, mutableContext);

    assertThat(mutableContext.isEmpty()).isTrue();
  }

  @Test public void extract_unsupportedFieldIdSecond_isEmpty() {
    TraceContextBinaryMarshaller.extract(new byte[] {
        0,
        0, 127, -1, -1, -1, -1, -1, -1, -1, -128, 0, 0, 0, 0, 0, 0, 0,
        4, -1, -1, -1, -1, -1, -1, -1, -1, // bad field number
        2, 1
    }, mutableContext);

    assertThat(mutableContext.isEmpty()).isTrue();
  }

  @Test public void extract_unsupportedFieldIdThird_toSampledNull() {
    TraceContextBinaryMarshaller.extract(new byte[] {
        0,
        0, 127, -1, -1, -1, -1, -1, -1, -1, -128, 0, 0, 0, 0, 0, 0, 0,
        1, -1, -1, -1, -1, -1, -1, -1, -1,
        4, 1 // bad field number
    }, mutableContext);

    assertThat(mutableContext.sampled()).isNull();
  }

  @Test public void extract_64BitTraceId_isEmpty() {
    TraceContextBinaryMarshaller.extract(new byte[] {
        0,
        0, 127, -1, -1, -1, -1, -1, -1, -1, // half a trace ID
        1, -1, -1, -1, -1, -1, -1, -1, -1,
        2, 1
    }, mutableContext);

    assertThat(mutableContext.isEmpty()).isTrue();
  }

  @Test public void extract_32BitSpanId_isEmpty() {
    TraceContextBinaryMarshaller.extract(new byte[] {
        0,
        0, 127, -1, -1, -1, -1, -1, -1, -1, -128, 0, 0, 0, 0, 0, 0, 0,
        1, -1, -1, -1, -1, // half a span ID
        2, 1
    }, mutableContext);

    assertThat(mutableContext.isEmpty()).isTrue();
  }

  @Test public void extract_truncatedTraceOptions_isEmpty() {
    TraceContextBinaryMarshaller.extract(new byte[] {
        0,
        0, 127, -1, -1, -1, -1, -1, -1, -1, -128, 0, 0, 0, 0, 0, 0, 0,
        1, -1, -1, -1, -1, -1, -1, -1, -1,
        2 // has field ID, but missing sampled bit
    }, mutableContext);

    assertThat(mutableContext.sampled()).isNull();
  }

  @Test public void extract_missingTraceOptions() {
    TraceContextBinaryMarshaller.extract(new byte[] {
        0,
        0, 127, -1, -1, -1, -1, -1, -1, -1, -128, 0, 0, 0, 0, 0, 0, 0,
        1, -1, -1, -1, -1, -1, -1, -1, -1,
        // no trace options field
    }, mutableContext);

    assertThat(mutableContext.toTraceContext())
        .isEqualTo(context);
  }
}
