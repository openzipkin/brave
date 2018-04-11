package brave.grpc;

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
  byte[] contextBytes = {
      0, // version
      0, 127, -1, -1, -1, -1, -1, -1, -1, -128, 0, 0, 0, 0, 0, 0, 0, // trace ID
      1, -1, -1, -1, -1, -1, -1, -1, -1, // span ID
      2, 1 // sampled
  };

  TraceContextBinaryMarshaller binaryMarshaller = new TraceContextBinaryMarshaller();

  @Test public void roundtrip() {
    byte[] serialized = binaryMarshaller.toBytes(context);
    assertThat(serialized)
        .containsExactly(contextBytes);

    assertThat(binaryMarshaller.parseBytes(serialized))
        .isEqualTo(context);
  }

  @Test public void roundtrip_unsampled() {
    context = context.toBuilder().sampled(false).build();

    byte[] serialized = binaryMarshaller.toBytes(context);
    contextBytes[contextBytes.length - 1] = 0; // unsampled
    assertThat(serialized)
        .containsExactly(contextBytes);

    assertThat(binaryMarshaller.parseBytes(serialized))
        .isEqualTo(context);
  }

  @Test public void parseBytes_empty_toNull() {
    assertThat(binaryMarshaller.parseBytes(new byte[0]))
        .isNull();
  }

  @Test public void parseBytes_unsupportedVersionId_toNull() {
    assertThat(binaryMarshaller.parseBytes(new byte[] {
        1, // bad version
        0, 127, -1, -1, -1, -1, -1, -1, -1, -128, 0, 0, 0, 0, 0, 0, 0,
        1, -1, -1, -1, -1, -1, -1, -1, -1,
        2, 1
    })).isNull();
  }

  @Test public void parseBytes_unsupportedFieldIdFirst_toNull() {
    assertThat(binaryMarshaller.parseBytes(new byte[] {
        0,
        4, 127, -1, -1, -1, -1, -1, -1, -1, -128, 0, 0, 0, 0, 0, 0, 0, // bad field number
        1, -1, -1, -1, -1, -1, -1, -1, -1,
        2, 1
    })).isNull();
  }

  @Test public void parseBytes_unsupportedFieldIdSecond_toNull() {
    assertThat(binaryMarshaller.parseBytes(new byte[] {
        0,
        0, 127, -1, -1, -1, -1, -1, -1, -1, -128, 0, 0, 0, 0, 0, 0, 0,
        4, -1, -1, -1, -1, -1, -1, -1, -1, // bad field number
        2, 1
    })).isNull();
  }

  @Test public void parseBytes_unsupportedFieldIdThird_toSampledNull() {
    assertThat(binaryMarshaller.parseBytes(new byte[] {
        0,
        0, 127, -1, -1, -1, -1, -1, -1, -1, -128, 0, 0, 0, 0, 0, 0, 0,
        1, -1, -1, -1, -1, -1, -1, -1, -1,
        4, 1 // bad field number
    }).sampled()).isNull();
  }

  @Test public void parseBytes_64BitTraceId_toNull() {
    assertThat(binaryMarshaller.parseBytes(new byte[] {
        0,
        0, 127, -1, -1, -1, -1, -1, -1, -1, // half a trace ID
        1, -1, -1, -1, -1, -1, -1, -1, -1,
        2, 1
    })).isNull();
  }

  @Test public void parseBytes_32BitSpanId_toNull() {
    assertThat(binaryMarshaller.parseBytes(new byte[] {
        0,
        0, 127, -1, -1, -1, -1, -1, -1, -1, -128, 0, 0, 0, 0, 0, 0, 0,
        1, -1, -1, -1, -1, // half a span ID
        2, 1
    })).isNull();
  }

  @Test public void parseBytes_truncatedTraceOptions_toNull() {
    assertThat(binaryMarshaller.parseBytes(new byte[] {
        0,
        0, 127, -1, -1, -1, -1, -1, -1, -1, -128, 0, 0, 0, 0, 0, 0, 0,
        1, -1, -1, -1, -1, -1, -1, -1, -1,
        2 // has field ID, but missing sampled bit
    })).isNull();
  }

  @Test public void parseBytes_missingTraceOptions() {
    assertThat(binaryMarshaller.parseBytes(new byte[] {
        0,
        0, 127, -1, -1, -1, -1, -1, -1, -1, -128, 0, 0, 0, 0, 0, 0, 0,
        1, -1, -1, -1, -1, -1, -1, -1, -1,
        // no trace options field
    })).isEqualTo(context);
  }
}
